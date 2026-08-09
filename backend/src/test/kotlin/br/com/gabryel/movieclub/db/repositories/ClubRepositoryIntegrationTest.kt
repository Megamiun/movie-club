package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.RatingScaleType
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.dto.RatingScaleRow
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedClubRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedEpisodeRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMemberRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMovieRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedRatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeasonRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeriesRepository
import br.com.gabryel.movieclub.db.tables.ClubMembers
import br.com.gabryel.movieclub.db.tables.Clubs
import br.com.gabryel.movieclub.db.tables.Members
import br.com.gabryel.movieclub.db.tables.RatingOptions
import br.com.gabryel.movieclub.db.tables.RatingScales
import br.com.gabryel.movieclub.service.ClubService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Verifies the nested-transaction assumption ClubService.createClub relies on: the service opens one
 * outer `transaction {}` and repository calls join it, so a failure partway through must leave nothing
 * committed. Every other test in this codebase mocks its repositories; this one deliberately talks to a
 * real (fresh, throwaway) Postgres because that assumption can't be verified with mocks.
 */
class ClubRepositoryIntegrationTest {
    private val clubRepository = ExposedClubRepository()
    private val insertedMemberIds = mutableListOf<Uuid>()

    @AfterTest
    fun cleanUp() {
        transaction {
            val clubIds = Clubs.selectAll().where { Clubs.name eq TEST_CLUB_NAME }.map { it[Clubs.id] }
            val scaleIds =
                RatingScales.selectAll().where { RatingScales.clubId inList clubIds }.map { it[RatingScales.id] }
            RatingOptions.deleteWhere { scaleId inList scaleIds }
            RatingScales.deleteWhere { clubId inList clubIds }
            ClubMembers.deleteWhere { clubId inList clubIds }
            Clubs.deleteWhere { id inList clubIds }
            Members.deleteWhere { id inList insertedMemberIds }
        }
    }

    @Test
    fun `createClub commits club, admin membership, and both rating scales together`() {
        val creatorId = insertTestMember()
        val clubService = ClubService(
            clubRepository,
            ExposedRatingScaleRepository(),
            ExposedMemberRepository(),
            ExposedMovieRepository(),
            ExposedSeriesRepository(),
            ExposedSeasonRepository(),
            ExposedEpisodeRepository(),
        )

        val club = clubService.createClub(TEST_CLUB_NAME, creatorId)

        assertEquals(1, club.members.size)
        assertEquals(creatorId, club.members.single().memberId)
        val scales = ExposedRatingScaleRepository().findScales(club.id)
        assertEquals(2, scales.size)
        assertTrue(scales.any { it.type == QUALITY })
        assertTrue(scales.any { it.type == SENTIMENT })
        scales.forEach { scale ->
            assertEquals(6, ExposedRatingScaleRepository().findOptions(scale.id).size)
        }
    }

    @Test
    fun `createClub rolls back everything when scale seeding fails partway through`() {
        val creatorId = insertTestMember()
        val failingScales = FailingAfterFirstScale(ExposedRatingScaleRepository())
        val clubService = ClubService(
            clubRepository,
            failingScales,
            ExposedMemberRepository(),
            ExposedMovieRepository(),
            ExposedSeriesRepository(),
            ExposedSeasonRepository(),
            ExposedEpisodeRepository(),
        )

        assertFailsWith<IllegalStateException> { clubService.createClub(TEST_CLUB_NAME, creatorId) }

        val persisted = transaction { Clubs.selectAll().where { Clubs.name eq TEST_CLUB_NAME }.toList() }
        assertTrue(persisted.isEmpty(), "Club row must not survive a failed createClub")
    }

    private fun insertTestMember(): Uuid =
        transaction {
            val result = Members.insert {
                it[Members.email] = "club-atomicity-${Uuid.random()}@example.com"
                it[Members.createdAt] = Clock.System.now()
            }
            result[Members.id].value
        }.also { insertedMemberIds.add(it) }

    private class FailingAfterFirstScale(private val delegate: RatingScaleRepository) : RatingScaleRepository by delegate {
        override fun createScale(clubId: Uuid, type: RatingScaleType): RatingScaleRow {
            if (type == SENTIMENT) error("Simulated failure seeding the sentiment scale")
            return delegate.createScale(clubId, type)
        }
    }

    companion object {
        private const val TEST_CLUB_NAME = "Atomicity Test Club"

        init {
            TestDatabase.startFresh()
        }
    }
}
