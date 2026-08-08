package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.ClubRow
import kotlin.uuid.Uuid

interface ClubRepository {
    fun create(name: String): ClubRow

    fun findById(id: Uuid): ClubRow?

    fun addMember(clubId: Uuid, memberId: Uuid, role: ClubRole, rotationOrder: Int): ClubMembershipRow

    fun findMembership(clubId: Uuid, memberId: Uuid): ClubMembershipRow?

    fun listMembers(clubId: Uuid): List<ClubMembershipRow>

    fun listClubsForMember(memberId: Uuid): List<ClubRow>

    fun updateRole(clubId: Uuid, memberId: Uuid, role: ClubRole): ClubMembershipRow

    fun updateRotationOrder(clubId: Uuid, memberId: Uuid, rotationOrder: Int): ClubMembershipRow

    fun countAdmins(clubId: Uuid): Int

    fun removeMember(clubId: Uuid, memberId: Uuid)
}
