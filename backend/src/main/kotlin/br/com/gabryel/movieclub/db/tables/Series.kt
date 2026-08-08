package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

/** Global series catalog, deduplicated by [imdbId] -- every club that picks the same real series shares this row. */
object Series : UuidTable("series") {
    val imdbId = varchar("imdb_id", 16).uniqueIndex()
    val tmdbId = varchar("tmdb_id", 16).nullable()
    val originalTitle = varchar("original_title", 512)
    val alternativeTitles = jsonb<List<AlternativeTitle>>("alternative_titles", Json.Default)

    val year = integer("year").nullable()
    val genre = array("genre", VarCharColumnType(255)).nullable()
    val originCountry = array("origin_country", VarCharColumnType(255)).nullable()
    val productionCountries = array("production_countries", VarCharColumnType(255)).nullable()
    val tmdbRating = decimal("tmdb_rating", precision = 4, scale = 1).nullable()
    val imdbRating = decimal("imdb_rating", precision = 4, scale = 1).nullable()
    val creator = varchar("creator", 512).nullable()

    val posterS3Key = varchar("poster_s3_key", 512).nullable()
    val metadataFetchedAt = timestamp("metadata_fetched_at").nullable()
    val createdAt = timestamp("created_at")
}
