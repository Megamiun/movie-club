package br.com.gabryel.movieclub.plugins

import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.routing.authRoutes
import br.com.gabryel.movieclub.service.MemberService
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting(
    jwtService: JwtService,
    memberService: MemberService,
) {
    routing {
        get("/health") {
            call.respondText("OK")
        }

        authRoutes(jwtService, memberService)
    }
}
