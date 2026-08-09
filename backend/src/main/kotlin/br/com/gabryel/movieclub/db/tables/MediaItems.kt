package br.com.gabryel.movieclub.db.tables

import br.com.gabryel.movieclub.db.MediaItemType
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

/** Universal handle for anything sourced from TMDB, deduplicated by [imdbId] -- created only by a successful TMDB
 * lookup, never from freeform input. Movie and Series catalog rows each carry a [br.com.gabryel.movieclub.db.tables.Movies.mediaItemId] /
 * [br.com.gabryel.movieclub.db.tables.Series.mediaItemId] pointing here, kept in sync alongside their own richer
 * columns; this table isn't a replacement for those, just a shared cross-type reference point (e.g. for Watchlist). */
object MediaItems : UuidTable("media_items") {
    val type = enumerationByName<MediaItemType>("type", 16)
    val imdbId = varchar("imdb_id", 16).uniqueIndex()
    val tmdbId = varchar("tmdb_id", 16).nullable()
    val title = varchar("title", 512)
    val year = integer("year").nullable()
    val posterUrl = varchar("poster_url", 1024).nullable()
    val imdbRating = decimal("imdb_rating", precision = 4, scale = 1).nullable()
    val createdAt = timestamp("created_at")
}
