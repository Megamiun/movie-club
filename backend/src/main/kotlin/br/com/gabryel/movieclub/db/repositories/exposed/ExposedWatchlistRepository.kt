package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.db.tables.MediaItems
import br.com.gabryel.movieclub.db.tables.WatchlistEntries
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedWatchlistRepository : WatchlistRepository {
    override fun create(clubId: Uuid, memberId: Uuid, mediaItemId: Uuid, notes: String?): WatchlistEntryRow = transaction {
        val type = MediaItems.selectAll().where { MediaItems.id eq mediaItemId }.map { it[MediaItems.type] }.single()
        val nextPosition = (
            joined()
                .selectAll()
                .where { (WatchlistEntries.clubId eq clubId) and (MediaItems.type eq type) }
                .maxOfOrNull { it[WatchlistEntries.position] } ?: -1
        ) + 1

        val id = WatchlistEntries.insert {
            it[WatchlistEntries.clubId] = clubId
            it[WatchlistEntries.memberId] = memberId
            it[WatchlistEntries.mediaItemId] = mediaItemId
            it[WatchlistEntries.notes] = notes
            it[WatchlistEntries.position] = nextPosition
            it[WatchlistEntries.createdAt] = Clock.System.now()
        }[WatchlistEntries.id].value

        findById(id)!!
    }

    override fun findById(id: Uuid): WatchlistEntryRow? = transaction {
        joined()
            .selectAll()
            .where { WatchlistEntries.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listByClub(clubId: Uuid): List<WatchlistEntryRow> = transaction {
        joined()
            .selectAll()
            .where { WatchlistEntries.clubId eq clubId }
            .map(::toRow)
    }

    override fun update(id: Uuid, notes: String?): WatchlistEntryRow = transaction {
        WatchlistEntries.update({ WatchlistEntries.id eq id }) {
            if (notes != null) it[WatchlistEntries.notes] = notes
        }
        findById(id)!!
    }

    override fun updatePosition(id: Uuid, position: Int): WatchlistEntryRow = transaction {
        WatchlistEntries.update({ WatchlistEntries.id eq id }) {
            it[WatchlistEntries.position] = position
        }
        findById(id)!!
    }

    override fun delete(id: Uuid) {
        transaction {
            WatchlistEntries.deleteWhere { WatchlistEntries.id eq id }
        }
    }

    private fun joined() = WatchlistEntries innerJoin MediaItems

    private fun toRow(row: ResultRow) = WatchlistEntryRow(
        id = row[WatchlistEntries.id].value,
        clubId = row[WatchlistEntries.clubId].value,
        memberId = row[WatchlistEntries.memberId].value,
        mediaItemId = row[WatchlistEntries.mediaItemId].value,
        type = row[MediaItems.type],
        title = row[MediaItems.title],
        imdbId = row[MediaItems.imdbId],
        year = row[MediaItems.year],
        posterUrl = row[MediaItems.posterUrl],
        tmdbRating = row[MediaItems.tmdbRating],
        imdbRating = row[MediaItems.imdbRating],
        notes = row[WatchlistEntries.notes],
        position = row[WatchlistEntries.position],
        createdAt = row[WatchlistEntries.createdAt],
    )
}
