package br.com.gabryel.movieclub.routing.movie

import kotlinx.serialization.Serializable

@Serializable
internal data class AddMovieRequest(
    val imdbUrlOrId: String,
    val watchLink: String? = null,
)

@Serializable
internal data class UpdateMovieRequest(
    val customTitle: String? = null,
    val preference: String? = null,
    val watchLink: String? = null,
)

@Serializable
internal data class RateMovieRequest(
    val qualityOptionId: String? = null,
    val sentimentOptionId: String? = null,
    val comment: String? = null,
)

@Serializable
internal data class AlternativeTitleResponse(
    val isoCode: String,
    val title: String,
    val type: String?,
)

@Serializable
internal data class MovieResponse(
    val id: String,
    val meetingId: String,
    val chosenById: String,
    val imdbId: String,
    val tmdbId: String?,
    val originalTitle: String,
    val alternativeTitles: List<AlternativeTitleResponse>,
    val customTitle: String?,
    val displayTitlePreference: String,
    val year: Int?,
    val director: String?,
    val runtimeMinutes: Int?,
    val genre: List<String>?,
    val originCountry: List<String>?,
    val productionCountries: List<String>?,
    val tmdbRating: String?,
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
