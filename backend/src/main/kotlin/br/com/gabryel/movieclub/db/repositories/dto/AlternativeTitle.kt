package br.com.gabryel.movieclub.db.repositories.dto

import kotlinx.serialization.Serializable

/** One entry from TMDB's `alternative_titles` -- a title as known in a specific country, plus TMDB's own free-text
 * [type] classifying it (e.g. "working title", "festival title", or blank for a plain regional title). Stored as
 * JSONB on the global catalog row (`Movies.alternativeTitles`/`Series.alternativeTitles`), shared by
 * [TmdbMovieMetadata] and [TmdbSeriesMetadata]. */
@Serializable
data class AlternativeTitle(
    val isoCode: String,
    val title: String,
    val type: String? = null,
)
