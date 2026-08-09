package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeSearchRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import br.com.gabryel.movieclub.db.tables.ClubSeries
import br.com.gabryel.movieclub.db.tables.Episodes
import br.com.gabryel.movieclub.db.tables.MeetingEpisodes
import br.com.gabryel.movieclub.db.tables.Meetings
import br.com.gabryel.movieclub.db.tables.MemberEpisodeReviews
import br.com.gabryel.movieclub.db.tables.People
import br.com.gabryel.movieclub.db.tables.Seasons
import br.com.gabryel.movieclub.db.tables.Series
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder.ASC
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
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
        val existing = joined()
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
        joined()
            .selectAll()
            .where { Episodes.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listBySeason(seasonId: Uuid): List<EpisodeRow> = transaction {
        joined()
            .selectAll()
            .where { Episodes.seasonId eq seasonId }
            .orderBy(Episodes.number to ASC)
            .map(::toRow)
    }

    override fun listByMeeting(meetingId: Uuid): List<EpisodeRow> = transaction {
        (MeetingEpisodes innerJoin Episodes)
            .leftJoin(Seasons)
            .leftJoin(People)
            .selectAll()
            .where { MeetingEpisodes.meetingId eq meetingId }
            .orderBy(Episodes.number)
            .orderBy(Seasons.number)
            .map(::toRow)
    }

    override fun searchByClub(clubId: Uuid, query: String, limit: Int): List<EpisodeSearchRow> = transaction {
        val pattern = "%${query.lowercase()}%"
        (ClubSeries innerJoin Series)
            .innerJoin(Seasons, { Series.id }, { Seasons.seriesId })
            .innerJoin(Episodes, { Seasons.id }, { Episodes.seasonId })
            .leftJoin(People)
            .selectAll()
            .where {
                (ClubSeries.clubId eq clubId) and (
                    (Episodes.title.lowerCase() like pattern) or
                        (Series.originalTitle.lowerCase() like pattern) or
                        (ClubSeries.customTitle.lowerCase() like pattern)
                )
            }
            .limit(limit)
            .map { row ->
                EpisodeSearchRow(
                    episode = toRow(row),
                    seasonNumber = row[Seasons.number],
                    seriesTitle = row[ClubSeries.customTitle] ?: row[Series.originalTitle],
                )
            }
    }

    /** The earliest (season, then episode number) episode of [globalSeriesId] that [clubId] hasn't scheduled to any
     * of its own meetings yet -- powers the "suggest next episode" prompt. Scans every known episode of the series
     * rather than the last-scheduled-plus-one, since gaps (an episode skipped, or added out of order) shouldn't
     * make the suggestion skip ahead past something still unwatched. */
    override fun findNextUnscheduled(clubId: Uuid, globalSeriesId: Uuid): EpisodeRow? = transaction {
        val scheduledEpisodeIds = (MeetingEpisodes innerJoin Meetings)
            .selectAll()
            .where { Meetings.clubId eq clubId }
            .map { it[MeetingEpisodes.episodeId].value }
            .toSet()

        (Seasons innerJoin Episodes).leftJoin(People)
            .selectAll()
            .where { Seasons.seriesId eq globalSeriesId }
            .orderBy(Seasons.number to ASC, Episodes.number to ASC)
            .map(::toRow)
            .firstOrNull { it.id !in scheduledEpisodeIds }
    }

    override fun findSeriesImdbId(episodeId: Uuid): String? = transaction {
        Series
            .innerJoin(Seasons, { Series.id }, { Seasons.seriesId })
            .innerJoin(Episodes, { Seasons.id }, { Episodes.seasonId })
            .selectAll()
            .where { Episodes.id eq episodeId }
            .map { it[Series.imdbId] }
            .singleOrNull()
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

    override fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid): Unit = transaction {
        MemberEpisodeReviews.update({ MemberEpisodeReviews.qualityOptionId eq oldOptionId }) {
            it[MemberEpisodeReviews.qualityOptionId] = newOptionId
        }
        MemberEpisodeReviews.update({ MemberEpisodeReviews.sentimentOptionId eq oldOptionId }) {
            it[MemberEpisodeReviews.sentimentOptionId] = newOptionId
        }
    }

    private fun joined() = Episodes.leftJoin(People)

    private fun toRow(row: ResultRow) = EpisodeRow(
        id = row[Episodes.id].value,
        seasonId = row[Episodes.seasonId].value,
        number = row[Episodes.number],
        title = row[Episodes.title],
        airDate = row[Episodes.airDate],
        overview = row[Episodes.overview],
        runtimeMinutes = row[Episodes.runtimeMinutes],
        director = row.getOrNull(People.name),
        directorImdbId = row.getOrNull(People.imdbId),
        imdbId = row[Episodes.imdbId],
        imdbRating = row[Episodes.imdbRating],
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
    this[Episodes.directorPersonId] = metadata.directorPersonId
    this[Episodes.imdbId] = metadata.imdbId
    this[Episodes.imdbRating] = metadata.imdbRating
    this[Episodes.metadataFetchedAt] = metadata.metadataFetchedAt
}
