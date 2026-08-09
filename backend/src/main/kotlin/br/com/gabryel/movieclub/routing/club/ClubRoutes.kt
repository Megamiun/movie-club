package br.com.gabryel.movieclub.routing.club

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.routing.toUuidOrBadRequest
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.ClubDetail
import br.com.gabryel.movieclub.service.ClubMemberDetail
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
            call.respond(Created, membership.toResponse())
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
            call.respond(membership.toResponse())
        }

        patch("/clubs/{clubId}/members/{memberId}/color") {
            val clubId = call.uuidPathParam("clubId")
            val targetMemberId = call.uuidPathParam("memberId")
            val body = call.receive<UpdateColorRequest>()
            val membership = clubService.updateColor(clubId, call.actingMemberId(), targetMemberId, body.color)
            call.respond(membership.toResponse())
        }

        delete("/clubs/{clubId}/members/{memberId}") {
            val clubId = call.uuidPathParam("clubId")
            val targetMemberId = call.uuidPathParam("memberId")
            clubService.removeMember(clubId, call.actingMemberId(), targetMemberId)
            call.respond(NoContent)
        }

        patch("/clubs/{clubId}/language-preferences") {
            val body = call.receive<UpdateLanguagePreferencesRequest>()
            val club = clubService.updateLanguagePreferences(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                body.preferredLanguages,
                body.ignoredLanguages,
            )
            call.respond(club.toDetailResponse())
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
                        scale.options.map { RatingOptionResponse(it.id.toString(), it.label, it.position, it.color) },
                    )
                },
            )
        }

        patch("/clubs/{clubId}/rating-options/{optionId}") {
            val body = call.receive<UpdateRatingOptionRequest>()
            val option = clubService.updateRatingOption(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                call.uuidPathParam("optionId"),
                body.label,
                body.color,
            )
            call.respond(RatingOptionResponse(option.id.toString(), option.label, option.position, option.color))
        }

        post("/clubs/{clubId}/rating-scales/{scaleId}/options") {
            val body = call.receive<CreateRatingOptionRequest>()
            val option = clubService.createRatingOption(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                call.uuidPathParam("scaleId"),
                body.label,
                body.color,
            )
            call.respond(Created, RatingOptionResponse(option.id.toString(), option.label, option.position, option.color))
        }

        delete("/clubs/{clubId}/rating-options/{optionId}") {
            val reassignToOptionId = call.request.queryParameters["reassignToOptionId"]
                ?: throw BadRequestException("reassignToOptionId query parameter is required")
            clubService.deleteRatingOption(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                call.uuidPathParam("optionId"),
                reassignToOptionId.toUuidOrBadRequest(),
            )
            call.respond(NoContent)
        }

        put("/clubs/{clubId}/rating-scales/{scaleId}/order") {
            val body = call.receive<UpdateRatingOptionOrderRequest>()
            clubService.updateRatingOptionOrder(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                call.uuidPathParam("scaleId"),
                body.optionIds.map { it.toUuidOrBadRequest() },
            )
            call.respond(NoContent)
        }
    }
}

private fun ClubDetail.toDetailResponse() = ClubDetailResponse(
    id = id.toString(),
    name = name,
    preferredLanguages = preferredLanguages,
    ignoredLanguages = ignoredLanguages,
    members = members.map { it.toResponse() },
)

private fun ClubMemberDetail.toResponse() =
    ClubMemberResponse(memberId.toString(), name, role.name, rotationOrder, color)

private fun String.toClubRoleOrBadRequest() = ClubRole.entries
    .find { it.name == this } ?: throw BadRequestException("Invalid role: $this")
