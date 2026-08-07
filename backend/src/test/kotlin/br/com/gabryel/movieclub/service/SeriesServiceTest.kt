package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.repositories.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.SeriesRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbTvDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbTvSummary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesServiceTest {
    private val seriesRepository = mockk<SeriesRepository>()
    private val clubService = mockk<ClubService>()
    private val tmdbClient = mockk<TmdbClient>()
    private val seriesService = SeriesService(seriesRepository, clubService, tmdbClient)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()

    @Test
    fun `addSeries throws BadRequestException when TMDB has no match`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.findTvByImdbId("tt0903747") } returns null

            assertFailsWith<BadRequestException> { seriesService.addSeries(clubId, memberId, "tt0903747") }
        }

    @Test
    fun `addSeries creates a series populated entirely from TMDB`() =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.findTvByImdbId("tt0903747") } returns TmdbTvSummary(id = 1396)
            coEvery { tmdbClient.getTvDetails(1396) } returns
                TmdbTvDetails(originalName = "Breaking Bad", name = "Breaking Bad", firstAirDate = "2008-01-20")
            val created = series()
            every {
                seriesRepository.create(
                    clubId = clubId,
                    chosenById = memberId,
                    imdbId = "tt0903747",
                    metadata = match {
                        it.tmdbId == "1396" &&
                            it.originalTitle == "Breaking Bad" &&
                            it.englishTitle == "Breaking Bad" &&
                            it.year == 2008 &&
                            it.genre == emptyList<String>() &&
                            it.country == emptyList<String>() &&
                            it.tmdbRating == null &&
                            it.creator == null
                    },
                )
            } returns created

            assertEquals(created, seriesService.addSeries(clubId, memberId, "tt0903747"))
        }

    @Test
    fun `updateDisplayTitle throws BadRequestException when CUSTOM has no title`() {
        val seriesId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> {
            seriesService.updateDisplayTitle(seriesId, memberId, null, DisplayTitlePreference.CUSTOM)
        }
    }

    @Test
    fun `rate rejects an option from the wrong scale type`() {
        val seriesId = Uuid.random()
        val optionId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { clubService.validateRatingOption(clubId, optionId, QUALITY) } throws
            BadRequestException("Rating option is not a QUALITY option")

        assertFailsWith<BadRequestException> { seriesService.rate(seriesId, memberId, optionId, null, null) }
    }

    @Test
    fun `rate accepts independently optional quality and sentiment`() {
        val seriesId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val review = SeriesReviewRow(seriesId, memberId, null, null, "great")
        every { seriesRepository.upsertReview(seriesId, memberId, null, null, "great") } returns review

        assertEquals(review, seriesService.rate(seriesId, memberId, null, null, "great"))
    }

    @Test
    fun `requireSeriesAccess denies non-members`() {
        val seriesId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId)
        every { clubService.requireMembership(clubId, memberId) } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { seriesService.listReviews(seriesId, memberId) }
    }

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun series(id: Uuid = Uuid.random()) =
        SeriesRow(
            id = id,
            clubId = clubId,
            chosenById = memberId,
            imdbId = "tt0903747",
            tmdbId = "1396",
            originalTitle = "Breaking Bad",
            englishTitle = "Breaking Bad",
            customTitle = null,
            displayTitlePreference = ORIGINAL,
            year = 2008,
            genre = listOf("Drama", "Crime"),
            country = listOf("US"),
            tmdbRating = null,
            creator = "Vince Gilligan",
            posterS3Key = null,
            metadataFetchedAt = null,
            createdAt = Clock.System.now(),
        )
}
