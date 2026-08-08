package br.com.gabryel.movieclub.routing

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.UnauthorizedException
import br.com.gabryel.movieclub.service.MoveDirection
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlin.uuid.Uuid

internal fun ApplicationCall.uuidPathParam(name: String): Uuid =
    parameters[name]?.let { Uuid.parseOrNull(it) }
        ?: throw BadRequestException("Invalid or missing path parameter: $name")

internal fun ApplicationCall.actingMemberId(): Uuid {
    val principal = principal<JWTPrincipal>() ?: throw UnauthorizedException("Missing or invalid authentication")
    return Uuid.parse(principal.payload.getClaim("memberId").asString())
}

internal fun String.toUuidOrBadRequest(): Uuid =
    Uuid.parseOrNull(this) ?: throw BadRequestException("Invalid id: $this")

internal fun String.toIntOrBadRequest(): Int =
    toIntOrNull() ?: throw BadRequestException("Invalid number: $this")

internal fun String.toDisplayTitlePreferenceOrBadRequest(): DisplayTitlePreference =
    DisplayTitlePreference.entries.find { it.name == this }
        ?: throw BadRequestException("Invalid display title preference: $this")

internal fun String.toMediaItemTypeOrBadRequest(): MediaItemType =
    MediaItemType.entries.find { it.name == this }
        ?: throw BadRequestException("Invalid media item type: $this")

internal fun String.toMoveDirectionOrBadRequest(): MoveDirection =
    MoveDirection.entries.find { it.name == this }
        ?: throw BadRequestException("Invalid move direction: $this")
