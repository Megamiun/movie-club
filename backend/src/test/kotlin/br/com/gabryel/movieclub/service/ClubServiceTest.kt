package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.repositories.ClubRepository
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.ClubRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ClubServiceTest {
    private val clubRepository = mockk<ClubRepository>()
    private val ratingScaleRepository = mockk<RatingScaleRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val clubService = ClubService(clubRepository, ratingScaleRepository, memberRepository)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()

    @Test
    fun `requireMembership throws ForbiddenException when not a member`() {
        every { clubRepository.findMembership(clubId, memberId) } returns null

        assertFailsWith<ForbiddenException> { clubService.requireMembership(clubId, memberId) }
    }

    @Test
    fun `requireMembership returns membership when a member`() {
        val membership = membership()
        every { clubRepository.findMembership(clubId, memberId) } returns membership

        assertEquals(membership, clubService.requireMembership(clubId, memberId))
    }

    @Test
    fun `requireAdmin throws ForbiddenException when member but not admin`() {
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = MEMBER)

        assertFailsWith<ForbiddenException> { clubService.requireAdmin(clubId, memberId) }
    }

    @Test
    fun `requireAdmin returns membership when admin`() {
        val membership = membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, memberId) } returns membership

        assertEquals(membership, clubService.requireAdmin(clubId, memberId))
    }

    @Test
    fun `getClub throws NotFoundException when club does not exist`() {
        every { clubRepository.findById(clubId) } returns null

        assertFailsWith<NotFoundException> { clubService.getClub(clubId, memberId) }
    }

    @Test
    fun `getClub throws ForbiddenException when not a member`() {
        every { clubRepository.findById(clubId) } returns clubRow()
        every { clubRepository.findMembership(clubId, memberId) } returns null

        assertFailsWith<ForbiddenException> { clubService.getClub(clubId, memberId) }
    }

    @Test
    fun `addMember throws ForbiddenException when acting member is not admin`() {
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = MEMBER)

        assertFailsWith<ForbiddenException> { clubService.addMember(clubId, memberId, Uuid.random()) }
        verify(exactly = 0) { clubRepository.addMember(any(), any(), any(), any()) }
    }

    @Test
    fun `addMember throws BadRequestException when target already a member`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns membership(memberId = targetId)

        assertFailsWith<BadRequestException> { clubService.addMember(clubId, memberId, targetId) }
    }

    @Test
    fun `addMember assigns next rotation order after admin check passes`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns null
        every { clubRepository.listMembers(clubId) } returns listOf(
            membership(rotationOrder = 0),
            membership(rotationOrder = 1),
        )

        val addedMembership = membership(memberId = targetId, role = MEMBER, rotationOrder = 2)
        every { clubRepository.addMember(clubId, targetId, MEMBER, 2) } returns addedMembership
        every { memberRepository.findById(targetId) } returns registeredMember(targetId)

        val result = clubService.addMember(clubId, memberId, targetId)
        assertEquals(targetId, result.memberId)
        assertEquals(2, result.rotationOrder)
    }

    @Test
    fun `changeRole throws BadRequestException when demoting the last admin`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns membership(memberId = targetId, role = ADMIN)
        every { clubRepository.countAdmins(clubId) } returns 1

        assertFailsWith<BadRequestException> { clubService.changeRole(clubId, memberId, targetId, MEMBER) }
        verify(exactly = 0) { clubRepository.updateRole(any(), any(), any()) }
    }

    @Test
    fun `changeRole allows demoting an admin when another admin remains`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns membership(memberId = targetId, role = ADMIN)
        every { clubRepository.countAdmins(clubId) } returns 2
        every { clubRepository.updateRole(clubId, targetId, MEMBER) } returns membership(memberId = targetId, role = MEMBER)
        every { memberRepository.findById(targetId) } returns registeredMember(targetId)

        val result = clubService.changeRole(clubId, memberId, targetId, MEMBER)
        assertEquals(targetId, result.memberId)
        assertEquals(MEMBER, result.role)
    }

    @Test
    fun `removeMember throws BadRequestException when removing the last admin`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns membership(memberId = targetId, role = ADMIN)
        every { clubRepository.countAdmins(clubId) } returns 1

        assertFailsWith<BadRequestException> { clubService.removeMember(clubId, memberId, targetId) }
        verify(exactly = 0) { clubRepository.removeMember(any(), any()) }
    }

    @Test
    fun `updateRotationOrder throws BadRequestException when member set does not match`() {
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.listMembers(clubId) } returns listOf(membership(rotationOrder = 0))

        assertFailsWith<BadRequestException> {
            clubService.updateRotationOrder(clubId, memberId, listOf(Uuid.random(), Uuid.random()))
        }
    }

    @Test
    fun `updateRotationOrder applies new order when member set matches exactly`() {
        val memberA = Uuid.random()
        val memberB = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.listMembers(clubId) } returns
            listOf(membership(memberId = memberA, rotationOrder = 0), membership(memberId = memberB, rotationOrder = 1))
        every { clubRepository.updateRotationOrder(clubId, memberB, 0) } returns membership(
            memberId = memberB,
            rotationOrder = 0,
        )
        every { clubRepository.updateRotationOrder(clubId, memberA, 1) } returns membership(
            memberId = memberA,
            rotationOrder = 1,
        )

        clubService.updateRotationOrder(clubId, memberId, listOf(memberB, memberA))

        verify { clubRepository.updateRotationOrder(clubId, memberB, 0) }
        verify { clubRepository.updateRotationOrder(clubId, memberA, 1) }
    }

    private fun clubRow() = ClubRow(clubId, "Movie Club", Clock.System.now())

    private fun membership(memberId: Uuid = this.memberId, role: ClubRole = MEMBER, rotationOrder: Int = 0) =
        ClubMembershipRow(clubId, memberId, role, rotationOrder, Clock.System.now())

    private fun registeredMember(id: Uuid) = RegisteredMember(id, "member@example.com", "Member Name", "hash")
}
