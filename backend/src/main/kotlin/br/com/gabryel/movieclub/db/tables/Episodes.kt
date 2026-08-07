package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

object Episodes : UuidTable("episodes") {
    val seasonId = reference("season_id", Seasons)
    val number = integer("number")
    val title = varchar("title", 512).nullable()
    val meetingId = optReference("meeting_id", Meetings)

    val airDate = date("air_date").nullable()
    val overview = text("overview").nullable()
    val runtimeMinutes = integer("runtime_minutes").nullable()
    val director = varchar("director", 512).nullable()
    val tmdbRating = decimal("tmdb_rating", precision = 4, scale = 1).nullable()
    val metadataFetchedAt = timestamp("metadata_fetched_at").nullable()
}
