package br.com.gabryel.movieclub.routing.auth

import kotlinx.serialization.Serializable

@Serializable
internal data class InviteRequest(
    val email: String,
)

@Serializable
internal data class InviteResponse(
    val memberId: String,
    val inviteToken: String,
)

@Serializable
internal data class RegisterRequest(
    val inviteToken: String,
    val name: String,
    val username: String,
    val password: String,
)

@Serializable
internal data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
internal data class AuthResponse(
    val token: String,
    val member: MemberResponse,
)

@Serializable
internal data class MemberResponse(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
)
