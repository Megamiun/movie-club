package br.com.gabryel.movieclub.routing.member

import br.com.gabryel.movieclub.service.MemberService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.memberRoutes(memberService: MemberService) {
    authenticate("auth-jwt") {
        get("/members/search") {
            val query = call.request.queryParameters["q"].orEmpty()
            val results = memberService.search(query)
            call.respond(results.map { MemberSummaryResponse(it.id.toString(), it.displayName, it.email) })
        }
    }
}
