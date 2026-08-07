package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.RatingScaleType
import br.com.gabryel.movieclub.db.tables.RatingOptions
import br.com.gabryel.movieclub.db.tables.RatingScales
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

data class RatingScaleRow(
    val id: Uuid,
    val clubId: Uuid,
    val type: RatingScaleType,
)

data class RatingOptionRow(
    val id: Uuid,
    val scaleId: Uuid,
    val label: String,
    val position: Int,
)

interface RatingScaleRepository {
    fun createScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow

    fun createOption(scaleId: Uuid, label: String, position: Int): RatingOptionRow

    fun findScales(clubId: Uuid): List<RatingScaleRow>

    fun findScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow?

    fun findOptions(scaleId: Uuid): List<RatingOptionRow>

    fun findOptionById(id: Uuid): RatingOptionRow?

    fun findOptionByLabel(scaleId: Uuid, label: String): RatingOptionRow?
}

class ExposedRatingScaleRepository : RatingScaleRepository {
    override fun createScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow =
        transaction {
            val result = RatingScales.insert {
                it[RatingScales.clubId] = clubId
                it[RatingScales.type] = type
            }
            toScaleRow(result.resultedValues!!.single())
        }

    override fun createOption(scaleId: Uuid, label: String, position: Int): RatingOptionRow =
        transaction {
            val result = RatingOptions.insert {
                it[RatingOptions.scaleId] = scaleId
                it[RatingOptions.label] = label
                it[RatingOptions.position] = position
            }
            toOptionRow(result.resultedValues!!.single())
        }

    override fun findScales(clubId: Uuid): List<RatingScaleRow> =
        transaction {
            RatingScales
                .selectAll()
                .where { RatingScales.clubId eq clubId }
                .map(::toScaleRow)
        }

    override fun findScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow? =
        transaction {
            RatingScales
                .selectAll()
                .where { (RatingScales.clubId eq clubId) and (RatingScales.type eq type) }
                .map(::toScaleRow)
                .singleOrNull()
        }

    override fun findOptions(scaleId: Uuid): List<RatingOptionRow> =
        transaction {
            RatingOptions
                .selectAll()
                .where { RatingOptions.scaleId eq scaleId }
                .orderBy(RatingOptions.position to SortOrder.ASC)
                .map(::toOptionRow)
        }

    override fun findOptionById(id: Uuid): RatingOptionRow? =
        transaction {
            RatingOptions
                .selectAll()
                .where { RatingOptions.id eq id }
                .map(::toOptionRow)
                .singleOrNull()
        }

    override fun findOptionByLabel(scaleId: Uuid, label: String): RatingOptionRow? =
        transaction {
            RatingOptions
                .selectAll()
                .where { (RatingOptions.scaleId eq scaleId) and (RatingOptions.label eq label) }
                .map(::toOptionRow)
                .singleOrNull()
        }

    private fun toScaleRow(row: ResultRow) =
        RatingScaleRow(
            id = row[RatingScales.id].value,
            clubId = row[RatingScales.clubId].value,
            type = row[RatingScales.type],
        )

    private fun toOptionRow(row: ResultRow) =
        RatingOptionRow(
            id = row[RatingOptions.id].value,
            scaleId = row[RatingOptions.scaleId].value,
            label = row[RatingOptions.label],
            position = row[RatingOptions.position],
        )
}
