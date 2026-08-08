package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

class MeetingService(
    private val meetingRepository: MeetingRepository,
    private val movieRepository: MovieRepository,
    private val clubService: ClubService,
) {
    fun createMeeting(clubId: Uuid, actingMemberId: Uuid, date: LocalDate, assignedMemberId: Uuid? = null): MeetingRow {
        clubService.requireMembership(clubId, actingMemberId)
        if (assignedMemberId != null)
            clubService.requireMembership(clubId, assignedMemberId)

        return meetingRepository.create(clubId, date, assignedMemberId)
    }

    fun listMeetings(clubId: Uuid, actingMemberId: Uuid): List<MeetingRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return meetingRepository.listByClub(clubId)
    }

    fun getMeeting(meetingId: Uuid, actingMemberId: Uuid): MeetingRow {
        val meeting = meetingRepository.findById(meetingId)
            ?: throw NotFoundException("Meeting not found")

        clubService.requireMembership(meeting.clubId, actingMemberId)
        return meeting
    }

    fun postponeMeeting(meetingId: Uuid, actingMemberId: Uuid, newDate: LocalDate): MeetingRow {
        val meeting = getMeeting(meetingId, actingMemberId)
        return meetingRepository.updateDate(meeting.id, newDate)
    }

    fun swapAssignments(meetingIdA: Uuid, meetingIdB: Uuid, actingMemberId: Uuid): Pair<MeetingRow, MeetingRow> {
        val a = getMeeting(meetingIdA, actingMemberId)
        val b = getMeeting(meetingIdB, actingMemberId)

        if (a.clubId != b.clubId)
            throw BadRequestException("Meetings must belong to the same club")

        val updatedA = meetingRepository.updateAssignedMember(a.id, b.assignedMemberId)
        val updatedB = meetingRepository.updateAssignedMember(b.id, a.assignedMemberId)
        return updatedA to updatedB
    }

    fun mergeMeetings(intoMeetingId: Uuid, fromMeetingId: Uuid, actingMemberId: Uuid): MeetingRow {
        val into = getMeeting(intoMeetingId, actingMemberId)
        val from = getMeeting(fromMeetingId, actingMemberId)

        if (into.clubId != from.clubId)
            throw BadRequestException("Meetings must belong to the same club")

        movieRepository.listByMeeting(from.id).forEach {
            movieRepository.updateMeeting(it.id, into.id)
        }
        meetingRepository.delete(from.id)
        return meetingRepository.updateAssignedMember(into.id)
    }

    fun deleteMeeting(meetingId: Uuid, actingMemberId: Uuid) {
        val meeting = getMeeting(meetingId, actingMemberId)

        if (movieRepository.listByMeeting(meeting.id).isNotEmpty())
            throw ConflictException("Meeting still has movies")

        meetingRepository.delete(meeting.id)
    }
}
