package br.com.gabryel.movieclub.routing

import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.service.MemberService
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(
    jwtService: JwtService,
    memberService: MemberService,
) {
    post("/auth/register") {
        val body = call.receive<RegisterRequest>()
        val member = memberService.register(body.inviteToken, body.name, body.password)
        call.respond(
            Created,
            AuthResponse(
                token = jwtService.generate(member.id),
                member = MemberResponse(member.id.toString(), member.name, member.email),
            ),
        )
    }

    post("/auth/login") {
        val body = call.receive<LoginRequest>()
        val member = memberService.login(body.email, body.password)
        call.respond(
            AuthResponse(
                token = jwtService.generate(member.id),
                member = MemberResponse(member.id.toString(), member.name, member.email),
            ),
        )
    }

    authenticate("auth-jwt") {
        post("/auth/invite") {
            val body = call.receive<InviteRequest>()
            val inviteToken = memberService.invite(body.email)
            call.respond(Created, InviteResponse(inviteToken.toString()))
        }
    }
}
