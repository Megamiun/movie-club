package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The externally-visible "series" -- a flattened join of the global `Series` catalog row (TMDB data, shared by
 * every club that's picked this `imdbId`) and the per-club `ClubSeries` pick row (`chosenById`/`customTitle`/
 * `displayTitlePreference`). [id] is the pick's id, not the catalog row's -- that's what routes key off.
 * [globalSeriesId] is the underlying catalog row's id, needed to resolve Season/Episode (which reference the
 * global row directly) and reviews (which are per member per global series, see [TmdbSeriesMetadata]'s doc for why). */
data class SeriesRow(
    val id: Uuid,
    val globalSeriesId: Uuid,
    val clubId: Uuid,
    val chosenById: Uuid,
    val imdbId: String,
    val tmdbId: String? = null,
    val originalTitle: String,
    val originalLanguage: String? = null,
    val translations: List<Translation>,
    val customTitle: String? = null,
    val displayTitlePreference: DisplayTitlePreference,
    val displayLanguageCode: String? = null,
    val year: Int? = null,
    val genre: List<String>? = null,
    val originCountry: List<String>? = null,
    val productionCountries: List<String>? = null,
    val imdbRating: BigDecimal? = null,
    val creator: String? = null,
    val posterS3Key: String? = null,
    val metadataFetchedAt: Instant? = null,
    val createdAt: Instant,
)

/** Keyed by the *global* series id + member -- one review per member per series, regardless of which club's pick
 * they watched it through (see [SeriesRow.globalSeriesId]). */
data class SeriesReviewRow(
    val seriesId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid? = null,
    val sentimentOptionId: Uuid? = null,
    val comment: String? = null,
)

/** Every non-user-entered field the global `series` catalog row stores -- same rationale as [TmdbMovieMetadata],
 * including [imdbRating] being sourced from OMDb rather than TMDB. See
 * [br.com.gabryel.movieclub.service.tmdb.TmdbTvDetails.toMetadata] for how TMDB's response becomes one. */
data class TmdbSeriesMetadata(
    val tmdbId: String? = null,
    val originalTitle: String,
    val originalLanguage: String? = null,
    val translations: List<Translation>,
    val year: Int? = null,
    val genre: List<String>? = null,
    val originCountry: List<String>? = null,
    val productionCountries: List<String>? = null,
    val imdbRating: BigDecimal? = null,
    val creator: String? = null,
    val metadataFetchedAt: Instant? = null,
)
