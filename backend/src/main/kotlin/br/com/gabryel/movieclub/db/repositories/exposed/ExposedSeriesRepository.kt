package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.db.tables.ClubMembers
import br.com.gabryel.movieclub.db.tables.ClubSeries
import br.com.gabryel.movieclub.db.tables.MemberSeriesReviews
import br.com.gabryel.movieclub.db.tables.Series
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedSeriesRepository : SeriesRepository {
    override fun create(clubId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbSeriesMetadata): SeriesRow = transaction {
        val seriesId = findOrCreateSeries(imdbId, metadata)
        val pickId = ClubSeries
            .insert {
                it[ClubSeries.clubId] = clubId
                it[ClubSeries.seriesId] = seriesId
                it[ClubSeries.chosenById] = chosenById
                it[ClubSeries.displayTitlePreference] = ORIGINAL
                it[ClubSeries.createdAt] = Clock.System.now()
            }[ClubSeries.id]
            .value
        findById(pickId)!!
    }

    override fun findById(id: Uuid): SeriesRow? = transaction {
        joined()
            .selectAll()
            .where { ClubSeries.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listByClub(clubId: Uuid): List<SeriesRow> = transaction {
        joined()
            .selectAll()
            .where { ClubSeries.clubId eq clubId }
            .map(::toRow)
    }

    override fun findByClubAndImdbId(clubId: Uuid, imdbId: String): SeriesRow? = transaction {
        joined()
            .selectAll()
            .where { (ClubSeries.clubId eq clubId) and (Series.imdbId eq imdbId) }
            .map(::toRow)
            .singleOrNull()
    }

    override fun findClubSeriesForMember(seriesId: Uuid, memberId: Uuid): SeriesRow? = transaction {
        joined()
            .innerJoin(ClubMembers, { ClubSeries.clubId }, { ClubMembers.clubId })
            .selectAll()
            .where { (Series.id eq seriesId) and (ClubMembers.memberId eq memberId) }
            .map(::toRow)
            .firstOrNull()
    }

    override fun updateDisplayTitle(
        seriesId: Uuid,
        customTitle: String?,
        preference: DisplayTitlePreference,
    ): SeriesRow = transaction {
        ClubSeries.update({ ClubSeries.id eq seriesId }) {
            it[ClubSeries.customTitle] = customTitle
            it[ClubSeries.displayTitlePreference] = preference
        }
        findById(seriesId)!!
    }

    /** Refreshes the shared global catalog row -- since multiple picks (even across clubs) can point at the same
     * `imdb_id`, this updates metadata for all of them at once, not just the given pick. */
    override fun updateTmdbMetadata(seriesId: Uuid, metadata: TmdbSeriesMetadata): SeriesRow = transaction {
        val globalSeriesId = ClubSeries
            .selectAll()
            .where { ClubSeries.id eq seriesId }
            .map { it[ClubSeries.seriesId].value }
            .single()
        Series.update({ Series.id eq globalSeriesId }) {
            it.applyTmdbMetadata(metadata)
        }
        findById(seriesId)!!
    }

    override fun upsertReview(
        seriesId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): SeriesReviewRow = transaction {
        val exists = findReview(seriesId, memberId) != null
        if (exists) {
            MemberSeriesReviews.update({
                (MemberSeriesReviews.seriesId eq seriesId) and (MemberSeriesReviews.memberId eq memberId)
            }) {
                it[MemberSeriesReviews.qualityOptionId] = qualityOptionId
                it[MemberSeriesReviews.sentimentOptionId] = sentimentOptionId
                it[MemberSeriesReviews.comment] = comment
            }
        } else {
            MemberSeriesReviews.insert {
                it[MemberSeriesReviews.seriesId] = seriesId
                it[MemberSeriesReviews.memberId] = memberId
                it[MemberSeriesReviews.qualityOptionId] = qualityOptionId
                it[MemberSeriesReviews.sentimentOptionId] = sentimentOptionId
                it[MemberSeriesReviews.comment] = comment
            }
        }
        findReview(seriesId, memberId)!!
    }

    override fun findReview(seriesId: Uuid, memberId: Uuid): SeriesReviewRow? = transaction {
        MemberSeriesReviews
            .selectAll()
            .where { (MemberSeriesReviews.seriesId eq seriesId) and (MemberSeriesReviews.memberId eq memberId) }
            .map(::toReviewRow)
            .singleOrNull()
    }

    override fun listReviews(seriesId: Uuid): List<SeriesReviewRow> = transaction {
        MemberSeriesReviews
            .selectAll()
            .where { MemberSeriesReviews.seriesId eq seriesId }
            .map(::toReviewRow)
    }

    /** Finds the global catalog row for [imdbId], creating it from [metadata] if this is the first time any club
     * has picked it; otherwise overwrites its TMDB data with [metadata] (harmless -- same `imdbId`, same canonical
     * TMDB response either way) so a refresh started from any one pick keeps the shared row current. */
    private fun findOrCreateSeries(imdbId: String, metadata: TmdbSeriesMetadata): Uuid {
        val existing = Series.selectAll().where { Series.imdbId eq imdbId }.map { it[Series.id].value }.singleOrNull()
        if (existing != null) {
            Series.update({ Series.id eq existing }) { it.applyTmdbMetadata(metadata) }
            return existing
        }
        return Series.insert {
            it[Series.imdbId] = imdbId
            it[Series.createdAt] = Clock.System.now()
            it.applyTmdbMetadata(metadata)
        }[Series.id].value
    }

    private fun joined() = ClubSeries innerJoin Series

    private fun toRow(row: ResultRow) = SeriesRow(
        id = row[ClubSeries.id].value,
        globalSeriesId = row[Series.id].value,
        clubId = row[ClubSeries.clubId].value,
        chosenById = row[ClubSeries.chosenById].value,
        imdbId = row[Series.imdbId],
        tmdbId = row[Series.tmdbId],
        originalTitle = row[Series.originalTitle],
        alternativeTitles = row[Series.alternativeTitles],
        customTitle = row[ClubSeries.customTitle],
        displayTitlePreference = row[ClubSeries.displayTitlePreference],
        year = row[Series.year],
        genre = row[Series.genre],
        originCountry = row[Series.originCountry],
        productionCountries = row[Series.productionCountries],
        tmdbRating = row[Series.tmdbRating],
        imdbRating = row[Series.imdbRating],
        creator = row[Series.creator],
        posterS3Key = row[Series.posterS3Key],
        metadataFetchedAt = row[Series.metadataFetchedAt],
        createdAt = row[ClubSeries.createdAt],
    )

    private fun toReviewRow(row: ResultRow) = SeriesReviewRow(
        seriesId = row[MemberSeriesReviews.seriesId].value,
        memberId = row[MemberSeriesReviews.memberId].value,
        qualityOptionId = row[MemberSeriesReviews.qualityOptionId]?.value,
        sentimentOptionId = row[MemberSeriesReviews.sentimentOptionId]?.value,
        comment = row[MemberSeriesReviews.comment],
    )
}

/** Shared by [ExposedSeriesRepository.findOrCreateSeries] and [ExposedSeriesRepository.updateTmdbMetadata] -- both
 * set the same TMDB-sourced fields on the global [Series] row; `InsertStatement`/`UpdateStatement` both extend
 * `UpdateBuilder`, so one function works for either. */
private fun UpdateBuilder<*>.applyTmdbMetadata(metadata: TmdbSeriesMetadata) {
    this[Series.tmdbId] = metadata.tmdbId
    this[Series.originalTitle] = metadata.originalTitle
    this[Series.alternativeTitles] = metadata.alternativeTitles
    this[Series.year] = metadata.year
    this[Series.genre] = metadata.genre
    this[Series.originCountry] = metadata.originCountry
    this[Series.productionCountries] = metadata.productionCountries
    this[Series.tmdbRating] = metadata.tmdbRating
    this[Series.imdbRating] = metadata.imdbRating
    this[Series.creator] = metadata.creator
    this[Series.metadataFetchedAt] = metadata.metadataFetchedAt
}
