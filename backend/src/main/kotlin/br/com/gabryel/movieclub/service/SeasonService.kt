package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeasonReviewRow
import br.com.gabryel.movieclub.db.repositories.SeasonRow
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlin.uuid.Uuid

class SeasonService(
    private val seasonRepository: SeasonRepository,
    private val seriesRepository: SeriesRepository,
    private val clubService: ClubService,
) {
    fun addSeason(seriesId: Uuid, actingMemberId: Uuid, number: Int, title: String? = null): SeasonRow {
        requireSeriesAccess(seriesId, actingMemberId)
        return seasonRepository.create(seriesId, number, title)
    }

    fun listSeasons(seriesId: Uuid, actingMemberId: Uuid): List<SeasonRow> {
        requireSeriesAccess(seriesId, actingMemberId)
        return seasonRepository.listBySeries(seriesId)
    }

    fun rate(
        seasonId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): SeasonReviewRow {
        val season = seasonRepository.findById(seasonId) ?: throw NotFoundException("Season not found")
        val series = requireSeriesAccess(season.seriesId, actingMemberId)
        qualityOptionId?.let { clubService.validateRatingOption(series.clubId, it, QUALITY) }
        sentimentOptionId?.let { clubService.validateRatingOption(series.clubId, it, SENTIMENT) }
        return seasonRepository.upsertReview(seasonId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    private fun requireSeriesAccess(seriesId: Uuid, actingMemberId: Uuid) =
        (seriesRepository.findById(seriesId) ?: throw NotFoundException("Series not found"))
            .also { clubService.requireMembership(it.clubId, actingMemberId) }
}
