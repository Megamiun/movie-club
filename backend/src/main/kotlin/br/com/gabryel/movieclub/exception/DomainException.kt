package br.com.gabryel.movieclub.exception

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadGateway
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized

sealed class DomainException(message: String, val status: HttpStatusCode) : Exception(message)

class BadRequestException(message: String) : DomainException(message, BadRequest)

class ForbiddenException(message: String) : DomainException(message, Forbidden)

class ConflictException(message: String) : DomainException(message, Conflict)

class UnauthorizedException(message: String) : DomainException(message, Unauthorized)

class NotFoundException(message: String) : DomainException(message, NotFound)

/** A dependency this server calls out to (TMDB, ...) responded with a non-2xx status -- [BadGateway], not the
 * upstream's own status code, since that status describes *our* client's relationship with *us* as a server, not
 * with the third party we failed to get a good response from. Deliberately distinct from [UnauthorizedException]:
 * an upstream 401 means our own server-side API key/token is misconfigured, not that the member calling our API
 * needs to log in -- conflating the two would tell the frontend the wrong thing to do about it. */
class UpstreamServiceException(message: String) : DomainException(message, BadGateway)
