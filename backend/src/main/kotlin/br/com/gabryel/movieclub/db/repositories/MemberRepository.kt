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

    fun invite(email: String): InvitedMember

    fun completeRegistration(id: Uuid, name: String, username: String, passwordHash: String): RegisteredMember
}
