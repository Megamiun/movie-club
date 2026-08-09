package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.dto.InvitedMember
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.exception.ForbiddenException
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val mediaItemRepository = mockk<MediaItemRepository>()
    private val adminService = AdminService(memberRepository, mediaItemRepository)

    private val memberId = Uuid.random()

    @Test
    fun `requireSiteAdmin throws ForbiddenException when the member isn't a site admin`() {
        every { memberRepository.findById(memberId) } returns registeredMember(isSiteAdmin = false)

        assertFailsWith<ForbiddenException> { adminService.requireSiteAdmin(memberId) }
    }

    @Test
    fun `requireSiteAdmin throws ForbiddenException when the member doesn't exist`() {
        every { memberRepository.findById(memberId) } returns null

        assertFailsWith<ForbiddenException> { adminService.requireSiteAdmin(memberId) }
    }

    @Test
    fun `requireSiteAdmin throws ForbiddenException for an invited-but-not-registered member`() {
        every { memberRepository.findById(memberId) } returns InvitedMember(memberId, "invited@example.com", Uuid.random())

        assertFailsWith<ForbiddenException> { adminService.requireSiteAdmin(memberId) }
    }

    @Test
    fun `requireSiteAdmin returns the member when they are a site admin`() {
        val admin = registeredMember(isSiteAdmin = true)
        every { memberRepository.findById(memberId) } returns admin

        assertEquals(admin, adminService.requireSiteAdmin(memberId))
    }

    @Test
    fun `listAllUsers throws ForbiddenException for a non-admin instead of hitting the repository`() {
        every { memberRepository.findById(memberId) } returns registeredMember(isSiteAdmin = false)

        assertFailsWith<ForbiddenException> { adminService.listAllUsers(memberId) }
    }

    @Test
    fun `listAllUsers returns every registered member for a site admin`() {
        every { memberRepository.findById(memberId) } returns registeredMember(isSiteAdmin = true)
        val allMembers = listOf(registeredMember(isSiteAdmin = true), registeredMember(isSiteAdmin = false))
        every { memberRepository.listAll() } returns allMembers

        assertEquals(allMembers, adminService.listAllUsers(memberId))
    }

    @Test
    fun `listAllMediaItems throws ForbiddenException for a non-admin instead of hitting the repository`() {
        every { memberRepository.findById(memberId) } returns registeredMember(isSiteAdmin = false)

        assertFailsWith<ForbiddenException> { adminService.listAllMediaItems(memberId) }
    }

    @Test
    fun `listAllMediaItems returns every media item for a site admin`() {
        every { memberRepository.findById(memberId) } returns registeredMember(isSiteAdmin = true)
        val allItems = listOf(mediaItem())
        every { mediaItemRepository.listAll() } returns allItems

        assertEquals(allItems, adminService.listAllMediaItems(memberId))
    }

    private fun registeredMember(isSiteAdmin: Boolean) = RegisteredMember(
        id = Uuid.random(),
        email = "member@example.com",
        name = "Member Name",
        username = "member_name",
        passwordHash = "hash",
        isSiteAdmin = isSiteAdmin,
    )

    private fun mediaItem() = MediaItemRow(
        id = Uuid.random(),
        type = MOVIE,
        imdbId = "tt4857264",
        title = "Contratiempo",
        createdAt = Clock.System.now(),
    )
}
