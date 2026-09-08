package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.RefreshCandidateRow
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("MetadataRefreshJob")

data class MetadataRefreshResult(
    val totalCatalogSize: Long,
    val budget: Int,
    val candidatesConsidered: Int,
    val succeeded: Int,
    val failed: Int,
)

private sealed class Candidate(val row: RefreshCandidateRow) {
    class Movie(row: RefreshCandidateRow) : Candidate(row)

    class Series(row: RefreshCandidateRow) : Candidate(row)

    class Episode(row: RefreshCandidateRow) : Candidate(row)
}

/**
 * Nightly (and admin-triggerable, see `AdminService.triggerMetadataRefresh`) sweep that keeps every Movie/Series/
 * Episode's TMDB/OMDb metadata from going stale -- backfills anything never fetched, and periodically re-fetches
 * already-fetched rows since IMDB ratings genuinely drift over time (more votes, occasional rediscovery), not just
 * for brand-new releases.
 *
 * Budget: [budgetFraction] (20%, per spec) of the *combined* Movie+Series+Episode catalog size, recomputed fresh
 * every run rather than a fixed number -- stays proportional as the catalog grows, and at this app's real scale
 * (a few hundred rows total) still fully cycles the whole catalog every ~5 runs while comfortably clearing OMDb's
 * free-tier daily quota (1,000 requests/day) even combined with the day's own organic adds/manual refreshes.
 * Movie/Series/Episode candidates are queried independently (each repository's own `findRefreshCandidates`, same
 * priority ordering) then merged into one list and re-sorted by that same priority before the budget is applied,
 * so e.g. a stale movie doesn't lose out to a merely-older-but-still-fresh episode just because episodes happen to
 * outnumber movies in the catalog.
 *
 * Priority (see each repository's own `findRefreshCandidates` doc for the exact SQL-level ordering): not-yet-
 * released rows sort last regardless of anything else (nothing to meaningfully refresh yet), then no-rating rows,
 * then oldest-fetched first. A row fetched more recently than [staleAfter] ago is excluded entirely *unless* it
 * was released within [recentReleaseWindow] -- a rating that's still actively moving is worth checking every run
 * even if just checked.
 *
 * [callDelay] paces each individual refresh rather than firing the whole batch back-to-back -- there was no
 * throttling anywhere in this codebase before this (confirmed: `SeriesService.importSeasonsAndEpisodes`'s own bulk
 * TMDB/OMDb loop has none either), and OMDb's daily quota is shared with every other caller of the same key (new
 * adds, manual refreshes), so a deliberate pace here leaves headroom instead of risking a burst that exhausts it.
 *
 * One candidate failing (a transient TMDB/OMDb hiccup, a since-deleted row) doesn't stop the run -- best-effort,
 * same posture `SeriesService.importSeasonsAndEpisodes`'s own per-episode loop already takes.
 */
class MetadataRefreshJob(
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val episodeRepository: EpisodeRepository,
    private val movieService: MovieService,
    private val seriesService: SeriesService,
    private val episodeService: EpisodeService,
    private val budgetFraction: Double = 0.2,
    private val staleAfter: Duration = 14.days,
    private val callDelay: Duration = 3.seconds,
) {
    suspend fun run(): MetadataRefreshResult {
        val totalCatalogSize = movieRepository.count() + seriesRepository.count() + episodeRepository.count()
        val budget = maxOf(1, ceil(totalCatalogSize * budgetFraction).toInt())

        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date
        val staleBefore = now - staleAfter
        val recentReleaseSince = today.minus(4, DateTimeUnit.MONTH)

        // Over-fetch [budget] from each type before merging -- the shared budget is applied only after combining
        // and re-sorting all three, so any one type alone might contribute up to the full budget.
        val candidates = (
            movieRepository.findRefreshCandidates(budget, today, staleBefore, recentReleaseSince).map { Candidate.Movie(it) } +
                seriesRepository.findRefreshCandidates(budget, today, staleBefore, recentReleaseSince).map { Candidate.Series(it) } +
                episodeRepository.findRefreshCandidates(budget, today, staleBefore, recentReleaseSince).map { Candidate.Episode(it) }
        )
            .sortedWith(
                compareBy(
                    { it.row.isUnreleased },
                    { it.row.imdbRating != null },
                    { it.row.metadataFetchedAt?.toEpochMilliseconds() ?: Long.MIN_VALUE },
                ),
            )
            .take(budget)

        var succeeded = 0
        var failed = 0
        for (candidate in candidates) {
            runCatching { refresh(candidate) }
                .onSuccess { succeeded++ }
                .onFailure {
                    failed++
                    logger.warn("Failed to refresh metadata for candidate {} ({})", candidate.row.id, candidate.row.imdbId, it)
                }
            delay(callDelay)
        }

        return MetadataRefreshResult(totalCatalogSize, budget, candidates.size, succeeded, failed)
    }

    private suspend fun refresh(candidate: Candidate) {
        when (candidate) {
            is Candidate.Movie -> movieService.refreshByImdbId(candidate.row.imdbId, candidate.row.tmdbId)
            is Candidate.Series -> seriesService.refreshByImdbId(candidate.row.imdbId, candidate.row.tmdbId)
            is Candidate.Episode -> episodeService.refreshCatalogMetadata(candidate.row.id)
        }
    }
}
