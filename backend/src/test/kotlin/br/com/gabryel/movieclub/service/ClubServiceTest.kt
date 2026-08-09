package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.ADMIN
import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.ClubRepository
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.RatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.ClubRow
import br.com.gabryel.movieclub.db.repositories.dto.RatingOptionRow
import br.com.gabryel.movieclub.db.repositories.dto.RatingScaleRow
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
    private val movieRepository = mockk<MovieRepository>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val clubService = ClubService(
        clubRepository,
        ratingScaleRepository,
        memberRepository,
        movieRepository,
        seriesRepository,
        seasonRepository,
        episodeRepository,
    )

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
        verify(exactly = 0) { clubRepository.addMember(any(), any(), any(), any(), any()) }
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
        // "#66BB6A" is MEMBER_COLOR_PALETTE[2] in ClubService -- auto-assigned by rotation order at add time
        every { clubRepository.addMember(clubId, targetId, MEMBER, 2, "#66BB6A") } returns addedMembership
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

    @Test
    fun `updateColor allows a member to change their own color`() {
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = MEMBER)
        every { clubRepository.updateColor(clubId, memberId, "#123456") } returns membership(color = "#123456")
        every { memberRepository.findById(memberId) } returns registeredMember(memberId)

        val result = clubService.updateColor(clubId, memberId, memberId, "#123456")

        assertEquals("#123456", result.color)
    }

    @Test
    fun `updateColor throws ForbiddenException when a non-admin tries to change someone else's color`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = MEMBER)

        assertFailsWith<ForbiddenException> { clubService.updateColor(clubId, memberId, targetId, "#123456") }
        verify(exactly = 0) { clubRepository.updateColor(any(), any(), any()) }
    }

    @Test
    fun `updateColor allows an admin to change another member's color`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns membership(memberId = targetId)
        every { clubRepository.updateColor(clubId, targetId, "#123456") } returns
            membership(memberId = targetId, color = "#123456")
        every { memberRepository.findById(targetId) } returns registeredMember(targetId)

        val result = clubService.updateColor(clubId, memberId, targetId, "#123456")

        assertEquals("#123456", result.color)
    }

    @Test
    fun `updateColor throws NotFoundException when the target isn't a member of this club`() {
        val targetId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { clubRepository.findMembership(clubId, targetId) } returns null

        assertFailsWith<NotFoundException> { clubService.updateColor(clubId, memberId, targetId, "#123456") }
    }

    @Test
    fun `updateRatingOption throws ForbiddenException when acting member is not admin`() {
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = MEMBER)

        assertFailsWith<ForbiddenException> {
            clubService.updateRatingOption(clubId, memberId, Uuid.random(), label = "New label")
        }
    }

    @Test
    fun `updateRatingOption throws BadRequestException when option belongs to another club`() {
        val optionId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findOptionById(optionId) } returns ratingOption(optionId, scaleId = Uuid.random())
        every { ratingScaleRepository.findScales(clubId) } returns emptyList()

        assertFailsWith<BadRequestException> {
            clubService.updateRatingOption(clubId, memberId, optionId, label = "New label")
        }
    }

    @Test
    fun `updateRatingOption keeps existing label or color when only one is given`() {
        val scaleId = Uuid.random()
        val optionId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findOptionById(optionId) } returns
            ratingOption(optionId, scaleId = scaleId, label = "Old label", color = "#111111")
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))
        every { ratingScaleRepository.updateOption(optionId, "Old label", "#222222") } returns
            ratingOption(optionId, scaleId = scaleId, label = "Old label", color = "#222222")

        val result = clubService.updateRatingOption(clubId, memberId, optionId, color = "#222222")

        assertEquals("#222222", result.color)
        verify { ratingScaleRepository.updateOption(optionId, "Old label", "#222222") }
    }

    @Test
    fun `updateRatingOptionOrder throws BadRequestException when option set does not match`() {
        val scaleId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))
        every { ratingScaleRepository.findOptions(scaleId) } returns listOf(ratingOption(Uuid.random(), scaleId))

        assertFailsWith<BadRequestException> {
            clubService.updateRatingOptionOrder(clubId, memberId, scaleId, listOf(Uuid.random(), Uuid.random()))
        }
    }

    @Test
    fun `updateRatingOptionOrder applies new positions when option set matches exactly`() {
        val scaleId = Uuid.random()
        val optionA = Uuid.random()
        val optionB = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))
        every { ratingScaleRepository.findOptions(scaleId) } returns
            listOf(ratingOption(optionA, scaleId, position = 0), ratingOption(optionB, scaleId, position = 1))
        every { ratingScaleRepository.updateOptionPosition(optionB, 0) } returns ratingOption(optionB, scaleId, position = 0)
        every { ratingScaleRepository.updateOptionPosition(optionA, 1) } returns ratingOption(optionA, scaleId, position = 1)

        clubService.updateRatingOptionOrder(clubId, memberId, scaleId, listOf(optionB, optionA))

        verify { ratingScaleRepository.updateOptionPosition(optionB, 0) }
        verify { ratingScaleRepository.updateOptionPosition(optionA, 1) }
    }

    @Test
    fun `createRatingOption appends at the end of the scale`() {
        val scaleId = Uuid.random()
        val newOption = ratingOption(Uuid.random(), scaleId, label = "New option", position = 2)
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))
        every { ratingScaleRepository.findOptions(scaleId) } returns
            listOf(ratingOption(Uuid.random(), scaleId, position = 0), ratingOption(Uuid.random(), scaleId, position = 1))
        every { ratingScaleRepository.createOption(scaleId, "New option", 2, "#333333") } returns newOption

        val result = clubService.createRatingOption(clubId, memberId, scaleId, "New option", "#333333")

        assertEquals(newOption, result)
    }

    @Test
    fun `createRatingOption throws ForbiddenException when acting member is not admin`() {
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = MEMBER)

        assertFailsWith<ForbiddenException> {
            clubService.createRatingOption(clubId, memberId, Uuid.random(), "New option", "#333333")
        }
    }

    @Test
    fun `deleteRatingOption reassigns reviews across every entity type before deleting`() {
        val scaleId = Uuid.random()
        val optionId = Uuid.random()
        val reassignToId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findOptionById(optionId) } returns ratingOption(optionId, scaleId, position = 0)
        every {
            ratingScaleRepository.findOptionById(reassignToId)
        } returns ratingOption(reassignToId, scaleId, position = 1)
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))
        every { ratingScaleRepository.findOptions(scaleId) } returnsMany listOf(
            // Before delete: both options still present (checked by the "not the last option" guard).
            listOf(ratingOption(optionId, scaleId, position = 0), ratingOption(reassignToId, scaleId, position = 1)),
            // After delete: only the survivor remains, still at its old position -- must be renumbered to 0 so
            // `position` stays contiguous (rankOf in InlineRatingEditor assumes 0..N-1, no gaps).
            listOf(ratingOption(reassignToId, scaleId, position = 1)),
        )
        every { movieRepository.reassignRatingOption(optionId, reassignToId) } returns Unit
        every { seriesRepository.reassignRatingOption(optionId, reassignToId) } returns Unit
        every { seasonRepository.reassignRatingOption(optionId, reassignToId) } returns Unit
        every { episodeRepository.reassignRatingOption(optionId, reassignToId) } returns Unit
        every { ratingScaleRepository.deleteOption(optionId) } returns Unit
        every {
            ratingScaleRepository.updateOptionPosition(reassignToId, 0)
        } returns ratingOption(reassignToId, scaleId, position = 0)

        clubService.deleteRatingOption(clubId, memberId, optionId, reassignToId)

        verify { movieRepository.reassignRatingOption(optionId, reassignToId) }
        verify { seriesRepository.reassignRatingOption(optionId, reassignToId) }
        verify { seasonRepository.reassignRatingOption(optionId, reassignToId) }
        verify { episodeRepository.reassignRatingOption(optionId, reassignToId) }
        verify { ratingScaleRepository.deleteOption(optionId) }
        verify { ratingScaleRepository.updateOptionPosition(reassignToId, 0) }
    }

    @Test
    fun `deleteRatingOption throws BadRequestException when it is the last option in the scale`() {
        val scaleId = Uuid.random()
        val optionId = Uuid.random()
        val reassignToId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findOptionById(optionId) } returns ratingOption(optionId, scaleId)
        every { ratingScaleRepository.findOptionById(reassignToId) } returns ratingOption(reassignToId, scaleId)
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))
        every { ratingScaleRepository.findOptions(scaleId) } returns listOf(ratingOption(optionId, scaleId))

        assertFailsWith<BadRequestException> {
            clubService.deleteRatingOption(clubId, memberId, optionId, reassignToId)
        }
        verify(exactly = 0) { ratingScaleRepository.deleteOption(any()) }
    }

    @Test
    fun `deleteRatingOption throws BadRequestException when reassigning to itself`() {
        val scaleId = Uuid.random()
        val optionId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findOptionById(optionId) } returns ratingOption(optionId, scaleId)
        every { ratingScaleRepository.findScales(clubId) } returns listOf(RatingScaleRow(scaleId, clubId, QUALITY))

        assertFailsWith<BadRequestException> {
            clubService.deleteRatingOption(clubId, memberId, optionId, optionId)
        }
    }

    @Test
    fun `deleteRatingOption throws BadRequestException when reassignment target is from a different scale`() {
        val scaleId = Uuid.random()
        val otherScaleId = Uuid.random()
        val optionId = Uuid.random()
        val reassignToId = Uuid.random()
        every { clubRepository.findMembership(clubId, memberId) } returns membership(role = ADMIN)
        every { ratingScaleRepository.findOptionById(optionId) } returns ratingOption(optionId, scaleId)
        every { ratingScaleRepository.findOptionById(reassignToId) } returns ratingOption(reassignToId, otherScaleId)
        every { ratingScaleRepository.findScales(clubId) } returns
            listOf(RatingScaleRow(scaleId, clubId, QUALITY), RatingScaleRow(otherScaleId, clubId, SENTIMENT))

        assertFailsWith<BadRequestException> {
            clubService.deleteRatingOption(clubId, memberId, optionId, reassignToId)
        }
    }

    private fun clubRow() = ClubRow(clubId, "Movie Club", createdAt = Clock.System.now())

    private fun membership(
        memberId: Uuid = this.memberId,
        role: ClubRole = MEMBER,
        rotationOrder: Int = 0,
        color: String? = null,
    ) = ClubMembershipRow(clubId, memberId, role, rotationOrder, Clock.System.now(), color)

    private fun registeredMember(id: Uuid) = RegisteredMember(id, "member@example.com", "Member Name", "member_name", "hash")

    private fun ratingOption(
        id: Uuid,
        scaleId: Uuid,
        label: String = "Label",
        position: Int = 0,
        color: String = "#111111",
    ) = RatingOptionRow(id, scaleId, label, position, color)
}
