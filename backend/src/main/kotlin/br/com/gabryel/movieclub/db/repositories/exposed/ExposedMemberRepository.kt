package br.com.gabryel.movieclub.db.repositories.exposed

import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.dto.InvitedMember
import br.com.gabryel.movieclub.db.repositories.dto.MemberRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.db.tables.Members
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ExposedMemberRepository : MemberRepository {
    override fun findById(id: Uuid): MemberRow? = transaction {
        Members
            .selectAll()
            .where { Members.id eq id }
            .mapNotNull(::toRow)
            .singleOrNull()
    }

    override fun findByEmail(email: String): MemberRow? = transaction {
        Members
            .selectAll()
            .where { Members.email eq email }
            .mapNotNull(::toRow)
            .singleOrNull()
    }

    override fun findByUsername(username: String): MemberRow? = transaction {
        Members
            .selectAll()
            .where { Members.username eq username }
            .mapNotNull(::toRow)
            .singleOrNull()
    }

    override fun findByInviteToken(token: Uuid): InvitedMember? = transaction {
        Members
            .selectAll()
            .where { Members.inviteToken eq token }
            .mapNotNull(::toRow)
            .filterIsInstance<InvitedMember>()
            .singleOrNull()
    }

    override fun search(query: String, limit: Int): List<MemberRow> = transaction {
        val pattern = "%${query.lowercase()}%"
        Members
            .selectAll()
            .where { (Members.email.lowerCase() like pattern) or (Members.name.lowerCase() like pattern) }
            .limit(limit)
            .mapNotNull(::toRow)
    }

    override fun invite(email: String): InvitedMember = transaction {
        val token = Uuid.random()
        val result = Members.insert {
            it[Members.email] = email
            it[Members.inviteToken] = token
            it[Members.createdAt] = Clock.System.now()
        }
        InvitedMember(
            id = result[Members.id].value,
            email = email,
            inviteToken = token,
        )
    }

    override fun completeRegistration(id: Uuid, name: String, username: String, passwordHash: String): RegisteredMember =
        transaction {
            Members.update({ Members.id eq id }) {
                it[Members.name] = name
                it[Members.username] = username
                it[Members.passwordHash] = passwordHash
                it[Members.inviteToken] = null
            }
            Members
                .selectAll()
                .where { Members.id eq id }
                .mapNotNull(::toRow)
                .filterIsInstance<RegisteredMember>()
                .single()
        }

    private fun toRow(row: ResultRow): MemberRow? {
        val id = row[Members.id].value
        val email = row[Members.email]
        val inviteToken = row[Members.inviteToken]
        val name = row[Members.name]
        val username = row[Members.username]
        val passwordHash = row[Members.passwordHash]
        return when {
            inviteToken != null -> InvitedMember(id, email, inviteToken)
            name != null && username != null && passwordHash != null -> RegisteredMember(id, email, name, username, passwordHash)
            else -> null
        }
    }
}
