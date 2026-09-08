package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.RefreshCandidateRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.uuid.Uuid

class MetadataRefreshJobTest {
    private val movieRepository = mockk<MovieRepository>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val movieService = mockk<MovieService>()
    private val seriesService = mockk<SeriesService>()
    private val episodeService = mockk<EpisodeService>()

    /** callDelay=ZERO -- the real 3-second default would make every test in this class take several seconds for
     * no reason; pacing itself is a fixed, trivial `delay()` call, not something worth slowing tests down to
     * verify. */
    private fun job(budgetFraction: Double = 0.2) = MetadataRefreshJob(
        movieRepository,
        seriesRepository,
        episodeRepository,
        movieService,
        seriesService,
        episodeService,
        budgetFraction = budgetFraction,
        callDelay = ZERO,
    )

    init {
        // No watchlist-only backfill candidates by default -- covered separately in its own test below.
        every { movieRepository.findWatchlistOnlyCandidates(any()) } returns emptyList()
        every { seriesRepository.findWatchlistOnlyCandidates(any()) } returns emptyList()
    }

    private fun noCandidatesElsewhere() {
        every { seriesRepository.findRefreshCandidates(any(), any(), any(), any()) } returns emptyList()
        every { episodeRepository.findRefreshCandidates(any(), any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `budget is 20 percent of the combined catalog size, rounded up`(): Unit = runBlocking {
        every { movieRepository.count() } returns 7
        every { seriesRepository.count() } returns 3
        every { episodeRepository.count() } returns 0
        // total = 10, 20% = 2 exactly
        every { movieRepository.findRefreshCandidates(2, any(), any(), any()) } returns emptyList()
        every { seriesRepository.findRefreshCandidates(2, any(), any(), any()) } returns emptyList()
        every { episodeRepository.findRefreshCandidates(2, any(), any(), any()) } returns emptyList()

        val result = job().run()

        assertEquals(10, result.totalCatalogSize)
        assertEquals(2, result.budget)
    }

    @Test
    fun `budget is never less than 1, even for a tiny catalog`(): Unit = runBlocking {
        every { movieRepository.count() } returns 1
        every { seriesRepository.count() } returns 0
        every { episodeRepository.count() } returns 0
        every { movieRepository.findRefreshCandidates(1, any(), any(), any()) } returns emptyList()
        every { seriesRepository.findRefreshCandidates(1, any(), any(), any()) } returns emptyList()
        every { episodeRepository.findRefreshCandidates(1, any(), any(), any()) } returns emptyList()

        assertEquals(1, job().run().budget)
    }

    @Test
    fun `merges candidates across all three types and dispatches each to its own service`(): Unit = runBlocking {
        every { movieRepository.count() } returns 1
        every { seriesRepository.count() } returns 1
        every { episodeRepository.count() } returns 1
        // total=3, budget=1 -- forces the merge/priority logic to actually pick a winner across types.
        val movieRow = RefreshCandidateRow(id = Uuid.random(), imdbId = "tt0111161", tmdbId = "278", metadataFetchedAt = null)
        every { movieRepository.findRefreshCandidates(1, any(), any(), any()) } returns listOf(movieRow)
        every { seriesRepository.findRefreshCandidates(1, any(), any(), any()) } returns
            listOf(RefreshCandidateRow(id = Uuid.random(), imdbId = "tt0903747", tmdbId = "1396", metadataFetchedAt = Clock.System.now()))
        every { episodeRepository.findRefreshCandidates(1, any(), any(), any()) } returns
            listOf(RefreshCandidateRow(id = Uuid.random(), imdbId = "tt0959621", metadataFetchedAt = Clock.System.now()))
        coEvery { movieService.refreshByImdbId(movieRow.imdbId, movieRow.tmdbId) } returns mediaItem(movieRow.imdbId)

        val result = job().run()

        // The never-fetched movie outranks the two already-fetched candidates -- only it gets refreshed.
        assertEquals(1, result.candidatesConsidered)
        assertEquals(1, result.succeeded)
        assertEquals(0, result.failed)
        coVerify(exactly = 1) { movieService.refreshByImdbId(movieRow.imdbId, movieRow.tmdbId) }
        coVerify(exactly = 0) { seriesService.refreshByImdbId(any(), any()) }
        coVerify(exactly = 0) { episodeService.refreshCatalogMetadata(any()) }
    }

    @Test
    fun `a failed candidate doesn't stop the rest of the batch`(): Unit = runBlocking {
        every { movieRepository.count() } returns 2
        noCandidatesElsewhere()
        every { seriesRepository.count() } returns 0
        every { episodeRepository.count() } returns 0
        val failing = RefreshCandidateRow(id = Uuid.random(), imdbId = "tt0000001", tmdbId = "1")
        val succeeding = RefreshCandidateRow(id = Uuid.random(), imdbId = "tt0000002", tmdbId = "2")
        // budgetFraction=1.0 over a catalog of 2 -- budget is 2, not the other tests' 1.
        every { movieRepository.findRefreshCandidates(2, any(), any(), any()) } returns listOf(failing, succeeding)
        coEvery { movieService.refreshByImdbId(failing.imdbId, failing.tmdbId) } throws RuntimeException("TMDB is down")
        coEvery { movieService.refreshByImdbId(succeeding.imdbId, succeeding.tmdbId) } returns mediaItem(succeeding.imdbId)

        val result = job(budgetFraction = 1.0).run()

        assertEquals(2, result.candidatesConsidered)
        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        coVerify { movieService.refreshByImdbId(succeeding.imdbId, succeeding.tmdbId) }
    }

    @Test
    fun `an unreleased row sorts behind a released one regardless of how stale the released row is`(): Unit = runBlocking {
        every { movieRepository.count() } returns 2
        noCandidatesElsewhere()
        every { seriesRepository.count() } returns 0
        every { episodeRepository.count() } returns 0
        val unreleasedButNeverFetched =
            RefreshCandidateRow(id = Uuid.random(), imdbId = "tt1111111", tmdbId = "1", metadataFetchedAt = null, isUnreleased = true)
        val releasedAndAlreadyFetched = RefreshCandidateRow(
            id = Uuid.random(),
            imdbId = "tt2222222",
            tmdbId = "2",
            metadataFetchedAt = Clock.System.now(),
            isUnreleased = false,
        )
        // budget=1 -- only one of the two can be refreshed this run.
        every { movieRepository.findRefreshCandidates(1, any(), any(), any()) } returns
            listOf(unreleasedButNeverFetched, releasedAndAlreadyFetched)
        coEvery { movieService.refreshByImdbId(releasedAndAlreadyFetched.imdbId, releasedAndAlreadyFetched.tmdbId) } returns
            mediaItem(releasedAndAlreadyFetched.imdbId)

        job().run()

        coVerify(exactly = 1) { movieService.refreshByImdbId(releasedAndAlreadyFetched.imdbId, releasedAndAlreadyFetched.tmdbId) }
        coVerify(exactly = 0) { movieService.refreshByImdbId(unreleasedButNeverFetched.imdbId, any()) }
    }

    @Test
    fun `merges in watchlist-only candidates that have no catalog row at all yet`(): Unit = runBlocking {
        every { movieRepository.count() } returns 0
        every { seriesRepository.count() } returns 0
        every { episodeRepository.count() } returns 0
        noCandidatesElsewhere()
        every { movieRepository.findRefreshCandidates(any(), any(), any(), any()) } returns emptyList()
        val orphan = RefreshCandidateRow(id = Uuid.random(), imdbId = "tt0111161")
        every { movieRepository.findWatchlistOnlyCandidates(any()) } returns listOf(orphan)
        coEvery { movieService.refreshByImdbId(orphan.imdbId) } returns mediaItem(orphan.imdbId)

        val result = job().run()

        assertEquals(1, result.candidatesConsidered)
        assertEquals(1, result.succeeded)
        coVerify { movieService.refreshByImdbId(orphan.imdbId) }
    }

    private fun mediaItem(imdbId: String) = MediaItemRow(
        id = Uuid.random(),
        type = br.com.gabryel.movieclub.db.MediaItemType.MOVIE,
        imdbId = imdbId,
        title = "Some Title",
        createdAt = Clock.System.now(),
    )
}
