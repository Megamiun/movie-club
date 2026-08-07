package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.tables.MemberMovieReviews
import br.com.gabryel.movieclub.db.tables.Movies
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class MovieRow(
    val id: Uuid,
    val meetingId: Uuid,
    val chosenById: Uuid,
    val imdbId: String,
    val tmdbId: String?,
    val originalTitle: String,
    val englishTitle: String?,
    val customTitle: String?,
    val displayTitlePreference: DisplayTitlePreference,
    val year: Int?,
    val director: String?,
    val runtimeMinutes: Int?,
    val genre: List<String>?,
    val country: List<String>?,
    val tmdbRating: BigDecimal?,
    val posterS3Key: String?,
    val watchLink: String?,
    val metadataFetchedAt: Instant?,
    val createdAt: Instant,
)

data class MovieReviewRow(
    val movieId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid?,
    val sentimentOptionId: Uuid?,
    val comment: String?,
)

/** Every non-user-entered field the `movies` table stores -- always constructed as a whole (even when some fields
 * are null, e.g. a CSV-imported row before its TMDB refresh succeeds), never assembled field by field at the call
 * site. See [br.com.gabryel.movieclub.service.tmdb.TmdbMovieDetails.toMetadata] for how TMDB's response becomes one. */
data class TmdbMovieMetadata(
    val tmdbId: String?,
    val originalTitle: String,
    val englishTitle: String?,
    val year: Int?,
    val director: String?,
    val runtimeMinutes: Int?,
    val genre: List<String>?,
    val country: List<String>?,
    val tmdbRating: BigDecimal?,
    val metadataFetchedAt: Instant?,
)

interface MovieRepository {
    fun create(meetingId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbMovieMetadata, watchLink: String? = null): MovieRow

    fun findById(id: Uuid): MovieRow?

    fun findByMeetingAndImdbId(meetingId: Uuid, imdbId: String): MovieRow?

    fun listByMeeting(meetingId: Uuid): List<MovieRow>

    fun updateMeeting(movieId: Uuid, newMeetingId: Uuid): MovieRow

    fun updateDisplayTitle(movieId: Uuid, customTitle: String?, preference: DisplayTitlePreference): MovieRow

    fun updateWatchLink(movieId: Uuid, watchLink: String?): MovieRow

    fun updateTmdbMetadata(movieId: Uuid, metadata: TmdbMovieMetadata): MovieRow

    fun delete(movieId: Uuid)

    fun upsertReview(
        movieId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): MovieReviewRow

    fun findReview(movieId: Uuid, memberId: Uuid): MovieReviewRow?

    fun listReviews(movieId: Uuid): List<MovieReviewRow>
}

class ExposedMovieRepository : MovieRepository {
    override fun create(meetingId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbMovieMetadata, watchLink: String?): MovieRow =
        transaction {
            val result = Movies.insert {
                it[Movies.meetingId] = meetingId
                it[Movies.chosenById] = chosenById
                it[Movies.imdbId] = imdbId
                it[Movies.displayTitlePreference] = ORIGINAL
                it[Movies.watchLink] = watchLink
                it[Movies.createdAt] = Clock.System.now()
                it.applyTmdbMetadata(metadata)
            }
            toRow(result.resultedValues!!.single())
        }

    override fun findById(id: Uuid): MovieRow? =
        transaction {
            Movies
                .selectAll()
                .where { Movies.id eq id }
                .map(::toRow)
                .singleOrNull()
        }

    override fun findByMeetingAndImdbId(meetingId: Uuid, imdbId: String): MovieRow? =
        transaction {
            Movies
                .selectAll()
                .where { (Movies.meetingId eq meetingId) and (Movies.imdbId eq imdbId) }
                .map(::toRow)
                .singleOrNull()
        }

    override fun listByMeeting(meetingId: Uuid): List<MovieRow> =
        transaction {
            Movies
                .selectAll()
                .where { Movies.meetingId eq meetingId }
                .map(::toRow)
        }

    override fun updateMeeting(movieId: Uuid, newMeetingId: Uuid): MovieRow =
        transaction {
            Movies.update({ Movies.id eq movieId }) {
                it[Movies.meetingId] = newMeetingId
            }
            findById(movieId)!!
        }

    override fun updateDisplayTitle(movieId: Uuid, customTitle: String?, preference: DisplayTitlePreference): MovieRow =
        transaction {
            Movies.update({ Movies.id eq movieId }) {
                it[Movies.customTitle] = customTitle
                it[Movies.displayTitlePreference] = preference
            }
            findById(movieId)!!
        }

    override fun updateWatchLink(movieId: Uuid, watchLink: String?): MovieRow =
        transaction {
            Movies.update({ Movies.id eq movieId }) {
                it[Movies.watchLink] = watchLink
            }
            findById(movieId)!!
        }

    override fun updateTmdbMetadata(movieId: Uuid, metadata: TmdbMovieMetadata): MovieRow =
        transaction {
            Movies.update({ Movies.id eq movieId }) {
                it.applyTmdbMetadata(metadata)
            }
            findById(movieId)!!
        }

    override fun delete(movieId: Uuid) {
        transaction {
            MemberMovieReviews.deleteWhere { MemberMovieReviews.movieId eq movieId }
            Movies.deleteWhere { Movies.id eq movieId }
        }
    }

    override fun upsertReview(
        movieId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): MovieReviewRow =
        transaction {
            val exists = findReview(movieId, memberId) != null
            if (exists) {
                MemberMovieReviews.update({
                    (MemberMovieReviews.movieId eq movieId) and (MemberMovieReviews.memberId eq memberId)
                }) {
                    it[MemberMovieReviews.qualityOptionId] = qualityOptionId
                    it[MemberMovieReviews.sentimentOptionId] = sentimentOptionId
                    it[MemberMovieReviews.comment] = comment
                }
            } else {
                MemberMovieReviews.insert {
                    it[MemberMovieReviews.movieId] = movieId
                    it[MemberMovieReviews.memberId] = memberId
                    it[MemberMovieReviews.qualityOptionId] = qualityOptionId
                    it[MemberMovieReviews.sentimentOptionId] = sentimentOptionId
                    it[MemberMovieReviews.comment] = comment
                }
            }
            findReview(movieId, memberId)!!
        }

    override fun findReview(movieId: Uuid, memberId: Uuid): MovieReviewRow? =
        transaction {
            MemberMovieReviews
                .selectAll()
                .where { (MemberMovieReviews.movieId eq movieId) and (MemberMovieReviews.memberId eq memberId) }
                .map(::toReviewRow)
                .singleOrNull()
        }

    override fun listReviews(movieId: Uuid): List<MovieReviewRow> =
        transaction {
            MemberMovieReviews
                .selectAll()
                .where { MemberMovieReviews.movieId eq movieId }
                .map(::toReviewRow)
        }

    private fun toRow(row: ResultRow) =
        MovieRow(
            id = row[Movies.id].value,
            meetingId = row[Movies.meetingId].value,
            chosenById = row[Movies.chosenById].value,
            imdbId = row[Movies.imdbId],
            tmdbId = row[Movies.tmdbId],
            originalTitle = row[Movies.originalTitle],
            englishTitle = row[Movies.englishTitle],
            customTitle = row[Movies.customTitle],
            displayTitlePreference = row[Movies.displayTitlePreference],
            year = row[Movies.year],
            director = row[Movies.director],
            runtimeMinutes = row[Movies.runtimeMinutes],
            genre = row[Movies.genre],
            country = row[Movies.country],
            tmdbRating = row[Movies.tmdbRating],
            posterS3Key = row[Movies.posterS3Key],
            watchLink = row[Movies.watchLink],
            metadataFetchedAt = row[Movies.metadataFetchedAt],
            createdAt = row[Movies.createdAt],
        )

    private fun toReviewRow(row: ResultRow) =
        MovieReviewRow(
            movieId = row[MemberMovieReviews.movieId].value,
            memberId = row[MemberMovieReviews.memberId].value,
            qualityOptionId = row[MemberMovieReviews.qualityOptionId]?.value,
            sentimentOptionId = row[MemberMovieReviews.sentimentOptionId]?.value,
            comment = row[MemberMovieReviews.comment],
        )
}

/** Shared by [ExposedMovieRepository.create] and [ExposedMovieRepository.updateTmdbMetadata] -- both set the same
 * TMDB-sourced fields; `InsertStatement`/`UpdateStatement` both extend `UpdateBuilder`, so one function works for either. */
private fun UpdateBuilder<*>.applyTmdbMetadata(metadata: TmdbMovieMetadata) {
    this[Movies.tmdbId] = metadata.tmdbId
    this[Movies.originalTitle] = metadata.originalTitle
    this[Movies.englishTitle] = metadata.englishTitle
    this[Movies.year] = metadata.year
    this[Movies.director] = metadata.director
    this[Movies.runtimeMinutes] = metadata.runtimeMinutes
    this[Movies.genre] = metadata.genre
    this[Movies.country] = metadata.country
    this[Movies.tmdbRating] = metadata.tmdbRating
    this[Movies.metadataFetchedAt] = metadata.metadataFetchedAt
}
