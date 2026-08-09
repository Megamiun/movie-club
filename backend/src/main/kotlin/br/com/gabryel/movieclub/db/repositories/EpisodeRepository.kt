package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeSearchRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import kotlin.uuid.Uuid

interface EpisodeRepository {
    /** Finds the global episode for ([seasonId], [number]) if any club has already added it, otherwise creates it. */
    fun create(seasonId: Uuid, number: Int, title: String? = null): EpisodeRow

    fun findById(id: Uuid): EpisodeRow?

    fun listBySeason(seasonId: Uuid): List<EpisodeRow>

    fun listByMeeting(meetingId: Uuid): List<EpisodeRow>

    /** Matches by episode title or by the parent series' title, scoped to series [clubId] follows -- used to pick
     * an existing episode to assign to a meeting instead of requiring its raw id. */
    fun searchByClub(clubId: Uuid, query: String, limit: Int = 20): List<EpisodeSearchRow>

    /** [clubId]'s own pick's title for the episode's parent series (custom title if set, else the global series'
     * original title) -- null if [clubId] doesn't actually follow that series. Used to group a meeting's episodes
     * by series. */
    fun findSeriesTitle(episodeId: Uuid, clubId: Uuid): String?

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
