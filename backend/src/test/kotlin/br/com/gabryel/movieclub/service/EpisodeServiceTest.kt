package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRow
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRow
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
    private val episodeService = EpisodeService(
        episodeRepository,
        seasonRepository,
        seriesRepository,
        meetingRepository,
        clubService,
        tmdbClient,
    )

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()
    private val seriesId = Uuid.random()
    private val seasonId = Uuid.random()

    @Test
    fun `addEpisode throws NotFoundException when season is missing`(): Unit =
        runBlocking {
            every { seasonRepository.findById(seasonId) } returns null

            assertFailsWith<NotFoundException> { episodeService.addEpisode(seasonId, memberId, 1) }
        }

    @Test
    fun `addEpisode throws BadRequestException when meeting belongs to a different club`(): Unit =
        runBlocking {
            val meetingId = Uuid.random()
            every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, seriesId, 1, null)
            every { seriesRepository.findById(seriesId) } returns series()
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            every { meetingRepository.findById(meetingId) } returns MeetingRow(
                meetingId,
                Uuid.random(),
                LocalDate(2026, 1, 5),
                null,
            )

            assertFailsWith<BadRequestException> {
                episodeService.addEpisode(seasonId, memberId, 1, meetingId = meetingId)
            }
        }

    @Test
    fun `addEpisode creates an unscheduled episode without a meeting, ignoring a failed TMDB enrichment`() =
        runBlocking {
            every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, seriesId, 1, null)
            every { seriesRepository.findById(seriesId) } returns series()
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            val expected = episode()
            every { episodeRepository.create(seasonId, 1, "Pilot", null) } returns expected
            every { episodeRepository.findById(expected.id) } returns expected

            assertEquals(expected, episodeService.addEpisode(seasonId, memberId, 1, "Pilot"))
        }

    @Test
    fun `rate resolves the club through season and series`() {
        val episodeId = Uuid.random()
        every { episodeRepository.findById(episodeId) } returns episode(episodeId)
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, seriesId, 1, null)
        every { seriesRepository.findById(seriesId) } returns series()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val review =
            br.com.gabryel.movieclub.db.repositories
                .EpisodeReviewRow(episodeId, memberId, null, null, "good pilot")
        every { episodeRepository.upsertReview(episodeId, memberId, null, null, "good pilot") } returns review

        assertEquals(review, episodeService.rate(episodeId, memberId, null, null, "good pilot"))
    }

    private fun episode(id: Uuid = Uuid.random()) = EpisodeRow(
        id = id,
        seasonId = seasonId,
        number = 1,
        title = "Pilot",
        meetingId = null,
        airDate = null,
        overview = null,
        runtimeMinutes = null,
        director = null,
        tmdbRating = null,
        metadataFetchedAt = null,
    )

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun series() =
        SeriesRow(
            id = seriesId,
            clubId = clubId,
            chosenById = memberId,
            imdbId = "tt0903747",
            tmdbId = "1396",
            originalTitle = "Breaking Bad",
            englishTitle = "Breaking Bad",
            customTitle = null,
            displayTitlePreference = ORIGINAL,
            year = 2008,
            genre = null,
            country = null,
            tmdbRating = null,
            creator = null,
            posterS3Key = null,
            metadataFetchedAt = null,
            createdAt = Clock.System.now(),
        )
}
