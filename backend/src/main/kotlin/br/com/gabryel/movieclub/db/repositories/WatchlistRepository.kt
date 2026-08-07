package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.tables.WatchlistEntries
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class WatchlistEntryRow(
    val id: Uuid,
    val clubId: Uuid,
    val memberId: Uuid,
    val title: String,
    val imdbUrl: String?,
    val notes: String?,
    val createdAt: Instant,
)

interface WatchlistRepository {
    fun create(
        clubId: Uuid,
        memberId: Uuid,
        title: String,
        imdbUrl: String? = null,
        notes: String? = null,
    ): WatchlistEntryRow

    fun findById(id: Uuid): WatchlistEntryRow?

    fun listByClub(clubId: Uuid): List<WatchlistEntryRow>

    fun update(id: Uuid, title: String?, imdbUrl: String?, notes: String?): WatchlistEntryRow

    fun delete(id: Uuid)
}

class ExposedWatchlistRepository : WatchlistRepository {
    override fun create(
        clubId: Uuid,
        memberId: Uuid,
        title: String,
        imdbUrl: String?,
        notes: String?,
    ): WatchlistEntryRow =
        transaction {
            val result = WatchlistEntries.insert {
                it[WatchlistEntries.clubId] = clubId
                it[WatchlistEntries.memberId] = memberId
                it[WatchlistEntries.title] = title
                it[WatchlistEntries.imdbUrl] = imdbUrl
                it[WatchlistEntries.notes] = notes
                it[WatchlistEntries.createdAt] = Clock.System.now()
            }
            toRow(result.resultedValues!!.single())
        }

    override fun findById(id: Uuid): WatchlistEntryRow? =
        transaction {
            WatchlistEntries
                .selectAll()
                .where { WatchlistEntries.id eq id }
                .map(::toRow)
                .singleOrNull()
        }

    override fun listByClub(clubId: Uuid): List<WatchlistEntryRow> =
        transaction {
            WatchlistEntries
                .selectAll()
                .where { WatchlistEntries.clubId eq clubId }
                .map(::toRow)
        }

    override fun update(id: Uuid, title: String?, imdbUrl: String?, notes: String?): WatchlistEntryRow =
        transaction {
            WatchlistEntries.update({ WatchlistEntries.id eq id }) {
                if (title != null) it[WatchlistEntries.title] = title
                if (imdbUrl != null) it[WatchlistEntries.imdbUrl] = imdbUrl
                if (notes != null) it[WatchlistEntries.notes] = notes
            }
            findById(id)!!
        }

    override fun delete(id: Uuid) {
        transaction {
            WatchlistEntries.deleteWhere { WatchlistEntries.id eq id }
        }
    }

    private fun toRow(row: ResultRow) =
        WatchlistEntryRow(
            id = row[WatchlistEntries.id].value,
            clubId = row[WatchlistEntries.clubId].value,
            memberId = row[WatchlistEntries.memberId].value,
            title = row[WatchlistEntries.title],
            imdbUrl = row[WatchlistEntries.imdbUrl],
            notes = row[WatchlistEntries.notes],
            createdAt = row[WatchlistEntries.createdAt],
        )
}
