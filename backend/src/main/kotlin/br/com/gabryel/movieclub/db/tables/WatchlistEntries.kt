package br.com.gabryel.movieclub.db.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object WatchlistEntries : UuidTable("watchlist_entries") {
    val clubId = reference("club_id", Clubs)
    val memberId = reference("member_id", Members)
    val mediaItemId = reference("media_item_id", MediaItems)
    val notes = text("notes").nullable()
    val position = integer("position")
    val createdAt = timestamp("created_at")
}
