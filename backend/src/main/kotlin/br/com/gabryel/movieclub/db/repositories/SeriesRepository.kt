package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.tables.MemberSeriesReviews
import br.com.gabryel.movieclub.db.tables.Series
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class SeriesRow(
    val id: Uuid,
    val clubId: Uuid,
    val chosenById: Uuid,
    val imdbId: String,
    val tmdbId: String?,
    val originalTitle: String,
    val englishTitle: String?,
    val customTitle: String?,
    val displayTitlePreference: DisplayTitlePreference,
    val year: Int?,
    val genre: List<String>?,
    val country: List<String>?,
    val tmdbRating: BigDecimal?,
    val creator: String?,
    val posterS3Key: String?,
    val metadataFetchedAt: Instant?,
    val createdAt: Instant,
)

data class SeriesReviewRow(
    val seriesId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid?,
    val sentimentOptionId: Uuid?,
    val comment: String?,
)

/** Every non-user-entered field the `series` table stores -- same rationale as [TmdbMovieMetadata]. See
 * [br.com.gabryel.movieclub.service.tmdb.TmdbTvDetails.toMetadata] for how TMDB's response becomes one. */
data class TmdbSeriesMetadata(
    val tmdbId: String?,
    val originalTitle: String,
    val englishTitle: String?,
    val year: Int?,
    val genre: List<String>?,
    val country: List<String>?,
    val tmdbRating: BigDecimal?,
    val creator: String?,
    val metadataFetchedAt: Instant?,
)

interface SeriesRepository {
    fun create(clubId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbSeriesMetadata): SeriesRow

    fun findById(id: Uuid): SeriesRow?

    fun listByClub(clubId: Uuid): List<SeriesRow>

    fun updateDisplayTitle(seriesId: Uuid, customTitle: String?, preference: DisplayTitlePreference): SeriesRow

    fun updateTmdbMetadata(seriesId: Uuid, metadata: TmdbSeriesMetadata): SeriesRow

    fun upsertReview(
        seriesId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): SeriesReviewRow

    fun findReview(seriesId: Uuid, memberId: Uuid): SeriesReviewRow?

    fun listReviews(seriesId: Uuid): List<SeriesReviewRow>
}

class ExposedSeriesRepository : SeriesRepository {
    override fun create(clubId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbSeriesMetadata): SeriesRow =
        transaction {
            val result = Series.insert {
                it[Series.clubId] = clubId
                it[Series.chosenById] = chosenById
                it[Series.imdbId] = imdbId
                it[Series.displayTitlePreference] = ORIGINAL
                it[Series.createdAt] = Clock.System.now()
                it.applyTmdbMetadata(metadata)
            }
            toRow(result.resultedValues!!.single())
        }

    override fun findById(id: Uuid): SeriesRow? =
        transaction {
            Series
                .selectAll()
                .where { Series.id eq id }
                .map(::toRow)
                .singleOrNull()
        }

    override fun listByClub(clubId: Uuid): List<SeriesRow> =
        transaction {
            Series
                .selectAll()
                .where { Series.clubId eq clubId }
                .map(::toRow)
        }

    override fun updateDisplayTitle(
        seriesId: Uuid,
        customTitle: String?,
        preference: DisplayTitlePreference,
    ): SeriesRow =
        transaction {
            Series.update({ Series.id eq seriesId }) {
                it[Series.customTitle] = customTitle
                it[Series.displayTitlePreference] = preference
            }
            findById(seriesId)!!
        }

    override fun updateTmdbMetadata(seriesId: Uuid, metadata: TmdbSeriesMetadata): SeriesRow =
        transaction {
            Series.update({ Series.id eq seriesId }) {
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
    ): SeriesReviewRow =
        transaction {
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

    override fun findReview(seriesId: Uuid, memberId: Uuid): SeriesReviewRow? =
        transaction {
            MemberSeriesReviews
                .selectAll()
                .where { (MemberSeriesReviews.seriesId eq seriesId) and (MemberSeriesReviews.memberId eq memberId) }
                .map(::toReviewRow)
                .singleOrNull()
        }

    override fun listReviews(seriesId: Uuid): List<SeriesReviewRow> =
        transaction {
            MemberSeriesReviews
                .selectAll()
                .where { MemberSeriesReviews.seriesId eq seriesId }
                .map(::toReviewRow)
        }

    private fun toRow(row: ResultRow) =
        SeriesRow(
            id = row[Series.id].value,
            clubId = row[Series.clubId].value,
            chosenById = row[Series.chosenById].value,
            imdbId = row[Series.imdbId],
            tmdbId = row[Series.tmdbId],
            originalTitle = row[Series.originalTitle],
            englishTitle = row[Series.englishTitle],
            customTitle = row[Series.customTitle],
            displayTitlePreference = row[Series.displayTitlePreference],
            year = row[Series.year],
            genre = row[Series.genre],
            country = row[Series.country],
            tmdbRating = row[Series.tmdbRating],
            creator = row[Series.creator],
            posterS3Key = row[Series.posterS3Key],
            metadataFetchedAt = row[Series.metadataFetchedAt],
            createdAt = row[Series.createdAt],
        )

    private fun toReviewRow(row: ResultRow) =
        SeriesReviewRow(
            seriesId = row[MemberSeriesReviews.seriesId].value,
            memberId = row[MemberSeriesReviews.memberId].value,
            qualityOptionId = row[MemberSeriesReviews.qualityOptionId]?.value,
            sentimentOptionId = row[MemberSeriesReviews.sentimentOptionId]?.value,
            comment = row[MemberSeriesReviews.comment],
        )
}

/** Shared by [ExposedSeriesRepository.create] and [ExposedSeriesRepository.updateTmdbMetadata] -- both set the same
 * TMDB-sourced fields; `InsertStatement`/`UpdateStatement` both extend `UpdateBuilder`, so one function works for either. */
private fun UpdateBuilder<*>.applyTmdbMetadata(metadata: TmdbSeriesMetadata) {
    this[Series.tmdbId] = metadata.tmdbId
    this[Series.originalTitle] = metadata.originalTitle
    this[Series.englishTitle] = metadata.englishTitle
    this[Series.year] = metadata.year
    this[Series.genre] = metadata.genre
    this[Series.country] = metadata.country
    this[Series.tmdbRating] = metadata.tmdbRating
    this[Series.creator] = metadata.creator
    this[Series.metadataFetchedAt] = metadata.metadataFetchedAt
}
