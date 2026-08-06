package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.kotlin.datetime.timestamp

object Movies : UUIDTable("movies") {
    val meetingId = reference("meeting_id", Meetings)
    val chosenById = reference("chosen_by_id", Members)

    val imdbId = varchar("imdb_id", 16)
    val tmdbId = varchar("tmdb_id", 16).nullable()
    val originalTitle = varchar("original_title", 512)
    val englishTitle = varchar("english_title", 512).nullable()
    val customTitle = varchar("custom_title", 512).nullable()
    val displayTitlePreference = enumerationByName<DisplayTitlePreference>("display_title_preference", 16)

    val year = integer("year").nullable()
    val director = varchar("director", 512).nullable()
    val runtimeMinutes = integer("runtime_minutes").nullable()
    val genre = varchar("genre", 512).nullable()
    val country = varchar("country", 512).nullable()
    val tmdbRating = decimal("tmdb_rating", precision = 4, scale = 1).nullable()

    val posterS3Key = varchar("poster_s3_key", 512).nullable()
    val watchLink = varchar("watch_link", 2048).nullable()
    val metadataFetchedAt = timestamp("metadata_fetched_at").nullable()
    val createdAt = timestamp("created_at")
}
