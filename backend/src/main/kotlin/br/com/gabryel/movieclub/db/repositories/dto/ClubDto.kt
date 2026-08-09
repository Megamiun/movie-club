package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.ClubRole
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class ClubRow(
    val id: Uuid,
    val name: String,
    /** Ordered ISO 639-1 codes, ranked most-preferred first -- used to pick a translated title when a pick's
     * [br.com.gabryel.movieclub.db.DisplayTitlePreference] is ORIGINAL (see [ignoredLanguages] for the other half
     * of that resolution). */
    val preferredLanguages: List<String> = emptyList(),
    /** ISO 639-1 codes to never default to, even as the original-language title. */
    val ignoredLanguages: List<String> = emptyList(),
    val createdAt: Instant,
)

data class ClubMembershipRow(
    val clubId: Uuid,
    val memberId: Uuid,
    val role: ClubRole,
    val rotationOrder: Int,
    val joinedAt: Instant,
    val color: String? = null,
)
