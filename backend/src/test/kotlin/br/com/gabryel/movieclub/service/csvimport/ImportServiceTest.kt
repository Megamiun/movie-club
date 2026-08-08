package br.com.gabryel.movieclub.service.csvimport

import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.RatingOptionRow
import br.com.gabryel.movieclub.db.repositories.dto.RatingScaleRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.service.ClubService
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.MovieService
import br.com.gabryel.movieclub.service.SeriesService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ImportServiceTest {
    private val clubService = mockk<ClubService>()
    private val meetingRepository = mockk<MeetingRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val movieService = mockk<MovieService>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val seriesService = mockk<SeriesService>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val episodeService = mockk<EpisodeService>()
    private val watchlistRepository = mockk<WatchlistRepository>()
    private val ratingScaleRepository = mockk<RatingScaleRepository>()
    private val importService = ImportService(
        clubService,
        meetingRepository,
        movieRepository,
        movieService,
        seriesRepository,
        seriesService,
        seasonRepository,
        episodeRepository,
        episodeService,
        watchlistRepository,
        ratingScaleRepository,
    )

    private val clubId = Uuid.random()
    private val actingMemberId = Uuid.random()
    private val personA = Uuid.random()
    private val personB = Uuid.random()
    private val mappings =
        listOf(ImportMemberMapping("A", "Person A", personA), ImportMemberMapping("B", "Person B", personB))

    init {
        every { clubService.requireAdmin(clubId, actingMemberId) } returns ClubMembershipRow(
            clubId,
            actingMemberId,
            ADMIN,
            0,
            Clock.System.now(),
        )
    }

    @Test
    fun `importMovies throws BadRequestException for an unmapped Choice initial`() {
        val csv = movieCsvHeader() + "\nX,Some Movie,05/01/2025,,,,,,,,,,,tt0000001"

        assertFailsWith<BadRequestException> {
            runBlocking {
                importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)
            }
        }
    }

    @Test
    fun `importMovies creates a meeting and movie, applies ratings, and refreshes TMDB metadata`(): Unit =
        runBlocking {
            val csv = movieCsvHeader() +
                "\nA,John Wick,23/02/2025,Bom,Gostei!,,,2014,101 min,Chad Stahelski,7.5,\"Action, Crime\",United States,tt2911666"
            val meetingId = Uuid.random()
            val movieId = Uuid.random()
            val date = LocalDate(2025, 2, 23)

            stubRatingScales()

            every { meetingRepository.findByClubAndDate(clubId, date) } returns null
            every {
                meetingRepository.create(clubId, date, personA)
            } returns MeetingRow(meetingId, clubId, date, personA)

            every { movieRepository.findByMeetingAndImdbId(meetingId, "tt2911666") } returns null

            every {
                movieRepository.create(any(), any(), any(), any(), any())
            } returns movie(id = movieId, meetingId = meetingId)

            coEvery {
                movieService.refreshMetadata(movieId, actingMemberId)
            } returns movie(id = movieId, meetingId = meetingId)

            every {
                movieRepository.upsertReview(movieId, personA, bomOptionId, gosteiOptionId)
            } returns MovieReviewRow(movieId, personA, bomOptionId, gosteiOptionId)

            val result = importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(1, result.created)
            assertTrue(result.warnings.isEmpty())
            coEvery { movieService.refreshMetadata(movieId, actingMemberId) }
            verify { movieRepository.upsertReview(movieId, personA, bomOptionId, gosteiOptionId) }
        }

    @Test
    fun `importMovies is idempotent -- an already-imported movie is skipped, not duplicated`(): Unit =
        runBlocking {
            val csv = movieCsvHeader() + "\nA,John Wick,23/02/2025,,,,,,,,,,,tt2911666"
            val meetingId = Uuid.random()
            val date = LocalDate(2025, 2, 23)
            stubRatingScales()
            every {
                meetingRepository.findByClubAndDate(clubId, date)
            } returns MeetingRow(meetingId, clubId, date, personA)

            every {
                movieRepository.findByMeetingAndImdbId(meetingId, "tt2911666")
            } returns movie(meetingId = meetingId)

            val result = importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(0, result.created)
            assertEquals(1, result.skipped.size)
            assertEquals("already imported", result.skipped.single().reason)
        }

    @Test
    fun `importMovies silently skips a row with no IMDB id but still creates the meeting`(): Unit =
        runBlocking {
            val csv = movieCsvHeader() + "\nB,The Tale of the Princess Kaguya,23/01/2027,,,,,,,,,,,"
            val date = LocalDate(2027, 1, 23)
            stubRatingScales()
            every { meetingRepository.findByClubAndDate(clubId, date) } returns null
            every {
                meetingRepository.create(clubId, date, personB)
            } returns MeetingRow(Uuid.random(), clubId, date, personB)

            val result = importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(0, result.created)
            assertTrue(result.skipped.isEmpty())
            verify { meetingRepository.create(clubId, date, personB) }
        }

    @Test
    fun `importSeries skips a series with no IMDB Id column present, without throwing`(): Unit =
        runBlocking {
            val csv =
                "Choice,Movie,When?,Person A's Rating,Person A - Liked?,Person B's Rating,Person B - Liked?\nA,Twin Peaks,,,,,"
            stubRatingScales()

            val result = importService.importSeries(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(0, result.created)
            assertEquals(1, result.skipped.size)
            assertTrue(
                result.skipped
                    .single()
                    .reason
                    .contains("Missing IMDB Id"),
            )
        }

    @Test
    fun `importSeries creates series, season, and episode when the IMDB Id column is present`(): Unit = runBlocking {
        val csv = "Choice,Movie,When?,Person A's Rating,Person A - Liked?,Person B's Rating,Person B - Liked?,IMDB Id\n" +
            "A,Breaking Bad,,,,,,tt0903747\n" +
            ",Season 1,,,,,,\n" +
            "1,Pilot,06/06/2026,,,,,"
        val seriesId = Uuid.random()
        val seasonId = Uuid.random()
        val episodeId = Uuid.random()
        val meetingId = Uuid.random()
        val date = LocalDate(2026, 6, 6)

        stubRatingScales()

        every { seriesRepository.findByClubAndImdbId(clubId, "tt0903747") } returns null
        every { seriesRepository.create(any(), any(), any(), any()) } returns series(seriesId)
        coEvery { seriesService.refreshMetadata(seriesId, actingMemberId) } returns series(seriesId)
        // TMDB match fails for the bulk import too -- falls back to creating season/episode from the CSV alone,
        // exactly like before importSeasonsAndEpisodes existed.
        coEvery { seriesService.importSeasonsAndEpisodes(seriesId, actingMemberId) } throws
            BadRequestException("Series has not been matched to TMDB yet")
        every { seasonRepository.listBySeries(seriesId) } returns emptyList()
        every { seasonRepository.create(seriesId, 1) } returns SeasonRow(seasonId, seriesId, 1)
        every { episodeRepository.listBySeason(seasonId) } returns emptyList()
        every { meetingRepository.findByClubAndDate(clubId, date) } returns null
        every { meetingRepository.create(clubId, date) } returns MeetingRow(meetingId, clubId, date)

        val episode = EpisodeRow(
            id = episodeId,
            seasonId = seasonId,
            number = 1,
            title = "Pilot",
        )

        every { episodeRepository.create(seasonId, 1, "Pilot") } returns episode
        every { episodeRepository.assignToMeeting(episodeId, meetingId) } just Runs
        coEvery { episodeService.refreshMetadata(episodeId, actingMemberId) } returns episode

        val result = importService.importSeries(clubId, actingMemberId, csv.byteInputStream(), mappings)

        assertEquals(3, result.created) // series + season + episode
        assertTrue(result.skipped.isEmpty())
        assertTrue(result.warnings.single().reason.contains("Could not import full series from TMDB"))
        verify { episodeRepository.create(seasonId, 1, "Pilot") }
        verify { episodeRepository.assignToMeeting(episodeId, meetingId) }
    }

    @Test
    fun `importSeries warns and skips a CSV row whose episode number isn't in the TMDB-imported catalog`(): Unit =
        runBlocking {
            val csv = "Choice,Movie,When?,Person A's Rating,Person A - Liked?,Person B's Rating,Person B - Liked?,IMDB Id\n" +
                "A,Breaking Bad,,,,,,tt0903747\n" +
                ",Season 1,,,,,,\n" +
                "1,Pilot,06/06/2026,,,,,\n" +
                "99,Nonexistent Episode,,,,,,"
            val seriesId = Uuid.random()
            val seasonId = Uuid.random()
            val episodeId = Uuid.random()
            val meetingId = Uuid.random()
            val date = LocalDate(2026, 6, 6)

            stubRatingScales()

            every { seriesRepository.findByClubAndImdbId(clubId, "tt0903747") } returns series(seriesId)
            coEvery { seriesService.importSeasonsAndEpisodes(seriesId, actingMemberId) } returns 2
            every { seasonRepository.listBySeries(seriesId) } returns listOf(SeasonRow(seasonId, seriesId, 1))
            every {
                episodeRepository.listBySeason(seasonId)
            } returns listOf(EpisodeRow(episodeId, seasonId, 1, title = "Pilot"))
            every { meetingRepository.findByClubAndDate(clubId, date) } returns null
            every { meetingRepository.create(clubId, date) } returns MeetingRow(meetingId, clubId, date)
            every { episodeRepository.assignToMeeting(episodeId, meetingId) } just Runs

            val result = importService.importSeries(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertTrue(
                result.warnings.any { it.reason.contains("Episode 99 not found in TMDB for season 1") },
            )
            verify(exactly = 0) { episodeRepository.create(seasonId, 99, any()) }
            verify { episodeRepository.assignToMeeting(episodeId, meetingId) }
        }

    @Test
    fun `importSeries warns and skips a whole season block when TMDB doesn't have that season number`(): Unit =
        runBlocking {
            val csv = "Choice,Movie,When?,Person A's Rating,Person A - Liked?,Person B's Rating,Person B - Liked?,IMDB Id\n" +
                "A,Breaking Bad,,,,,,tt0903747\n" +
                ",Season 99,,,,,,\n" +
                "1,Some Episode,,,,,,"
            val seriesId = Uuid.random()

            stubRatingScales()

            every { seriesRepository.findByClubAndImdbId(clubId, "tt0903747") } returns series(seriesId)
            coEvery { seriesService.importSeasonsAndEpisodes(seriesId, actingMemberId) } returns 0
            every { seasonRepository.listBySeries(seriesId) } returns emptyList()

            val result = importService.importSeries(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertTrue(
                result.warnings.any { it.reason.contains("Season 99 not found in TMDB for this series") },
            )
            verify(exactly = 0) { episodeRepository.listBySeason(any()) }
        }

    @Test
    fun `importReserve creates entries and skips ones already present`() {
        val csv = "Movies,,Series,\nPerson A,Person B,Person A,Person B\nDune,,,"
        every {
            watchlistRepository.listByClub(clubId)
        } returns listOf(
            WatchlistEntryRow(Uuid.random(), clubId, personA, "Dune", createdAt = Clock.System.now()),
        )

        val result = importService.importReserve(clubId, actingMemberId, csv.byteInputStream(), mappings)

        assertEquals(0, result.created)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun `importReserve creates a new entry that is not already present`() {
        val csv = "Movies,,Series,\nPerson A,Person B,Person A,Person B\nDune,,,"
        every { watchlistRepository.listByClub(clubId) } returns emptyList()
        every {
            watchlistRepository.create(clubId, personA, "Dune")
        } returns WatchlistEntryRow(Uuid.random(), clubId, personA, "Dune", createdAt = Clock.System.now())

        val result = importService.importReserve(clubId, actingMemberId, csv.byteInputStream(), mappings)

        assertEquals(1, result.created)
        assertTrue(result.skipped.isEmpty())
    }

    private lateinit var bomOptionId: Uuid
    private lateinit var gosteiOptionId: Uuid

    private fun stubRatingScales() {
        val qualityScaleId = Uuid.random()
        val sentimentScaleId = Uuid.random()
        bomOptionId = Uuid.random()
        gosteiOptionId = Uuid.random()
        every {
            ratingScaleRepository.findScales(clubId)
        } returns listOf(RatingScaleRow(qualityScaleId, clubId, QUALITY), RatingScaleRow(sentimentScaleId, clubId, SENTIMENT))

        every {
            ratingScaleRepository.findOptions(qualityScaleId)
        } returns listOf(RatingOptionRow(bomOptionId, qualityScaleId, "Bom", 2, "#C0CA33"))

        every {
            ratingScaleRepository.findOptions(sentimentScaleId)
        } returns listOf(RatingOptionRow(gosteiOptionId, sentimentScaleId, "Gostei!", 1, "#7CB342"))
    }

    private fun movieCsvHeader() =
        "Choice,Movie,When?,Person A's Rating,Person A - Liked?,Person B's Rating,Person B - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,IMDB Id"

    private fun movie(id: Uuid = Uuid.random(), meetingId: Uuid) = MovieRow(
        id = id,
        meetingId = meetingId,
        chosenById = personA,
        imdbId = "tt2911666",
        originalTitle = "John Wick",
        alternativeTitles = emptyList(),
        displayTitlePreference = ORIGINAL,
        year = 2014,
        createdAt = Clock.System.now(),
    )

    private fun series(id: Uuid, globalSeriesId: Uuid = id) = SeriesRow(
        id = id,
        globalSeriesId = globalSeriesId,
        clubId = clubId,
        chosenById = personA,
        imdbId = "tt0903747",
        originalTitle = "Breaking Bad",
        alternativeTitles = emptyList(),
        displayTitlePreference = ORIGINAL,
        createdAt = Clock.System.now(),
    )
}
