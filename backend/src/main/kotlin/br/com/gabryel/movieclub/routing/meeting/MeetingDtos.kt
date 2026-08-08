package br.com.gabryel.movieclub.routing.meeting

import kotlinx.serialization.Serializable

@Serializable
internal data class CreateMeetingRequest(
    val date: String,
    val assignedMemberId: String? = null,
)

@Serializable
internal data class PostponeMeetingRequest(
    val date: String,
)

@Serializable
internal data class MeetingResponse(
    val id: String,
    val clubId: String,
    val date: String,
    val assignedMemberId: String?,
)
