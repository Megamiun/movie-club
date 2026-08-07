package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object WatchlistEntries : UuidTable("watchlist_entries") {
    val clubId = reference("club_id", Clubs)
    val memberId = reference("member_id", Members)
    val title = varchar("title", 512)
    val imdbUrl = varchar("imdb_url", 1024).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
}
