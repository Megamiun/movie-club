package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/** Global episode catalog, deduplicated by ([seasonId], [number]) -- shared by every club following that series.
 * Meeting assignment lives in [MeetingEpisodes], not here, since multiple clubs can each schedule the same
 * episode to their own meeting. */
object Episodes : UuidTable("episodes") {
    val seasonId = reference("season_id", Seasons)
    val number = integer("number")
    val title = varchar("title", 512).nullable()

    val airDate = date("air_date").nullable()
    val overview = text("overview").nullable()
    val runtimeMinutes = integer("runtime_minutes").nullable()
    val directorPersonId = reference("director_person_id", People).nullable()
    val imdbId = varchar("imdb_id", 16).nullable()
    val imdbRating = decimal("imdb_rating", precision = 4, scale = 1).nullable()
    val metadataFetchedAt = timestamp("metadata_fetched_at").nullable()

    init {
        uniqueIndex(seasonId, number)
    }
}
