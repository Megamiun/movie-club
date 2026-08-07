package br.com.gabryel.movieclub.routing

import br.com.gabryel.movieclub.db.repositories.MeetingRow
import br.com.gabryel.movieclub.service.MeetingService
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
import kotlinx.datetime.LocalDate

fun Route.meetingRoutes(meetingService: MeetingService) {
    authenticate("auth-jwt") {
        post("/clubs/{clubId}/meetings") {
            val body = call.receive<CreateMeetingRequest>()
            val meeting = meetingService.createMeeting(
                call.uuidPathParam("clubId"),
                call.actingMemberId(),
                LocalDate.parse(body.date),
                body.assignedMemberId?.toUuidOrBadRequest(),
            )
            call.respond(Created, meeting.toResponse())
        }

        get("/clubs/{clubId}/meetings") {
            val meetings = meetingService.listMeetings(call.uuidPathParam("clubId"), call.actingMemberId())
            call.respond(meetings.map { it.toResponse() })
        }

        get("/meetings/{meetingId}") {
            val meeting = meetingService.getMeeting(call.uuidPathParam("meetingId"), call.actingMemberId())
            call.respond(meeting.toResponse())
        }

        patch("/meetings/{meetingId}") {
            val body = call.receive<PostponeMeetingRequest>()
            val meeting = meetingService.postponeMeeting(
                call.uuidPathParam("meetingId"),
                call.actingMemberId(),
                LocalDate.parse(body.date),
            )
            call.respond(meeting.toResponse())
        }

        post("/meetings/{meetingId}/swap/{otherId}") {
            val (a, b) = meetingService.swapAssignments(
                call.uuidPathParam("meetingId"),
                call.uuidPathParam("otherId"),
                call.actingMemberId(),
            )
            call.respond(listOf(a.toResponse(), b.toResponse()))
        }

        post("/meetings/{meetingId}/merge/{fromId}") {
            val meeting = meetingService.mergeMeetings(
                call.uuidPathParam("meetingId"),
                call.uuidPathParam("fromId"),
                call.actingMemberId(),
            )
            call.respond(meeting.toResponse())
        }

        delete("/meetings/{meetingId}") {
            meetingService.deleteMeeting(call.uuidPathParam("meetingId"), call.actingMemberId())
            call.respond(NoContent)
        }
    }
}

private fun MeetingRow.toResponse() =
    MeetingResponse(
        id = id.toString(),
        clubId = clubId.toString(),
        date = date.toString(),
        assignedMemberId = assignedMemberId?.toString(),
    )
