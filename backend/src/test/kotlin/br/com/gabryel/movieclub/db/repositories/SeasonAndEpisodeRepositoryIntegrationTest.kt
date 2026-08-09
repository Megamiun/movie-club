package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.exposed.ExposedEpisodeRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeasonRepository
import br.com.gabryel.movieclub.db.tables.Clubs
import br.com.gabryel.movieclub.db.tables.Episodes
import br.com.gabryel.movieclub.db.tables.MeetingEpisodes
import br.com.gabryel.movieclub.db.tables.Meetings
import br.com.gabryel.movieclub.db.tables.MemberEpisodeReviews
import br.com.gabryel.movieclub.db.tables.MemberSeasonReviews
import br.com.gabryel.movieclub.db.tables.Seasons
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
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Verifies Season/Episode's global dedup ((seriesId, number) / (seasonId, number)) and the [MeetingEpisodes] join
 * that lets multiple clubs independently schedule the same global episode to their own meetings -- none of which
 * mocks can verify. Talks to a real (fresh, throwaway) Postgres, like [ClubRepositoryIntegrationTest].
 */
class SeasonAndEpisodeRepositoryIntegrationTest {
    companion object {
        init {
            TestDatabase.startFresh()
        }
    }

    private val seasonRepository = ExposedSeasonRepository()
    private val episodeRepository = ExposedEpisodeRepository()
    private val seriesIds = mutableListOf<Uuid>()
    private val clubIds = mutableListOf<Uuid>()

    @AfterTest
    fun cleanUp() {
        transaction {
            val seasonIds = Seasons.selectAll().where { Seasons.seriesId inList seriesIds }.map { it[Seasons.id] }
            val episodeIds = Episodes.selectAll().where { Episodes.seasonId inList seasonIds }.map { it[Episodes.id] }
            val meetingIds = Meetings.selectAll().where { Meetings.clubId inList clubIds }.map { it[Meetings.id] }
            MemberEpisodeReviews.deleteWhere { episodeId inList episodeIds }
            MeetingEpisodes.deleteWhere { episodeId inList episodeIds }
            Episodes.deleteWhere { id inList episodeIds }
            MemberSeasonReviews.deleteWhere { seasonId inList seasonIds }
            Seasons.deleteWhere { id inList seasonIds }
            Series.deleteWhere { id inList seriesIds }
            Meetings.deleteWhere { id inList meetingIds }
            Clubs.deleteWhere { id inList clubIds }
        }
    }

    @Test
    fun `creating the same season number twice returns the same global row`() {
        val series = newSeries()

        val first = seasonRepository.create(series, 1, "Season 1")
        val second = seasonRepository.create(series, 1, "A different title, ignored on the second call")

        assertEquals(first.id, second.id)
        assertEquals("Season 1", second.title, "the existing row wins -- the second call's title is not applied")
    }

    @Test
    fun `two different series each get their own season 1 without colliding`() {
        val seriesA = newSeries()
        val seriesB = newSeries()

        val seasonA = seasonRepository.create(seriesA, 1)
        val seasonB = seasonRepository.create(seriesB, 1)

        assertNotEquals(seasonA.id, seasonB.id)
    }

    @Test
    fun `creating the same episode number twice returns the same global row`() {
        val season = seasonRepository.create(newSeries(), 1)

        val first = episodeRepository.create(season.id, 1, "Pilot")
        val second = episodeRepository.create(season.id, 1, "Ignored title")

        assertEquals(first.id, second.id)
    }

    @Test
    fun `assignToMeeting is idempotent`() {
        val season = seasonRepository.create(newSeries(), 1)
        val episode = episodeRepository.create(season.id, 1, "Pilot")
        val meeting = newMeeting()

        episodeRepository.assignToMeeting(episode.id, meeting)
        episodeRepository.assignToMeeting(episode.id, meeting)

        val assignmentCount = transaction {
            MeetingEpisodes.selectAll().where { MeetingEpisodes.episodeId eq episode.id }.count()
        }
        assertEquals(1, assignmentCount, "assigning the same episode to the same meeting twice must not duplicate")
    }

    @Test
    fun `two clubs independently assign the same global episode to two different meetings`() {
        val season = seasonRepository.create(newSeries(), 1)
        val episode = episodeRepository.create(season.id, 1, "Pilot")
        val meetingA = newMeeting()
        val meetingB = newMeeting()

        episodeRepository.assignToMeeting(episode.id, meetingA)
        episodeRepository.assignToMeeting(episode.id, meetingB)

        assertTrue(episodeRepository.listByMeeting(meetingA).any { it.id == episode.id })
        assertTrue(episodeRepository.listByMeeting(meetingB).any { it.id == episode.id })
    }

    @Test
    fun `unassignFromMeeting removes only that meeting's assignment`() {
        val season = seasonRepository.create(newSeries(), 1)
        val episode = episodeRepository.create(season.id, 1, "Pilot")
        val meetingA = newMeeting()
        val meetingB = newMeeting()
        episodeRepository.assignToMeeting(episode.id, meetingA)
        episodeRepository.assignToMeeting(episode.id, meetingB)

        episodeRepository.unassignFromMeeting(episode.id, meetingA)

        assertTrue(episodeRepository.listByMeeting(meetingA).none { it.id == episode.id })
        assertTrue(episodeRepository.listByMeeting(meetingB).any { it.id == episode.id })
    }

    @Test
    fun `findNextUnscheduled returns the earliest episode not yet scheduled to any of the club's meetings`() {
        val series = newSeries()
        val season1 = seasonRepository.create(series, 1)
        val season2 = seasonRepository.create(series, 2)
        val episode1 = episodeRepository.create(season1.id, 1, "Pilot")
        episodeRepository.create(season1.id, 2, "Episode 2")
        episodeRepository.create(season2.id, 1, "Season 2 Premiere")
        val clubId = IntegrationFixtures.insertClub().also { clubIds.add(it) }
        episodeRepository.assignToMeeting(episode1.id, IntegrationFixtures.insertMeeting(clubId))

        val next = episodeRepository.findNextUnscheduled(clubId, series)

        assertEquals(2, next?.number, "episode 1 is already scheduled, so season 1's episode 2 is next")
        assertEquals(season1.id, next?.seasonId)
    }

    @Test
    fun `findNextUnscheduled returns null once every known episode has been scheduled`() {
        val series = newSeries()
        val season = seasonRepository.create(series, 1)
        val episode = episodeRepository.create(season.id, 1, "Pilot")
        val clubId = IntegrationFixtures.insertClub().also { clubIds.add(it) }
        episodeRepository.assignToMeeting(episode.id, IntegrationFixtures.insertMeeting(clubId))

        assertEquals(null, episodeRepository.findNextUnscheduled(clubId, series))
    }

    @Test
    fun `findNextUnscheduled is scoped per club -- another club's scheduling doesn't count`() {
        val series = newSeries()
        val season = seasonRepository.create(series, 1)
        val episode = episodeRepository.create(season.id, 1, "Pilot")
        val clubA = IntegrationFixtures.insertClub().also { clubIds.add(it) }
        val clubB = IntegrationFixtures.insertClub().also { clubIds.add(it) }
        episodeRepository.assignToMeeting(episode.id, IntegrationFixtures.insertMeeting(clubA))

        val next = episodeRepository.findNextUnscheduled(clubB, series)

        assertEquals(episode.id, next?.id, "club B hasn't scheduled this episode itself, so it's still next for them")
    }

    private fun newSeries() = IntegrationFixtures.insertSeries().also { seriesIds.add(it) }

    private fun newMeeting(): Uuid {
        val clubId = IntegrationFixtures.insertClub().also { clubIds.add(it) }
        return IntegrationFixtures.insertMeeting(clubId)
    }
}
