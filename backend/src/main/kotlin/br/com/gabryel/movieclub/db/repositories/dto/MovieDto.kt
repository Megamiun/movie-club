package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The externally-visible "movie" -- a flattened join of the global `Movies` catalog row (TMDB data, shared by
 * every club that's picked this `imdbId`) and the per-meeting `MeetingMovies` pick row (`chosenById`/`customTitle`/
 * `displayTitlePreference`/`watchLink`). [id] is the pick's id, not the catalog row's -- that's what routes/reviews
 * key off, so the same movie picked at two different meetings gets two different [MovieRow]s. */
data class MovieRow(
    val id: Uuid,
    val meetingId: Uuid,
    val chosenById: Uuid,
    val imdbId: String,
    val tmdbId: String? = null,
    val originalTitle: String,
    val alternativeTitles: List<AlternativeTitle>,
    val customTitle: String? = null,
    val displayTitlePreference: DisplayTitlePreference,
    val year: Int? = null,
    val director: String? = null,
    val runtimeMinutes: Int? = null,
    val genre: List<String>? = null,
    val originCountry: List<String>? = null,
    val productionCountries: List<String>? = null,
    val tmdbRating: BigDecimal? = null,
    val imdbRating: BigDecimal? = null,
    val posterS3Key: String? = null,
    val watchLink: String? = null,
    val metadataFetchedAt: Instant? = null,
    val createdAt: Instant,
)

data class MovieReviewRow(
    val movieId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid? = null,
    val sentimentOptionId: Uuid? = null,
    val comment: String? = null,
)

/** Every non-user-entered field the global `movies` catalog row stores -- always constructed as a whole (even when
 * some fields are null, e.g. a CSV-imported row before its TMDB refresh succeeds), never assembled field by field
 * at the call site. See [br.com.gabryel.movieclub.service.tmdb.TmdbMovieDetails.toMetadata] for how TMDB's
 * response becomes one. [imdbRating] is the one field here NOT sourced from TMDB -- TMDB's API has no IMDB rating,
 * so it's fetched separately from OMDb (see [br.com.gabryel.movieclub.service.omdb.OmdbClient]) and merged in
 * alongside the rest before this reaches the repository.
 */
data class TmdbMovieMetadata(
    val tmdbId: String? = null,
    val originalTitle: String,
    val alternativeTitles: List<AlternativeTitle>,
    val year: Int? = null,
    val director: String? = null,
    val runtimeMinutes: Int? = null,
    val genre: List<String>? = null,
    val originCountry: List<String>? = null,
    val productionCountries: List<String>? = null,
    val tmdbRating: BigDecimal? = null,
    val imdbRating: BigDecimal? = null,
    val metadataFetchedAt: Instant? = null,
)
