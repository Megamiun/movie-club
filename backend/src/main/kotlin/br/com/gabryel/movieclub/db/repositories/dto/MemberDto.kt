package br.com.gabryel.movieclub.db.repositories.dto

import kotlin.uuid.Uuid

sealed class MemberRow {
    abstract val id: Uuid
    abstract val email: String
    abstract val displayName: String
}

data class InvitedMember(
    override val id: Uuid,
    override val email: String,
    val inviteToken: Uuid,
) : MemberRow() {
    override val displayName: String get() = email
}

data class RegisteredMember(
    override val id: Uuid,
    override val email: String,
    val name: String,
    val passwordHash: String,
) : MemberRow() {
    override val displayName: String get() = name
}
