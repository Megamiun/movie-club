package br.com.gabryel.movieclub.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class AddWatchlistEntryRequest(
    val title: String,
    val imdbUrl: String? = null,
    val notes: String? = null,
)

@Serializable
internal data class UpdateWatchlistEntryRequest(
    val title: String? = null,
    val imdbUrl: String? = null,
    val notes: String? = null,
)

@Serializable
internal data class WatchlistEntryResponse(
    val id: String,
    val clubId: String,
    val memberId: String,
    val title: String,
    val imdbUrl: String?,
    val notes: String?,
)
