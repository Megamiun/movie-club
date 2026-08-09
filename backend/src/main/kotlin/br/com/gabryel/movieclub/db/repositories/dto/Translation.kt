package br.com.gabryel.movieclub.db.repositories.dto

import kotlinx.serialization.Serializable

/** One TMDB-sourced translated title, keyed by language (not country -- see [languageCode]) so title resolution
 * can match it against a club's preferred/ignored language lists. Stored as JSONB on the global catalog row
 * (`Movies.translations`/`Series.translations`), shared by [TmdbMovieMetadata] and [TmdbSeriesMetadata]. Only
 * entries where TMDB actually has a translated title are kept -- see
 * [br.com.gabryel.movieclub.service.tmdb.TmdbTranslations.toTranslations]. */
@Serializable
data class Translation(
    val languageCode: String,
    val countryCode: String,
    val englishName: String,
    val title: String,
)
