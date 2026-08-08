package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.parseImdbId
import kotlin.uuid.Uuid

class SeriesService(
    private val seriesRepository: SeriesRepository,
    private val clubService: ClubService,
    private val tmdbClient: TmdbClient,
    private val seasonRepository: SeasonRepository,
    private val episodeRepository: EpisodeRepository,
) {
    suspend fun addSeries(clubId: Uuid, actingMemberId: Uuid, imdbUrlOrId: String): SeriesRow {
        clubService.requireMembership(clubId, actingMemberId)
        val imdbId = parseImdbId(imdbUrlOrId)
        val summary = tmdbClient.findTvByImdbId(imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for $imdbId")

        val metadata = tmdbClient.getTvDetails(summary.id).toMetadata(summary.id)

        return seriesRepository.create(clubId, actingMemberId, imdbId, metadata)
    }

    suspend fun refreshMetadata(seriesId: Uuid, actingMemberId: Uuid): SeriesRow {
        val series = requireSeriesAccess(seriesId, actingMemberId)
        val summary = tmdbClient.findTvByImdbId(series.imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for ${series.imdbId}")

        val metadata = tmdbClient.getTvDetails(summary.id).toMetadata(summary.id)

        return seriesRepository.updateTmdbMetadata(seriesId, metadata)
    }

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
    ): SeriesRow {
        requireSeriesAccess(seriesId, actingMemberId)
        if (preference == CUSTOM && customTitle.isNullOrBlank())
            throw BadRequestException("customTitle is required when preference is CUSTOM")

        return seriesRepository.updateDisplayTitle(seriesId, customTitle, preference)
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
