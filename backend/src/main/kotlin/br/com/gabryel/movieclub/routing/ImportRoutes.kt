package br.com.gabryel.movieclub.routing

import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.service.csvimport.ImportMemberMapping
import br.com.gabryel.movieclub.service.csvimport.ImportResult
import br.com.gabryel.movieclub.service.csvimport.ImportRowIssue
import br.com.gabryel.movieclub.service.csvimport.ImportService
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
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

            var type: String? = null
            var mappingsJson: String? = null
            var fileBytes: ByteArray? = null

            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "type" -> type = part.value
                        "mappings" -> mappingsJson = part.value
                    }

                    is PartData.FileItem -> fileBytes = part.provider().toInputStream().use { it.readBytes() }
                    else -> Unit
                }
                part.dispose()
            }

            val importType = type ?: throw BadRequestException("Missing 'type' field (movies|series|reserve)")
            val bytes = fileBytes ?: throw BadRequestException("Missing 'file' part")
            val mappings = mappingsJson
                ?.let { json.decodeFromString<List<ImportMemberMappingDto>>(it) }
                .orEmpty()
                .map { ImportMemberMapping(it.choiceInitial, it.csvDisplayName, it.memberId.toUuidOrBadRequest()) }

            val result = when (importType) {
                "movies" -> importService.importMovies(clubId, actingMemberId, ByteArrayInputStream(bytes), mappings)
                "series" -> importService.importSeries(clubId, actingMemberId, ByteArrayInputStream(bytes), mappings)
                "reserve" -> importService.importReserve(clubId, actingMemberId, ByteArrayInputStream(bytes), mappings)
                else -> throw BadRequestException("Unknown import type: $importType (expected movies|series|reserve)")
            }

            call.respond(result.toResponse(clubId.toString(), importType))
        }
    }
}

private fun ImportResult.toResponse(clubId: String, type: String) =
    ImportResultResponse(
        clubId = clubId,
        type = type,
        created = created,
        updated = updated,
        skipped = skipped.map { it.toResponse() },
        warnings = warnings.map { it.toResponse() },
    )

private fun ImportRowIssue.toResponse() = ImportRowIssueResponse(row, reason)
