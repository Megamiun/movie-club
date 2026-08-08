package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.ClubRole
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class ClubRow(
    val id: Uuid,
    val name: String,
    val createdAt: Instant,
)

data class ClubMembershipRow(
    val clubId: Uuid,
    val memberId: Uuid,
    val role: ClubRole,
    val rotationOrder: Int,
    val joinedAt: Instant,
)
