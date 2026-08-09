package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.SeasonReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import kotlin.uuid.Uuid

interface SeasonRepository {
    /** Finds the global season for ([seriesId], [number]) if any club has already added it, otherwise creates it. */
    fun create(seriesId: Uuid, number: Int, title: String? = null): SeasonRow

    fun findById(id: Uuid): SeasonRow?

    fun listBySeries(seriesId: Uuid): List<SeasonRow>

    fun upsertReview(
        seasonId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): SeasonReviewRow

    fun findReview(seasonId: Uuid, memberId: Uuid): SeasonReviewRow?

    fun listReviews(seasonId: Uuid): List<SeasonReviewRow>

    /** Repoints every review currently using [oldOptionId] (as either its quality or sentiment choice, whichever
     * applies) to [newOptionId] instead -- used when a rating option is deleted, so existing reviews aren't left
     * dangling. */
    fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid)
}
