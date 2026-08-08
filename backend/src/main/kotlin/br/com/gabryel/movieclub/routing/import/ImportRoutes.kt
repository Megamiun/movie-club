package br.com.gabryel.movieclub.routing.import

import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.routing.toUuidOrBadRequest
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.csvimport.ImportMemberMapping
import br.com.gabryel.movieclub.service.csvimport.ImportResult
import br.com.gabryel.movieclub.service.csvimport.ImportRowIssue
import br.com.gabryel.movieclub.service.csvimport.ImportService
import io.ktor.http.content.PartData.FileItem
import io.ktor.http.content.PartData.FormItem
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream

private val json = Json { ignoreUnknownKeys = true }

fun Route.importRoutes(importService: ImportService) {
    authenticate("auth-jwt") {
        post("/clubs/{clubId}/import") {
            val clubId = call.uuidPathParam("clubId")
            val actingMemberId = call.actingMemberId()

            val parsed = call.parseImportMultipart()

            val importType = parsed.type ?: throw BadRequestException("Missing 'type' field (movies|series|reserve)")
            if (parsed.files.isEmpty()) throw BadRequestException("Missing 'file' part")
            val mappings = parsed.mappingsJson
                ?.let { json.decodeFromString<List<ImportMemberMappingDto>>(it) }
                .orEmpty()
                .map { ImportMemberMapping(it.choiceInitial, it.csvDisplayName, it.memberId.toUuidOrBadRequest()) }

            val results = parsed.files.map { bytes ->
                when (importType) {
                    "movies" -> importService.importMovies(clubId, actingMemberId, ByteArrayInputStream(bytes), mappings)
                    "series" -> importService.importSeries(clubId, actingMemberId, ByteArrayInputStream(bytes), mappings)
                    "reserve" -> importService.importReserve(clubId, actingMemberId, ByteArrayInputStream(bytes), mappings)
                    else -> throw BadRequestException("Unknown import type: $importType (expected movies|series|reserve)")
                }
            }

            call.respond(results.merge().toResponse(clubId.toString(), importType))
        }
    }
}

private fun ImportResult.toResponse(clubId: String, type: String) = ImportResultResponse(
    clubId = clubId,
    type = type,
    created = created,
    updated = updated,
    skipped = skipped.map { it.toResponse() },
    warnings = warnings.map { it.toResponse() },
)

private fun ImportRowIssue.toResponse() = ImportRowIssueResponse(row, reason)

private fun List<ImportResult>.merge() = ImportResult(
    created = sumOf { it.created },
    updated = sumOf { it.updated },
    skipped = flatMap { it.skipped },
    warnings = flatMap { it.warnings },
)

private class ParsedImportMultipart(
    val type: String?,
    val mappingsJson: String?,
    val files: List<ByteArray>,
)

private suspend fun ApplicationCall.parseImportMultipart(): ParsedImportMultipart {
    var type: String? = null
    var mappingsJson: String? = null
    val files = mutableListOf<ByteArray>()

    receiveMultipart().forEachPart { part ->
        when (part) {
            is FormItem -> when (part.name) {
                "type" -> type = part.value
                "mappings" -> mappingsJson = part.value
            }
            is FileItem -> files.add(part.provider().toInputStream().use { it.readBytes() })
            else -> Unit
        }
        part.dispose()
    }

    return ParsedImportMultipart(type, mappingsJson, files)
}
