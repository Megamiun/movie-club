package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.RatingScaleType
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.ClubRepository
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
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
    val createdAt: Instant,
    val members: List<ClubMemberDetail>,
)

data class ClubMemberDetail(
    val memberId: Uuid,
    val name: String,
    val role: ClubRole,
    val rotationOrder: Int,
)

data class RatingScaleWithOptions(
    val id: Uuid,
    val type: RatingScaleType,
    val options: List<RatingOptionRow>,
)

private val DEFAULT_QUALITY_LABELS = listOf("Excepcional!", "Muito bom", "Bom", "Regular", "Ruim", "Horrível")
private val DEFAULT_SENTIMENT_LABELS =
    listOf("Adorei", "Gostei!", "Ambivalente", "Indiferente", "Desgostei", "Detestei")

/** Best-to-worst color gradient (green -> red) applied by position to any default scale -- both default scales are
 * six options ordered best-first, so the same gradient fits either one. */
private val DEFAULT_RATING_COLORS = listOf("#2E7D32", "#7CB342", "#C0CA33", "#FDD835", "#FB8C00", "#E53935")

class ClubService(
    private val clubRepository: ClubRepository,
    private val ratingScaleRepository: RatingScaleRepository,
    private val memberRepository: MemberRepository,
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
        clubRepository.addMember(club.id, creatorMemberId, ADMIN, rotationOrder = 0)

        seedScale(club.id, QUALITY, DEFAULT_QUALITY_LABELS)
        seedScale(club.id, SENTIMENT, DEFAULT_SENTIMENT_LABELS)

        ClubDetail(club.id, club.name, club.createdAt, clubRepository.listMembers(club.id).map { it.toDetail() })
    }

    private fun seedScale(clubId: Uuid, type: RatingScaleType, labels: List<String>) {
        val scale = ratingScaleRepository.createScale(clubId, type)
        labels.forEachIndexed { position, label ->
            ratingScaleRepository.createOption(scale.id, label, position, DEFAULT_RATING_COLORS[position])
        }
    }

    fun getClub(clubId: Uuid, actingMemberId: Uuid): ClubDetail {
        val club = clubRepository.findById(clubId) ?: throw NotFoundException("Club not found")
        requireMembership(clubId, actingMemberId)
        return ClubDetail(club.id, club.name, club.createdAt, clubRepository.listMembers(clubId).map { it.toDetail() })
    }

    fun listMyClubs(memberId: Uuid): List<ClubRow> = clubRepository.listClubsForMember(memberId)

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
        return clubRepository.addMember(clubId, targetMemberId, role, nextRotationOrder).toDetail()
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

    private fun requireClubOption(clubId: Uuid, optionId: Uuid): RatingOptionRow {
        val option = ratingScaleRepository.findOptionById(optionId) ?: throw NotFoundException("Rating option not found")
        val ownsScale = ratingScaleRepository.findScales(clubId).any { it.id == option.scaleId }
        if (!ownsScale) throw BadRequestException("Rating option does not belong to this club")
        return option
    }

    private fun ClubMembershipRow.toDetail() = ClubMemberDetail(memberId, resolveMemberName(memberId), role, rotationOrder)

    private fun resolveMemberName(memberId: Uuid): String =
        memberRepository.findById(memberId)?.displayName ?: memberId.toString()
}
