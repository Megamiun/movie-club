package br.com.gabryel.movieclub.plugins

import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.service.auth.REFRESHED_TOKEN_HEADER
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.header
import kotlin.uuid.Uuid

fun Application.configureAuthentication(jwtService: JwtService) {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "movie-club"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build(),
            )
            validate { credential ->
                val memberId = credential.payload.getClaim("memberId").asString()
                if (memberId == null) {
                    null
                } else {
                    jwtService.renewIfStale(Uuid.parse(memberId), credential.payload.issuedAt)?.let { renewed ->
                        response.header(REFRESHED_TOKEN_HEADER, renewed)
                    }
                    JWTPrincipal(credential.payload)
                }
            }
        }
    }
}
