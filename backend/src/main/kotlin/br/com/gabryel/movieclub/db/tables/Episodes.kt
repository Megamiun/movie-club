package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object Episodes : UUIDTable("episodes") {
    val seasonId = reference("season_id", Seasons)
    val number = integer("number")
    val title = varchar("title", 512).nullable()
    val meetingId = optReference("meeting_id", Meetings)
}
