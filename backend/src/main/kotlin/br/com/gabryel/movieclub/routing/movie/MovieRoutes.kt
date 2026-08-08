package br.com.gabryel.movieclub.routing.movie

import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.routing.toDisplayTitlePreferenceOrBadRequest
import br.com.gabryel.movieclub.routing.toUuidOrBadRequest
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.MovieService
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.movieRoutes(movieService: MovieService) {
    authenticate("auth-jwt") {
        post("/meetings/{meetingId}/movies") {
            val body = call.receive<AddMovieRequest>()
            val actingMemberId = call.actingMemberId()
            val movie =
                movieService.addMovie(call.uuidPathParam("meetingId"), actingMemberId, body.imdbUrlOrId, body.watchLink)
            call.respond(Created, movie.toResponse())
        }

        get("/meetings/{meetingId}/movies") {
            val movies = movieService.listMovies(call.uuidPathParam("meetingId"), call.actingMemberId())
            call.respond(movies.map { it.toResponse() })
        }

        patch("/movies/{movieId}") {
            val movieId = call.uuidPathParam("movieId")
            val actingMemberId = call.actingMemberId()
            val body = call.receive<UpdateMovieRequest>()
            if (body.preference != null) {
                movieService.updateDisplayTitle(
                    movieId,
                    actingMemberId,
                    body.customTitle,
                    body.preference.toDisplayTitlePreferenceOrBadRequest(),
                )
            }
            if (body.watchLink != null) {
                movieService.updateWatchLink(movieId, actingMemberId, body.watchLink)
            }
            call.respond(movieService.getMovie(movieId, actingMemberId).toResponse())
        }

        post("/movies/{movieId}/refresh-metadata") {
            val movie = movieService.refreshMetadata(call.uuidPathParam("movieId"), call.actingMemberId())
            call.respond(movie.toResponse())
        }

        delete("/movies/{movieId}") {
            movieService.deleteMovie(call.uuidPathParam("movieId"), call.actingMemberId())
            call.respond(NoContent)
        }

        put("/movies/{movieId}/review") {
            val body = call.receive<RateMovieRequest>()
            val review = movieService.rate(
                call.uuidPathParam("movieId"),
                call.actingMemberId(),
                body.qualityOptionId?.toUuidOrBadRequest(),
                body.sentimentOptionId?.toUuidOrBadRequest(),
                body.comment,
            )
            call.respond(review.toResponse())
        }

        get("/movies/{movieId}/reviews") {
            val reviews = movieService.listReviews(call.uuidPathParam("movieId"), call.actingMemberId())
            call.respond(reviews.map { it.toResponse() })
        }
    }
}

private fun MovieRow.toResponse() = MovieResponse(
    id = id.toString(),
    meetingId = meetingId.toString(),
    chosenById = chosenById.toString(),
    imdbId = imdbId,
    tmdbId = tmdbId,
    originalTitle = originalTitle,
    alternativeTitles = alternativeTitles.map { it.toResponse() },
    customTitle = customTitle,
    displayTitlePreference = displayTitlePreference.name,
    year = year,
    director = director,
    runtimeMinutes = runtimeMinutes,
    genre = genre,
    originCountry = originCountry,
    productionCountries = productionCountries,
    tmdbRating = tmdbRating?.toPlainString(),
    posterS3Key = posterS3Key,
    watchLink = watchLink,
)

private fun AlternativeTitle.toResponse() = AlternativeTitleResponse(isoCode, title, type)

private fun MovieReviewRow.toResponse() = MovieReviewResponse(
    movieId = movieId.toString(),
    memberId = memberId.toString(),
    qualityOptionId = qualityOptionId?.toString(),
    sentimentOptionId = sentimentOptionId?.toString(),
    comment = comment,
)
