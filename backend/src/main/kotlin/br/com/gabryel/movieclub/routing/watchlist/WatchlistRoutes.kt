package br.com.gabryel.movieclub.routing.watchlist

import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.routing.toMediaItemTypeOrBadRequest
import br.com.gabryel.movieclub.routing.toMoveDirectionOrBadRequest
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.WatchlistService
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

fun Route.watchlistRoutes(watchlistService: WatchlistService) {
    authenticate("auth-jwt") {
        post("/clubs/{clubId}/watchlist") {
            val body = call.receive<AddWatchlistEntryRequest>()
            val entry = watchlistService.addEntry(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                body.type.toMediaItemTypeOrBadRequest(),
                body.tmdbId,
                body.notes,
            )
            call.respond(Created, entry.toResponse())
        }

        get("/clubs/{clubId}/watchlist") {
            val entries = watchlistService.listEntries(call.uuidPathParam("clubId"), call.actingMemberId())
            call.respond(entries.map { it.toResponse() })
        }

        patch("/watchlist/{entryId}") {
            val body = call.receive<UpdateWatchlistEntryRequest>()
            val entry = watchlistService.updateEntry(call.uuidPathParam("entryId"), call.actingMemberId(), body.notes)
            call.respond(entry.toResponse())
        }

        post("/watchlist/{entryId}/move") {
            val body = call.receive<MoveWatchlistEntryRequest>()
            val entry = watchlistService.moveEntry(
                call.uuidPathParam("entryId"),
                call.actingMemberId(),
                body.direction.toMoveDirectionOrBadRequest(),
            )
            call.respond(entry.toResponse())
        }

        delete("/watchlist/{entryId}") {
            watchlistService.deleteEntry(call.uuidPathParam("entryId"), call.actingMemberId())
            call.respond(NoContent)
        }
    }
}

private fun WatchlistEntryRow.toResponse() = WatchlistEntryResponse(
    id = id.toString(),
    clubId = clubId.toString(),
    memberId = memberId.toString(),
    mediaItemId = mediaItemId.toString(),
    type = type.name,
    title = title,
    imdbId = imdbId,
    year = year,
    posterUrl = posterUrl,
    tmdbRating = tmdbRating?.toPlainString(),
    imdbRating = imdbRating?.toPlainString(),
    notes = notes,
    position = position,
)
