package br.com.gabryel.movieclub.routing.movie

import kotlinx.serialization.Serializable

@Serializable
internal data class AddMovieRequest(
    val imdbUrlOrId: String? = null,
    val tmdbId: String? = null,
    val watchLink: String? = null,
)

@Serializable
internal data class MovieSearchResultResponse(
    val tmdbId: String,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val posterUrl: String?,
)

@Serializable
internal data class UpdateMovieRequest(
    val customTitle: String? = null,
    val preference: String? = null,
    val languageCode: String? = null,
    val watchLink: String? = null,
)

@Serializable
internal data class RateMovieRequest(
    val qualityOptionId: String? = null,
    val sentimentOptionId: String? = null,
    val comment: String? = null,
)

@Serializable
internal data class TranslationResponse(
    val languageCode: String,
    val countryCode: String,
    val englishName: String,
    val title: String,
)

@Serializable
internal data class MovieResponse(
    val id: String,
    val meetingId: String,
    val chosenById: String,
    val imdbId: String,
    val tmdbId: String?,
    val originalTitle: String,
    val originalLanguage: String?,
    val translations: List<TranslationResponse>,
    val customTitle: String?,
    val displayTitlePreference: String,
    val displayLanguageCode: String?,
    val year: Int?,
    val director: String?,
    val runtimeMinutes: Int?,
    val genre: List<String>?,
    val originCountry: List<String>?,
    val productionCountries: List<String>?,
    val tmdbRating: String?,
    val imdbRating: String?,
    val posterS3Key: String?,
    val watchLink: String?,
)

@Serializable
internal data class MovieReviewResponse(
    val movieId: String,
    val memberId: String,
    val qualityOptionId: String?,
    val sentimentOptionId: String?,
    val comment: String?,
)
