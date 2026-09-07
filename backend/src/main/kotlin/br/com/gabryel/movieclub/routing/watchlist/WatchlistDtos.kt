package br.com.gabryel.movieclub.routing.watchlist

import br.com.gabryel.movieclub.routing.movie.TranslationResponse
import kotlinx.serialization.Serializable

@Serializable
internal data class AddWatchlistEntryRequest(
    val type: String,
    val tmdbId: String,
)

@Serializable
internal data class MoveWatchlistEntryRequest(
    val targetPosition: Int,
)

@Serializable
internal data class WatchlistEntryResponse(
    val id: String,
    val clubId: String,
    val memberId: String,
    val mediaItemId: String,
    val type: String,
    val title: String,
    val imdbId: String,
    val year: Int?,
    val posterUrl: String?,
    val imdbRating: String?,
    val originalLanguage: String?,
    val translations: List<TranslationResponse>,
    val position: Int,
)
