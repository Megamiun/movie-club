package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.SeasonReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlin.uuid.Uuid

class SeasonService(
    private val seasonRepository: SeasonRepository,
    private val seriesRepository: SeriesRepository,
    private val clubService: ClubService,
) {
    /** [seriesId] here is the club-scoped pick id (same one [SeriesService] uses), since adding/listing seasons
     * is an operation on "my club's series" -- it's resolved to the underlying global series id before touching
     * [seasonRepository], since Season is shared across every club following the series. */
    fun addSeason(seriesId: Uuid, actingMemberId: Uuid, number: Int, title: String? = null): SeasonRow {
        val series = requireClubSeriesAccess(seriesId, actingMemberId)
        return seasonRepository.create(series.globalSeriesId, number, title)
    }

    fun listSeasons(seriesId: Uuid, actingMemberId: Uuid): List<SeasonRow> {
        val series = requireClubSeriesAccess(seriesId, actingMemberId)
        return seasonRepository.listBySeries(series.globalSeriesId)
    }

    /** [seasonId] here is the *global* season id, same as [rate] -- access is derived the same way, by finding the
     * acting member's own club's pick of the season's parent series. */
    fun getById(seasonId: Uuid, actingMemberId: Uuid): SeasonRow {
        val season = seasonRepository.findById(seasonId) ?: throw NotFoundException("Season not found")
        requireClubSeriesForMember(season.seriesId, actingMemberId)
        return season
    }

    /** [seasonId] here is the *global* season id (Season has no per-club fields, so routes address it directly) --
     * access is derived by finding the acting member's own club's pick of the season's parent series. */
    fun rate(
        seasonId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): SeasonReviewRow {
        val season = seasonRepository.findById(seasonId) ?: throw NotFoundException("Season not found")
        val series = requireClubSeriesForMember(season.seriesId, actingMemberId)

        if (qualityOptionId != null)
            clubService.validateRatingOption(series.clubId, qualityOptionId, QUALITY)

        if (sentimentOptionId != null)
            clubService.validateRatingOption(series.clubId, sentimentOptionId, SENTIMENT)

        return seasonRepository.upsertReview(seasonId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    private fun requireClubSeriesAccess(seriesId: Uuid, actingMemberId: Uuid) =
        (seriesRepository.findById(seriesId) ?: throw NotFoundException("Series not found"))
            .also { clubService.requireMembership(it.clubId, actingMemberId) }

    private fun requireClubSeriesForMember(globalSeriesId: Uuid, actingMemberId: Uuid): SeriesRow =
        seriesRepository.findClubSeriesForMember(globalSeriesId, actingMemberId)
            ?: throw ForbiddenException("Not a member of a club following this series")
}
