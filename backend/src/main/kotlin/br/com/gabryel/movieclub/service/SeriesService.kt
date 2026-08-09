package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.DisplayTitlePreference.LANGUAGE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
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
        val metadata = details.toMetadata(tmdbId).copy(imdbRating = imdbRating)
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
        val metadata = details.toMetadata(summary.id).copy(imdbRating = imdbRating)
        val mediaItem = linkMediaItem(details, summary.id, series.imdbId, metadata, imdbRating)

        return seriesRepository.updateTmdbMetadata(seriesId, metadata, mediaItem)
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
     * episodes), never duplicates. Returns the number of Season/Episode rows newly created. */
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

            tmdbClient.getSeasonDetails(tmdbId, seasonSummary.seasonNumber).episodes.forEach { episodeEntry ->
                val existingEpisode = episodeRepository.listBySeason(season.id)
                    .find { it.number == episodeEntry.episodeNumber }
                if (existingEpisode == null) {
                    val inserted = episodeRepository.create(season.id, episodeEntry.episodeNumber, episodeEntry.name)
                    episodeRepository.updateTmdbMetadata(inserted.id, episodeEntry.toMetadata())
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
