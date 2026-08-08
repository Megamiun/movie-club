package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import kotlin.uuid.Uuid

interface EpisodeRepository {
    /** Finds the global episode for ([seasonId], [number]) if any club has already added it, otherwise creates it. */
    fun create(seasonId: Uuid, number: Int, title: String? = null): EpisodeRow

    fun findById(id: Uuid): EpisodeRow?

    fun listBySeason(seasonId: Uuid): List<EpisodeRow>

    fun listByMeeting(meetingId: Uuid): List<EpisodeRow>

    /** Schedules the global episode for [meetingId] -- idempotent, since multiple clubs (or repeated calls) may
     * each want the same episode assigned to their own meeting. */
    fun assignToMeeting(episodeId: Uuid, meetingId: Uuid)

    /** Removes this specific meeting's assignment only -- other clubs' assignments of the same episode, if any,
     * are untouched. */
    fun unassignFromMeeting(episodeId: Uuid, meetingId: Uuid)

    fun updateTmdbMetadata(episodeId: Uuid, metadata: TmdbEpisodeMetadata): EpisodeRow

    fun upsertReview(
        episodeId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): EpisodeReviewRow

    fun findReview(episodeId: Uuid, memberId: Uuid): EpisodeReviewRow?

    fun listReviews(episodeId: Uuid): List<EpisodeReviewRow>
}
