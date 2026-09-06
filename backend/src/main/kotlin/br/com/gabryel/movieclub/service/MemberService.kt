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
import br.com.gabryel.movieclub.service.storage.S3StorageClient
import kotlin.uuid.Uuid

private val ALLOWED_PHOTO_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024

class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordService: PasswordService,
    private val storageClient: S3StorageClient,
) {
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

    /** Self-service only, unlike a club's per-membership `color` (which any club admin can also edit) -- a photo
     * is a global Member fact, not scoped to any one club, so there's no natural "which club's admin" to extend
     * edit rights to the way there is for color. */
    fun uploadPhoto(memberId: Uuid, actingMemberId: Uuid, bytes: ByteArray, contentType: String): RegisteredMember {
        if (memberId != actingMemberId) throw ForbiddenException("Only the member themselves can change their own photo")
        if (contentType !in ALLOWED_PHOTO_CONTENT_TYPES) throw BadRequestException("Unsupported image type: $contentType")
        if (bytes.size > MAX_PHOTO_BYTES) throw BadRequestException("Photo must be under ${MAX_PHOTO_BYTES / (1024 * 1024)}MB")

        val key = storageClient.upload("member-photos/$memberId", bytes, contentType)
        return memberRepository.updatePhoto(memberId, key)
    }

    fun photoUrl(photoS3Key: String?): String? = photoS3Key?.let { storageClient.publicUrlFor(it) }
}
