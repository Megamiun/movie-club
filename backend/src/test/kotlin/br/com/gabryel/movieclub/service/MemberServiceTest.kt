package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.dto.InvitedMember
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.UnauthorizedException
import br.com.gabryel.movieclub.service.auth.PasswordService
import br.com.gabryel.movieclub.service.storage.S3StorageClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class MemberServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val passwordService = mockk<PasswordService>()
    private val storageClient = mockk<S3StorageClient>()
    private val memberService = MemberService(memberRepository, passwordService, storageClient)

    @Test
    fun `search returns empty list for blank query without hitting the repository`() {
        assertEquals(emptyList(), memberService.search("   "))
        verify(exactly = 0) { memberRepository.search(any()) }
    }

    @Test
    fun `search trims the query and delegates to the repository`() {
        val member = registeredMember()
        every { memberRepository.search("ana") } returns listOf(member)

        assertEquals(listOf(member), memberService.search("  ana  "))
    }

    @Test
    fun `invite returns the invited member when email is new`() {
        val invited = invitedMember()
        every { memberRepository.findByEmail(invited.email) } returns null
        every { memberRepository.invite(invited.email) } returns invited

        assertEquals(invited, memberService.invite(invited.email))
    }

    @Test
    fun `invite throws ConflictException when email already exists`() {
        every { memberRepository.findByEmail(any()) } returns registeredMember()

        assertFailsWith<ConflictException> { memberService.invite("existing@example.com") }
        verify(exactly = 0) { memberRepository.invite(any()) }
    }

    @Test
    fun `register throws BadRequestException for malformed invite token`() {
        assertFailsWith<BadRequestException> { memberService.register("not-a-uuid", "Name", "username", "pass") }
    }

    @Test
    fun `register throws ForbiddenException when token not found`() {
        val token = Uuid.random()
        every { memberRepository.findByInviteToken(token) } returns null

        assertFailsWith<ForbiddenException> { memberService.register(token.toString(), "Name", "username", "pass") }
    }

    @Test
    fun `register throws ConflictException when username already taken`() {
        val token = Uuid.random()
        val invited = invitedMember(inviteToken = token)
        every { memberRepository.findByInviteToken(token) } returns invited
        every { memberRepository.findByUsername("username") } returns registeredMember()

        assertFailsWith<ConflictException> { memberService.register(token.toString(), "Name", "username", "pass") }
        verify(exactly = 0) { memberRepository.completeRegistration(any(), any(), any(), any()) }
    }

    @Test
    fun `register completes registration and returns registered member`() {
        val token = Uuid.random()
        val invited = invitedMember(inviteToken = token)
        val registered = registeredMember(id = invited.id)
        every { memberRepository.findByInviteToken(token) } returns invited
        every { memberRepository.findByUsername("username") } returns null
        every { passwordService.hash("pass") } returns "hashed"
        every { memberRepository.completeRegistration(invited.id, "Name", "username", "hashed") } returns registered

        assertEquals(registered, memberService.register(token.toString(), "Name", "username", "pass"))
    }

    @Test
    fun `login throws UnauthorizedException when email not found`() {
        every { memberRepository.findByEmail(any()) } returns null

        assertFailsWith<UnauthorizedException> { memberService.login("unknown@example.com", "pass") }
    }

    @Test
    fun `login throws UnauthorizedException when account is not yet registered`() {
        every { memberRepository.findByEmail(any()) } returns invitedMember()

        assertFailsWith<UnauthorizedException> { memberService.login("user@example.com", "pass") }
    }

    @Test
    fun `login throws UnauthorizedException when password is wrong`() {
        every { memberRepository.findByEmail(any()) } returns registeredMember()
        every { passwordService.verify("hashed", "wrong") } returns false

        assertFailsWith<UnauthorizedException> { memberService.login("user@example.com", "wrong") }
    }

    @Test
    fun `login returns member on valid credentials`() {
        val member = registeredMember()
        every { memberRepository.findByEmail(member.email) } returns member
        every { passwordService.verify("hashed", "pass") } returns true

        assertEquals(member, memberService.login(member.email, "pass"))
    }

    @Test
    fun `uploadPhoto throws ForbiddenException when acting member is not the target member`() {
        val memberId = Uuid.random()
        val actingMemberId = Uuid.random()

        assertFailsWith<ForbiddenException> {
            memberService.uploadPhoto(memberId, actingMemberId, byteArrayOf(1, 2, 3), "image/jpeg")
        }
        verify(exactly = 0) { storageClient.upload(any(), any(), any()) }
    }

    @Test
    fun `uploadPhoto throws BadRequestException for an unsupported content type`() {
        val memberId = Uuid.random()

        assertFailsWith<BadRequestException> {
            memberService.uploadPhoto(memberId, memberId, byteArrayOf(1, 2, 3), "application/pdf")
        }
        verify(exactly = 0) { storageClient.upload(any(), any(), any()) }
    }

    @Test
    fun `uploadPhoto throws BadRequestException when the file is too large`() {
        val memberId = Uuid.random()
        val tooLarge = ByteArray(5 * 1024 * 1024 + 1)

        assertFailsWith<BadRequestException> {
            memberService.uploadPhoto(memberId, memberId, tooLarge, "image/jpeg")
        }
        verify(exactly = 0) { storageClient.upload(any(), any(), any()) }
    }

    @Test
    fun `uploadPhoto stores the photo and persists the resulting key`() {
        val memberId = Uuid.random()
        val bytes = byteArrayOf(1, 2, 3)
        val updated = registeredMember(id = memberId)
        every { storageClient.upload("member-photos/$memberId", bytes, "image/jpeg") } returns "member-photos/$memberId/abc"
        every { memberRepository.updatePhoto(memberId, "member-photos/$memberId/abc") } returns updated

        assertEquals(updated, memberService.uploadPhoto(memberId, memberId, bytes, "image/jpeg"))
    }

    @Test
    fun `photoUrl resolves through the storage client when a key is set`() {
        every { storageClient.publicUrlFor("some-key") } returns "https://cdn.example.com/some-key"

        assertEquals("https://cdn.example.com/some-key", memberService.photoUrl("some-key"))
    }

    @Test
    fun `photoUrl is null when there is no key`() {
        assertEquals(null, memberService.photoUrl(null))
        verify(exactly = 0) { storageClient.publicUrlFor(any()) }
    }

    private fun invitedMember(
        id: Uuid = Uuid.random(),
        email: String = "user@example.com",
        inviteToken: Uuid = Uuid.random(),
    ) = InvitedMember(id, email, inviteToken)

    private fun registeredMember(
        id: Uuid = Uuid.random(),
        email: String = "user@example.com",
        name: String = "Test User",
        username: String = "test_user",
        passwordHash: String = "hashed",
    ) = RegisteredMember(id, email, name, username, passwordHash)
}
