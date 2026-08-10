package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbExternalIds
import br.com.gabryel.movieclub.service.tmdb.TmdbMovieDetails
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class WatchlistServiceTest {
    private val watchlistRepository = mockk<WatchlistRepository>()
    private val clubService = mockk<ClubService>()
    private val mediaItemRepository = mockk<MediaItemRepository>()
    private val tmdbClient = mockk<TmdbClient>()
    private val omdbClient = mockk<OmdbClient>()
    private val watchlistService =
        WatchlistService(watchlistRepository, clubService, mediaItemRepository, tmdbClient, omdbClient)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()

    init {
        coEvery { omdbClient.getImdbRating(any()) } returns null
        every { watchlistRepository.findByClubMemberAndMediaItem(any(), any(), any()) } returns null
    }

    @Test
    fun `addEntry throws BadRequestException for a non-numeric tmdbId`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> { watchlistService.addEntry(clubId, memberId, MOVIE, "not-a-number") }
    }

    @Test
    fun `addEntry resolves the movie through TMDB and creates an entry referencing its MediaItem`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.getMovieDetails(438631) } returns TmdbMovieDetails(
                originalTitle = "Dune",
                title = "Dune",
                externalIds = TmdbExternalIds(imdbId = "tt1160419"),
            )
            val item = mediaItem()
            every {
                mediaItemRepository.findOrCreate(MOVIE, "tt1160419", "Dune", "438631", null, null, null)
            } returns item

            val expected = entry(mediaItemId = item.id)
            every { watchlistRepository.create(clubId, memberId, item.id) } returns expected

            assertEquals(expected, watchlistService.addEntry(clubId, memberId, MOVIE, "438631"))
        }

    @Test
    fun `addEntry throws BadRequestException when this member already has the media item in their watchlist`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { tmdbClient.getMovieDetails(438631) } returns TmdbMovieDetails(
                originalTitle = "Dune",
                title = "Dune",
                externalIds = TmdbExternalIds(imdbId = "tt1160419"),
            )
            val item = mediaItem()
            every {
                mediaItemRepository.findOrCreate(MOVIE, "tt1160419", "Dune", "438631", null, null, null)
            } returns item
            every { watchlistRepository.findByClubMemberAndMediaItem(clubId, memberId, item.id) } returns entry(mediaItemId = item.id)

            assertFailsWith<BadRequestException> { watchlistService.addEntry(clubId, memberId, MOVIE, "438631") }
        }

    @Test
    fun `deleteEntry throws ForbiddenException when acting member is not the owner`() {
        val entryId = Uuid.random()
        val ownerId = Uuid.random()

        every { watchlistRepository.findById(entryId) } returns entry(id = entryId, memberId = ownerId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<ForbiddenException> { watchlistService.deleteEntry(entryId, memberId) }
        verify(exactly = 0) { watchlistRepository.delete(any()) }
    }

    @Test
    fun `moveEntry swaps positions with the adjacent entry in the same owner's column, even when acting member isn't the owner`() {
        val entryId = Uuid.random()
        val otherId = Uuid.random()
        val ownerId = Uuid.random()
        val current = entry(id = entryId, memberId = ownerId, position = 1)
        val other = entry(id = otherId, memberId = ownerId, position = 0)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(other, current)
        every { watchlistRepository.updatePosition(entryId, 0) } returns current.copy(position = 0)
        every { watchlistRepository.updatePosition(otherId, 1) } returns other.copy(position = 1)

        watchlistService.moveEntry(entryId, memberId, MoveDirection.UP)

        verify { watchlistRepository.updatePosition(entryId, 0) }
        verify { watchlistRepository.updatePosition(otherId, 1) }
    }

    @Test
    fun `moveEntry never swaps across a different member's column, even with an adjacent position`() {
        val entryId = Uuid.random()
        val otherMembersEntryId = Uuid.random()
        val current = entry(id = entryId, position = 1)
        val otherMembersEntry = entry(id = otherMembersEntryId, memberId = Uuid.random(), position = 0)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(otherMembersEntry, current)

        watchlistService.moveEntry(entryId, memberId, MoveDirection.UP)

        verify(exactly = 0) { watchlistRepository.updatePosition(any(), any()) }
    }

    @Test
    fun `moveEntry is a no-op at the edge of the list`() {
        val entryId = Uuid.random()
        val current = entry(id = entryId, position = 0)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(current)

        watchlistService.moveEntry(entryId, memberId, MoveDirection.UP)

        verify(exactly = 0) { watchlistRepository.updatePosition(any(), any()) }
    }

    @Test
    fun `deleteEntry throws NotFoundException when entry is missing`() {
        val entryId = Uuid.random()
        every { watchlistRepository.findById(entryId) } returns null

        assertFailsWith<NotFoundException> { watchlistService.deleteEntry(entryId, memberId) }
    }

    @Test
    fun `listEntries returns entries visible to any club member, not just owners`() {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val entries = listOf(entry(memberId = Uuid.random()), entry(memberId = memberId))
        every { watchlistRepository.listByClub(clubId) } returns entries

        assertEquals(entries, watchlistService.listEntries(clubId, memberId))
    }

    private fun membership() = ClubMembershipRow(clubId, memberId, MEMBER, 0, Clock.System.now())

    private fun mediaItem(id: Uuid = Uuid.random()) = MediaItemRow(
        id = id,
        type = MOVIE,
        imdbId = "tt1160419",
        title = "Dune",
        createdAt = Clock.System.now(),
    )

    private fun entry(
        id: Uuid = Uuid.random(),
        memberId: Uuid = this.memberId,
        mediaItemId: Uuid = Uuid.random(),
        position: Int = 0,
    ) = WatchlistEntryRow(
        id = id,
        clubId = clubId,
        memberId = memberId,
        mediaItemId = mediaItemId,
        type = SERIES,
        title = "Dune",
        imdbId = "tt1160419",
        position = position,
        createdAt = Clock.System.now(),
    )
}
