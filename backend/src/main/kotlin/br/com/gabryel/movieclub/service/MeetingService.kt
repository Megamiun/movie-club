package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ConflictException
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Pairs a pick with every member's review of it (like the spreadsheet's one column per person) -- reviews are
 * already club-visible elsewhere (e.g. [br.com.gabryel.movieclub.service.MovieService.listReviews]), so there's no
 * access-control reason to filter this down to just the acting member. */
data class MeetingMoviePick(val movie: MovieRow, val reviews: List<MovieReviewRow>)

/** [series] is the acting club's own pick of the episode's parent series (full row, same shape a Series page would
 * show -- title, year, genre, country, rating, etc.), not just a title, so the meetings list can show as much about
 * the series as it does about a movie pick. Null if the club doesn't actually follow that series. */
data class MeetingEpisodePick(val episode: EpisodeRow, val reviews: List<EpisodeReviewRow>, val series: SeriesRow?)

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
    private val seriesRepository: SeriesRepository,
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
        return loadPicks(meetingRepository.listByClub(clubId))
    }

    fun getMeeting(meetingId: Uuid, actingMemberId: Uuid): MeetingWithPicks =
        loadPicks(listOf(requireMeetingAccess(meetingId, actingMemberId))).single()

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

    /** Batch-loads every pick (+ reviews, + each episode's parent series) for [meetings] in a small fixed number
     * of queries, instead of the previous one-query-per-meeting-then-one-per-pick walk (an N+1 that used to turn
     * a single `GET /clubs/{clubId}/meetings` -- polled every 10s by the Meetings page -- into roughly
     * `O(meetings + movies + episodes×3)` round-trips). Used by both [listMeetings] (a club's whole history) and
     * [getMeeting] (a list of one), so there's one code path either way. */
    private fun loadPicks(meetings: List<MeetingRow>): List<MeetingWithPicks> {
        if (meetings.isEmpty()) return emptyList()
        val meetingIds = meetings.map { it.id }

        val moviesByMeeting = movieRepository.listByMeetings(meetingIds).groupBy { it.meetingId }
        val allMovies = moviesByMeeting.values.flatten()
        val movieReviewsByMovie = movieRepository.listReviewsByMovies(allMovies.map { it.id }).groupBy { it.movieId }

        val episodesByMeeting = episodeRepository.listByMeetings(meetingIds)
        val allEpisodes = episodesByMeeting.values.flatten()
        val episodeReviewsByEpisode = episodeRepository.listReviewsByEpisodes(allEpisodes.map { it.id }).groupBy { it.episodeId }
        val seriesImdbIdByEpisode = episodeRepository.findSeriesImdbIds(allEpisodes.map { it.id })
        // Every meeting here belongs to the same club (listByClub's own scoping, or getMeeting's single-meeting
        // list) -- safe to read it off the first one now that the empty-list guard above has already returned.
        val seriesByImdbId = seriesRepository
            .findByClubAndImdbIds(meetings.first().clubId, seriesImdbIdByEpisode.values.distinct())
            .associateBy { it.imdbId }

        return meetings.map { meeting ->
            MeetingWithPicks(
                id = meeting.id,
                clubId = meeting.clubId,
                date = meeting.date,
                assignedMemberId = meeting.assignedMemberId,
                movies = moviesByMeeting[meeting.id].orEmpty().map {
                    MeetingMoviePick(it, movieReviewsByMovie[it.id].orEmpty())
                },
                episodes = episodesByMeeting[meeting.id].orEmpty().map {
                    val series = seriesImdbIdByEpisode[it.id]?.let { imdbId -> seriesByImdbId[imdbId] }
                    MeetingEpisodePick(it, episodeReviewsByEpisode[it.id].orEmpty(), series)
                },
            )
        }
    }
}
