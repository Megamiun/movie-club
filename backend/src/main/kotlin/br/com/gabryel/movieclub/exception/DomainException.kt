package br.com.gabryel.movieclub.exception

import io.ktor.http.HttpStatusCode
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
