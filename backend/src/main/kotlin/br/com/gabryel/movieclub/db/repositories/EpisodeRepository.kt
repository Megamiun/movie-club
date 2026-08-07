package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.tables.Episodes
import br.com.gabryel.movieclub.db.tables.MemberEpisodeReviews
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder.ASC
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class EpisodeRow(
    val id: Uuid,
    val seasonId: Uuid,
    val number: Int,
    val title: String?,
    val meetingId: Uuid?,
    val airDate: LocalDate?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val director: String?,
    val tmdbRating: BigDecimal?,
    val metadataFetchedAt: Instant?,
)

data class EpisodeReviewRow(
    val episodeId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid?,
    val sentimentOptionId: Uuid?,
    val comment: String?,
)

/** Every non-user-entered field the `episodes` table stores -- same rationale as [TmdbMovieMetadata], but purely
 * additive: unlike Movie/Series, an episode has no separate original/english/custom title split, so `title` stays
 * whatever the user/CSV entered and TMDB only fills in the fields it uniquely knows about. See
 * [br.com.gabryel.movieclub.service.tmdb.TmdbEpisodeDetails.toMetadata] for how TMDB's response becomes one. */
data class TmdbEpisodeMetadata(
    val airDate: LocalDate?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val director: String?,
    val tmdbRating: BigDecimal?,
    val metadataFetchedAt: Instant?,
)

interface EpisodeRepository {
    fun create(seasonId: Uuid, number: Int, title: String? = null, meetingId: Uuid? = null): EpisodeRow

    fun findById(id: Uuid): EpisodeRow?

    fun listBySeason(seasonId: Uuid): List<EpisodeRow>

    fun listByMeeting(meetingId: Uuid): List<EpisodeRow>

    fun updateMeeting(episodeId: Uuid, meetingId: Uuid?): EpisodeRow

    fun updateTmdbMetadata(episodeId: Uuid, metadata: TmdbEpisodeMetadata): EpisodeRow

    fun upsertReview(
        episodeId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): EpisodeReviewRow

    fun findReview(episodeId: Uuid, memberId: Uuid): EpisodeReviewRow?

    fun listReviews(episodeId: Uuid): List<EpisodeReviewRow>
}

class ExposedEpisodeRepository : EpisodeRepository {
    override fun create(seasonId: Uuid, number: Int, title: String?, meetingId: Uuid?): EpisodeRow =
        transaction {
            val result = Episodes.insert {
                it[Episodes.seasonId] = seasonId
                it[Episodes.number] = number
                it[Episodes.title] = title
                it[Episodes.meetingId] = meetingId
            }
            toRow(result.resultedValues!!.single())
        }

    override fun findById(id: Uuid): EpisodeRow? =
        transaction {
            Episodes
                .selectAll()
                .where { Episodes.id eq id }
                .map(::toRow)
                .singleOrNull()
        }

    override fun listBySeason(seasonId: Uuid): List<EpisodeRow> =
        transaction {
            Episodes
                .selectAll()
                .where { Episodes.seasonId eq seasonId }
                .orderBy(Episodes.number to ASC)
                .map(::toRow)
        }

    override fun listByMeeting(meetingId: Uuid): List<EpisodeRow> =
        transaction {
            Episodes
                .selectAll()
                .where { Episodes.meetingId eq meetingId }
                .map(::toRow)
        }

    override fun updateMeeting(episodeId: Uuid, meetingId: Uuid?): EpisodeRow =
        transaction {
            Episodes.update({ Episodes.id eq episodeId }) {
                it[Episodes.meetingId] = meetingId
            }
            findById(episodeId)!!
        }

    override fun updateTmdbMetadata(episodeId: Uuid, metadata: TmdbEpisodeMetadata): EpisodeRow =
        transaction {
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
    ): EpisodeReviewRow =
        transaction {
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

    override fun findReview(episodeId: Uuid, memberId: Uuid): EpisodeReviewRow? =
        transaction {
            MemberEpisodeReviews
                .selectAll()
                .where { (MemberEpisodeReviews.episodeId eq episodeId) and (MemberEpisodeReviews.memberId eq memberId) }
                .map(::toReviewRow)
                .singleOrNull()
        }

    override fun listReviews(episodeId: Uuid): List<EpisodeReviewRow> =
        transaction {
            MemberEpisodeReviews
                .selectAll()
                .where { MemberEpisodeReviews.episodeId eq episodeId }
                .map(::toReviewRow)
        }

    private fun toRow(row: ResultRow) =
        EpisodeRow(
            id = row[Episodes.id].value,
            seasonId = row[Episodes.seasonId].value,
            number = row[Episodes.number],
            title = row[Episodes.title],
            meetingId = row[Episodes.meetingId]?.value,
            airDate = row[Episodes.airDate],
            overview = row[Episodes.overview],
            runtimeMinutes = row[Episodes.runtimeMinutes],
            director = row[Episodes.director],
            tmdbRating = row[Episodes.tmdbRating],
            metadataFetchedAt = row[Episodes.metadataFetchedAt],
        )

    private fun toReviewRow(row: ResultRow) =
        EpisodeReviewRow(
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
