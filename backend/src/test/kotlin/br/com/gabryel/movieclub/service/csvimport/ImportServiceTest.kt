package br.com.gabryel.movieclub.service.csvimport

import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRow
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.MovieRow
import br.com.gabryel.movieclub.db.repositories.RatingOptionRow
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRow
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRow
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRow
import br.com.gabryel.movieclub.db.repositories.WatchlistEntryRow
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.service.ClubService
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.MovieService
import br.com.gabryel.movieclub.service.SeriesService
import io.mockk.coEvery
import io.mockk.every
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
    private val gabryel = Uuid.random()
    private val camila = Uuid.random()
    private val mappings =
        listOf(ImportMemberMapping("G", "Gabryel", gabryel), ImportMemberMapping("C", "Camila", camila))

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
                importService.importMovies(
                    clubId,
                    actingMemberId,
                    csv.byteInputStream(),
                    mappings,
                )
            }
        }
    }

    @Test
    fun `importMovies creates a meeting and movie, applies ratings, and refreshes TMDB metadata`(): Unit =
        runBlocking {
            val csv = movieCsvHeader() +
                "\nG,John Wick,23/02/2025,Bom,Gostei!,,,2014,101 min,Chad Stahelski,7.5,\"Action, Crime\",United States,tt2911666"
            val meetingId = Uuid.random()
            val movieId = Uuid.random()
            val date = LocalDate(2025, 2, 23)
            stubRatingScales()
            every { meetingRepository.findByClubAndDate(clubId, date) } returns null
            every { meetingRepository.create(clubId, date, gabryel) } returns MeetingRow(
                meetingId,
                clubId,
                date,
                gabryel,
            )
            every { movieRepository.findByMeetingAndImdbId(meetingId, "tt2911666") } returns null
            every {
                movieRepository.create(any(), any(), any(), any(), any())
            } returns movie(id = movieId, meetingId = meetingId)
            coEvery { movieService.refreshMetadata(movieId, actingMemberId) } returns movie(
                id = movieId,
                meetingId = meetingId,
            )
            every {
                movieRepository.upsertReview(
                    movieId,
                    gabryel,
                    bomOptionId,
                    gosteiOptionId,
                    null,
                )
            } returns MovieReviewRow(movieId, gabryel, bomOptionId, gosteiOptionId, null)

            val result = importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(1, result.created)
            assertTrue(result.warnings.isEmpty())
            coEvery { movieService.refreshMetadata(movieId, actingMemberId) }
            verify { movieRepository.upsertReview(movieId, gabryel, bomOptionId, gosteiOptionId, null) }
        }

    @Test
    fun `importMovies is idempotent -- an already-imported movie is skipped, not duplicated`(): Unit =
        runBlocking {
            val csv = movieCsvHeader() + "\nG,John Wick,23/02/2025,,,,,,,,,,,tt2911666"
            val meetingId = Uuid.random()
            val date = LocalDate(2025, 2, 23)
            stubRatingScales()
            every { meetingRepository.findByClubAndDate(clubId, date) } returns MeetingRow(
                meetingId,
                clubId,
                date,
                gabryel,
            )
            every {
                movieRepository.findByMeetingAndImdbId(
                    meetingId,
                    "tt2911666",
                )
            } returns movie(meetingId = meetingId)

            val result = importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(0, result.created)
            assertEquals(1, result.skipped.size)
            assertEquals("already imported", result.skipped.single().reason)
        }

    @Test
    fun `importMovies silently skips a row with no IMDB id but still creates the meeting`(): Unit =
        runBlocking {
            val csv = movieCsvHeader() + "\nC,The Tale of the Princess Kaguya,23/01/2027,,,,,,,,,,,"
            val date = LocalDate(2027, 1, 23)
            stubRatingScales()
            every { meetingRepository.findByClubAndDate(clubId, date) } returns null
            every { meetingRepository.create(clubId, date, camila) } returns MeetingRow(
                Uuid.random(),
                clubId,
                date,
                camila,
            )

            val result = importService.importMovies(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(0, result.created)
            assertTrue(result.skipped.isEmpty())
            verify { meetingRepository.create(clubId, date, camila) }
        }

    @Test
    fun `importSeries skips a series with no IMDB Id column present, without throwing`(): Unit =
        runBlocking {
            val csv =
                "Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?\nG,Twin Peaks,,,,,"
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
    fun `importSeries creates series, season, and episode when the IMDB Id column is present`(): Unit =
        runBlocking {
            val csv = "Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,IMDB Id\n" +
                "G,Breaking Bad,,,,,,tt0903747\n" +
                ",Season 1,,,,,,\n" +
                "1,Pilot,06/06/2026,,,,,"
            val seriesId = Uuid.random()
            val seasonId = Uuid.random()
            val episodeId = Uuid.random()
            val meetingId = Uuid.random()
            val date = LocalDate(2026, 6, 6)
            stubRatingScales()
            every { seriesRepository.listByClub(clubId) } returns emptyList()
            every { seriesRepository.create(any(), any(), any(), any()) } returns series(seriesId)
            coEvery { seriesService.refreshMetadata(seriesId, actingMemberId) } returns series(seriesId)
            every { seasonRepository.listBySeries(seriesId) } returns emptyList()
            every { seasonRepository.create(seriesId, 1) } returns SeasonRow(seasonId, seriesId, 1, null)
            every { episodeRepository.listBySeason(seasonId) } returns emptyList()
            every { meetingRepository.findByClubAndDate(clubId, date) } returns null
            every { meetingRepository.create(clubId, date, null) } returns MeetingRow(meetingId, clubId, date, null)
            val episode = EpisodeRow(
                id = episodeId,
                seasonId = seasonId,
                number = 1,
                title = "Pilot",
                meetingId = meetingId,
                airDate = null,
                overview = null,
                runtimeMinutes = null,
                director = null,
                tmdbRating = null,
                metadataFetchedAt = null,
            )
            every { episodeRepository.create(seasonId, 1, "Pilot", meetingId) } returns episode
            coEvery { episodeService.refreshMetadata(episodeId, actingMemberId) } returns episode

            val result = importService.importSeries(clubId, actingMemberId, csv.byteInputStream(), mappings)

            assertEquals(3, result.created) // series + season + episode
            assertTrue(result.skipped.isEmpty())
            verify { episodeRepository.create(seasonId, 1, "Pilot", meetingId) }
        }

    @Test
    fun `importReserve creates entries and skips ones already present`() {
        val csv = "Movies,,Series,\nGabryel,Camila,Gabryel,Camila\nDune,,,"
        every { watchlistRepository.listByClub(clubId) } returns listOf(
            WatchlistEntryRow(
                Uuid.random(),
                clubId,
                gabryel,
                "Dune",
                null,
                null,
                Clock.System.now(),
            ),
        )

        val result = importService.importReserve(clubId, actingMemberId, csv.byteInputStream(), mappings)

        assertEquals(0, result.created)
        assertEquals(1, result.skipped.size)
    }

    @Test
    fun `importReserve creates a new entry that is not already present`() {
        val csv = "Movies,,Series,\nGabryel,Camila,Gabryel,Camila\nDune,,,"
        every { watchlistRepository.listByClub(clubId) } returns emptyList()
        every { watchlistRepository.create(clubId, gabryel, "Dune", null, null) } returns
            WatchlistEntryRow(Uuid.random(), clubId, gabryel, "Dune", null, null, Clock.System.now())

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
        every { ratingScaleRepository.findScales(clubId) } returns
            listOf(RatingScaleRow(qualityScaleId, clubId, QUALITY), RatingScaleRow(sentimentScaleId, clubId, SENTIMENT))
        every { ratingScaleRepository.findOptions(qualityScaleId) } returns listOf(
            RatingOptionRow(
                bomOptionId,
                qualityScaleId,
                "Bom",
                2,
            ),
        )
        every { ratingScaleRepository.findOptions(sentimentScaleId) } returns listOf(
            RatingOptionRow(
                gosteiOptionId,
                sentimentScaleId,
                "Gostei!",
                1,
            ),
        )
    }

    private fun movieCsvHeader() =
        "Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,IMDB Id"

    private fun movie(id: Uuid = Uuid.random(), meetingId: Uuid) =
        MovieRow(
            id = id,
            meetingId = meetingId,
            chosenById = gabryel,
            imdbId = "tt2911666",
            tmdbId = null,
            originalTitle = "John Wick",
            englishTitle = null,
            customTitle = null,
            displayTitlePreference = ORIGINAL,
            year = 2014,
            director = null,
            runtimeMinutes = null,
            genre = null,
            country = null,
            tmdbRating = null,
            posterS3Key = null,
            watchLink = null,
            metadataFetchedAt = null,
            createdAt = Clock.System.now(),
        )

    private fun series(id: Uuid) =
        SeriesRow(
            id = id,
            clubId = clubId,
            chosenById = gabryel,
            imdbId = "tt0903747",
            tmdbId = null,
            originalTitle = "Breaking Bad",
            englishTitle = null,
            customTitle = null,
            displayTitlePreference = ORIGINAL,
            year = null,
            genre = null,
            country = null,
            tmdbRating = null,
            creator = null,
            posterS3Key = null,
            metadataFetchedAt = null,
            createdAt = Clock.System.now(),
        )
}
