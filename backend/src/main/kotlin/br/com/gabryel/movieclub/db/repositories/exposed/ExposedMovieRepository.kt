package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import br.com.gabryel.movieclub.db.tables.MeetingMovies
import br.com.gabryel.movieclub.db.tables.MemberMovieReviews
import br.com.gabryel.movieclub.db.tables.Movies
import br.com.gabryel.movieclub.db.tables.People
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedMovieRepository : MovieRepository {
    override fun create(
        meetingId: Uuid,
        chosenById: Uuid,
        imdbId: String,
        metadata: TmdbMovieMetadata,
        mediaItemId: Uuid?,
        watchLink: String?,
    ): MovieRow = transaction {
        val movieId = findOrCreateMovie(imdbId, metadata, mediaItemId)
        val pickId = MeetingMovies.insert {
            it[MeetingMovies.meetingId] = meetingId
            it[MeetingMovies.movieId] = movieId
            it[MeetingMovies.chosenById] = chosenById
            it[MeetingMovies.displayTitlePreference] = ORIGINAL
            it[MeetingMovies.watchLink] = watchLink
            it[MeetingMovies.createdAt] = Clock.System.now()
        }[MeetingMovies.id].value

        findById(pickId)!!
    }

    override fun findById(id: Uuid): MovieRow? = transaction {
        joined()
            .selectAll()
            .where { MeetingMovies.id eq id }
            .map(::toRow)
            .singleOrNull()
    }

    override fun findByMeetingAndImdbId(meetingId: Uuid, imdbId: String): MovieRow? = transaction {
        joined()
            .selectAll()
            .where { (MeetingMovies.meetingId eq meetingId) and (Movies.imdbId eq imdbId) }
            .map(::toRow)
            .singleOrNull()
    }

    override fun listByMeeting(meetingId: Uuid): List<MovieRow> = transaction {
        joined()
            .selectAll()
            .where { MeetingMovies.meetingId eq meetingId }
            .map(::toRow)
    }

    override fun updateMeeting(movieId: Uuid, newMeetingId: Uuid): MovieRow = transaction {
        MeetingMovies.update({ MeetingMovies.id eq movieId }) {
            it[MeetingMovies.meetingId] = newMeetingId
        }
        findById(movieId)!!
    }

    override fun updateDisplayTitle(
        movieId: Uuid,
        customTitle: String?,
        preference: DisplayTitlePreference,
        displayLanguageCode: String?,
    ): MovieRow = transaction {
        MeetingMovies.update({ MeetingMovies.id eq movieId }) {
            it[MeetingMovies.customTitle] = customTitle
            it[MeetingMovies.displayTitlePreference] = preference
            it[MeetingMovies.displayLanguageCode] = displayLanguageCode
        }
        findById(movieId)!!
    }

    override fun updateWatchLink(movieId: Uuid, watchLink: String?): MovieRow = transaction {
        MeetingMovies.update({ MeetingMovies.id eq movieId }) {
            it[MeetingMovies.watchLink] = watchLink
        }
        findById(movieId)!!
    }

    /** Refreshes the shared global catalog row -- since multiple picks (even across clubs) can point at the same
     * `imdb_id`, this updates metadata for all of them at once, not just the given pick. */
    override fun updateTmdbMetadata(movieId: Uuid, metadata: TmdbMovieMetadata, mediaItemId: Uuid?): MovieRow = transaction {
        val globalMovieId = MeetingMovies
            .selectAll()
            .where { MeetingMovies.id eq movieId }
            .map { it[MeetingMovies.movieId].value }
            .single()
        Movies.update({ Movies.id eq globalMovieId }) {
            it.applyTmdbMetadata(metadata, mediaItemId)
        }
        findById(movieId)!!
    }

    /** Deletes only this pick (and its reviews) -- the shared global catalog row is left alone since other
     * meetings/clubs may still reference it. */
    override fun delete(movieId: Uuid) {
        transaction {
            MemberMovieReviews.deleteWhere { MemberMovieReviews.meetingMovieId eq movieId }
            MeetingMovies.deleteWhere { MeetingMovies.id eq movieId }
        }
    }

    override fun upsertReview(
        movieId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): MovieReviewRow = transaction {
        val exists = findReview(movieId, memberId) != null
        if (exists) {
            MemberMovieReviews.update({
                (MemberMovieReviews.meetingMovieId eq movieId) and (MemberMovieReviews.memberId eq memberId)
            }) {
                it[MemberMovieReviews.qualityOptionId] = qualityOptionId
                it[MemberMovieReviews.sentimentOptionId] = sentimentOptionId
                it[MemberMovieReviews.comment] = comment
            }
        } else {
            MemberMovieReviews.insert {
                it[MemberMovieReviews.meetingMovieId] = movieId
                it[MemberMovieReviews.memberId] = memberId
                it[MemberMovieReviews.qualityOptionId] = qualityOptionId
                it[MemberMovieReviews.sentimentOptionId] = sentimentOptionId
                it[MemberMovieReviews.comment] = comment
            }
        }
        findReview(movieId, memberId)!!
    }

    override fun findReview(movieId: Uuid, memberId: Uuid): MovieReviewRow? = transaction {
        MemberMovieReviews
            .selectAll()
            .where { (MemberMovieReviews.meetingMovieId eq movieId) and (MemberMovieReviews.memberId eq memberId) }
            .map(::toReviewRow)
            .singleOrNull()
    }

    override fun listReviews(movieId: Uuid): List<MovieReviewRow> = transaction {
        MemberMovieReviews
            .selectAll()
            .where { MemberMovieReviews.meetingMovieId eq movieId }
            .map(::toReviewRow)
    }

    override fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid): Unit = transaction {
        MemberMovieReviews.update({ MemberMovieReviews.qualityOptionId eq oldOptionId }) {
            it[MemberMovieReviews.qualityOptionId] = newOptionId
        }
        MemberMovieReviews.update({ MemberMovieReviews.sentimentOptionId eq oldOptionId }) {
            it[MemberMovieReviews.sentimentOptionId] = newOptionId
        }
    }

    /** Finds the global catalog row for [imdbId], creating it from [metadata] if this is the first time any club
     * has picked it; otherwise overwrites its TMDB data with [metadata] (harmless -- same `imdbId`, same canonical
     * TMDB response either way) so a refresh started from any one pick keeps the shared row current. */
    private fun findOrCreateMovie(imdbId: String, metadata: TmdbMovieMetadata, mediaItemId: Uuid?): Uuid {
        val existing = Movies
            .selectAll()
            .where { Movies.imdbId eq imdbId }
            .map { it[Movies.id].value }
            .singleOrNull()

        if (existing != null) {
            Movies.update({ Movies.id eq existing }) { it.applyTmdbMetadata(metadata, mediaItemId) }
            return existing
        }

        return Movies.insert {
            it[Movies.imdbId] = imdbId
            it[Movies.createdAt] = Clock.System.now()
            it.applyTmdbMetadata(metadata, mediaItemId)
        }[Movies.id].value
    }

    private fun joined() = MeetingMovies.innerJoin(Movies).leftJoin(People)

    private fun toRow(row: ResultRow) = MovieRow(
        id = row[MeetingMovies.id].value,
        meetingId = row[MeetingMovies.meetingId].value,
        chosenById = row[MeetingMovies.chosenById].value,
        imdbId = row[Movies.imdbId],
        tmdbId = row[Movies.tmdbId],
        originalTitle = row[Movies.originalTitle],
        originalLanguage = row[Movies.originalLanguage],
        translations = row[Movies.translations],
        customTitle = row[MeetingMovies.customTitle],
        displayTitlePreference = row[MeetingMovies.displayTitlePreference],
        displayLanguageCode = row[MeetingMovies.displayLanguageCode],
        year = row[Movies.year],
        director = row.getOrNull(People.name),
        directorImdbId = row.getOrNull(People.imdbId),
        runtimeMinutes = row[Movies.runtimeMinutes],
        genre = row[Movies.genre],
        originCountry = row[Movies.originCountry],
        productionCountries = row[Movies.productionCountries],
        imdbRating = row[Movies.imdbRating],
        posterS3Key = row[Movies.posterS3Key],
        watchLink = row[MeetingMovies.watchLink],
        metadataFetchedAt = row[Movies.metadataFetchedAt],
        createdAt = row[MeetingMovies.createdAt],
    )

    private fun toReviewRow(row: ResultRow) = MovieReviewRow(
        movieId = row[MemberMovieReviews.meetingMovieId].value,
        memberId = row[MemberMovieReviews.memberId].value,
        qualityOptionId = row[MemberMovieReviews.qualityOptionId]?.value,
        sentimentOptionId = row[MemberMovieReviews.sentimentOptionId]?.value,
        comment = row[MemberMovieReviews.comment],
    )
}

/** Shared by [ExposedMovieRepository.findOrCreateMovie] and [ExposedMovieRepository.updateTmdbMetadata] -- both set
 * the same TMDB-sourced fields on the global [Movies] row; `InsertStatement`/`UpdateStatement` both extend
 * `UpdateBuilder`, so one function works for either. */
private fun UpdateBuilder<*>.applyTmdbMetadata(metadata: TmdbMovieMetadata, mediaItemId: Uuid?) {
    this[Movies.tmdbId] = metadata.tmdbId
    this[Movies.originalTitle] = metadata.originalTitle
    this[Movies.originalLanguage] = metadata.originalLanguage
    this[Movies.translations] = metadata.translations
    this[Movies.year] = metadata.year
    this[Movies.directorPersonId] = metadata.directorPersonId
    this[Movies.runtimeMinutes] = metadata.runtimeMinutes
    this[Movies.genre] = metadata.genre
    this[Movies.originCountry] = metadata.originCountry
    this[Movies.productionCountries] = metadata.productionCountries
    this[Movies.imdbRating] = metadata.imdbRating
    this[Movies.metadataFetchedAt] = metadata.metadataFetchedAt
    this[Movies.mediaItemId] = mediaItemId
}
