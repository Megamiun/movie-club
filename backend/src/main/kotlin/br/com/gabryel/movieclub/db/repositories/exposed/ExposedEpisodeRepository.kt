package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import br.com.gabryel.movieclub.db.tables.Episodes
import br.com.gabryel.movieclub.db.tables.MeetingEpisodes
import br.com.gabryel.movieclub.db.tables.MemberEpisodeReviews
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder.ASC
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedEpisodeRepository : EpisodeRepository {
    override fun create(seasonId: Uuid, number: Int, title: String?): EpisodeRow = transaction {
        val existing = Episodes
            .selectAll()
            .where { (Episodes.seasonId eq seasonId) and (Episodes.number eq number) }
            .map(::toRow)
            .singleOrNull()
        existing ?: run {
            val result = Episodes.insert {
                it[Episodes.seasonId] = seasonId
                it[Episodes.number] = number
                it[Episodes.title] = title
            }
            toRow(result.resultedValues!!.single())
        }
    }

    override fun findById(id: Uuid): EpisodeRow? = transaction {
        Episodes
            .selectAll()
            .where { Episodes.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listBySeason(seasonId: Uuid): List<EpisodeRow> = transaction {
        Episodes
            .selectAll()
            .where { Episodes.seasonId eq seasonId }
            .orderBy(Episodes.number to ASC)
            .map(::toRow)
    }

    override fun listByMeeting(meetingId: Uuid): List<EpisodeRow> = transaction {
        (MeetingEpisodes innerJoin Episodes)
            .selectAll()
            .where { MeetingEpisodes.meetingId eq meetingId }
            .map(::toRow)
    }

    override fun assignToMeeting(episodeId: Uuid, meetingId: Uuid) {
        transaction {
            val alreadyAssigned = MeetingEpisodes
                .selectAll()
                .where { (MeetingEpisodes.meetingId eq meetingId) and (MeetingEpisodes.episodeId eq episodeId) }
                .any()
            if (!alreadyAssigned) {
                MeetingEpisodes.insert {
                    it[MeetingEpisodes.meetingId] = meetingId
                    it[MeetingEpisodes.episodeId] = episodeId
                    it[MeetingEpisodes.createdAt] = Clock.System.now()
                }
            }
        }
    }

    override fun unassignFromMeeting(episodeId: Uuid, meetingId: Uuid) {
        transaction {
            MeetingEpisodes.deleteWhere {
                (MeetingEpisodes.meetingId eq meetingId) and (MeetingEpisodes.episodeId eq episodeId)
            }
        }
    }

    override fun updateTmdbMetadata(episodeId: Uuid, metadata: TmdbEpisodeMetadata): EpisodeRow = transaction {
        Episodes.update({ Episodes.id eq episodeId }) {
            it.applyTmdbMetadata(metadata)
        }
        findById(episodeId)!!
    }

    override fun upsertReview(
        episodeId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): EpisodeReviewRow = transaction {
        val exists = findReview(episodeId, memberId) != null
        if (exists) {
            MemberEpisodeReviews.update({
                (MemberEpisodeReviews.episodeId eq episodeId) and (MemberEpisodeReviews.memberId eq memberId)
            }) {
                it[MemberEpisodeReviews.qualityOptionId] = qualityOptionId
                it[MemberEpisodeReviews.sentimentOptionId] = sentimentOptionId
                it[MemberEpisodeReviews.comment] = comment
            }
        } else {
            MemberEpisodeReviews.insert {
                it[MemberEpisodeReviews.episodeId] = episodeId
                it[MemberEpisodeReviews.memberId] = memberId
                it[MemberEpisodeReviews.qualityOptionId] = qualityOptionId
                it[MemberEpisodeReviews.sentimentOptionId] = sentimentOptionId
                it[MemberEpisodeReviews.comment] = comment
            }
        }
        findReview(episodeId, memberId)!!
    }

    override fun findReview(episodeId: Uuid, memberId: Uuid): EpisodeReviewRow? = transaction {
        MemberEpisodeReviews
            .selectAll()
            .where { (MemberEpisodeReviews.episodeId eq episodeId) and (MemberEpisodeReviews.memberId eq memberId) }
            .map(::toReviewRow)
            .singleOrNull()
    }

    override fun listReviews(episodeId: Uuid): List<EpisodeReviewRow> = transaction {
        MemberEpisodeReviews
            .selectAll()
            .where { MemberEpisodeReviews.episodeId eq episodeId }
            .map(::toReviewRow)
    }

    private fun toRow(row: ResultRow) = EpisodeRow(
        id = row[Episodes.id].value,
        seasonId = row[Episodes.seasonId].value,
        number = row[Episodes.number],
        title = row[Episodes.title],
        airDate = row[Episodes.airDate],
        overview = row[Episodes.overview],
        runtimeMinutes = row[Episodes.runtimeMinutes],
        director = row[Episodes.director],
        tmdbRating = row[Episodes.tmdbRating],
        metadataFetchedAt = row[Episodes.metadataFetchedAt],
    )

    private fun toReviewRow(row: ResultRow) = EpisodeReviewRow(
        episodeId = row[MemberEpisodeReviews.episodeId].value,
        memberId = row[MemberEpisodeReviews.memberId].value,
        qualityOptionId = row[MemberEpisodeReviews.qualityOptionId]?.value,
        sentimentOptionId = row[MemberEpisodeReviews.sentimentOptionId]?.value,
        comment = row[MemberEpisodeReviews.comment],
    )
}

private fun UpdateBuilder<*>.applyTmdbMetadata(metadata: TmdbEpisodeMetadata) {
    this[Episodes.airDate] = metadata.airDate
    this[Episodes.overview] = metadata.overview
    this[Episodes.runtimeMinutes] = metadata.runtimeMinutes
    this[Episodes.director] = metadata.director
    this[Episodes.tmdbRating] = metadata.tmdbRating
    this[Episodes.metadataFetchedAt] = metadata.metadataFetchedAt
}
