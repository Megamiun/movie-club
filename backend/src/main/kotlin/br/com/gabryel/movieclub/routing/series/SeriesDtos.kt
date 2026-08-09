package br.com.gabryel.movieclub.routing.series

import br.com.gabryel.movieclub.routing.movie.TranslationResponse
import kotlinx.serialization.Serializable

@Serializable
internal data class AddSeriesRequest(
    val imdbUrlOrId: String? = null,
    val tmdbId: String? = null,
)

@Serializable
internal data class SeriesSearchResultResponse(
    val tmdbId: String,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val posterUrl: String?,
)

@Serializable
internal data class EpisodeSearchResultResponse(
    val episodeId: String,
    val seasonId: String,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String?,
)

@Serializable
internal data class UpdateSeriesRequest(
    val customTitle: String? = null,
    val preference: String,
    val languageCode: String? = null,
)

@Serializable
internal data class SeriesResponse(
    val id: String,
    val clubId: String,
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
    val genre: List<String>?,
    val originCountry: List<String>?,
    val productionCountries: List<String>?,
    val tmdbRating: String?,
    val imdbRating: String?,
    val creator: String?,
    val posterS3Key: String?,
)

@Serializable
internal data class AddSeasonRequest(
    val number: Int,
    val title: String? = null,
)

@Serializable
internal data class SeasonResponse(
    val id: String,
    val seriesId: String,
    val number: Int,
    val title: String?,
)

@Serializable
internal data class AddEpisodeRequest(
    val number: Int,
    val title: String? = null,
    val meetingId: String? = null,
)

@Serializable
internal data class EpisodeResponse(
    val id: String,
    val seasonId: String,
    val number: Int,
    val title: String?,
    val airDate: String?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val director: String?,
    val tmdbRating: String?,
)

@Serializable
internal data class RateRequest(
    val qualityOptionId: String? = null,
    val sentimentOptionId: String? = null,
    val comment: String? = null,
)

@Serializable
internal data class SeriesReviewResponse(
    val seriesId: String,
    val memberId: String,
    val qualityOptionId: String?,
    val sentimentOptionId: String?,
    val comment: String?,
)

@Serializable
internal data class SeasonReviewResponse(
    val seasonId: String,
    val memberId: String,
    val qualityOptionId: String?,
    val sentimentOptionId: String?,
    val comment: String?,
)

@Serializable
internal data class EpisodeReviewResponse(
    val episodeId: String,
    val memberId: String,
    val qualityOptionId: String?,
    val sentimentOptionId: String?,
    val comment: String?,
)
