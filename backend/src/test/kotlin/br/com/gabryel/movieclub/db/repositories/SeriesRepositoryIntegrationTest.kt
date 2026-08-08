package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeriesRepository
import br.com.gabryel.movieclub.db.tables.ClubMembers
import br.com.gabryel.movieclub.db.tables.ClubSeries
import br.com.gabryel.movieclub.db.tables.Clubs
import br.com.gabryel.movieclub.db.tables.MemberSeriesReviews
import br.com.gabryel.movieclub.db.tables.Members
import br.com.gabryel.movieclub.db.tables.Series
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
 * Verifies the global-catalog-vs-per-club-pick split for Series, and [SeriesRepository.findClubSeriesForMember]'s
 * cross-table membership lookup -- neither can be verified with mocks. Talks to a real (fresh, throwaway) Postgres,
 * like [ClubRepositoryIntegrationTest].
 */
class SeriesRepositoryIntegrationTest {
    companion object {
        init {
            TestDatabase.startFresh()
        }
    }

    private val seriesRepository = ExposedSeriesRepository()
    private val clubIds = mutableListOf<Uuid>()
    private val memberIds = mutableListOf<Uuid>()

    @AfterTest
    fun cleanUp() {
        transaction {
            val picks = ClubSeries.selectAll().where { ClubSeries.clubId inList clubIds }.toList()
            val pickIds = picks.map { it[ClubSeries.id] }
            val seriesIds = picks.map { it[ClubSeries.seriesId] }
            MemberSeriesReviews.deleteWhere { seriesId inList seriesIds }
            ClubSeries.deleteWhere { id inList pickIds }
            Series.deleteWhere { id inList seriesIds }
            ClubMembers.deleteWhere { clubId inList clubIds }
            Clubs.deleteWhere { id inList clubIds }
            Members.deleteWhere { id inList memberIds }
        }
    }

    @Test
    fun `two clubs picking the same imdb_id share one global catalog row`() {
        val clubA = newClub()
        val clubB = newClub()
        val member = newMember()

        val pickA = seriesRepository.create(clubA, member, "tt0903747", metadata())
        val pickB = seriesRepository.create(clubB, member, "tt0903747", metadata())

        val catalogRowCount = transaction { Series.selectAll().where { Series.imdbId eq "tt0903747" }.count() }

        assertNotEquals(pickA.id, pickB.id, "each club's pick should be its own row")
        assertEquals(pickA.globalSeriesId, pickB.globalSeriesId, "both picks should share the same catalog row")
        assertEquals(1, catalogRowCount)
    }

    @Test
    fun `refreshing metadata from one pick updates the shared catalog row for every pick`() {
        val clubA = newClub()
        val clubB = newClub()
        val member = newMember()
        val pickA = seriesRepository.create(clubA, member, "tt0903747", metadata(originalTitle = "Old Title"))
        val pickB = seriesRepository.create(clubB, member, "tt0903747", metadata(originalTitle = "Old Title"))

        seriesRepository.updateTmdbMetadata(pickA.id, metadata(originalTitle = "Breaking Bad"))

        assertEquals("Breaking Bad", seriesRepository.findById(pickA.id)!!.originalTitle)
        assertEquals("Breaking Bad", seriesRepository.findById(pickB.id)!!.originalTitle)
    }

    @Test
    fun `findClubSeriesForMember finds the pick for a member of the picking club`() {
        val club = newClub()
        val member = newMember()

        IntegrationFixtures.insertClubMembership(club, member)

        val pick = seriesRepository.create(club, member, "tt0903747", metadata())

        val found = seriesRepository.findClubSeriesForMember(pick.globalSeriesId, member)

        assertEquals(pick.id, found?.id)
    }

    @Test
    fun `findClubSeriesForMember returns null for a member of no club following the series`() {
        val club = newClub()
        val chooser = newMember()
        val outsider = newMember()

        IntegrationFixtures.insertClubMembership(club, chooser)

        val pick = seriesRepository.create(club, chooser, "tt0903747", metadata())

        val found = seriesRepository.findClubSeriesForMember(pick.globalSeriesId, outsider)

        assertNull(found)
    }

    @Test
    fun `a review is keyed by the global series id, visible through either club's pick`() {
        val clubA = newClub()
        val clubB = newClub()
        val member = newMember()
        val pickA = seriesRepository.create(clubA, member, "tt0903747", metadata())
        val pickB = seriesRepository.create(clubB, member, "tt0903747", metadata())

        seriesRepository.upsertReview(pickA.globalSeriesId, member, comment = "loved it")

        val reviewViaA = seriesRepository.findReview(pickA.globalSeriesId, member)
        val reviewViaB = seriesRepository.findReview(pickB.globalSeriesId, member)
        assertEquals("loved it", reviewViaA?.comment)
        assertEquals(reviewViaA, reviewViaB, "both picks share the same global series id, so the same review")
    }

    private fun metadata(originalTitle: String = "Breaking Bad") = TmdbSeriesMetadata(
        tmdbId = "1396",
        originalTitle = originalTitle,
        alternativeTitles = emptyList(),
        year = 2008,
        genre = listOf("Drama", "Crime"),
        originCountry = listOf("US"),
        productionCountries = listOf("United States of America"),
        creator = "Vince Gilligan",
    )

    private fun newMember() = IntegrationFixtures.insertMember().also { memberIds.add(it) }

    private fun newClub() = IntegrationFixtures.insertClub().also { clubIds.add(it) }
}
