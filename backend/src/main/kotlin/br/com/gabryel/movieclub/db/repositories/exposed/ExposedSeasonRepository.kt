package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.dto.SeasonReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.tables.MemberSeasonReviews
import br.com.gabryel.movieclub.db.tables.Seasons
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedSeasonRepository : SeasonRepository {
    override fun create(seriesId: Uuid, number: Int, title: String?): SeasonRow = transaction {
        val existing = Seasons
            .selectAll()
            .where { (Seasons.seriesId eq seriesId) and (Seasons.number eq number) }
            .map(::toRow)
            .singleOrNull()
        existing ?: run {
            val result = Seasons.insert {
                it[Seasons.seriesId] = seriesId
                it[Seasons.number] = number
                it[Seasons.title] = title
            }
            toRow(result.resultedValues!!.single())
        }
    }

    override fun findById(id: Uuid): SeasonRow? = transaction {
        Seasons
            .selectAll()
            .where { Seasons.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listBySeries(seriesId: Uuid): List<SeasonRow> = transaction {
        Seasons
            .selectAll()
            .where { Seasons.seriesId eq seriesId }
            .orderBy(Seasons.number to SortOrder.ASC)
            .map(::toRow)
    }

    override fun upsertReview(
        seasonId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): SeasonReviewRow = transaction {
        val exists = findReview(seasonId, memberId) != null
        if (exists) {
            MemberSeasonReviews.update({
                (MemberSeasonReviews.seasonId eq seasonId) and (MemberSeasonReviews.memberId eq memberId)
            }) {
                it[MemberSeasonReviews.qualityOptionId] = qualityOptionId
                it[MemberSeasonReviews.sentimentOptionId] = sentimentOptionId
                it[MemberSeasonReviews.comment] = comment
            }
        } else {
            MemberSeasonReviews.insert {
                it[MemberSeasonReviews.seasonId] = seasonId
                it[MemberSeasonReviews.memberId] = memberId
                it[MemberSeasonReviews.qualityOptionId] = qualityOptionId
                it[MemberSeasonReviews.sentimentOptionId] = sentimentOptionId
                it[MemberSeasonReviews.comment] = comment
            }
        }
        findReview(seasonId, memberId)!!
    }

    override fun findReview(seasonId: Uuid, memberId: Uuid): SeasonReviewRow? = transaction {
        MemberSeasonReviews
            .selectAll()
            .where { (MemberSeasonReviews.seasonId eq seasonId) and (MemberSeasonReviews.memberId eq memberId) }
            .map(::toReviewRow)
            .singleOrNull()
    }

    override fun listReviews(seasonId: Uuid): List<SeasonReviewRow> = transaction {
        MemberSeasonReviews
            .selectAll()
            .where { MemberSeasonReviews.seasonId eq seasonId }
            .map(::toReviewRow)
    }

    private fun toRow(row: ResultRow) = SeasonRow(
        id = row[Seasons.id].value,
        seriesId = row[Seasons.seriesId].value,
        number = row[Seasons.number],
        title = row[Seasons.title],
    )

    private fun toReviewRow(row: ResultRow) = SeasonReviewRow(
        seasonId = row[MemberSeasonReviews.seasonId].value,
        memberId = row[MemberSeasonReviews.memberId].value,
        qualityOptionId = row[MemberSeasonReviews.qualityOptionId]?.value,
        sentimentOptionId = row[MemberSeasonReviews.sentimentOptionId]?.value,
        comment = row[MemberSeasonReviews.comment],
    )
}
