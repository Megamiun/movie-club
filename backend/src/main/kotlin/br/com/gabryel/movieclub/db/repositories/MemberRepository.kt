package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.tables.Members
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.time.Clock
import kotlin.uuid.Uuid

sealed class MemberRow {
    abstract val id: UUID
    abstract val email: String
}

data class InvitedMember(
    override val id: UUID,
    override val email: String,
    val inviteToken: UUID,
) : MemberRow()

data class RegisteredMember(
    override val id: UUID,
    override val email: String,
    val name: String,
    val passwordHash: String,
) : MemberRow()

interface MemberRepository {
    fun findById(id: UUID): MemberRow?

    fun findByEmail(email: String): MemberRow?

    fun findByInviteToken(token: UUID): InvitedMember?

    fun invite(email: String): InvitedMember

    fun completeRegistration(id: UUID, name: String, passwordHash: String): RegisteredMember
}

class ExposedMemberRepository : MemberRepository {
    override fun findById(id: UUID): MemberRow? =
        transaction {
            Members
                .selectAll()
                .where { Members.id eq id.toKotlinUuid() }
                .mapNotNull(::toRow)
                .singleOrNull()
        }

    override fun findByEmail(email: String): MemberRow? =
        transaction {
            Members
                .selectAll()
                .where { Members.email eq email }
                .mapNotNull(::toRow)
                .singleOrNull()
        }

    override fun findByInviteToken(token: UUID): InvitedMember? =
        transaction {
            Members
                .selectAll()
                .where { Members.inviteToken eq token.toKotlinUuid() }
                .mapNotNull(::toRow)
                .filterIsInstance<InvitedMember>()
                .singleOrNull()
        }

    override fun invite(email: String): InvitedMember =
        transaction {
            val token = UUID.randomUUID()
            val result = Members.insert {
                it[Members.email] = email
                it[Members.inviteToken] = token.toKotlinUuid()
                it[Members.createdAt] = Clock.System.now()
            }
            InvitedMember(
                id = result[Members.id].value.toJavaUuid(),
                email = email,
                inviteToken = token,
            )
        }

    override fun completeRegistration(id: UUID, name: String, passwordHash: String): RegisteredMember =
        transaction {
            Members.update({ Members.id eq id.toKotlinUuid() }) {
                it[Members.name] = name
                it[Members.passwordHash] = passwordHash
                it[Members.inviteToken] = null
            }
            Members
                .selectAll()
                .where { Members.id eq id.toKotlinUuid() }
                .mapNotNull(::toRow)
                .filterIsInstance<RegisteredMember>()
                .single()
        }

    private fun toRow(row: ResultRow): MemberRow? {
        val id = row[Members.id].value.toJavaUuid()
        val email = row[Members.email]
        val inviteToken = row[Members.inviteToken]?.toJavaUuid()
        val name = row[Members.name]
        val passwordHash = row[Members.passwordHash]
        return when {
            inviteToken != null -> InvitedMember(id, email, inviteToken)
            name != null && passwordHash != null -> RegisteredMember(id, email, name, passwordHash)
            else -> null
        }
    }
}

private fun UUID.toKotlinUuid() = Uuid.fromLongs(mostSignificantBits, leastSignificantBits)

private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())
