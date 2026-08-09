package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.DisplayTitlePreference.LANGUAGE
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.Translation
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbEpisodeDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbExternalIds
import br.com.gabryel.movieclub.service.tmdb.TmdbSeasonDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbSeasonSummary
import br.com.gabryel.movieclub.service.tmdb.TmdbTvDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbTvSummary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class SeriesServiceTest {
    private val seriesRepository = mockk<SeriesRepository>()
    private val clubService = mockk<ClubService>()
    private val tmdbClient = mockk<TmdbClient>()
    private val omdbClient = mockk<OmdbClient>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val mediaItemRepository = mockk<MediaItemRepository>()
    private val seriesService = SeriesService(
        seriesRepository,
        clubService,
        mediaItemRepository,
        tmdbClient,
        omdbClient,
        seasonRepository,
        episodeRepository,
    )

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()

    init {
        coEvery { omdbClient.getImdbRating(any()) } returns null
        every {
            mediaItemRepository.findOrCreate(any(), any(), any(), any(), any(), any(), any())
        } returns mediaItem()
        every { seriesRepository.findByClubAndImdbId(any(), any()) } returns null
    }

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
                TmdbTvDetails(
                    originalName = "Breaking Bad",
                    name = "Breaking Bad",
                    firstAirDate = "2008-01-20",
                    externalIds = TmdbExternalIds(imdbId = "tt0903747"),
                )
            val created = series()
            every {
                seriesRepository.create(
                    clubId = clubId,
                    chosenById = memberId,
                    imdbId = "tt0903747",
                    metadata = match {
                        it.tmdbId == "1396" &&
                            it.originalTitle == "Breaking Bad" &&
                            it.translations == emptyList<Translation>() &&
                            it.year == 2008 &&
                            it.genre == emptyList<String>() &&
                            it.originCountry == emptyList<String>() &&
                            it.creator == null
                    },
                    mediaItemId = any(),
                )
            } returns created

            assertEquals(created, seriesService.addSeries(clubId, memberId, "tt0903747"))
        }

    @Test
    fun `addSeries throws BadRequestException when the series is already in this club`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.findTvByImdbId("tt0903747") } returns TmdbTvSummary(id = 1396)
            coEvery { tmdbClient.getTvDetails(1396) } returns
                TmdbTvDetails(
                    originalName = "Breaking Bad",
                    name = "Breaking Bad",
                    externalIds = TmdbExternalIds(imdbId = "tt0903747"),
                )
            every { seriesRepository.findByClubAndImdbId(clubId, "tt0903747") } returns series()

            assertFailsWith<BadRequestException> { seriesService.addSeries(clubId, memberId, "tt0903747") }
        }

    @Test
    fun `addSeries also imports the full season and episode catalog`(): Unit =
        runBlocking {
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.findTvByImdbId("tt0903747") } returns TmdbTvSummary(id = 1396)
            coEvery { tmdbClient.getTvDetails(1396) } returns
                TmdbTvDetails(
                    originalName = "Breaking Bad",
                    name = "Breaking Bad",
                    firstAirDate = "2008-01-20",
                    seasons = listOf(TmdbSeasonSummary(1)),
                    externalIds = TmdbExternalIds(imdbId = "tt0903747"),
                )
            val created = series(id = seriesId, globalSeriesId = globalSeriesId)
            every { seriesRepository.create(clubId, memberId, "tt0903747", any(), any()) } returns created
            every { seriesRepository.findById(seriesId) } returns created

            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(seasonId, globalSeriesId, 1)
            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)),
            )
            every { episodeRepository.listBySeason(seasonId) } returns emptyList()
            every {
                episodeRepository.create(seasonId, 1, "Pilot")
            } returns EpisodeRow(Uuid.random(), seasonId, 1, title = "Pilot")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers {
                firstArg<Uuid>().let { id -> EpisodeRow(id, seasonId, 1) }
            }

            seriesService.addSeries(clubId, memberId, "tt0903747")

            verify { seasonRepository.create(globalSeriesId, 1) }
            verify { episodeRepository.create(seasonId, 1, "Pilot") }
        }

    @Test
    fun `addSeries merges in the IMDB rating fetched from OMDb`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.findTvByImdbId("tt0903747") } returns TmdbTvSummary(id = 1396)
            coEvery { tmdbClient.getTvDetails(1396) } returns
                TmdbTvDetails(
                    originalName = "Breaking Bad",
                    name = "Breaking Bad",
                    externalIds = TmdbExternalIds(imdbId = "tt0903747"),
                )
            coEvery { omdbClient.getImdbRating("tt0903747") } returns BigDecimal("9.5")

            val created = series()
            every {
                seriesRepository.create(
                    clubId = clubId,
                    chosenById = memberId,
                    imdbId = "tt0903747",
                    metadata = match { it.imdbRating == BigDecimal("9.5") },
                    mediaItemId = any(),
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
            seriesService.updateDisplayTitle(seriesId, memberId, preference = CUSTOM)
        }
    }

    @Test
    fun `updateDisplayTitle throws BadRequestException when LANGUAGE has no languageCode`() {
        val seriesId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> {
            seriesService.updateDisplayTitle(seriesId, memberId, preference = LANGUAGE)
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

        assertFailsWith<BadRequestException> { seriesService.rate(seriesId, memberId, optionId) }
    }

    @Test
    fun `rate accepts independently optional quality and sentiment`() {
        val seriesId = Uuid.random()
        val globalSeriesId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId, globalSeriesId = globalSeriesId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val review = SeriesReviewRow(globalSeriesId, memberId, comment = "great")
        every { seriesRepository.upsertReview(globalSeriesId, memberId, comment = "great") } returns review

        assertEquals(review, seriesService.rate(seriesId, memberId, comment = "great"))
    }

    @Test
    fun `importSeasonsAndEpisodes throws BadRequestException when the series has no tmdbId`(): Unit =
        runBlocking {
            val seriesId = Uuid.random()
            every { seriesRepository.findById(seriesId) } returns series(id = seriesId).copy(tmdbId = null)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            assertFailsWith<BadRequestException> { seriesService.importSeasonsAndEpisodes(seriesId, memberId) }
        }

    @Test
    fun `importSeasonsAndEpisodes creates every season and episode TMDB has, none previously known`(): Unit =
        runBlocking {
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val season1Id = Uuid.random()
            val season2Id = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Breaking Bad",
                name = "Breaking Bad",
                seasons = listOf(TmdbSeasonSummary(1), TmdbSeasonSummary(2)),
            )
            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(season1Id, globalSeriesId, 1)
            every { seasonRepository.create(globalSeriesId, 2) } returns SeasonRow(season2Id, globalSeriesId, 2)

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)),
            )
            coEvery { tmdbClient.getSeasonDetails(1396, 2) } returns TmdbSeasonDetails(
                seasonNumber = 2,
                episodes = listOf(TmdbEpisodeDetails(name = "Seven Thirty-Seven", episodeNumber = 1)),
            )
            every { episodeRepository.listBySeason(season1Id) } returns emptyList()
            every { episodeRepository.listBySeason(season2Id) } returns emptyList()
            every {
                episodeRepository.create(season1Id, 1, "Pilot")
            } returns EpisodeRow(Uuid.random(), season1Id, 1, title = "Pilot")
            every {
                episodeRepository.create(season2Id, 1, "Seven Thirty-Seven")
            } returns EpisodeRow(Uuid.random(), season2Id, 1, title = "Seven Thirty-Seven")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers {
                firstArg<Uuid>().let { id ->
                    EpisodeRow(id, season1Id, 1)
                }
            }

            val created = seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            assertEquals(4, created) // 2 seasons + 2 episodes
            verify { episodeRepository.create(season1Id, 1, "Pilot") }
            verify { episodeRepository.create(season2Id, 1, "Seven Thirty-Seven") }
        }

    @Test
    fun `importSeasonsAndEpisodes is idempotent -- existing seasons and episodes aren't recreated`(): Unit =
        runBlocking {
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Breaking Bad",
                name = "Breaking Bad",
                seasons = listOf(TmdbSeasonSummary(1)),
            )
            every {
                seasonRepository.listBySeries(globalSeriesId)
            } returns listOf(SeasonRow(seasonId, globalSeriesId, 1))

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)),
            )
            every {
                episodeRepository.listBySeason(seasonId)
            } returns listOf(EpisodeRow(Uuid.random(), seasonId, 1, title = "Pilot"))

            val created = seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            assertEquals(0, created)
            verify(exactly = 0) { seasonRepository.create(any(), any()) }
            verify(exactly = 0) { episodeRepository.create(any(), any(), any()) }
        }

    @Test
    fun `requireSeriesAccess denies non-members`() {
        val seriesId = Uuid.random()
        every { seriesRepository.findById(seriesId) } returns series(id = seriesId)
        every { clubService.requireMembership(clubId, memberId) } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { seriesService.listReviews(seriesId, memberId) }
    }

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun series(id: Uuid = Uuid.random(), globalSeriesId: Uuid = Uuid.random()) = SeriesRow(
        id = id,
        globalSeriesId = globalSeriesId,
        clubId = clubId,
        chosenById = memberId,
        imdbId = "tt0903747",
        tmdbId = "1396",
        originalTitle = "Breaking Bad",
        translations = listOf(Translation("en", "US", "English", "Breaking Bad")),
        displayTitlePreference = ORIGINAL,
        year = 2008,
        genre = listOf("Drama", "Crime"),
        originCountry = listOf("US"),
        productionCountries = listOf("United States of America"),
        creator = "Vince Gilligan",
        createdAt = Clock.System.now(),
    )

    private fun mediaItem() = MediaItemRow(
        id = Uuid.random(),
        type = SERIES,
        imdbId = "tt0903747",
        title = "Breaking Bad",
        createdAt = Clock.System.now(),
    )
}
