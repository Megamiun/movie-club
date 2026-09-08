package br.com.gabryel.movieclub.routing.mediaitem

import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import kotlinx.serialization.Serializable

@Serializable
internal data class MediaItemResponse(
    val id: String,
    val type: String,
    val imdbId: String,
    val title: String,
    val tmdbId: String?,
    val year: Int?,
    val posterUrl: String?,
    val imdbRating: String?,
)

internal fun MediaItemRow.toResponse() = MediaItemResponse(
    id = id.toString(),
    type = type.name,
    imdbId = imdbId,
    title = title,
    tmdbId = tmdbId,
    year = year,
    posterUrl = posterUrl,
    imdbRating = imdbRating?.toPlainString(),
)
