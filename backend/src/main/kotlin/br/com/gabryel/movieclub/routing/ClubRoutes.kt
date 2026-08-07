package br.com.gabryel.movieclub.routing

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.service.ClubDetail
import br.com.gabryel.movieclub.service.ClubService
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

fun Route.clubRoutes(clubService: ClubService) {
    authenticate("auth-jwt") {
        post("/clubs") {
            val body = call.receive<CreateClubRequest>()
            val club = clubService.createClub(body.name, call.actingMemberId())
            call.respond(Created, club.toDetailResponse())
        }

        get("/clubs") {
            val clubs = clubService.listMyClubs(call.actingMemberId())
            call.respond(clubs.map { ClubResponse(it.id.toString(), it.name) })
        }

        get("/clubs/{clubId}") {
            val club = clubService.getClub(call.uuidPathParam("clubId"), call.actingMemberId())
            call.respond(club.toDetailResponse())
        }

        post("/clubs/{clubId}/members") {
            val clubId = call.uuidPathParam("clubId")
            val body = call.receive<AddClubMemberRequest>()
            val membership = clubService.addMember(
                clubId,
                call.actingMemberId(),
                body.memberId.toUuidOrBadRequest(),
                body.role.toClubRoleOrBadRequest(),
            )
            call.respond(
                Created,
                ClubMemberResponse(membership.memberId.toString(), membership.role.name, membership.rotationOrder),
            )
        }

        patch("/clubs/{clubId}/members/{memberId}") {
            val clubId = call.uuidPathParam("clubId")
            val targetMemberId = call.uuidPathParam("memberId")
            val body = call.receive<ChangeRoleRequest>()
            val membership = clubService.changeRole(
                clubId,
                call.actingMemberId(),
                targetMemberId,
                body.role.toClubRoleOrBadRequest(),
            )
            call.respond(
                ClubMemberResponse(membership.memberId.toString(), membership.role.name, membership.rotationOrder),
            )
        }

        delete("/clubs/{clubId}/members/{memberId}") {
            val clubId = call.uuidPathParam("clubId")
            val targetMemberId = call.uuidPathParam("memberId")
            clubService.removeMember(clubId, call.actingMemberId(), targetMemberId)
            call.respond(NoContent)
        }

        put("/clubs/{clubId}/rotation") {
            val clubId = call.uuidPathParam("clubId")
            val body = call.receive<UpdateRotationRequest>()
            clubService.updateRotationOrder(
                clubId,
                call.actingMemberId(),
                body.memberIds.map { it.toUuidOrBadRequest() },
            )
            call.respond(NoContent)
        }

        get("/clubs/{clubId}/rating-scales") {
            val clubId = call.uuidPathParam("clubId")
            val scales = clubService.getRatingScales(clubId, call.actingMemberId())
            call.respond(
                scales.map { scale ->
                    RatingScaleResponse(
                        scale.id.toString(),
                        scale.type.name,
                        scale.options.map { RatingOptionResponse(it.id.toString(), it.label, it.position) },
                    )
                },
            )
        }
    }
}

private fun ClubDetail.toDetailResponse() =
    ClubDetailResponse(
        id = id.toString(),
        name = name,
        members = members.map { ClubMemberResponse(it.memberId.toString(), it.role.name, it.rotationOrder) },
    )

private fun String.toClubRoleOrBadRequest(): ClubRole =
    ClubRole.entries.find { it.name == this } ?: throw BadRequestException("Invalid role: $this")
