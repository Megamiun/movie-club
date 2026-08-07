package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object Series : UuidTable("series") {
    val clubId = reference("club_id", Clubs)
    val chosenById = reference("chosen_by_id", Members)

    val imdbId = varchar("imdb_id", 16)
    val tmdbId = varchar("tmdb_id", 16).nullable()
    val originalTitle = varchar("original_title", 512)
    val englishTitle = varchar("english_title", 512).nullable()
    val customTitle = varchar("custom_title", 512).nullable()
    val displayTitlePreference = enumerationByName<DisplayTitlePreference>("display_title_preference", 16)

    val year = integer("year").nullable()
    val genre = array("genre", VarCharColumnType(255)).nullable()
    val country = array("country", VarCharColumnType(255)).nullable()
    val tmdbRating = decimal("tmdb_rating", precision = 4, scale = 1).nullable()
    val creator = varchar("creator", 512).nullable()

    val posterS3Key = varchar("poster_s3_key", 512).nullable()
    val metadataFetchedAt = timestamp("metadata_fetched_at").nullable()
    val createdAt = timestamp("created_at")
}
