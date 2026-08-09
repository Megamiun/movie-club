package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.Translation
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbCrewMember
import br.com.gabryel.movieclub.service.tmdb.TmdbEpisodeDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbExternalIds
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class EpisodeServiceTest {
    private val episodeRepository = mockk<EpisodeRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val meetingRepository = mockk<MeetingRepository>()
    private val clubService = mockk<ClubService>()
    private val tmdbClient = mockk<TmdbClient>()
    private val omdbClient = mockk<OmdbClient>()
    private val watchlistRepository = mockk<WatchlistRepository>()
    private val episodeService = EpisodeService(
        episodeRepository,
        seasonRepository,
        seriesRepository,
        meetingRepository,
        clubService,
        tmdbClient,
        omdbClient,
        watchlistRepository,
    )

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()
    private val seriesId = Uuid.random()
    private val globalSeriesId = Uuid.random()
    private val seasonId = Uuid.random()

    init {
        coEvery { omdbClient.getImdbRating(any()) } returns null
    }

    @Test
    fun `addEpisode throws NotFoundException when season is missing`(): Unit =
        runBlocking {
            every { seasonRepository.findById(seasonId) } returns null

            assertFailsWith<NotFoundException> { episodeService.addEpisode(seasonId, memberId, 1) }
        }

    @Test
    fun `addEpisode throws ForbiddenException when acting member isn't in the target meeting's club`(): Unit =
        runBlocking {
            val meetingId = Uuid.random()
            val otherClubId = Uuid.random()
            val created = episode()
            every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
            every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()
            every { episodeRepository.create(seasonId, 1, "Pilot") } returns created
            every { episodeRepository.findById(created.id) } returns created

            every {
                meetingRepository.findById(meetingId)
            } returns MeetingRow(meetingId, otherClubId, LocalDate(2026, 1, 5))

            every { clubService.requireMembership(otherClubId, memberId) } throws
                ForbiddenException("Not a member of this club")

            assertFailsWith<ForbiddenException> {
                episodeService.addEpisode(seasonId, memberId, 1, "Pilot", meetingId = meetingId)
            }
        }

    @Test
    fun `addEpisode creates an unscheduled episode without a meeting, ignoring a failed TMDB enrichment`() =
        runBlocking {
            every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
            every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()

            val expected = episode()
            every { episodeRepository.create(seasonId, 1, "Pilot") } returns expected
            every { episodeRepository.findById(expected.id) } returns expected

            assertEquals(expected, episodeService.addEpisode(seasonId, memberId, 1, "Pilot"))
        }

    @Test
    fun `assignToMeeting throws NotFoundException when the episode doesn't exist`() {
        val episodeId = Uuid.random()
        val meetingId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns null

        assertFailsWith<NotFoundException> { episodeService.assignToMeeting(episodeId, memberId, meetingId) }
    }

    @Test
    fun `assignToMeeting inserts the meeting_episodes row when the acting member is in the meeting's club`() {
        val episodeId = Uuid.random()
        val meetingId = Uuid.random()
        val expected = episode(episodeId)
        every { episodeRepository.findById(episodeId) } returns expected
        every { meetingRepository.findById(meetingId) } returns MeetingRow(meetingId, clubId, LocalDate(2026, 1, 5))
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { episodeRepository.listByMeeting(meetingId) } returns emptyList()
        every { episodeRepository.assignToMeeting(episodeId, meetingId) } just Runs

        assertEquals(expected, episodeService.assignToMeeting(episodeId, memberId, meetingId))

        verify { episodeRepository.assignToMeeting(episodeId, meetingId) }
    }

    @Test
    fun `assignToMeeting throws BadRequestException when the episode is already in this meeting`() {
        val episodeId = Uuid.random()
        val meetingId = Uuid.random()
        val expected = episode(episodeId)
        every { episodeRepository.findById(episodeId) } returns expected
        every { meetingRepository.findById(meetingId) } returns MeetingRow(meetingId, clubId, LocalDate(2026, 1, 5))
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { episodeRepository.listByMeeting(meetingId) } returns listOf(expected)

        assertFailsWith<BadRequestException> { episodeService.assignToMeeting(episodeId, memberId, meetingId) }
        verify(exactly = 0) { episodeRepository.assignToMeeting(any(), any()) }
    }

    @Test
    fun `assignToMeeting throws ForbiddenException when acting member isn't in the meeting's club`() {
        val episodeId = Uuid.random()
        val meetingId = Uuid.random()
        val otherClubId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { meetingRepository.findById(meetingId) } returns MeetingRow(meetingId, otherClubId, LocalDate(2026, 1, 5))
        every { clubService.requireMembership(otherClubId, memberId) } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { episodeService.assignToMeeting(episodeId, memberId, meetingId) }
    }

    @Test
    fun `unassignFromMeeting removes the meeting_episodes row when the acting member is in the meeting's club`() {
        val episodeId = Uuid.random()
        val meetingId = Uuid.random()
        val expected = episode(episodeId)
        every { episodeRepository.findById(episodeId) } returns expected
        every { meetingRepository.findById(meetingId) } returns MeetingRow(meetingId, clubId, LocalDate(2026, 1, 5))
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { episodeRepository.unassignFromMeeting(episodeId, meetingId) } just Runs

        assertEquals(expected, episodeService.unassignFromMeeting(episodeId, memberId, meetingId))

        verify { episodeRepository.unassignFromMeeting(episodeId, meetingId) }
    }

    @Test
    fun `refreshMetadata resolves the director's IMDB id via a TMDB person lookup`() = runBlocking {
        val episodeId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
        every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()
        coEvery { tmdbClient.getEpisodeDetails(1396, 1, 1) } returns TmdbEpisodeDetails(
            name = "Pilot",
            episodeNumber = 1,
            crew = listOf(TmdbCrewMember("Vince Gilligan", "Director", id = 66633)),
        )
        coEvery { tmdbClient.getPersonExternalIds(66633) } returns TmdbExternalIds(imdbId = "nm0316704")

        val updated = episode(episodeId)
        every {
            episodeRepository.updateTmdbMetadata(episodeId, match { it.directorImdbId == "nm0316704" })
        } returns updated

        assertEquals(updated, episodeService.refreshMetadata(episodeId, memberId))
    }

    @Test
    fun `refreshMetadata tolerates a failed director lookup instead of throwing`() = runBlocking {
        val episodeId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
        every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()
        coEvery { tmdbClient.getEpisodeDetails(1396, 1, 1) } returns TmdbEpisodeDetails(
            name = "Pilot",
            episodeNumber = 1,
            crew = listOf(TmdbCrewMember("Vince Gilligan", "Director", id = 66633)),
        )
        coEvery { tmdbClient.getPersonExternalIds(66633) } throws RuntimeException("TMDB is down")

        val updated = episode(episodeId)
        every {
            episodeRepository.updateTmdbMetadata(episodeId, match { it.directorImdbId == null })
        } returns updated

        assertEquals(updated, episodeService.refreshMetadata(episodeId, memberId))
    }

    @Test
    fun `refreshMetadata fetches the episode's own IMDB rating via OMDb once TMDB resolves its imdb_id`() = runBlocking {
        val episodeId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
        every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()
        coEvery { tmdbClient.getEpisodeDetails(1396, 1, 1) } returns TmdbEpisodeDetails(
            name = "Pilot",
            episodeNumber = 1,
            externalIds = TmdbExternalIds(imdbId = "tt0959621"),
        )
        coEvery { omdbClient.getImdbRating("tt0959621") } returns BigDecimal("8.2")

        val updated = episode(episodeId)
        every {
            episodeRepository.updateTmdbMetadata(episodeId, match { it.imdbRating == BigDecimal("8.2") })
        } returns updated

        assertEquals(updated, episodeService.refreshMetadata(episodeId, memberId))
    }

    @Test
    fun `refreshMetadata never calls OMDb when TMDB has no imdb_id for the episode yet`() = runBlocking {
        val episodeId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
        every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()
        coEvery { tmdbClient.getEpisodeDetails(1396, 1, 1) } returns TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)
        every { episodeRepository.updateTmdbMetadata(episodeId, any()) } returns episode(episodeId)

        episodeService.refreshMetadata(episodeId, memberId)

        coVerify(exactly = 0) { omdbClient.getImdbRating(any()) }
    }

    @Test
    fun `listNextSuggestions returns one suggestion per series, skipping series with nothing left to suggest`() {
        val clubId = this.clubId
        val fullyScheduledSeriesGlobalId = Uuid.random()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { seriesRepository.listByClub(clubId) } returns listOf(
            series(),
            series().copy(globalSeriesId = fullyScheduledSeriesGlobalId, customTitle = "Fully Scheduled Show"),
        )
        every { episodeRepository.findNextUnscheduled(clubId, globalSeriesId) } returns episode()
        every { episodeRepository.findNextUnscheduled(clubId, fullyScheduledSeriesGlobalId) } returns null
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)

        val result = episodeService.listNextSuggestions(clubId, memberId)

        assertEquals(1, result.size)
        assertEquals("Breaking Bad", result.single().seriesTitle)
        assertEquals(1, result.single().seasonNumber)
    }

    @Test
    fun `listNextSuggestions skips an ended series the club isn't watchlisting`() {
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { seriesRepository.listByClub(clubId) } returns listOf(series().copy(status = "Ended"))
        every { watchlistRepository.existsByClubAndMediaItemImdbId(clubId, "tt0903747") } returns false

        val result = episodeService.listNextSuggestions(clubId, memberId)

        assertEquals(0, result.size)
        verify(exactly = 0) { episodeRepository.findNextUnscheduled(any(), any()) }
    }

    @Test
    fun `listNextSuggestions still suggests an ended series the club is watchlisting`() {
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { seriesRepository.listByClub(clubId) } returns listOf(series().copy(status = "Ended"))
        every { watchlistRepository.existsByClubAndMediaItemImdbId(clubId, "tt0903747") } returns true
        every { episodeRepository.findNextUnscheduled(clubId, globalSeriesId) } returns episode()
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)

        val result = episodeService.listNextSuggestions(clubId, memberId)

        assertEquals(1, result.size)
    }

    @Test
    fun `rate resolves the club through season and the global series`() {
        val episodeId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
        every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()
        val review = EpisodeReviewRow(episodeId, memberId, comment = "good pilot")
        every { episodeRepository.upsertReview(episodeId, memberId, comment = "good pilot") } returns review

        assertEquals(review, episodeService.rate(episodeId, memberId, comment = "good pilot"))
    }

    private fun episode(id: Uuid = Uuid.random()) = EpisodeRow(id = id, seasonId = seasonId, number = 1, title = "Pilot")

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun series() = SeriesRow(
        id = seriesId,
        globalSeriesId = globalSeriesId,
        clubId = clubId,
        chosenById = memberId,
        imdbId = "tt0903747",
        tmdbId = "1396",
        originalTitle = "Breaking Bad",
        translations = listOf(Translation("en", "US", "English", "Breaking Bad")),
        displayTitlePreference = ORIGINAL,
        year = 2008,
        createdAt = Clock.System.now(),
    )
}
