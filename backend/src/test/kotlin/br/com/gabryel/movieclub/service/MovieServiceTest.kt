package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbMovieDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbMovieSummary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MovieServiceTest {
    private val movieRepository = mockk<MovieRepository>()
    private val meetingRepository = mockk<MeetingRepository>()
    private val clubService = mockk<ClubService>()
    private val tmdbClient = mockk<TmdbClient>()
    private val movieService = MovieService(movieRepository, meetingRepository, clubService, tmdbClient)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()
    private val meetingId = Uuid.random()

    @Test
    fun `addMovie throws BadRequestException for an unparseable imdb id`(): Unit = runBlocking {
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> { movieService.addMovie(meetingId, memberId, "not-an-imdb-id") }
    }

    @Test
    fun `addMovie throws BadRequestException when TMDB has no match`(): Unit = runBlocking {
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        coEvery { tmdbClient.findByImdbId("tt4857264") } returns null

        assertFailsWith<BadRequestException> { movieService.addMovie(meetingId, memberId, "tt4857264") }
    }

    @Test
    fun `addMovie creates a movie populated entirely from TMDB`() = runBlocking {
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        coEvery { tmdbClient.findByImdbId("tt4857264") } returns TmdbMovieSummary(id = 411088)

        coEvery { tmdbClient.getMovieDetails(411088) } returns TmdbMovieDetails(
            originalTitle = "Contratiempo",
            title = "The Invisible Guest",
            releaseDate = "2017-01-06",
            runtime = 107,
        )

        val created = movie()
        every {
            movieRepository.create(
                meetingId = meetingId,
                chosenById = memberId,
                imdbId = "tt4857264",
                metadata = match {
                    it.tmdbId == "411088" &&
                        it.originalTitle == "Contratiempo" &&
                        it.alternativeTitles == emptyList<AlternativeTitle>() &&
                        it.year == 2017 &&
                        it.director == null &&
                        it.runtimeMinutes == 107 &&
                        it.genre == emptyList<String>() &&
                        it.originCountry == emptyList<String>() &&
                        it.tmdbRating == null
                },
            )
        } returns created

        assertEquals(created, movieService.addMovie(meetingId, memberId, "https://www.imdb.com/title/tt4857264/"))
    }

    @Test
    fun `rate throws BadRequestException when quality option belongs to sentiment scale`() {
        val movieId = Uuid.random()
        val optionId = Uuid.random()
        every { movieRepository.findById(movieId) } returns movie(id = movieId)
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { clubService.validateRatingOption(clubId, optionId, QUALITY) } throws
            BadRequestException("Rating option is not a QUALITY option")

        assertFailsWith<BadRequestException> { movieService.rate(movieId, memberId, optionId) }
    }

    @Test
    fun `rate accepts independently optional quality and sentiment`() {
        val movieId = Uuid.random()
        every { movieRepository.findById(movieId) } returns movie(id = movieId)
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val review = MovieReviewRow(movieId, memberId, comment = "great")
        every { movieRepository.upsertReview(movieId, memberId, comment = "great") } returns review

        assertEquals(review, movieService.rate(movieId, memberId, comment = "great"))
    }

    @Test
    fun `updateDisplayTitle throws BadRequestException when CUSTOM has no title`() {
        val movieId = Uuid.random()
        every { movieRepository.findById(movieId) } returns movie(id = movieId)
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> {
            movieService.updateDisplayTitle(movieId, memberId, preference = CUSTOM)
        }
    }

    @Test
    fun `requireMovieAccess denies non-members`() {
        val movieId = Uuid.random()
        every { movieRepository.findById(movieId) } returns movie(id = movieId)
        every { meetingRepository.findById(meetingId) } returns meeting()
        every { clubService.requireMembership(clubId, memberId) } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { movieService.listReviews(movieId, memberId) }
    }

    private fun meeting() = MeetingRow(meetingId, clubId, LocalDate(2026, 1, 5))

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun movie(id: Uuid = Uuid.random()) = MovieRow(
        id = id,
        meetingId = meetingId,
        chosenById = memberId,
        imdbId = "tt4857264",
        tmdbId = "411088",
        originalTitle = "Contratiempo",
        alternativeTitles = listOf(AlternativeTitle("US", "The Invisible Guest")),
        displayTitlePreference = ORIGINAL,
        year = 2017,
        director = "Oriol Paulo",
        runtimeMinutes = 107,
        genre = listOf("Drama", "Mystery", "Thriller"),
        originCountry = listOf("ES"),
        productionCountries = listOf("Spain"),
        createdAt = Clock.System.now(),
    )
}
