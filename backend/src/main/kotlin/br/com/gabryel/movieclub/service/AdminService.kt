package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.exception.ForbiddenException
import kotlin.uuid.Uuid

/** Site-wide, not per-club -- unlike every other `*Service` in this codebase, which scopes access by club
 * membership/role. `is_site_admin` is a member-level flag (see [br.com.gabryel.movieclub.db.tables.Members]),
 * bootstrapped onto the earliest-created account by a migration since there's no self-service way to grant it. */
class AdminService(
    private val memberRepository: MemberRepository,
    private val mediaItemRepository: MediaItemRepository,
) {
    fun requireSiteAdmin(actingMemberId: Uuid): RegisteredMember {
        val member = memberRepository.findById(actingMemberId) as? RegisteredMember
        if (member?.isSiteAdmin != true) throw ForbiddenException("Must be a site admin")
        return member
    }

    fun listAllUsers(actingMemberId: Uuid): List<RegisteredMember> {
        requireSiteAdmin(actingMemberId)
        return memberRepository.listAll()
    }

    fun listAllMediaItems(actingMemberId: Uuid): List<MediaItemRow> {
        requireSiteAdmin(actingMemberId)
        return mediaItemRepository.listAll()
    }
}
