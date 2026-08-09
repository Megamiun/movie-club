package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.RatingScaleType
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.ClubRepository
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.ClubRow
import br.com.gabryel.movieclub.db.repositories.dto.RatingOptionRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class ClubDetail(
    val id: Uuid,
    val name: String,
    val preferredLanguages: List<String>,
    val ignoredLanguages: List<String>,
    val createdAt: Instant,
    val members: List<ClubMemberDetail>,
)

data class ClubMemberDetail(
    val memberId: Uuid,
    val name: String,
    val role: ClubRole,
    val rotationOrder: Int,
    val color: String? = null,
)

data class RatingScaleWithOptions(
    val id: Uuid,
    val type: RatingScaleType,
    val options: List<RatingOptionRow>,
)

private val DEFAULT_QUALITY_LABELS = listOf("Excepcional!", "Muito bom", "Bom", "Regular", "Ruim", "Horrível")
private val DEFAULT_SENTIMENT_LABELS =
    listOf("Adorei", "Gostei!", "Ambivalente", "Indiferente", "Desgostei", "Detestei")

/** Matches the original spreadsheet's own per-option chip colors (see `samples/img_1.png`/`img_2.png`) rather than
 * a generic shared gradient -- each scale has its own distinct best-to-worst palette, not just a lighter/darker
 * shade of the same color. Quality's chips are mostly solid/dark; sentiment's are consistently pastel -- the
 * frontend picks white-vs-dark text per chip by luminance (`contrastTextColor`) rather than assuming either style. */
private val DEFAULT_QUALITY_COLORS = listOf("#11734B", "#181492", "#487FBE", "#753800", "#B96713", "#B10202")
private val DEFAULT_SENTIMENT_COLORS = listOf("#C6DBE1", "#D4EDBC", "#FFE5A0", "#FFE5A0", "#FFC8AA", "#FFCFC9")

/** Auto-assigned to a member's own initials badge (meetings table, rotation list) when they join, cycling by
 * rotation order -- distinct from the rating-scale palettes above, which color rating *options*, not people.
 * Editable afterwards via [ClubService.updateColor]. */
private val MEMBER_COLOR_PALETTE =
    listOf("#EF5350", "#42A5F5", "#66BB6A", "#FFCA28", "#AB47BC", "#26A69A", "#EC407A", "#8D6E63")

class ClubService(
    private val clubRepository: ClubRepository,
    private val ratingScaleRepository: RatingScaleRepository,
    private val memberRepository: MemberRepository,
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val seasonRepository: SeasonRepository,
    private val episodeRepository: EpisodeRepository,
) {
    fun requireMembership(clubId: Uuid, memberId: Uuid): ClubMembershipRow =
        clubRepository.findMembership(clubId, memberId)
            ?: throw ForbiddenException("Not a member of this club")

    fun requireAdmin(clubId: Uuid, memberId: Uuid): ClubMembershipRow =
        requireMembership(clubId, memberId).also {
            if (it.role != ADMIN) throw ForbiddenException("Must be a club admin")
        }

    fun createClub(name: String, creatorMemberId: Uuid): ClubDetail = transaction {
        val club = clubRepository.create(name)
        clubRepository.addMember(club.id, creatorMemberId, ADMIN, rotationOrder = 0, color = MEMBER_COLOR_PALETTE[0])

        seedScale(club.id, QUALITY, DEFAULT_QUALITY_LABELS, DEFAULT_QUALITY_COLORS)
        seedScale(club.id, SENTIMENT, DEFAULT_SENTIMENT_LABELS, DEFAULT_SENTIMENT_COLORS)

        club.toDetail(clubRepository.listMembers(club.id).map { it.toDetail() })
    }

    private fun seedScale(clubId: Uuid, type: RatingScaleType, labels: List<String>, colors: List<String>) {
        val scale = ratingScaleRepository.createScale(clubId, type)
        labels.forEachIndexed { position, label ->
            ratingScaleRepository.createOption(scale.id, label, position, colors[position])
        }
    }

    fun getClub(clubId: Uuid, actingMemberId: Uuid): ClubDetail {
        val club = clubRepository.findById(clubId) ?: throw NotFoundException("Club not found")
        requireMembership(clubId, actingMemberId)
        return club.toDetail(clubRepository.listMembers(clubId).map { it.toDetail() })
    }

    fun listMyClubs(memberId: Uuid): List<ClubRow> = clubRepository.listClubsForMember(memberId)

    fun updateLanguagePreferences(
        clubId: Uuid,
        actingMemberId: Uuid,
        preferredLanguages: List<String>,
        ignoredLanguages: List<String>,
    ): ClubDetail {
        requireAdmin(clubId, actingMemberId)
        val club = clubRepository.updateLanguagePreferences(clubId, preferredLanguages, ignoredLanguages)
        return club.toDetail(clubRepository.listMembers(clubId).map { it.toDetail() })
    }

    fun addMember(
        clubId: Uuid,
        actingMemberId: Uuid,
        targetMemberId: Uuid,
        role: ClubRole = MEMBER,
    ): ClubMemberDetail {
        requireAdmin(clubId, actingMemberId)
        if (clubRepository.findMembership(clubId, targetMemberId) != null)
            throw BadRequestException("Member already belongs to this club")

        val nextRotationOrder = (clubRepository.listMembers(clubId).maxOfOrNull { it.rotationOrder } ?: -1) + 1
        val color = MEMBER_COLOR_PALETTE[nextRotationOrder % MEMBER_COLOR_PALETTE.size]
        return clubRepository.addMember(clubId, targetMemberId, role, nextRotationOrder, color).toDetail()
    }

    fun changeRole(clubId: Uuid, actingMemberId: Uuid, targetMemberId: Uuid, newRole: ClubRole): ClubMemberDetail {
        requireAdmin(clubId, actingMemberId)
        val target = clubRepository.findMembership(clubId, targetMemberId)
            ?: throw NotFoundException("Member not found in this club")

        if (target.role == ADMIN && newRole != ADMIN && clubRepository.countAdmins(clubId) <= 1)
            throw BadRequestException("Club must have at least one admin")

        return clubRepository.updateRole(clubId, targetMemberId, newRole).toDetail()
    }

    fun removeMember(clubId: Uuid, actingMemberId: Uuid, targetMemberId: Uuid) {
        requireAdmin(clubId, actingMemberId)
        val target = clubRepository.findMembership(clubId, targetMemberId)
            ?: throw NotFoundException("Member not found in this club")

        if (target.role == ADMIN && clubRepository.countAdmins(clubId) <= 1)
            throw BadRequestException("Club must have at least one admin")

        clubRepository.removeMember(clubId, targetMemberId)
    }

    fun updateRotationOrder(clubId: Uuid, actingMemberId: Uuid, orderedMemberIds: List<Uuid>) {
        requireAdmin(clubId, actingMemberId)
        val currentMemberIds = clubRepository.listMembers(clubId).map { it.memberId }.toSet()
        if (orderedMemberIds.toSet() != currentMemberIds || orderedMemberIds.size != currentMemberIds.size)
            throw BadRequestException("Rotation order must include every club member exactly once")

        orderedMemberIds.forEachIndexed { index, memberId ->
            clubRepository.updateRotationOrder(clubId, memberId, index)
        }
    }

    /** A member's color is personal, like their watchlist -- self-service by default, with admins able to fix up
     * anyone's (e.g. two members picking clashing colors) rather than gating it behind [requireAdmin] entirely. */
    fun updateColor(clubId: Uuid, actingMemberId: Uuid, targetMemberId: Uuid, color: String): ClubMemberDetail {
        val actingMembership = requireMembership(clubId, actingMemberId)
        if (actingMemberId != targetMemberId && actingMembership.role != ADMIN)
            throw ForbiddenException("Can only change your own color")
        if (clubRepository.findMembership(clubId, targetMemberId) == null)
            throw NotFoundException("Member not found in this club")

        return clubRepository.updateColor(clubId, targetMemberId, color).toDetail()
    }

    fun getRatingScales(clubId: Uuid, actingMemberId: Uuid): List<RatingScaleWithOptions> {
        requireMembership(clubId, actingMemberId)
        return ratingScaleRepository.findScales(clubId).map { scale ->
            RatingScaleWithOptions(scale.id, scale.type, ratingScaleRepository.findOptions(scale.id))
        }
    }

    fun validateRatingOption(clubId: Uuid, optionId: Uuid, expectedType: RatingScaleType) {
        val option =
            ratingScaleRepository.findOptionById(optionId) ?: throw BadRequestException("Invalid rating option")
        val scale = ratingScaleRepository.findScales(clubId).find { it.id == option.scaleId }
            ?: throw BadRequestException("Rating option does not belong to this club")
        if (scale.type != expectedType) throw BadRequestException("Rating option is not a ${expectedType.name} option")
    }

    fun updateRatingOption(
        clubId: Uuid,
        actingMemberId: Uuid,
        optionId: Uuid,
        label: String? = null,
        color: String? = null,
    ): RatingOptionRow {
        requireAdmin(clubId, actingMemberId)
        val option = requireClubOption(clubId, optionId)
        return ratingScaleRepository.updateOption(optionId, label ?: option.label, color ?: option.color)
    }

    fun updateRatingOptionOrder(clubId: Uuid, actingMemberId: Uuid, scaleId: Uuid, orderedOptionIds: List<Uuid>) {
        requireAdmin(clubId, actingMemberId)
        if (ratingScaleRepository.findScales(clubId).none { it.id == scaleId })
            throw BadRequestException("Rating scale does not belong to this club")

        val currentIds = ratingScaleRepository.findOptions(scaleId).map { it.id }.toSet()
        if (orderedOptionIds.toSet() != currentIds || orderedOptionIds.size != currentIds.size)
            throw BadRequestException("Order must include every option exactly once")

        orderedOptionIds.forEachIndexed { index, optionId -> ratingScaleRepository.updateOptionPosition(optionId, index) }
    }

    fun createRatingOption(clubId: Uuid, actingMemberId: Uuid, scaleId: Uuid, label: String, color: String): RatingOptionRow {
        requireAdmin(clubId, actingMemberId)
        if (ratingScaleRepository.findScales(clubId).none { it.id == scaleId })
            throw BadRequestException("Rating scale does not belong to this club")

        val position = ratingScaleRepository.findOptions(scaleId).size
        return ratingScaleRepository.createOption(scaleId, label, position, color)
    }

    /** Deleting an option would otherwise leave any review that already used it pointing at a dangling id, so a
     * replacement is mandatory, not optional -- every review using [optionId] (across Movie/Series/Season/Episode,
     * whichever apply) is repointed to [reassignToOptionId] first. Repositories don't depend on each other (see
     * CLAUDE.md), so this cross-entity fan-out is composed here instead, the same way [SeriesService] composes
     * multiple repositories for its own cross-entity work. */
    fun deleteRatingOption(clubId: Uuid, actingMemberId: Uuid, optionId: Uuid, reassignToOptionId: Uuid) {
        requireAdmin(clubId, actingMemberId)
        val option = requireClubOption(clubId, optionId)
        if (reassignToOptionId == optionId)
            throw BadRequestException("Cannot reassign a deleted option's reviews to itself")
        val reassignTo = requireClubOption(clubId, reassignToOptionId)
        if (reassignTo.scaleId != option.scaleId)
            throw BadRequestException("Reassignment target must belong to the same scale")
        if (ratingScaleRepository.findOptions(option.scaleId).size <= 1)
            throw BadRequestException("Cannot delete the last remaining option in a scale")

        movieRepository.reassignRatingOption(optionId, reassignToOptionId)
        seriesRepository.reassignRatingOption(optionId, reassignToOptionId)
        seasonRepository.reassignRatingOption(optionId, reassignToOptionId)
        episodeRepository.reassignRatingOption(optionId, reassignToOptionId)
        ratingScaleRepository.deleteOption(optionId)
    }

    private fun requireClubOption(clubId: Uuid, optionId: Uuid): RatingOptionRow {
        val option = ratingScaleRepository.findOptionById(optionId) ?: throw NotFoundException("Rating option not found")
        val ownsScale = ratingScaleRepository.findScales(clubId).any { it.id == option.scaleId }
        if (!ownsScale) throw BadRequestException("Rating option does not belong to this club")
        return option
    }

    private fun ClubRow.toDetail(members: List<ClubMemberDetail>) =
        ClubDetail(id, name, preferredLanguages, ignoredLanguages, createdAt, members)

    private fun ClubMembershipRow.toDetail() =
        ClubMemberDetail(memberId, resolveMemberName(memberId), role, rotationOrder, color)

    private fun resolveMemberName(memberId: Uuid): String =
        memberRepository.findById(memberId)?.displayName ?: memberId.toString()
}
