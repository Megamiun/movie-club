package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole
import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.DisplayTitlePreference.ORIGINAL
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
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
    private val episodeRepository = mockk<EpisodeRepository>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val clubService = mockk<ClubService>()
    private val meetingService =
        MeetingService(meetingRepository, movieRepository, episodeRepository, seriesRepository, clubService)

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
        every { meetingRepository.create(clubId, date) } returns expected

        assertEquals(expected, meetingService.createMeeting(clubId, memberId, date))
    }

    @Test
    fun `createMeeting validates the assigned member belongs to the club`() {
        val assignedId = Uuid.random()
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { clubService.requireMembership(clubId, assignedId) } throws ForbiddenException("Not a member of this club")

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
    fun `getMeeting composes the meeting with its movies, episodes, and everyone's reviews`() {
        val meetingRow = meeting()
        val movieRow = movie(meetingId = meetingRow.id)
        val episodeRow = EpisodeRow(id = Uuid.random(), seasonId = Uuid.random(), number = 1, title = "Pilot")
        val movieReview = MovieReviewRow(movieRow.id, memberId, comment = "great")
        every { meetingRepository.findById(meetingRow.id) } returns meetingRow
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { movieRepository.listByMeetings(listOf(meetingRow.id)) } returns listOf(movieRow)
        every { episodeRepository.listByMeetings(listOf(meetingRow.id)) } returns mapOf(meetingRow.id to listOf(episodeRow))
        val seriesRow = series()
        every { movieRepository.listReviewsByMovies(listOf(movieRow.id)) } returns listOf(movieReview)
        every { episodeRepository.listReviewsByEpisodes(listOf(episodeRow.id)) } returns emptyList()
        every { episodeRepository.findSeriesImdbIds(listOf(episodeRow.id)) } returns mapOf(episodeRow.id to "tt0903747")
        every { seriesRepository.findByClubAndImdbIds(clubId, listOf("tt0903747")) } returns listOf(seriesRow)

        val result = meetingService.getMeeting(meetingRow.id, memberId)

        assertEquals(listOf(MeetingMoviePick(movieRow, listOf(movieReview))), result.movies)
        assertEquals(listOf(MeetingEpisodePick(episodeRow, emptyList(), seriesRow)), result.episodes)
    }

    @Test
    fun `listMeetings composes every meeting with its own movies and episodes`() {
        val meetingA = meeting()
        val meetingB = meeting()
        val movieA = movie(meetingId = meetingA.id)
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { meetingRepository.listByClub(clubId) } returns listOf(meetingA, meetingB)
        every { movieRepository.listByMeetings(listOf(meetingA.id, meetingB.id)) } returns listOf(movieA)
        every { episodeRepository.listByMeetings(listOf(meetingA.id, meetingB.id)) } returns emptyMap()
        every { movieRepository.listReviewsByMovies(listOf(movieA.id)) } returns emptyList()
        every { episodeRepository.listReviewsByEpisodes(emptyList()) } returns emptyList()
        every { episodeRepository.findSeriesImdbIds(emptyList()) } returns emptyMap()
        every { seriesRepository.findByClubAndImdbIds(clubId, emptyList()) } returns emptyList()

        val result = meetingService.listMeetings(clubId, memberId)

        assertEquals(listOf(MeetingMoviePick(movieA, emptyList())), result.first { it.id == meetingA.id }.movies)
        assertEquals(emptyList(), result.first { it.id == meetingB.id }.movies)
    }

    @Test
    fun `listMeetings attributes each movie to its own meeting, not every meeting`() {
        // The real regression this batching change could introduce: listByMeetings now returns every movie across
        // every meeting in one flat list, so grouping it back by meetingId has to be exact -- a movie belonging to
        // meetingA must never show up under meetingB just because they were fetched in the same call.
        val meetingA = meeting()
        val meetingB = meeting()
        val movieA = movie(meetingId = meetingA.id)
        val movieB = movie(meetingId = meetingB.id)
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { meetingRepository.listByClub(clubId) } returns listOf(meetingA, meetingB)
        every { movieRepository.listByMeetings(listOf(meetingA.id, meetingB.id)) } returns listOf(movieA, movieB)
        every { episodeRepository.listByMeetings(listOf(meetingA.id, meetingB.id)) } returns emptyMap()
        every { movieRepository.listReviewsByMovies(listOf(movieA.id, movieB.id)) } returns emptyList()
        every { episodeRepository.listReviewsByEpisodes(emptyList()) } returns emptyList()
        every { episodeRepository.findSeriesImdbIds(emptyList()) } returns emptyMap()
        every { seriesRepository.findByClubAndImdbIds(clubId, emptyList()) } returns emptyList()

        val result = meetingService.listMeetings(clubId, memberId)

        assertEquals(listOf(movieA), result.first { it.id == meetingA.id }.movies.map { it.movie })
        assertEquals(listOf(movieB), result.first { it.id == meetingB.id }.movies.map { it.movie })
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
            meetingRepository.updateAssignedMember(meetingA.id, memberB)
        } returns meetingA.copy(assignedMemberId = memberB)

        every {
            meetingRepository.updateAssignedMember(meetingB.id, memberA)
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
        every { meetingRepository.updateAssignedMember(into.id) } returns into.copy(assignedMemberId = null)

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

    private fun movie(id: Uuid = Uuid.random(), meetingId: Uuid, chosenById: Uuid = memberId) = MovieRow(
        id = id,
        meetingId = meetingId,
        chosenById = chosenById,
        imdbId = "tt0000000",
        originalTitle = "A Movie",
        translations = emptyList(),
        displayTitlePreference = ORIGINAL,
        createdAt = Clock.System.now(),
    )

    private fun membership(role: ClubRole = MEMBER) = ClubMembershipRow(clubId, memberId, role, 0, Clock.System.now())

    private fun series(id: Uuid = Uuid.random(), globalSeriesId: Uuid = Uuid.random()) = SeriesRow(
        id = id,
        globalSeriesId = globalSeriesId,
        clubId = clubId,
        chosenById = memberId,
        imdbId = "tt0903747",
        originalTitle = "Breaking Bad",
        translations = emptyList(),
        displayTitlePreference = ORIGINAL,
        createdAt = Clock.System.now(),
    )
}
