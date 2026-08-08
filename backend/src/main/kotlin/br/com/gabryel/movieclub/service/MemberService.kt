package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.MemberRepository
import br.com.gabryel.movieclub.db.repositories.dto.InvitedMember
import br.com.gabryel.movieclub.db.repositories.dto.MemberRow
import br.com.gabryel.movieclub.db.repositories.dto.RegisteredMember
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.UnauthorizedException
import br.com.gabryel.movieclub.service.auth.PasswordService
import kotlin.uuid.Uuid

class MemberService(private val memberRepository: MemberRepository, private val passwordService: PasswordService) {
    fun search(query: String): List<MemberRow> {
        if (query.isBlank()) return emptyList()
        return memberRepository.search(query.trim())
    }

    fun invite(email: String): InvitedMember {
        if (memberRepository.findByEmail(email) != null)
            throw ConflictException("Email already exists")

        return memberRepository.invite(email)
    }

    fun register(inviteToken: String, name: String, username: String, password: String): RegisteredMember {
        val token = Uuid.parseOrNull(inviteToken)
            ?: throw BadRequestException("Invalid invite token")

        val member = memberRepository.findByInviteToken(token)
            ?: throw ForbiddenException("Invalid or expired invite token")

        if (memberRepository.findByUsername(username) != null)
            throw ConflictException("Username already taken")

        return memberRepository.completeRegistration(member.id, name, username, passwordService.hash(password))
    }

    fun login(email: String, password: String): RegisteredMember {
        val member = memberRepository.findByEmail(email) as? RegisteredMember
            ?: throw UnauthorizedException("Invalid credentials")

        if (!passwordService.verify(member.passwordHash, password))
            throw UnauthorizedException("Invalid credentials")

        return member
    }
}
