package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRow
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.MovieRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MeetingServiceTest {
    private val meetingRepository = mockk<MeetingRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val clubService = mockk<ClubService>()
    private val meetingService = MeetingService(meetingRepository, movieRepository, clubService)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()
    private val date = LocalDate(2026, 1, 5)

    @Test
    fun `createMeeting requires club membership`() {
        every { clubService.requireMembership(clubId, memberId) } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { meetingService.createMeeting(clubId, memberId, date) }
        verify(exactly = 0) { meetingRepository.create(any(), any(), any()) }
    }

    @Test
    fun `createMeeting allows a null assigned member (empty slot)`() {
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val expected = meeting(assignedMemberId = null)
        every { meetingRepository.create(clubId, date, null) } returns expected

        assertEquals(expected, meetingService.createMeeting(clubId, memberId, date))
    }

    @Test
    fun `createMeeting validates the assigned member belongs to the club`() {
        val assignedId = Uuid.random()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every {
            clubService.requireMembership(
                clubId,
                assignedId,
            )
        } throws ForbiddenException("Not a member of this club")

        assertFailsWith<ForbiddenException> { meetingService.createMeeting(clubId, memberId, date, assignedId) }
        verify(exactly = 0) { meetingRepository.create(any(), any(), any()) }
    }

    @Test
    fun `getMeeting throws NotFoundException when missing`() {
        val meetingId = Uuid.random()
        every { meetingRepository.findById(meetingId) } returns null

        assertFailsWith<NotFoundException> { meetingService.getMeeting(meetingId, memberId) }
    }

    @Test
    fun `swapAssignments throws BadRequestException for meetings in different clubs`() {
        val meetingA = meeting()
        val meetingB = meeting(clubId = Uuid.random())
        every { meetingRepository.findById(meetingA.id) } returns meetingA
        every { meetingRepository.findById(meetingB.id) } returns meetingB
        every { clubService.requireMembership(any(), any()) } returns membership()

        assertFailsWith<BadRequestException> { meetingService.swapAssignments(meetingA.id, meetingB.id, memberId) }
    }

    @Test
    fun `swapAssignments exchanges assigned members`() {
        val memberA = Uuid.random()
        val memberB = Uuid.random()
        val meetingA = meeting(assignedMemberId = memberA)
        val meetingB = meeting(assignedMemberId = memberB)
        every { meetingRepository.findById(meetingA.id) } returns meetingA
        every { meetingRepository.findById(meetingB.id) } returns meetingB
        every { clubService.requireMembership(any(), any()) } returns membership()
        every {
            meetingRepository.updateAssignedMember(
                meetingA.id,
                memberB,
            )
        } returns meetingA.copy(assignedMemberId = memberB)
        every {
            meetingRepository.updateAssignedMember(
                meetingB.id,
                memberA,
            )
        } returns meetingB.copy(assignedMemberId = memberA)

        val (updatedA, updatedB) = meetingService.swapAssignments(meetingA.id, meetingB.id, memberId)

        assertEquals(memberB, updatedA.assignedMemberId)
        assertEquals(memberA, updatedB.assignedMemberId)
    }

    @Test
    fun `mergeMeetings moves movies, nulls assignment, and deletes the source meeting`() {
        val into = meeting(assignedMemberId = memberId)
        val from = meeting()
        val movie = movie(meetingId = from.id)
        every { meetingRepository.findById(into.id) } returns into
        every { meetingRepository.findById(from.id) } returns from
        every { clubService.requireMembership(any(), any()) } returns membership()
        every { movieRepository.listByMeeting(from.id) } returns listOf(movie)
        every { movieRepository.updateMeeting(movie.id, into.id) } returns movie.copy(meetingId = into.id)
        every { meetingRepository.delete(from.id) } returns Unit
        every { meetingRepository.updateAssignedMember(into.id, null) } returns into.copy(assignedMemberId = null)

        val result = meetingService.mergeMeetings(into.id, from.id, memberId)

        assertEquals(null, result.assignedMemberId)
        verify { movieRepository.updateMeeting(movie.id, into.id) }
        verify { meetingRepository.delete(from.id) }
    }

    @Test
    fun `deleteMeeting throws ConflictException when movies remain`() {
        val meetingRow = meeting()
        every { meetingRepository.findById(meetingRow.id) } returns meetingRow
        every { clubService.requireMembership(any(), any()) } returns membership()
        every { movieRepository.listByMeeting(meetingRow.id) } returns listOf(movie(meetingId = meetingRow.id))

        assertFailsWith<ConflictException> { meetingService.deleteMeeting(meetingRow.id, memberId) }
        verify(exactly = 0) { meetingRepository.delete(any()) }
    }

    @Test
    fun `deleteMeeting succeeds for an empty meeting`() {
        val meetingRow = meeting()
        every { meetingRepository.findById(meetingRow.id) } returns meetingRow
        every { clubService.requireMembership(any(), any()) } returns membership()
        every { movieRepository.listByMeeting(meetingRow.id) } returns emptyList()
        every { meetingRepository.delete(meetingRow.id) } returns Unit

        meetingService.deleteMeeting(meetingRow.id, memberId)

        verify { meetingRepository.delete(meetingRow.id) }
    }

    private fun meeting(
        id: Uuid = Uuid.random(),
        clubId: Uuid = this.clubId,
        date: LocalDate = this.date,
        assignedMemberId: Uuid? = null,
    ) = MeetingRow(id, clubId, date, assignedMemberId)

    private fun movie(
        id: Uuid = Uuid.random(),
        meetingId: Uuid,
        chosenById: Uuid = memberId,
    ) = MovieRow(
        id = id,
        meetingId = meetingId,
        chosenById = chosenById,
        imdbId = "tt0000000",
        tmdbId = null,
        originalTitle = "A Movie",
        englishTitle = null,
        customTitle = null,
        displayTitlePreference = ORIGINAL,
        year = null,
        director = null,
        runtimeMinutes = null,
        genre = null,
        country = null,
        tmdbRating = null,
        posterS3Key = null,
        watchLink = null,
        metadataFetchedAt = null,
        createdAt = Clock.System.now(),
    )

    private fun membership(role: ClubRole = MEMBER) = ClubMembershipRow(clubId, memberId, role, 0, Clock.System.now())
}
