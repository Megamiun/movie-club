package br.com.gabryel.movieclub.routing.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AdminUserResponse(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
    val isSiteAdmin: Boolean,
)

@Serializable
internal data class AdminMediaItemResponse(
    val id: String,
    val type: String,
    val imdbId: String,
    val tmdbId: String?,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val imdbRating: String?,
)
