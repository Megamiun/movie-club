package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.DisplayTitlePreference.LANGUAGE
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.PersonRepository
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
import br.com.gabryel.movieclub.service.omdb.OmdbSeasonEpisode
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
    private val personRepository = mockk<PersonRepository>()
    private val seriesService = SeriesService(
        seriesRepository,
        clubService,
        mediaItemRepository,
        tmdbClient,
        omdbClient,
        seasonRepository,
        episodeRepository,
        personRepository,
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
                            it.creatorPersonId == null
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

            coEvery { omdbClient.getSeasonEpisodes(any(), any()) } returns null
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
            coEvery { omdbClient.getSeasonEpisodes(any(), any()) } returns null
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
    fun `importSeasonsAndEpisodes creates the episode under OMDb's canonical number when it disagrees with TMDB's`(): Unit =
        runBlocking {
            // Reproduces the real Cowboy Bebop bug: TMDB numbers this episode 14 (broadcast order), but OMDb
            // (IMDB) -- and every English-language source, including this club's own CSV -- calls it episode 4.
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Cowboy Bebop",
                name = "Cowboy Bebop",
                seasons = listOf(TmdbSeasonSummary(1)),
            )
            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(seasonId, globalSeriesId, 1)

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Gateway Shuffle", episodeNumber = 14)),
            )
            coEvery { omdbClient.getSeasonEpisodes("tt0903747", 1) } returns listOf(
                OmdbSeasonEpisode(title = "Gateway Shuffle", episode = "4", imdbId = "tt0618968", imdbRating = "7.7"),
            )
            every { episodeRepository.listBySeason(seasonId) } returns emptyList()
            every {
                episodeRepository.create(seasonId, 4, "Gateway Shuffle")
            } returns EpisodeRow(Uuid.random(), seasonId, 4, title = "Gateway Shuffle")
            every {
                episodeRepository.updateTmdbMetadata(any(), match { it.imdbId == "tt0618968" && it.imdbRating == BigDecimal("7.7") })
            } answers { EpisodeRow(firstArg(), seasonId, 4, title = "Gateway Shuffle") }

            seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            verify { episodeRepository.create(seasonId, 4, "Gateway Shuffle") }
            verify(exactly = 0) { episodeRepository.create(seasonId, 14, any()) }
        }

    @Test
    fun `importSeasonsAndEpisodes falls back to TMDB's own number for one episode OMDb has no confident match for`(): Unit =
        runBlocking {
            // Season has two episodes; OMDb only has usable data for one of them (e.g. a title drifted too far,
            // or OMDb is simply missing that entry) -- the other one keeps TMDB's own number, not a forced match.
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Cowboy Bebop",
                name = "Cowboy Bebop",
                seasons = listOf(TmdbSeasonSummary(1)),
            )
            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(seasonId, globalSeriesId, 1)

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(
                    TmdbEpisodeDetails(name = "Gateway Shuffle", episodeNumber = 14),
                    TmdbEpisodeDetails(name = "A Completely Unmatched Recap Special", episodeNumber = 27),
                ),
            )
            coEvery { omdbClient.getSeasonEpisodes("tt0903747", 1) } returns listOf(
                OmdbSeasonEpisode(title = "Gateway Shuffle", episode = "4", imdbId = "tt0618968"),
            )
            every { episodeRepository.listBySeason(seasonId) } returns emptyList()
            every {
                episodeRepository.create(seasonId, 4, "Gateway Shuffle")
            } returns EpisodeRow(Uuid.random(), seasonId, 4, title = "Gateway Shuffle")
            every {
                episodeRepository.create(seasonId, 27, "A Completely Unmatched Recap Special")
            } returns EpisodeRow(Uuid.random(), seasonId, 27, title = "A Completely Unmatched Recap Special")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers {
                EpisodeRow(firstArg(), seasonId, 1)
            }

            seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            verify { episodeRepository.create(seasonId, 4, "Gateway Shuffle") }
            verify { episodeRepository.create(seasonId, 27, "A Completely Unmatched Recap Special") }
        }

    @Test
    fun `importSeasonsAndEpisodes falls back to TMDB's own numbering entirely when OMDb has nothing for this season`(): Unit =
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
            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(seasonId, globalSeriesId, 1)

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)),
            )
            coEvery { omdbClient.getSeasonEpisodes("tt0903747", 1) } returns null
            every { episodeRepository.listBySeason(seasonId) } returns emptyList()
            every {
                episodeRepository.create(seasonId, 1, "Pilot")
            } returns EpisodeRow(Uuid.random(), seasonId, 1, title = "Pilot")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers {
                EpisodeRow(firstArg(), seasonId, 1)
            }

            seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            verify { episodeRepository.create(seasonId, 1, "Pilot") }
        }

    @Test
    fun `importSeasonsAndEpisodes falls back to TMDB's own number when the OMDb-corrected one collides with an unrelated episode`(): Unit =
        runBlocking {
            // TMDB ep5 "Bonus Recap" has no OMDb match and already sits at its own TMDB number (5). TMDB ep14
            // "Real Episode" gets OMDb-corrected to that same number (5) -- an unrelated coincidence, not the same
            // story. (season, number) is a real DB uniqueness constraint, so "Real Episode" must fall back to its
            // own TMDB number (14) rather than being silently dropped because 5 is already taken.
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Cowboy Bebop",
                name = "Cowboy Bebop",
                seasons = listOf(TmdbSeasonSummary(1)),
            )
            every { seasonRepository.listBySeries(globalSeriesId) } returns listOf(SeasonRow(seasonId, globalSeriesId, 1))

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(
                    TmdbEpisodeDetails(name = "Bonus Recap", episodeNumber = 5),
                    TmdbEpisodeDetails(name = "Real Episode", episodeNumber = 14),
                ),
            )
            coEvery { omdbClient.getSeasonEpisodes("tt0903747", 1) } returns listOf(
                OmdbSeasonEpisode(title = "Real Episode", episode = "5", imdbId = "tt0000001"),
            )
            every {
                episodeRepository.listBySeason(seasonId)
            } returns listOf(EpisodeRow(Uuid.random(), seasonId, 5, title = "Bonus Recap"))
            every {
                episodeRepository.create(seasonId, 14, "Real Episode")
            } returns EpisodeRow(Uuid.random(), seasonId, 14, title = "Real Episode")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers { EpisodeRow(firstArg(), seasonId, 14) }

            seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            verify { episodeRepository.create(seasonId, 14, "Real Episode") }
            verify(exactly = 0) { episodeRepository.create(seasonId, 5, any()) }
        }

    @Test
    fun `importSeasonsAndEpisodes does not create a duplicate for a legacy episode already sitting under TMDB's old wrong number`(): Unit =
        runBlocking {
            // A prior (pre-fix) import already created this episode under TMDB's own wrong number (14). Re-running
            // now that OMDb corrects it to 4 must not create a second row for the same story at number 4 -- the
            // stale number=14 row stays exactly as it is (per the "fill only, never renumber" decision), but no
            // duplicate is created alongside it.
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Cowboy Bebop",
                name = "Cowboy Bebop",
                seasons = listOf(TmdbSeasonSummary(1)),
            )
            every { seasonRepository.listBySeries(globalSeriesId) } returns listOf(SeasonRow(seasonId, globalSeriesId, 1))

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Gateway Shuffle", episodeNumber = 14)),
            )
            coEvery { omdbClient.getSeasonEpisodes("tt0903747", 1) } returns listOf(
                OmdbSeasonEpisode(title = "Gateway Shuffle", episode = "4", imdbId = "tt0618968"),
            )
            every {
                episodeRepository.listBySeason(seasonId)
            } returns listOf(EpisodeRow(Uuid.random(), seasonId, 14, title = "Gateway Shuffle"))

            val created = seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            assertEquals(0, created)
            verify(exactly = 0) { episodeRepository.create(any(), any(), any()) }
        }

    @Test
    fun `importSeasonsAndEpisodes creates both halves of a two-parter, not just the first`(): Unit =
        runBlocking {
            // Real bug, caught live against Cowboy Bebop: "Jupiter Jazz (1)" vs "Jupiter Jazz (2)" score 0.5 on
            // episodeTitleSimilarity -- exactly TITLE_MATCH_THRESHOLD. On a fresh import (nothing pre-existing),
            // the old code re-queried listBySeason() on every loop iteration, so by the time "(2)" was processed
            // it saw "(1)" -- created moments earlier in this same run -- and treated it as "already exists",
            // silently dropping the second half. Both must be created from an empty season.
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val seasonId = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Cowboy Bebop",
                name = "Cowboy Bebop",
                seasons = listOf(TmdbSeasonSummary(1)),
            )
            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(seasonId, globalSeriesId, 1)

            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(
                    TmdbEpisodeDetails(name = "Jupiter Jazz (1)", episodeNumber = 8),
                    TmdbEpisodeDetails(name = "Jupiter Jazz (2)", episodeNumber = 9),
                ),
            )
            coEvery { omdbClient.getSeasonEpisodes("tt0903747", 1) } returns listOf(
                OmdbSeasonEpisode(title = "Jupiter Jazz: Part 1", episode = "12", imdbId = "tt0618973"),
                OmdbSeasonEpisode(title = "Jupiter Jazz: Part 2", episode = "13", imdbId = "tt0824569"),
            )
            every { episodeRepository.listBySeason(seasonId) } returns emptyList()
            every {
                episodeRepository.create(seasonId, 12, "Jupiter Jazz (1)")
            } returns EpisodeRow(Uuid.random(), seasonId, 12, title = "Jupiter Jazz (1)")
            every {
                episodeRepository.create(seasonId, 13, "Jupiter Jazz (2)")
            } returns EpisodeRow(Uuid.random(), seasonId, 13, title = "Jupiter Jazz (2)")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers { EpisodeRow(firstArg(), seasonId, 1) }

            val created = seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            assertEquals(3, created) // season + both episodes
            verify { episodeRepository.create(seasonId, 12, "Jupiter Jazz (1)") }
            verify { episodeRepository.create(seasonId, 13, "Jupiter Jazz (2)") }
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
            coEvery { omdbClient.getSeasonEpisodes(any(), any()) } returns null
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
    fun `importSeasonsAndEpisodes recovers on retry after a transient failure partway through`(): Unit =
        runBlocking {
            // Mirrors the real-world "Twin Peaks" symptom: a single best-effort import call hits a transient
            // TMDB failure partway through, leaving later seasons permanently missing since nothing retries it.
            // The fix is the manual re-import endpoint -- this proves calling importSeasonsAndEpisodes again
            // picks up exactly what the first call didn't finish, without duplicating what it did.
            val seriesId = Uuid.random()
            val globalSeriesId = Uuid.random()
            val season1Id = Uuid.random()
            val season2Id = Uuid.random()
            every {
                seriesRepository.findById(seriesId)
            } returns series(id = seriesId, globalSeriesId = globalSeriesId)
            every { clubService.requireMembership(clubId, memberId) } returns membership()

            coEvery { tmdbClient.getTvDetails(1396) } returns TmdbTvDetails(
                originalName = "Twin Peaks",
                name = "Twin Peaks",
                seasons = listOf(TmdbSeasonSummary(1), TmdbSeasonSummary(2)),
            )
            coEvery { omdbClient.getSeasonEpisodes(any(), any()) } returns null
            every { seasonRepository.create(globalSeriesId, 1) } returns SeasonRow(season1Id, globalSeriesId, 1)
            coEvery { tmdbClient.getSeasonDetails(1396, 1) } returns TmdbSeasonDetails(
                seasonNumber = 1,
                episodes = listOf(TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)),
            )
            every { episodeRepository.listBySeason(season1Id) } returns emptyList()
            every {
                episodeRepository.create(season1Id, 1, "Pilot")
            } returns EpisodeRow(Uuid.random(), season1Id, 1, title = "Pilot")
            every { episodeRepository.updateTmdbMetadata(any(), any()) } answers {
                firstArg<Uuid>().let { id -> EpisodeRow(id, season1Id, 1) }
            }

            // First call: season 1 imports fine, season 2 hits a transient failure.
            every { seasonRepository.listBySeries(globalSeriesId) } returns emptyList()
            coEvery { tmdbClient.getSeasonDetails(1396, 2) } throws RuntimeException("TMDB rate limited")

            assertFailsWith<RuntimeException> { seriesService.importSeasonsAndEpisodes(seriesId, memberId) }
            verify { seasonRepository.create(globalSeriesId, 1) }
            verify { episodeRepository.create(season1Id, 1, "Pilot") }

            // Retry via the manual re-import endpoint: season 1 is already there (idempotent, not recreated),
            // season 2 (the revival, in the real-world case) now succeeds and gets created.
            every {
                seasonRepository.listBySeries(globalSeriesId)
            } returns listOf(SeasonRow(season1Id, globalSeriesId, 1))
            every {
                episodeRepository.listBySeason(season1Id)
            } returns listOf(EpisodeRow(Uuid.random(), season1Id, 1, title = "Pilot"))
            every { seasonRepository.create(globalSeriesId, 2) } returns SeasonRow(season2Id, globalSeriesId, 2)
            coEvery { tmdbClient.getSeasonDetails(1396, 2) } returns TmdbSeasonDetails(
                seasonNumber = 2,
                episodes = listOf(TmdbEpisodeDetails(name = "The Return", episodeNumber = 1)),
            )
            every { episodeRepository.listBySeason(season2Id) } returns emptyList()
            every {
                episodeRepository.create(season2Id, 1, "The Return")
            } returns EpisodeRow(Uuid.random(), season2Id, 1, title = "The Return")

            val created = seriesService.importSeasonsAndEpisodes(seriesId, memberId)

            assertEquals(2, created) // season 2 + its 1 episode
            verify(exactly = 1) { seasonRepository.create(globalSeriesId, 1) }
            verify { seasonRepository.create(globalSeriesId, 2) }
            verify { episodeRepository.create(season2Id, 1, "The Return") }
        }

    @Test
    fun `episodeTitleSimilarity is 1 for an exact match, near 0 for unrelated titles`() {
        assertEquals(1.0, episodeTitleSimilarity("Gateway Shuffle", "Gateway Shuffle"))
        assertEquals(0.0, episodeTitleSimilarity("Waltz for Venus", "Gateway Shuffle"))
    }

    @Test
    fun `episodeTitleSimilarity tolerates the punctuation-only drift seen between TMDB and OMDb titles`() {
        // TMDB: "Jupiter Jazz (1)" / OMDb: "Jupiter Jazz: Part 1" -- same episode, different formatting.
        val similarity = episodeTitleSimilarity("Jupiter Jazz (1)", "Jupiter Jazz: Part 1")

        assert(similarity > 0.5) { "expected > 0.5, was $similarity" }
    }

    @Test
    fun `episodeTitleSimilarity does not confuse a two-parter's other half`() {
        val correctPart = episodeTitleSimilarity("Jupiter Jazz (1)", "Jupiter Jazz: Part 1")
        val wrongPart = episodeTitleSimilarity("Jupiter Jazz (1)", "Jupiter Jazz: Part 2")

        assert(wrongPart < correctPart) { "expected Part 2 ($wrongPart) to score below Part 1 ($correctPart)" }
    }

    @Test
    fun `matchOmdbEpisodes pairs TMDB's mis-numbered episodes to OMDb's canonical numbering by title`() {
        // Real Cowboy Bebop data: TMDB's own numbering is broadcast-order, not the internationally-known
        // session order OMDb (IMDB) uses -- TMDB's episode 4 is "Waltz for Venus", but OMDb (correctly) calls
        // that episode 8, and TMDB's episode 14 ("Gateway Shuffle" per TMDB) is OMDb's episode 4.
        val tmdbEpisodes = listOf(
            TmdbEpisodeDetails(name = "Waltz for Venus", episodeNumber = 4),
            TmdbEpisodeDetails(name = "Gateway Shuffle", episodeNumber = 14),
        )
        val omdbEpisodes = listOf(
            OmdbSeasonEpisode(title = "Gateway Shuffle", episode = "4", imdbId = "tt0618968"),
            OmdbSeasonEpisode(title = "Waltz for Venus", episode = "8", imdbId = "tt0618981"),
        )

        val matches = matchOmdbEpisodes(tmdbEpisodes, omdbEpisodes)

        assertEquals(8, matches[4]?.number)
        assertEquals(4, matches[14]?.number)
    }

    @Test
    fun `matchOmdbEpisodes omits a TMDB episode with no confident OMDb match`() {
        val tmdbEpisodes = listOf(TmdbEpisodeDetails(name = "A Bonus Recap Special", episodeNumber = 27))
        val omdbEpisodes = listOf(OmdbSeasonEpisode(title = "Gateway Shuffle", episode = "4", imdbId = "tt0618968"))

        assertEquals(emptyMap(), matchOmdbEpisodes(tmdbEpisodes, omdbEpisodes))
    }

    @Test
    fun `matchOmdbEpisodes never reuses the same OMDb episode for two different TMDB episodes`() {
        // Both TMDB titles are near-identical ("Jupiter Jazz" two-parter) -- without a uniqueness guarantee,
        // a naive best-match-per-TMDB-entry pass could greedily give both the same OMDb episode.
        val tmdbEpisodes = listOf(
            TmdbEpisodeDetails(name = "Jupiter Jazz (1)", episodeNumber = 8),
            TmdbEpisodeDetails(name = "Jupiter Jazz (2)", episodeNumber = 9),
        )
        val omdbEpisodes = listOf(
            OmdbSeasonEpisode(title = "Jupiter Jazz: Part 1", episode = "12", imdbId = "tt0618973"),
            OmdbSeasonEpisode(title = "Jupiter Jazz: Part 2", episode = "13", imdbId = "tt0824569"),
        )

        val matches = matchOmdbEpisodes(tmdbEpisodes, omdbEpisodes)

        assertEquals(12, matches[8]?.number)
        assertEquals(13, matches[9]?.number)
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
