package br.com.gabryel.movieclub.service.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import kotlin.uuid.Uuid

const val REFRESHED_TOKEN_HEADER = "X-Refreshed-Token"

private const val TOKEN_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000L
private const val RENEWAL_THROTTLE_MS = 7 * 24 * 60 * 60 * 1000L

class JwtService(secret: String, private val issuer: String, private val audience: String) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun generate(memberId: Uuid): String {
        val now = Date()
        return JWT
            .create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("memberId", memberId.toString())
            .withIssuedAt(now)
            .withExpiresAt(Date(now.time + TOKEN_LIFETIME_MS))
            .sign(algorithm)
    }

    // Sliding session: any token older than a week gets silently replaced with a fresh one on its
    // next use, so an actively-used session never hits the redirect-to-login path — keyed off the
    // token's own age rather than its nearness to the fixed expiry, so it doesn't matter whether
    // that next use happens to land in some narrow trailing window before the old expiry or not.
    // Throttled so a token isn't re-signed on every single request.
    fun renewIfStale(memberId: Uuid, issuedAt: Date?): String? {
        if (issuedAt == null || System.currentTimeMillis() - issuedAt.time < RENEWAL_THROTTLE_MS) {
            return null
        }
        return generate(memberId)
    }
}
