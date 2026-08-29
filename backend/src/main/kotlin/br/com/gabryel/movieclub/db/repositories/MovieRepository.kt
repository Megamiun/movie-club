package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.repositories.dto.CatalogTitleInfo
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import kotlin.uuid.Uuid

interface MovieRepository {
    fun create(
        meetingId: Uuid,
        chosenById: Uuid,
        imdbId: String,
        metadata: TmdbMovieMetadata,
        mediaItemId: Uuid? = null,
        watchLink: String? = null,
    ): MovieRow

    fun findById(id: Uuid): MovieRow?

    fun findByMeetingAndImdbId(meetingId: Uuid, imdbId: String): MovieRow?

    fun listByMeeting(meetingId: Uuid): List<MovieRow>

    /** Batched form of [listByMeeting] for multiple meetings at once -- each [MovieRow] already carries its own
     * `meetingId`, so the caller groups the flat result itself. Used by `MeetingService` to avoid a query per
     * meeting when listing a club's whole history. */
    fun listByMeetings(meetingIds: List<Uuid>): List<MovieRow>

    fun updateMeeting(movieId: Uuid, newMeetingId: Uuid): MovieRow

    fun updateDisplayTitle(
        movieId: Uuid,
        customTitle: String? = null,
        preference: DisplayTitlePreference,
        displayLanguageCode: String? = null,
    ): MovieRow

    fun updateWatchLink(movieId: Uuid, watchLink: String? = null): MovieRow

    fun updateTmdbMetadata(movieId: Uuid, metadata: TmdbMovieMetadata, mediaItemId: Uuid? = null): MovieRow

    /** Reverse lookup from a MediaItem back to its own catalog row's `originalLanguage`/`translations`, keyed by
     * `Movies.mediaItemId` rather than the catalog row's own id -- used by `WatchlistService` to give a
     * Watchlist entry (which references a MediaItem directly, with no Movie pick of its own) enough to resolve a
     * language-aware display title. Batched since `WatchlistService.listEntries` needs this for every entry at
     * once; entries whose MediaItem has no matching Movie catalog row (e.g. not yet backfilled) are simply absent
     * from the result rather than erroring. */
    fun findCatalogTitleInfoByMediaItemIds(mediaItemIds: List<Uuid>): Map<Uuid, CatalogTitleInfo>

    fun delete(movieId: Uuid)

    fun upsertReview(
        movieId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): MovieReviewRow

    fun findReview(movieId: Uuid, memberId: Uuid): MovieReviewRow?

    /** Sets *only* [qualityOptionId] on this member's review, creating it if it doesn't exist yet -- unlike
     * [upsertReview], the update statement never touches `sentimentOptionId`/`comment` at all, so a concurrent
     * update to either of those can never be clobbered by this call (or vice versa). `null` unambiguously clears
     * the quality rating, since there's no other field it could be mistaken for "leave unchanged". */
    fun updateReviewQuality(movieId: Uuid, memberId: Uuid, qualityOptionId: Uuid?): MovieReviewRow

    /** Same as [updateReviewQuality], for `sentimentOptionId` instead. */
    fun updateReviewSentiment(movieId: Uuid, memberId: Uuid, sentimentOptionId: Uuid?): MovieReviewRow

    fun listReviews(movieId: Uuid): List<MovieReviewRow>

    /** Batched form of [listReviews] for multiple movies at once -- each [MovieReviewRow] already carries its own
     * `movieId`, so the caller groups the flat result itself. */
    fun listReviewsByMovies(movieIds: List<Uuid>): List<MovieReviewRow>

    /** Repoints every review currently using [oldOptionId] (as either its quality or sentiment choice, whichever
     * applies) to [newOptionId] instead -- used when a rating option is deleted, so existing reviews aren't left
     * dangling. */
    fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid)
}
