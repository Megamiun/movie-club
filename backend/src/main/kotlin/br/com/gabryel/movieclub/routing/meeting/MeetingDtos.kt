package br.com.gabryel.movieclub.routing.meeting

import br.com.gabryel.movieclub.routing.movie.MovieResponse
import br.com.gabryel.movieclub.routing.movie.MovieReviewResponse
import br.com.gabryel.movieclub.routing.series.EpisodeResponse
import br.com.gabryel.movieclub.routing.series.EpisodeReviewResponse
import br.com.gabryel.movieclub.routing.series.SeriesResponse
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
internal data class MeetingMoviePickResponse(
    val movie: MovieResponse,
    val reviews: List<MovieReviewResponse>,
)

@Serializable
internal data class MeetingEpisodePickResponse(
    val episode: EpisodeResponse,
    val reviews: List<EpisodeReviewResponse>,
    val series: SeriesResponse?,
)

@Serializable
internal data class MeetingWithPicksResponse(
    val id: String,
    val clubId: String,
    val date: String,
    val assignedMemberId: String?,
    val movies: List<MeetingMoviePickResponse>,
    val episodes: List<MeetingEpisodePickResponse>,
)
