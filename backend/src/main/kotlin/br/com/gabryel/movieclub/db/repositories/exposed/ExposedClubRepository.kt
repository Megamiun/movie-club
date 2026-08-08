package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.repositories.ClubRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.ClubRow
import br.com.gabryel.movieclub.db.tables.ClubMembers
import br.com.gabryel.movieclub.db.tables.Clubs
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder.ASC
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedClubRepository : ClubRepository {
    override fun create(name: String): ClubRow = transaction {
        val result = Clubs.insert {
            it[Clubs.name] = name
            it[Clubs.createdAt] = Clock.System.now()
        }
        toClubRow(result.resultedValues!!.single())
    }

    override fun findById(id: Uuid): ClubRow? = transaction {
        Clubs
            .selectAll()
            .where { Clubs.id eq id }
            .map(::toClubRow)
            .singleOrNull()
    }

    override fun addMember(clubId: Uuid, memberId: Uuid, role: ClubRole, rotationOrder: Int): ClubMembershipRow = transaction {
        val joinedAt = Clock.System.now()
        ClubMembers.insert {
            it[ClubMembers.clubId] = clubId
            it[ClubMembers.memberId] = memberId
            it[ClubMembers.role] = role
            it[ClubMembers.rotationOrder] = rotationOrder
            it[ClubMembers.joinedAt] = joinedAt
        }
        ClubMembershipRow(clubId, memberId, role, rotationOrder, joinedAt)
    }

    override fun findMembership(clubId: Uuid, memberId: Uuid): ClubMembershipRow? = transaction {
        ClubMembers
            .selectAll()
            .where { (ClubMembers.clubId eq clubId) and (ClubMembers.memberId eq memberId) }
            .map(::toMembershipRow)
            .singleOrNull()
    }

    override fun listMembers(clubId: Uuid): List<ClubMembershipRow> = transaction {
        ClubMembers
            .selectAll()
            .where { ClubMembers.clubId eq clubId }
            .orderBy(ClubMembers.rotationOrder to ASC)
            .map(::toMembershipRow)
    }

    override fun listClubsForMember(memberId: Uuid): List<ClubRow> = transaction {
        (Clubs innerJoin ClubMembers)
            .selectAll()
            .where { ClubMembers.memberId eq memberId }
            .map(::toClubRow)
    }

    override fun updateRole(clubId: Uuid, memberId: Uuid, role: ClubRole): ClubMembershipRow = transaction {
        ClubMembers.update({ (ClubMembers.clubId eq clubId) and (ClubMembers.memberId eq memberId) }) {
            it[ClubMembers.role] = role
        }
        findMembership(clubId, memberId)!!
    }

    override fun updateRotationOrder(clubId: Uuid, memberId: Uuid, rotationOrder: Int): ClubMembershipRow = transaction {
        ClubMembers.update({ (ClubMembers.clubId eq clubId) and (ClubMembers.memberId eq memberId) }) {
            it[ClubMembers.rotationOrder] = rotationOrder
        }
        findMembership(clubId, memberId)!!
    }

    override fun countAdmins(clubId: Uuid): Int = transaction {
        ClubMembers
            .selectAll()
            .where { (ClubMembers.clubId eq clubId) and (ClubMembers.role eq ADMIN) }
            .count()
            .toInt()
    }

    override fun removeMember(clubId: Uuid, memberId: Uuid) {
        transaction {
            ClubMembers.deleteWhere { (ClubMembers.clubId eq clubId) and (ClubMembers.memberId eq memberId) }
        }
    }

    private fun toClubRow(row: ResultRow) = ClubRow(
        id = row[Clubs.id].value,
        name = row[Clubs.name],
        createdAt = row[Clubs.createdAt],
    )

    private fun toMembershipRow(row: ResultRow) = ClubMembershipRow(
        clubId = row[ClubMembers.clubId].value,
        memberId = row[ClubMembers.memberId].value,
        role = row[ClubMembers.role],
        rotationOrder = row[ClubMembers.rotationOrder],
        joinedAt = row[ClubMembers.joinedAt],
    )
}
