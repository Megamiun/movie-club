package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.service.auth.PasswordService
import br.com.gabryel.movieclub.db.repositories.InvitedMember
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.RegisteredMember
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.UnauthorizedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemberServiceTest {
    private val memberRepository = mockk<MemberRepository>()
    private val passwordService = mockk<PasswordService>()
    private val memberService = MemberService(memberRepository, passwordService)

    @Test
    fun `invite returns token when email is new`() {
        val invited = invitedMember()
        every { memberRepository.findByEmail(invited.email) } returns null
        every { memberRepository.invite(invited.email) } returns invited

        assertEquals(invited.inviteToken, memberService.invite(invited.email))
    }

    @Test
    fun `invite throws ConflictException when email already exists`() {
        every { memberRepository.findByEmail(any()) } returns registeredMember()

        assertFailsWith<ConflictException> { memberService.invite("existing@example.com") }
        verify(exactly = 0) { memberRepository.invite(any()) }
    }

    @Test
    fun `register throws BadRequestException for malformed invite token`() {
        assertFailsWith<BadRequestException> { memberService.register("not-a-uuid", "Name", "pass") }
    }

    @Test
    fun `register throws ForbiddenException when token not found`() {
        val token = UUID.randomUUID()
        every { memberRepository.findByInviteToken(token) } returns null

        assertFailsWith<ForbiddenException> { memberService.register(token.toString(), "Name", "pass") }
    }

    @Test
    fun `register completes registration and returns registered member`() {
        val token = UUID.randomUUID()
        val invited = invitedMember(inviteToken = token)
        val registered = registeredMember(id = invited.id)
        every { memberRepository.findByInviteToken(token) } returns invited
        every { passwordService.hash("pass") } returns "hashed"
        every { memberRepository.completeRegistration(invited.id, "Name", "hashed") } returns registered

        assertEquals(registered, memberService.register(token.toString(), "Name", "pass"))
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

    private fun invitedMember(
        id: UUID = UUID.randomUUID(),
        email: String = "user@example.com",
        inviteToken: UUID = UUID.randomUUID(),
    ) = InvitedMember(id, email, inviteToken)

    private fun registeredMember(
        id: UUID = UUID.randomUUID(),
        email: String = "user@example.com",
        name: String = "Test User",
        passwordHash: String = "hashed",
    ) = RegisteredMember(id, email, name, passwordHash)
}
