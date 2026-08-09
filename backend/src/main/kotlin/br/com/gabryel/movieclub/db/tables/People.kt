package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

/** A director/creator credited on a Movie/Episode/Series, deduplicated by [tmdbId] (always known once TMDB credits
 * are fetched) or, failing that, [imdbId] (only resolved via a second best-effort per-person lookup -- see
 * [br.com.gabryel.movieclub.db.repositories.PersonRepository.findOrCreate]). Both are nullable and unique -- a
 * Postgres unique index treats multiple `NULL`s as distinct, so rows with neither id yet resolved don't collide. */
object People : UuidTable("people") {
    val name = varchar("name", 512)
    val imdbId = varchar("imdb_id", 16).uniqueIndex().nullable()
    val tmdbId = varchar("tmdb_id", 16).uniqueIndex().nullable()
    val createdAt = timestamp("created_at")
}
