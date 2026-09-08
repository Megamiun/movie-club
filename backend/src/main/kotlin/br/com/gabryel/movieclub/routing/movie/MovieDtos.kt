package br.com.gabryel.movieclub.routing.movie

import kotlinx.serialization.Serializable

@Serializable
internal data class AddMovieRequest(
    val imdbUrlOrId: String? = null,
    val tmdbId: String? = null,
    val watchLink: String? = null,
    // Sources the new pick from an existing Watchlist entry (WatchlistService.moveEntryToMeeting) instead of a
    // fresh TMDB/IMDB lookup -- mutually exclusive with imdbUrlOrId/tmdbId. Folded into this same "add movie to
    // meeting" endpoint rather than a separate POST /watchlist/{entryId}/move-to-meeting/{meetingId}, since the
    // result either way is the same thing: a new movie pick under this meeting.
    val fromWatchlistEntryId: String? = null,
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
    // Moves the pick to a different meeting (MovieService.moveToMeeting) when set -- folded into this same PATCH
    // instead of a separate POST /movies/{movieId}/move, since "which meeting this pick belongs to" is just
    // another field on the movie resource, not a distinct action.
    val meetingId: String? = null,
)

@Serializable
internal data class RateMovieRequest(
    val qualityOptionId: String? = null,
    val sentimentOptionId: String? = null,
    val comment: String? = null,
)

@Serializable
internal data class RateMovieOptionRequest(
    val optionId: String?,
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
    val directorImdbId: String?,
    val runtimeMinutes: Int?,
    val genre: List<String>?,
    val originCountry: List<String>?,
    val productionCountries: List<String>?,
    val imdbRating: String?,
    val posterS3Key: String?,
    val posterUrl: String?,
    val watchLink: String?,
    val mediaItemId: String?,
)

@Serializable
internal data class MovieReviewResponse(
    val movieId: String,
    val memberId: String,
    val qualityOptionId: String?,
    val sentimentOptionId: String?,
    val comment: String?,
)
