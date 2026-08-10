package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.db.tables.MediaItems
import br.com.gabryel.movieclub.db.tables.WatchlistEntries
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedWatchlistRepository : WatchlistRepository {
    override fun create(clubId: Uuid, memberId: Uuid, mediaItemId: Uuid): WatchlistEntryRow = transaction {
        val type = MediaItems.selectAll().where { MediaItems.id eq mediaItemId }.map { it[MediaItems.type] }.single()
        // Scoped to this member's own entries of this type, not every member's -- the UI shows one column per
        // member (see WatchlistPage), each with its own independently ordered list.
        val nextPosition = (
            joined().selectAll().where {
                (WatchlistEntries.clubId eq clubId) and
                    (WatchlistEntries.memberId eq memberId) and
                    (MediaItems.type eq type)
            }.maxOfOrNull { it[WatchlistEntries.position] } ?: -1
        ) + 1

        val id = WatchlistEntries.insert {
            it[WatchlistEntries.clubId] = clubId
            it[WatchlistEntries.memberId] = memberId
            it[WatchlistEntries.mediaItemId] = mediaItemId
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

    override fun findByClubMemberAndMediaItem(clubId: Uuid, memberId: Uuid, mediaItemId: Uuid): WatchlistEntryRow? =
        transaction {
            joined()
                .selectAll()
                .where {
                    (WatchlistEntries.clubId eq clubId) and
                        (WatchlistEntries.memberId eq memberId) and
                        (WatchlistEntries.mediaItemId eq mediaItemId)
                }
                .map(::toRow)
                .singleOrNull()
        }

    override fun listByClub(clubId: Uuid): List<WatchlistEntryRow> = transaction {
        joined()
            .selectAll()
            .where { WatchlistEntries.clubId eq clubId }
            .map(::toRow)
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

    override fun existsByClubAndMediaItemImdbId(clubId: Uuid, imdbId: String): Boolean = transaction {
        joined()
            .selectAll()
            .where { (WatchlistEntries.clubId eq clubId) and (MediaItems.imdbId eq imdbId) }
            .limit(1)
            .any()
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
        imdbRating = row[MediaItems.imdbRating],
        position = row[WatchlistEntries.position],
        createdAt = row[WatchlistEntries.createdAt],
    )
}
