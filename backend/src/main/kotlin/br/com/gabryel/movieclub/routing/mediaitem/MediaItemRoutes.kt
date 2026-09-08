package br.com.gabryel.movieclub.routing.mediaitem

import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.MediaItemService
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import io.ktor.http.ContentType
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** [imageProxy]: a narrow, TMDB-image-only proxy -- the frontend's month-share-image feature (`CalendarPage`,
 * drawing a month's poster grid into a `<canvas>` to export it as a PNG) needs to load poster pixel data
 * same-origin-safe, but TMDB's own CDN doesn't send CORS headers (a browser can't otherwise read pixel data
 * pulled directly from `image.tmdb.org` into a canvas without tainting it). Deliberately public/unauthenticated,
 * unlike the rest of the API -- a poster image is already public content anyone can view directly at its own TMDB
 * URL, so there's nothing to protect by gating this. [url] is restricted to TMDB's own image host so this can't
 * become an open arbitrary-URL proxy (an SSRF risk otherwise); the global CORS plugin (`plugins.configureCORS`,
 * `anyHost()`) already covers every route including this one, so no per-route CORS header is needed here.
 *
 * The refresh-metadata route replaces what used to be three near-identical ones
 * (`POST /movies/{id}/refresh-metadata`, `/series/{id}/refresh-metadata`, `/episodes/{id}/refresh-metadata`) --
 * see [MediaItemService]'s own doc for why this consolidation and its looser (auth-only, not per-club) access
 * check are correct here. */
fun Route.mediaItemRoutes(tmdbClient: TmdbClient, mediaItemService: MediaItemService) {
    get("/media-items/image-proxy") {
        val url = call.request.queryParameters["url"] ?: throw BadRequestException("Missing url parameter")
        if (!url.startsWith("https://image.tmdb.org/")) {
            throw BadRequestException("Only TMDB image URLs can be proxied")
        }

        val bytes = tmdbClient.fetchImageBytes(url)
        call.respondBytes(bytes, ContentType.Image.JPEG)
    }

    authenticate("auth-jwt") {
        post("/media-items/{mediaItemId}/refresh-metadata") {
            val mediaItem = mediaItemService.refreshMetadata(call.uuidPathParam("mediaItemId"))
            call.respond(mediaItem.toResponse())
        }
    }
}
