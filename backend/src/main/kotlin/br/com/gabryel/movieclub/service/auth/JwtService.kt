package br.com.gabryel.movieclub.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

private val TOKEN_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000L

class JwtService(
    secret: String,
    private val issuer: String,
    private val audience: String,
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun generate(memberId: UUID): String =
        JWT
            .create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("memberId", memberId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_LIFETIME_MS))
            .sign(algorithm)
}
