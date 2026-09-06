package br.com.gabryel.movieclub.routing.member

import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.routing.auth.MemberResponse
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.MemberService
import io.ktor.http.content.PartData.FileItem
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream

fun Route.memberRoutes(memberService: MemberService) {
    authenticate("auth-jwt") {
        get("/members/search") {
            val query = call.request.queryParameters["q"].orEmpty()
            val results = memberService.search(query)
            call.respond(
                results.map {
                    val photoUrl = (it as? RegisteredMember)?.photoS3Key?.let(memberService::photoUrl)
                    MemberSummaryResponse(it.id.toString(), it.displayName, it.email, photoUrl)
                },
            )
        }

        post("/members/{memberId}/photo") {
            val memberId = call.uuidPathParam("memberId")
            var bytes: ByteArray? = null
            var contentType: String? = null

            call.receiveMultipart().forEachPart { part ->
                if (part is FileItem && bytes == null) {
                    bytes = part.provider().toInputStream().use { it.readBytes() }
                    contentType = part.contentType?.toString()
                }
                part.dispose()
            }

            val member = memberService.uploadPhoto(
                memberId,
                call.actingMemberId(),
                bytes ?: throw BadRequestException("No photo file provided"),
                contentType ?: throw BadRequestException("Missing content type"),
            )
            call.respond(
                MemberResponse(
                    member.id.toString(),
                    member.name,
                    member.username,
                    member.email,
                    member.isSiteAdmin,
                    memberService.photoUrl(member.photoS3Key),
                ),
            )
        }
    }
}
