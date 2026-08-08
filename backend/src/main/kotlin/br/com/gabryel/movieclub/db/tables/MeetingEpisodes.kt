package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

/** One row per club's meeting assignment of a global [Episodes] row -- multiple clubs can independently schedule
 * the same episode to their own (different) meetings. */
object MeetingEpisodes : UuidTable("meeting_episodes") {
    val meetingId = reference("meeting_id", Meetings)
    val episodeId = reference("episode_id", Episodes)
    val createdAt = timestamp("created_at")

    init {
        uniqueIndex(meetingId, episodeId)
    }
}
