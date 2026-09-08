package br.com.gabryel.movieclub.db.repositories.dto

import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One catalog row (Movie, Series, or Episode) eligible for the nightly metadata-refresh job -- just enough to
 * re-fetch and persist its TMDB/OMDb metadata and to sort it by priority, not a full Movie/Series/Episode row.
 * Shared across all three domains since the job's own prioritization (unreleased-last, then no-rating-first, then
 * oldest-fetched) is identical regardless of type -- see `MetadataRefreshJob`. [tmdbId] is null for an Episode
 * candidate (episodes have no standalone TMDB id of their own, see `TmdbEpisodeMetadata`) and for a
 * not-yet-matched Movie/Series row. [isUnreleased] is precomputed by each repository's own query (comparing its
 * `releaseDate`/`airDate` against the query's `today` argument) rather than left for `MetadataRefreshJob` to
 * recompute -- it needs to survive the merge across all three types' result lists, where the per-type SQL
 * `ORDER BY` that originally computed it no longer applies. */
data class RefreshCandidateRow(
    val id: Uuid,
    val imdbId: String,
    val tmdbId: String? = null,
    val imdbRating: BigDecimal? = null,
    val metadataFetchedAt: Instant? = null,
    val isUnreleased: Boolean = false,
)
