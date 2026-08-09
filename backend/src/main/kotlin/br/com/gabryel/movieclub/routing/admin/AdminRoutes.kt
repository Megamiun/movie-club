package br.com.gabryel.movieclub.routing.admin

import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.service.AdminService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.adminRoutes(adminService: AdminService) {
    authenticate("auth-jwt") {
        get("/admin/users") {
            val users = adminService.listAllUsers(call.actingMemberId())
            call.respond(users.map { it.toResponse() })
        }

        get("/admin/media-items") {
            val mediaItems = adminService.listAllMediaItems(call.actingMemberId())
            call.respond(mediaItems.map { it.toResponse() })
        }
    }
}

private fun RegisteredMember.toResponse() = AdminUserResponse(
    id = id.toString(),
    name = name,
    username = username,
    email = email,
    isSiteAdmin = isSiteAdmin,
)

private fun MediaItemRow.toResponse() = AdminMediaItemResponse(
    id = id.toString(),
    type = type.name,
    imdbId = imdbId,
    tmdbId = tmdbId,
    title = title,
    year = year,
    posterUrl = posterUrl,
    imdbRating = imdbRating?.toPlainString(),
)
