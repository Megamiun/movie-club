package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.InvitedMember
import br.com.gabryel.movieclub.db.repositories.dto.MemberRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import kotlin.uuid.Uuid

interface MemberRepository {
    fun findById(id: Uuid): MemberRow?

    fun findByEmail(email: String): MemberRow?

    fun findByUsername(username: String): MemberRow?

    fun findByInviteToken(token: Uuid): InvitedMember?

    fun search(query: String, limit: Int = 10): List<MemberRow>

    /** Every registered account, site-wide -- unlike [search], not club-scoped or query-filtered. Used by the
     * site-admin panel, so invited-but-not-yet-registered members (who have no [RegisteredMember.name]/[RegisteredMember.username]
     * yet) are excluded, same as [findByInviteToken]'s counterpart filtering. */
    fun listAll(): List<RegisteredMember>

    fun invite(email: String): InvitedMember

    fun completeRegistration(id: Uuid, name: String, username: String, passwordHash: String): RegisteredMember
}
