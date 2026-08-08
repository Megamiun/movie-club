package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeasonServiceTest {
    private val seasonRepository = mockk<SeasonRepository>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val clubService = mockk<ClubService>()
    private val seasonService = SeasonService(seasonRepository, seriesRepository, clubService)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()
    private val seriesId = Uuid.random()
    private val globalSeriesId = Uuid.random()

    @Test
    fun `addSeason throws NotFoundException when series is missing`() {
        every { seriesRepository.findById(seriesId) } returns null

        assertFailsWith<NotFoundException> { seasonService.addSeason(seriesId, memberId, 1) }
        verify(exactly = 0) { seasonRepository.create(any(), any(), any()) }
    }

    @Test
    fun `addSeason denies non-members of the series' club`() {
        every { seriesRepository.findById(seriesId) } returns series()
        every { clubService.requireMembership(clubId, memberId) } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { seasonService.addSeason(seriesId, memberId, 1) }
    }

    @Test
    fun `addSeason creates a season under the global series`() {
        every { seriesRepository.findById(seriesId) } returns series()
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val expected = SeasonRow(Uuid.random(), globalSeriesId, 1, "Season 1")
        every { seasonRepository.create(globalSeriesId, 1, "Season 1") } returns expected

        assertEquals(expected, seasonService.addSeason(seriesId, memberId, 1, "Season 1"))
    }

    @Test
    fun `rate resolves the club through the season's global series`() {
        val seasonId = Uuid.random()
        every { seasonRepository.findById(seasonId) } returns SeasonRow(seasonId, globalSeriesId, 1)
        every { seriesRepository.findClubSeriesForMember(globalSeriesId, memberId) } returns series()

        val review = SeasonReviewRow(seasonId, memberId, comment = "solid season")
        every { seasonRepository.upsertReview(seasonId, memberId, comment = "solid season") } returns review

        assertEquals(review, seasonService.rate(seasonId, memberId, comment = "solid season"))
    }

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun series() = SeriesRow(
        id = seriesId,
        globalSeriesId = globalSeriesId,
        clubId = clubId,
        chosenById = memberId,
        imdbId = "tt0903747",
        tmdbId = "1396",
        originalTitle = "Breaking Bad",
        alternativeTitles = listOf(AlternativeTitle("US", "Breaking Bad")),
        displayTitlePreference = ORIGINAL,
        year = 2008,
        createdAt = Clock.System.now(),
    )
}
