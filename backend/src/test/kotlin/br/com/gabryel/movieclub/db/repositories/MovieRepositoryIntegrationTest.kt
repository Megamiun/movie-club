package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMovieRepository
import br.com.gabryel.movieclub.db.tables.ClubMembers
import br.com.gabryel.movieclub.db.tables.Clubs
import br.com.gabryel.movieclub.db.tables.MeetingMovies
import br.com.gabryel.movieclub.db.tables.Meetings
import br.com.gabryel.movieclub.db.tables.MemberMovieReviews
import br.com.gabryel.movieclub.db.tables.Members
import br.com.gabryel.movieclub.db.tables.Movies
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Verifies the global-catalog-vs-per-meeting-pick split that mocks can't: whether two picks of the same `imdb_id`
 * actually share one row in Postgres, whether a unique constraint/join produces the right data, etc. Talks to a
 * real (fresh, throwaway) Postgres, like [ClubRepositoryIntegrationTest].
 */
class MovieRepositoryIntegrationTest {
    companion object {
        init {
            TestDatabase.startFresh()
        }
    }

    private val movieRepository = ExposedMovieRepository()
    private val clubIds = mutableListOf<Uuid>()
    private val memberIds = mutableListOf<Uuid>()
    private val meetingIds = mutableListOf<Uuid>()

    @AfterTest
    fun cleanUp() {
        transaction {
            val picks = MeetingMovies.selectAll().where { MeetingMovies.meetingId inList meetingIds }.toList()
            val pickIds = picks.map { it[MeetingMovies.id] }
            val movieIds = picks.map { it[MeetingMovies.movieId] }
            MemberMovieReviews.deleteWhere { meetingMovieId inList pickIds }
            MeetingMovies.deleteWhere { id inList pickIds }
            Movies.deleteWhere { id inList movieIds }
            Meetings.deleteWhere { id inList meetingIds }
            ClubMembers.deleteWhere { clubId inList clubIds }
            Clubs.deleteWhere { id inList clubIds }
            Members.deleteWhere { id inList memberIds }
        }
    }

    @Test
    fun `two meetings picking the same imdb_id share one global catalog row`() {
        val member = newMember()
        val meetingA = newMeeting()
        val meetingB = newMeeting()

        val pickA = movieRepository.create(meetingA, member, "tt2911666", metadata())
        val pickB = movieRepository.create(meetingB, member, "tt2911666", metadata())

        val catalogRowCount = transaction { Movies.selectAll().where { Movies.imdbId eq "tt2911666" }.count() }

        assertNotEquals(pickA.id, pickB.id, "each meeting's pick should be its own row")
        assertEquals(1, catalogRowCount, "only one global catalog row should exist for a shared imdb_id")
    }

    @Test
    fun `refreshing metadata from one pick updates the shared catalog row for every pick`() {
        val member = newMember()
        val meetingA = newMeeting()
        val meetingB = newMeeting()
        val pickA = movieRepository.create(meetingA, member, "tt2911666", metadata(originalTitle = "Old Title"))
        val pickB = movieRepository.create(meetingB, member, "tt2911666", metadata(originalTitle = "Old Title"))

        movieRepository.updateTmdbMetadata(pickA.id, metadata(originalTitle = "John Wick"))

        assertEquals("John Wick", movieRepository.findById(pickA.id)!!.originalTitle)
        assertEquals("John Wick", movieRepository.findById(pickB.id)!!.originalTitle, "the other club's pick shares the same catalog row")
    }

    @Test
    fun `deleting a pick leaves the shared catalog row and other picks intact`() {
        val member = newMember()
        val meetingA = newMeeting()
        val meetingB = newMeeting()
        val pickA = movieRepository.create(meetingA, member, "tt2911666", metadata())
        val pickB = movieRepository.create(meetingB, member, "tt2911666", metadata())

        movieRepository.delete(pickA.id)

        assertNull(movieRepository.findById(pickA.id))
        assertEquals(pickB.id, movieRepository.findById(pickB.id)?.id)
        val catalogRowCount = transaction { Movies.selectAll().where { Movies.imdbId eq "tt2911666" }.count() }

        assertEquals(1, catalogRowCount, "the shared catalog row must survive deleting just one pick")
    }

    @Test
    fun `upsertReview round-trips through the pick id`() {
        val member = newMember()
        val meeting = newMeeting()
        val pick = movieRepository.create(meeting, member, "tt2911666", metadata())

        movieRepository.upsertReview(pick.id, member, comment = "great rewatch")
        val review = movieRepository.findReview(pick.id, member)

        assertEquals("great rewatch", review?.comment)
    }

    private fun metadata(originalTitle: String = "John Wick") = TmdbMovieMetadata(
        tmdbId = "245891",
        originalTitle = originalTitle,
        translations = emptyList(),
        year = 2014,
        director = "Chad Stahelski",
        runtimeMinutes = 101,
        genre = listOf("Action"),
        originCountry = listOf("US"),
        productionCountries = listOf("United States of America"),
    )

    private fun newMember() = IntegrationFixtures.insertMember().also { memberIds.add(it) }

    private fun newMeeting(): Uuid {
        val clubId = IntegrationFixtures.insertClub().also { clubIds.add(it) }
        return IntegrationFixtures.insertMeeting(clubId).also { meetingIds.add(it) }
    }
}
