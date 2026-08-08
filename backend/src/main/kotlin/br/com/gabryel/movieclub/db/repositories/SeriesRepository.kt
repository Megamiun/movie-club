package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import kotlin.uuid.Uuid

interface SeriesRepository {
    fun create(clubId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbSeriesMetadata, mediaItemId: Uuid? = null): SeriesRow

    fun findById(id: Uuid): SeriesRow?

    fun listByClub(clubId: Uuid): List<SeriesRow>

    fun findByClubAndImdbId(clubId: Uuid, imdbId: String): SeriesRow?

    /** Given the id of a *global* series, finds the acting member's own club's pick of it (if any) -- used to
     * authorize and resolve rating-scale context for Season/Episode, which no longer have one club of their own
     * to check directly since they're shared across every club following the series. */
    fun findClubSeriesForMember(seriesId: Uuid, memberId: Uuid): SeriesRow?

    fun updateDisplayTitle(seriesId: Uuid, customTitle: String? = null, preference: DisplayTitlePreference): SeriesRow

    fun updateTmdbMetadata(seriesId: Uuid, metadata: TmdbSeriesMetadata, mediaItemId: Uuid? = null): SeriesRow

    fun upsertReview(
        seriesId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): SeriesReviewRow

    fun findReview(seriesId: Uuid, memberId: Uuid): SeriesReviewRow?

    fun listReviews(seriesId: Uuid): List<SeriesReviewRow>
}
