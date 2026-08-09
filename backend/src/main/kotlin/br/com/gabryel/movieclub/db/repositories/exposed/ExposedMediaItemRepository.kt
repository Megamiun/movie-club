package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.tables.MediaItems
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedMediaItemRepository : MediaItemRepository {
    override fun findOrCreate(
        type: MediaItemType,
        imdbId: String,
        title: String,
        tmdbId: String?,
        year: Int?,
        posterUrl: String?,
        imdbRating: BigDecimal?,
    ): MediaItemRow = transaction {
        val existing = MediaItems
            .selectAll()
            .where { MediaItems.imdbId eq imdbId }
            .map { it[MediaItems.id].value }
            .singleOrNull()

        val id = if (existing != null) {
            MediaItems.update({ MediaItems.id eq existing }) {
                it.apply(type, title, tmdbId, year, posterUrl, imdbRating)
            }
            existing
        } else {
            MediaItems.insert {
                it[MediaItems.imdbId] = imdbId
                it[MediaItems.createdAt] = Clock.System.now()
                it.apply(type, title, tmdbId, year, posterUrl, imdbRating)
            }[MediaItems.id].value
        }

        findById(id)!!
    }

    override fun findById(id: Uuid): MediaItemRow? = transaction {
        MediaItems
            .selectAll()
            .where { MediaItems.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listAll(): List<MediaItemRow> = transaction {
        MediaItems.selectAll().map(::toRow)
    }

    private fun UpdateBuilder<*>.apply(
        type: MediaItemType,
        title: String,
        tmdbId: String?,
        year: Int?,
        posterUrl: String?,
        imdbRating: BigDecimal?,
    ) {
        this[MediaItems.type] = type
        this[MediaItems.title] = title
        this[MediaItems.tmdbId] = tmdbId
        this[MediaItems.year] = year
        this[MediaItems.posterUrl] = posterUrl
        this[MediaItems.imdbRating] = imdbRating
    }

    private fun toRow(row: ResultRow) = MediaItemRow(
        id = row[MediaItems.id].value,
        type = row[MediaItems.type],
        imdbId = row[MediaItems.imdbId],
        tmdbId = row[MediaItems.tmdbId],
        title = row[MediaItems.title],
        year = row[MediaItems.year],
        posterUrl = row[MediaItems.posterUrl],
        imdbRating = row[MediaItems.imdbRating],
        createdAt = row[MediaItems.createdAt],
    )
}
