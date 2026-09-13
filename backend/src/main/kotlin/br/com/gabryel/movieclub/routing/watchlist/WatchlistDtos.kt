package br.com.gabryel.movieclub.routing.watchlist

import br.com.gabryel.movieclub.routing.movie.TranslationResponse
import kotlinx.serialization.Serializable

@Serializable
internal data class AddWatchlistEntryRequest(
    val type: String,
    val tmdbId: String,
    // Absent/null means "add to my own list" -- any club member may target any other member's list, matching
    // WatchlistService.moveEntry/moveEntryToMeeting's existing "not owner-restricted" posture, so this must stay
    // optional for existing clients that only ever added to their own list.
    val memberId: String? = null,
)

@Serializable
internal data class UpdateWatchlistEntryRequest(
    val position: Int,
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
