package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.SeriesRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.parseImdbId
import br.com.gabryel.movieclub.service.tmdb.toMetadata
import kotlin.uuid.Uuid

class SeriesService(
    private val seriesRepository: SeriesRepository,
    private val clubService: ClubService,
    private val tmdbClient: TmdbClient,
) {
    suspend fun addSeries(clubId: Uuid, actingMemberId: Uuid, imdbUrlOrId: String): SeriesRow {
        clubService.requireMembership(clubId, actingMemberId)
        val imdbId = parseImdbId(imdbUrlOrId)

        val summary =
            tmdbClient.findTvByImdbId(imdbId) ?: throw BadRequestException("Could not find TMDB metadata for $imdbId")
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

    fun listSeries(clubId: Uuid, actingMemberId: Uuid): List<SeriesRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return seriesRepository.listByClub(clubId)
    }

    fun getSeries(seriesId: Uuid, actingMemberId: Uuid): SeriesRow = requireSeriesAccess(seriesId, actingMemberId)

    fun updateDisplayTitle(
        seriesId: Uuid,
        actingMemberId: Uuid,
        customTitle: String?,
        preference: DisplayTitlePreference,
    ): SeriesRow {
        requireSeriesAccess(seriesId, actingMemberId)
        if (preference == CUSTOM && customTitle.isNullOrBlank())
            throw BadRequestException("customTitle is required when preference is CUSTOM")
        return seriesRepository.updateDisplayTitle(seriesId, customTitle, preference)
    }

    fun rate(
        seriesId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): SeriesReviewRow {
        val series = requireSeriesAccess(seriesId, actingMemberId)
        qualityOptionId?.let { clubService.validateRatingOption(series.clubId, it, QUALITY) }
        sentimentOptionId?.let { clubService.validateRatingOption(series.clubId, it, SENTIMENT) }
        return seriesRepository.upsertReview(seriesId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    fun listReviews(seriesId: Uuid, actingMemberId: Uuid): List<SeriesReviewRow> {
        requireSeriesAccess(seriesId, actingMemberId)
        return seriesRepository.listReviews(seriesId)
    }

    private fun requireSeriesAccess(seriesId: Uuid, actingMemberId: Uuid): SeriesRow {
        val series = seriesRepository.findById(seriesId) ?: throw NotFoundException("Series not found")
        clubService.requireMembership(series.clubId, actingMemberId)
        return series
    }
}
