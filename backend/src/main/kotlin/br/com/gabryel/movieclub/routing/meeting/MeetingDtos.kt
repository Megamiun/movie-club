package br.com.gabryel.movieclub.routing.meeting

import br.com.gabryel.movieclub.routing.movie.MovieResponse
import br.com.gabryel.movieclub.routing.series.EpisodeResponse
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateMeetingRequest(
    val date: String,
    val assignedMemberId: String? = null,
)

@Serializable
internal data class PostponeMeetingRequest(
    val date: String,
)

@Serializable
internal data class MeetingResponse(
    val id: String,
    val clubId: String,
    val date: String,
    val assignedMemberId: String?,
)

@Serializable
internal data class MeetingWithPicksResponse(
    val id: String,
    val clubId: String,
    val date: String,
    val assignedMemberId: String?,
    val movies: List<MovieResponse>,
    val episodes: List<EpisodeResponse>,
)
