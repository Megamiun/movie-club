package br.com.gabryel.movieclub.routing.watchlist

import kotlinx.serialization.Serializable

@Serializable
internal data class AddWatchlistEntryRequest(
    val type: String,
    val tmdbId: String,
    val notes: String? = null,
)

@Serializable
internal data class UpdateWatchlistEntryRequest(
    val notes: String? = null,
)

@Serializable
internal data class MoveWatchlistEntryRequest(
    val direction: String,
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
    val tmdbRating: String?,
    val imdbRating: String?,
    val notes: String?,
    val position: Int,
)
