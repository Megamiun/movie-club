package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.DisplayTitlePreference.LANGUAGE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.PersonRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.omdb.OmdbSeasonEpisode
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbEpisodeDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbTvDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbTvSearchItem
import br.com.gabryel.movieclub.service.tmdb.parseImdbId
import br.com.gabryel.movieclub.service.tmdb.toTmdbPosterUrl
import java.math.BigDecimal
import kotlin.uuid.Uuid

class SeriesService(
    private val seriesRepository: SeriesRepository,
    private val clubService: ClubService,
    private val mediaItemRepository: MediaItemRepository,
    private val tmdbClient: TmdbClient,
    private val omdbClient: OmdbClient,
    private val seasonRepository: SeasonRepository,
    private val episodeRepository: EpisodeRepository,
    private val personRepository: PersonRepository,
) {
    suspend fun searchSeries(query: String): List<TmdbTvSearchItem> {
        if (query.isBlank()) return emptyList()
        return tmdbClient.searchTv(query.trim())
    }

    suspend fun addSeries(clubId: Uuid, actingMemberId: Uuid, imdbUrlOrId: String): SeriesRow {
        clubService.requireMembership(clubId, actingMemberId)
        val imdbId = parseImdbId(imdbUrlOrId)
        val summary = tmdbClient.findTvByImdbId(imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for $imdbId")

        return createFromTmdb(clubId, actingMemberId, summary.id)
    }

    suspend fun addSeriesByTmdbId(clubId: Uuid, actingMemberId: Uuid, tmdbId: Int): SeriesRow {
        clubService.requireMembership(clubId, actingMemberId)
        return createFromTmdb(clubId, actingMemberId, tmdbId)
    }

    /** Populates the full Season/Episode catalog right away (see [importSeasonsAndEpisodes]) so a newly added
     * series shows up with all its seasons/episodes instead of the empty list the old manual-add-per-season flow
     * left behind. Best-effort like [EpisodeService.addEpisode]'s metadata enrichment -- a TMDB hiccup here
     * shouldn't fail the add itself, since [importSeasonsAndEpisodes] is safe to re-run later (e.g. from the
     * series page) to pick up whatever didn't come through. */
    private suspend fun createFromTmdb(clubId: Uuid, actingMemberId: Uuid, tmdbId: Int): SeriesRow {
        val details = tmdbClient.getTvDetails(tmdbId)
        val imdbId = details.externalIds?.imdbId
            ?: throw BadRequestException("TMDB series $tmdbId has no linked IMDB id")

        if (seriesRepository.findByClubAndImdbId(clubId, imdbId) != null)
            throw BadRequestException("This series has already been added to this club")

        val imdbRating = omdbClient.getImdbRating(imdbId)
        val metadata = details.toMetadata(tmdbId).copy(imdbRating = imdbRating, creatorPersonId = resolveCreatorPersonId(details))
        val mediaItem = linkMediaItem(details, tmdbId, imdbId, metadata, imdbRating)
        val series = seriesRepository.create(clubId, actingMemberId, imdbId, metadata, mediaItem)
        runCatching { importSeasonsAndEpisodes(series.id, actingMemberId) }
        return series
    }

    suspend fun refreshMetadata(seriesId: Uuid, actingMemberId: Uuid): SeriesRow {
        val series = requireSeriesAccess(seriesId, actingMemberId)
        val summary = tmdbClient.findTvByImdbId(series.imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for ${series.imdbId}")

        val details = tmdbClient.getTvDetails(summary.id)
        val imdbRating = omdbClient.getImdbRating(series.imdbId)
        val metadata = details.toMetadata(summary.id).copy(imdbRating = imdbRating, creatorPersonId = resolveCreatorPersonId(details))
        val mediaItem = linkMediaItem(details, summary.id, series.imdbId, metadata, imdbRating)

        return seriesRepository.updateTmdbMetadata(seriesId, metadata, mediaItem)
    }

    /** Unlike [MovieService.resolveDirectorPersonId]/[EpisodeService.resolveDirectorPersonId], no IMDB id lookup
     * here -- nothing yet reads a creator's IMDB id, so the extra best-effort [TmdbClient.getPersonExternalIds]
     * round trip isn't worth making on every refresh. Deduping on the TMDB person id alone (see
     * [TmdbTvDetails.creatorTmdbId]) still avoids a duplicate [br.com.gabryel.movieclub.db.repositories.dto.PersonRow]
     * per refresh; a later pass can add the IMDB lookup the same way director resolution already does. */
    private fun resolveCreatorPersonId(details: TmdbTvDetails): Uuid? {
        val creatorName = details.creator ?: return null
        return personRepository.findOrCreate(creatorName, details.creatorTmdbId?.toString()).id
    }

    private fun linkMediaItem(
        details: TmdbTvDetails,
        tmdbId: Int,
        imdbId: String,
        metadata: TmdbSeriesMetadata,
        imdbRating: BigDecimal?,
    ): Uuid = mediaItemRepository.findOrCreate(
        type = SERIES,
        imdbId = imdbId,
        title = details.originalName,
        tmdbId = tmdbId.toString(),
        year = details.year,
        posterUrl = details.posterPath?.toTmdbPosterUrl(),
        imdbRating = imdbRating,
    ).id

    /** Bulk-imports every season/episode TMDB knows about for this series -- unlike [refreshMetadata] (which only
     * touches the series' own top-level fields), this populates the full Season/Episode catalog, including
     * episodes the club never watched. Idempotent: re-running only inserts what's missing (e.g. newly released
     * episodes), never duplicates -- deliberately including a mis-numbered episode already sitting in the DB from
     * before OMDb-sourced numbering existed: this never renumbers an existing row (a stale number needs a manual
     * fix), but it also never creates a second row for the same story under the corrected number either, since the
     * existing row's own title is checked against every candidate before creating one -- see the by-title fallback
     * below. Returns the number of Season/Episode rows newly created. */
    suspend fun importSeasonsAndEpisodes(seriesId: Uuid, actingMemberId: Uuid): Int {
        val series = requireSeriesAccess(seriesId, actingMemberId)
        val tmdbId = series.tmdbId?.toIntOrNull()
            ?: throw BadRequestException("Series has not been matched to TMDB yet")

        var created = 0
        tmdbClient.getTvDetails(tmdbId).seasons.forEach { seasonSummary ->
            val existingSeason = seasonRepository.listBySeries(series.globalSeriesId)
                .find { it.number == seasonSummary.seasonNumber }
            val season = existingSeason
                ?: seasonRepository.create(series.globalSeriesId, seasonSummary.seasonNumber).also { created++ }

            val tmdbEpisodes = tmdbClient.getSeasonDetails(tmdbId, seasonSummary.seasonNumber).episodes
            // OMDb (IMDB) is the numbering source of truth here -- some shows' TMDB entries number episodes by
            // original broadcast order rather than the internationally-known release order (e.g. Cowboy Bebop),
            // which OMDb doesn't share. Matched by title (see matchOmdbEpisodes); a TMDB episode with no confident
            // OMDb match, or no OMDb data for this season at all, falls back to TMDB's own number unchanged.
            val omdbEpisodes = omdbClient.getSeasonEpisodes(series.imdbId, seasonSummary.seasonNumber).orEmpty()
            val omdbMatches = matchOmdbEpisodes(tmdbEpisodes, omdbEpisodes)

            tmdbEpisodes.forEach { episodeEntry ->
                val omdbMatch = omdbMatches[episodeEntry.episodeNumber]
                val number = omdbMatch?.number ?: episodeEntry.episodeNumber

                val episodesInSeason = episodeRepository.listBySeason(season.id)
                // Falls back to a title match (not just the corrected number) so a legacy row already sitting
                // under TMDB's old, wrong number -- from before OMDb-sourced numbering existed -- doesn't get a
                // second, correctly-numbered row created alongside it once this fix corrects the number going
                // forward. The stale row itself is still never touched/renumbered (see the doc comment above).
                val existingEpisode = episodesInSeason.find { it.number == number }
                    ?: episodesInSeason.find { existing ->
                        existing.title != null && episodeTitleSimilarity(existing.title, episodeEntry.name) >= TITLE_MATCH_THRESHOLD
                    }
                if (existingEpisode == null) {
                    val inserted = episodeRepository.create(season.id, number, episodeEntry.name)
                    val directorPersonId = episodeEntry.director?.let {
                        personRepository.findOrCreate(it, episodeEntry.directorTmdbId?.toString()).id
                    }
                    val metadata = episodeEntry.toMetadata().copy(
                        directorPersonId = directorPersonId,
                        imdbId = omdbMatch?.imdbId,
                        imdbRating = omdbMatch?.imdbRating?.toBigDecimalOrNull(),
                    )
                    episodeRepository.updateTmdbMetadata(inserted.id, metadata)
                    created++
                }
            }
        }
        return created
    }

    fun listSeries(clubId: Uuid, actingMemberId: Uuid): List<SeriesRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return seriesRepository.listByClub(clubId)
    }

    fun getSeries(seriesId: Uuid, actingMemberId: Uuid): SeriesRow = requireSeriesAccess(seriesId, actingMemberId)

    fun updateDisplayTitle(
        seriesId: Uuid,
        actingMemberId: Uuid,
        customTitle: String? = null,
        preference: DisplayTitlePreference,
        languageCode: String? = null,
    ): SeriesRow {
        requireSeriesAccess(seriesId, actingMemberId)
        if (preference == CUSTOM && customTitle.isNullOrBlank())
            throw BadRequestException("customTitle is required when preference is CUSTOM")
        if (preference == LANGUAGE && languageCode.isNullOrBlank())
            throw BadRequestException("languageCode is required when preference is LANGUAGE")

        return seriesRepository.updateDisplayTitle(seriesId, customTitle, preference, languageCode)
    }

    /** Reviews are keyed by the *global* series id (one review per member per series, shared across whichever
     * club they watched it through), not the club-scoped [seriesId] -- see [SeriesRow.globalSeriesId]. */
    fun rate(
        seriesId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): SeriesReviewRow {
        val series = requireSeriesAccess(seriesId, actingMemberId)
        if (qualityOptionId != null) clubService.validateRatingOption(series.clubId, qualityOptionId, QUALITY)
        if (sentimentOptionId != null) clubService.validateRatingOption(series.clubId, sentimentOptionId, SENTIMENT)
        return seriesRepository.upsertReview(series.globalSeriesId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    fun listReviews(seriesId: Uuid, actingMemberId: Uuid): List<SeriesReviewRow> {
        val series = requireSeriesAccess(seriesId, actingMemberId)
        return seriesRepository.listReviews(series.globalSeriesId)
    }

    private fun requireSeriesAccess(seriesId: Uuid, actingMemberId: Uuid): SeriesRow {
        val series = seriesRepository.findById(seriesId)
            ?: throw NotFoundException("Series not found")
        clubService.requireMembership(series.clubId, actingMemberId)
        return series
    }
}

/** Below [TITLE_MATCH_THRESHOLD], two titles are treated as unrelated rather than the same episode described
 * slightly differently -- picked so small formatting drift (e.g. TMDB's "Jupiter Jazz (1)" vs OMDb's "Jupiter
 * Jazz: Part 1", still 0.75 -- see [episodeTitleSimilarity]) matches, while a two-parter's other half (0.4) or a
 * genuinely different episode doesn't. */
private const val TITLE_MATCH_THRESHOLD = 0.5

/** Token-set (Jaccard) similarity in `[0,1]` between two episode titles, insensitive to case/punctuation. Chosen
 * over a character-level distance (e.g. Levenshtein) because the drift actually observed between TMDB and OMDb
 * titles for the same episode is whole inserted/reworded words ("(1)" vs ": Part 1"), which a token-set overlap
 * tolerates naturally without needing a hand-tuned stopword list. */
fun episodeTitleSimilarity(a: String, b: String): Double {
    val left = normalizeEpisodeTitle(a)
    val right = normalizeEpisodeTitle(b)
    if (left.isEmpty() || right.isEmpty()) return 0.0
    return left.intersect(right).size.toDouble() / left.union(right).size
}

private fun normalizeEpisodeTitle(title: String): Set<String> =
    title.lowercase().split(Regex("[^a-z0-9]+")).filterNot { it.isBlank() }.toSet()

/** Pairs each TMDB season episode with the OMDb episode describing the same story, by title -- used to recover
 * OMDb's canonical episode number (and `imdb_id`) for shows where TMDB's own `episode_number` doesn't match it
 * (see [SeriesService.importSeasonsAndEpisodes]). Greedy highest-similarity-first assignment so no OMDb episode is
 * claimed by two different TMDB episodes (relevant for two-parters, whose titles are otherwise near-identical --
 * see the "never reuses" test). A TMDB episode whose best available match falls below [TITLE_MATCH_THRESHOLD] is
 * omitted from the result entirely, not force-matched to whatever scored highest -- callers fall back to TMDB's
 * own number for it instead. Keyed by TMDB's own `episodeNumber`, which is unique within one season by construction
 * (that's also the DB's own uniqueness key for an episode within a season). */
fun matchOmdbEpisodes(
    tmdbEpisodes: List<TmdbEpisodeDetails>,
    omdbEpisodes: List<OmdbSeasonEpisode>,
): Map<Int, OmdbSeasonEpisode> {
    val candidates = tmdbEpisodes
        .flatMap { tmdb -> omdbEpisodes.map { omdb -> Triple(tmdb, omdb, episodeTitleSimilarity(tmdb.name, omdb.title)) } }
        .filter { (_, omdb, score) -> score >= TITLE_MATCH_THRESHOLD && omdb.number != null }
        .sortedByDescending { it.third }

    val matches = mutableMapOf<Int, OmdbSeasonEpisode>()
    val usedOmdbIds = mutableSetOf<String>()
    for ((tmdb, omdb, _) in candidates) {
        if (tmdb.episodeNumber in matches) continue
        if (omdb.imdbId in usedOmdbIds) continue
        matches[tmdb.episodeNumber] = omdb
        usedOmdbIds += omdb.imdbId
    }
    return matches
}
