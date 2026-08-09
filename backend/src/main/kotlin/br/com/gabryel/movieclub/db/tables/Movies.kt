package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.repositories.dto.Translation
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

/** Global movie catalog, deduplicated by [imdbId] -- every club that picks the same real movie shares this row. */
object Movies : UuidTable("movies") {
    val imdbId = varchar("imdb_id", 16).uniqueIndex()
    val tmdbId = varchar("tmdb_id", 16).nullable()
    val originalTitle = varchar("original_title", 512)
    val originalLanguage = varchar("original_language", 8).nullable()
    val translations = jsonb<List<Translation>>("translations", Json.Default)

    val year = integer("year").nullable()
    val directorPersonId = reference("director_person_id", People).nullable()
    val runtimeMinutes = integer("runtime_minutes").nullable()
    val genre = array("genre", VarCharColumnType(255)).nullable()
    val originCountry = array("origin_country", VarCharColumnType(255)).nullable()
    val productionCountries = array("production_countries", VarCharColumnType(255)).nullable()
    val imdbRating = decimal("imdb_rating", precision = 4, scale = 1).nullable()

    val posterS3Key = varchar("poster_s3_key", 512).nullable()
    val metadataFetchedAt = timestamp("metadata_fetched_at").nullable()
    val mediaItemId = reference("media_item_id", MediaItems).nullable()
    val createdAt = timestamp("created_at")
}
