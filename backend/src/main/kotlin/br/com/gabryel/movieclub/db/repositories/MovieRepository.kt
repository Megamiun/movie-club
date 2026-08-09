package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.DisplayTitlePreference
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

    fun updateMeeting(movieId: Uuid, newMeetingId: Uuid): MovieRow

    fun updateDisplayTitle(
        movieId: Uuid,
        customTitle: String? = null,
        preference: DisplayTitlePreference,
        displayLanguageCode: String? = null,
    ): MovieRow

    fun updateWatchLink(movieId: Uuid, watchLink: String? = null): MovieRow

    fun updateTmdbMetadata(movieId: Uuid, metadata: TmdbMovieMetadata, mediaItemId: Uuid? = null): MovieRow

    fun delete(movieId: Uuid)

    fun upsertReview(
        movieId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): MovieReviewRow

    fun findReview(movieId: Uuid, memberId: Uuid): MovieReviewRow?

    fun listReviews(movieId: Uuid): List<MovieReviewRow>

    /** Repoints every review currently using [oldOptionId] (as either its quality or sentiment choice, whichever
     * applies) to [newOptionId] instead -- used when a rating option is deleted, so existing reviews aren't left
     * dangling. */
    fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid)
}
