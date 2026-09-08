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
    private val metadataRefreshJob: MetadataRefreshJob,
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

    /** On-demand run of the same sweep the nightly scheduler triggers automatically (see `Application.kt`'s own
     * coroutine loop) -- lets a site admin force a refresh cycle immediately instead of waiting for the next
     * scheduled one, e.g. right after noticing a batch of stale ratings. Same OMDb-quota-aware budget either way;
     * running this manually still counts against the same daily allowance a scheduled run would. */
    suspend fun triggerMetadataRefresh(actingMemberId: Uuid): MetadataRefreshResult {
        requireSiteAdmin(actingMemberId)
        return metadataRefreshJob.run()
    }
}
