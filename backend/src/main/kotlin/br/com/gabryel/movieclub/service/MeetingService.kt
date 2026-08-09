package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Pairs a pick with the *acting* member's own review of it (if any) -- the meetings list shows and edits only the
 * viewer's own rating, not everyone's, so there's no reason to fetch every member's review here. */
data class MeetingMoviePick(val movie: MovieRow, val myReview: MovieReviewRow?)

data class MeetingEpisodePick(val episode: EpisodeRow, val myReview: EpisodeReviewRow?)

/** [listMeetings]/[getMeeting] compose the bare [MeetingRow] with its picks so the meetings list can show what was
 * watched without a separate round trip per meeting -- the other mutation endpoints (postpone/swap/merge/delete)
 * don't need this and keep returning a bare [MeetingRow]. */
data class MeetingWithPicks(
    val id: Uuid,
    val clubId: Uuid,
    val date: LocalDate,
    val assignedMemberId: Uuid? = null,
    val movies: List<MeetingMoviePick>,
    val episodes: List<MeetingEpisodePick>,
)

class MeetingService(
    private val meetingRepository: MeetingRepository,
    private val movieRepository: MovieRepository,
    private val episodeRepository: EpisodeRepository,
    private val clubService: ClubService,
) {
    fun createMeeting(clubId: Uuid, actingMemberId: Uuid, date: LocalDate, assignedMemberId: Uuid? = null): MeetingRow {
        clubService.requireMembership(clubId, actingMemberId)
        if (assignedMemberId != null)
            clubService.requireMembership(clubId, assignedMemberId)

        return meetingRepository.create(clubId, date, assignedMemberId)
    }

    fun listMeetings(clubId: Uuid, actingMemberId: Uuid): List<MeetingWithPicks> {
        clubService.requireMembership(clubId, actingMemberId)
        return meetingRepository.listByClub(clubId).map { it.withPicks(actingMemberId) }
    }

    fun getMeeting(meetingId: Uuid, actingMemberId: Uuid): MeetingWithPicks =
        requireMeetingAccess(meetingId, actingMemberId).withPicks(actingMemberId)

    fun postponeMeeting(meetingId: Uuid, actingMemberId: Uuid, newDate: LocalDate): MeetingRow {
        val meeting = requireMeetingAccess(meetingId, actingMemberId)
        return meetingRepository.updateDate(meeting.id, newDate)
    }

    fun swapAssignments(meetingIdA: Uuid, meetingIdB: Uuid, actingMemberId: Uuid): Pair<MeetingRow, MeetingRow> {
        val a = requireMeetingAccess(meetingIdA, actingMemberId)
        val b = requireMeetingAccess(meetingIdB, actingMemberId)

        if (a.clubId != b.clubId)
            throw BadRequestException("Meetings must belong to the same club")

        val updatedA = meetingRepository.updateAssignedMember(a.id, b.assignedMemberId)
        val updatedB = meetingRepository.updateAssignedMember(b.id, a.assignedMemberId)
        return updatedA to updatedB
    }

    fun mergeMeetings(intoMeetingId: Uuid, fromMeetingId: Uuid, actingMemberId: Uuid): MeetingRow {
        val into = requireMeetingAccess(intoMeetingId, actingMemberId)
        val from = requireMeetingAccess(fromMeetingId, actingMemberId)

        if (into.clubId != from.clubId)
            throw BadRequestException("Meetings must belong to the same club")

        movieRepository.listByMeeting(from.id).forEach {
            movieRepository.updateMeeting(it.id, into.id)
        }
        meetingRepository.delete(from.id)
        return meetingRepository.updateAssignedMember(into.id)
    }

    fun deleteMeeting(meetingId: Uuid, actingMemberId: Uuid) {
        val meeting = requireMeetingAccess(meetingId, actingMemberId)

        if (movieRepository.listByMeeting(meeting.id).isNotEmpty())
            throw ConflictException("Meeting still has movies")

        meetingRepository.delete(meeting.id)
    }

    private fun requireMeetingAccess(meetingId: Uuid, actingMemberId: Uuid): MeetingRow {
        val meeting = meetingRepository.findById(meetingId)
            ?: throw NotFoundException("Meeting not found")

        clubService.requireMembership(meeting.clubId, actingMemberId)
        return meeting
    }

    private fun MeetingRow.withPicks(actingMemberId: Uuid) = MeetingWithPicks(
        id = id,
        clubId = clubId,
        date = date,
        assignedMemberId = assignedMemberId,
        movies = movieRepository.listByMeeting(id).map {
            MeetingMoviePick(it, movieRepository.findReview(it.id, actingMemberId))
        },
        episodes = episodeRepository.listByMeeting(id).map {
            MeetingEpisodePick(it, episodeRepository.findReview(it.id, actingMemberId))
        },
    )
}
