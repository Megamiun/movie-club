package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.kotlin.datetime.timestamp

object WatchlistEntries : UUIDTable("watchlist_entries") {
    val clubId = reference("club_id", Clubs)
    val memberId = reference("member_id", Members)
    val title = varchar("title", 512)
    val imdbUrl = varchar("imdb_url", 1024).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
}
