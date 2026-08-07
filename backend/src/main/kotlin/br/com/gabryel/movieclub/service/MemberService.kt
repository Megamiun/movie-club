package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.service.auth.PasswordService
import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.RegisteredMember
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.UnauthorizedException
import java.util.UUID

class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordService: PasswordService,
) {
    fun invite(email: String): UUID {
        if (memberRepository.findByEmail(email) != null)
            throw ConflictException("Email already exists")

        return memberRepository.invite(email).inviteToken
    }

    fun register(inviteToken: String, name: String, password: String): RegisteredMember {
        val token = runCatching { UUID.fromString(inviteToken) }.getOrNull()
            ?: throw BadRequestException("Invalid invite token")

        val member = memberRepository.findByInviteToken(token)
            ?: throw ForbiddenException("Invalid or expired invite token")

        return memberRepository.completeRegistration(member.id, name, passwordService.hash(password))
    }

    fun login(email: String, password: String): RegisteredMember {
        val member = memberRepository.findByEmail(email) as? RegisteredMember
            ?: throw UnauthorizedException("Invalid credentials")

        if (!passwordService.verify(member.passwordHash, password))
            throw UnauthorizedException("Invalid credentials")

        return member
    }
}
