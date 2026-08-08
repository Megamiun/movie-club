package br.com.gabryel.movieclub.routing.import

import kotlinx.serialization.Serializable

@Serializable
internal data class ImportMemberMappingDto(
    val choiceInitial: String,
    val csvDisplayName: String,
    val memberId: String,
)

@Serializable
internal data class ImportRowIssueResponse(
    val row: Int,
    val reason: String,
)

@Serializable
internal data class ImportResultResponse(
    val clubId: String,
    val type: String,
    val created: Int,
    val updated: Int,
    val skipped: List<ImportRowIssueResponse>,
    val warnings: List<ImportRowIssueResponse>,
)
