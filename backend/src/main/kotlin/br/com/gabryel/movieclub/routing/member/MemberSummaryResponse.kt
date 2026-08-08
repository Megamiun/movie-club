package br.com.gabryel.movieclub.routing.member

import kotlinx.serialization.Serializable

@Serializable
internal data class MemberSummaryResponse(
    val id: String,
    val name: String,
    val email: String,
)
