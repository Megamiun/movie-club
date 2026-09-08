package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.CatalogTitleInfo
import br.com.gabryel.movieclub.db.repositories.dto.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.Translation
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val movieRepository = mockk<MovieRepository>()
    private val seriesRepository = mockk<SeriesRepository>()
    private val tmdbClient = mockk<TmdbClient>()
    private val movieService = mockk<MovieService>()
    private val seriesService = mockk<SeriesService>()
    private val watchlistService = WatchlistService(
        watchlistRepository,
        clubService,
        movieRepository,
        seriesRepository,
        tmdbClient,
        movieService,
        seriesService,
    )

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()

    init {
        every { watchlistRepository.findByClubMemberAndMediaItem(any(), any(), any()) } returns null
        every { movieRepository.findCatalogTitleInfoByMediaItemIds(any()) } returns emptyMap()
        every { seriesRepository.findCatalogTitleInfoByMediaItemIds(any()) } returns emptyMap()
    }

    @Test
    fun `addEntry throws BadRequestException for a non-numeric tmdbId`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> { watchlistService.addEntry(clubId, memberId, MOVIE, "not-a-number") }
    }

    @Test
    fun `addEntry resolves the movie through MovieService and creates an entry referencing its MediaItem`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            val item = mediaItem()
            coEvery { movieService.findOrCreateCatalogMediaItem(438631) } returns item

            val expected = entry(mediaItemId = item.id)
            every { watchlistRepository.create(clubId, memberId, item.id) } returns expected

            assertEquals(expected, watchlistService.addEntry(clubId, memberId, MOVIE, "438631"))
        }

    /** Guards against the actual bug this delegation was introduced to fix: a bare `mediaItemRepository.findOrCreate`
     * (this used to call that directly) never creates the Series *catalog* row, so a series added straight to the
     * Watchlist had no `originalLanguage`/`translations` for `resolveTitle` (frontend `utils/title.ts`) to work
     * with -- the club's language preferences were silently never applied to it. Routing through
     * `SeriesService.findOrCreateCatalogMediaItem` instead ensures that catalog row always exists. */
    @Test
    fun `addEntry resolves a series through SeriesService, not a bare MediaItem lookup`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val item = mediaItem().copy(type = SERIES)
        coEvery { seriesService.findOrCreateCatalogMediaItem(1396) } returns item

        val expected = entry(mediaItemId = item.id, type = SERIES)
        every { watchlistRepository.create(clubId, memberId, item.id) } returns expected

        assertEquals(expected, watchlistService.addEntry(clubId, memberId, SERIES, "1396"))
    }

    @Test
    fun `addEntry throws BadRequestException when this member already has the media item in their watchlist`(): Unit =
        runBlocking {
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            val item = mediaItem()
            coEvery { movieService.findOrCreateCatalogMediaItem(438631) } returns item
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
    fun `moveEntry swaps positions with the adjacent entry in the same owner's list, even when acting member isn't the owner`(): Unit =
        runBlocking {
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

            watchlistService.moveEntry(entryId, memberId, 0)

            verify { watchlistRepository.updatePosition(entryId, 0) }
            verify { watchlistRepository.updatePosition(otherId, 1) }
        }

    @Test
    fun `moveEntry swaps across movie and series entries, since they now share one mixed list per member`(): Unit = runBlocking {
        val entryId = Uuid.random()
        val otherId = Uuid.random()
        val ownerId = Uuid.random()
        val current = entry(id = entryId, memberId = ownerId, position = 1, type = MOVIE)
        val other = entry(id = otherId, memberId = ownerId, position = 0, type = SERIES)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(other, current)
        every { watchlistRepository.updatePosition(entryId, 0) } returns current.copy(position = 0)
        every { watchlistRepository.updatePosition(otherId, 1) } returns other.copy(position = 1)

        watchlistService.moveEntry(entryId, memberId, 0)

        verify { watchlistRepository.updatePosition(entryId, 0) }
        verify { watchlistRepository.updatePosition(otherId, 1) }
    }

    @Test
    fun `moveEntry shifts every sibling in between in one call, not just the adjacent one`(): Unit = runBlocking {
        val entryId = Uuid.random()
        val ownerId = Uuid.random()
        val current = entry(id = entryId, memberId = ownerId, position = 0)
        val second = entry(id = Uuid.random(), memberId = ownerId, position = 1)
        val third = entry(id = Uuid.random(), memberId = ownerId, position = 2)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(current, second, third)
        every { watchlistRepository.updatePosition(any(), any()) } answers { current }

        watchlistService.moveEntry(entryId, memberId, 2)

        verify { watchlistRepository.updatePosition(second.id, 0) }
        verify { watchlistRepository.updatePosition(third.id, 1) }
        verify { watchlistRepository.updatePosition(entryId, 2) }
    }

    @Test
    fun `moveEntry never swaps across a different member's list, even with an adjacent position`(): Unit = runBlocking {
        val entryId = Uuid.random()
        val otherMembersEntryId = Uuid.random()
        val current = entry(id = entryId, position = 1)
        val otherMembersEntry = entry(id = otherMembersEntryId, memberId = Uuid.random(), position = 0)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(otherMembersEntry, current)

        watchlistService.moveEntry(entryId, memberId, 0)

        verify(exactly = 0) { watchlistRepository.updatePosition(any(), any()) }
    }

    @Test
    fun `moveEntry clamps an out-of-bounds target position instead of failing`(): Unit = runBlocking {
        val entryId = Uuid.random()
        val current = entry(id = entryId, position = 0)

        every { watchlistRepository.findById(entryId) } returns current
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        every { watchlistRepository.listByClub(clubId) } returns listOf(current)

        watchlistService.moveEntry(entryId, memberId, 5)

        verify(exactly = 0) { watchlistRepository.updatePosition(any(), any()) }
    }

    @Test
    fun `deleteEntry throws NotFoundException when entry is missing`() {
        val entryId = Uuid.random()
        every { watchlistRepository.findById(entryId) } returns null

        assertFailsWith<NotFoundException> { watchlistService.deleteEntry(entryId, memberId) }
    }

    @Test
    fun `moveEntryToMeeting adds the movie to the meeting and deletes the entry, even when acting member isn't the owner`(): Unit =
        runBlocking {
            val entryId = Uuid.random()
            val meetingId = Uuid.random()
            val ownerId = Uuid.random()
            val movieEntry = entry(id = entryId, memberId = ownerId, type = MOVIE)
            val createdMovie = mockk<MovieRow>()

            every { watchlistRepository.findById(entryId) } returns movieEntry
            every { clubService.requireMembership(clubId, memberId) } returns membership()
            coEvery { movieService.addMovie(meetingId, memberId, movieEntry.imdbId) } returns createdMovie
            every { watchlistRepository.delete(entryId) } returns Unit

            val result = watchlistService.moveEntryToMeeting(entryId, meetingId, memberId)

            assertEquals(createdMovie, result)
            verify { watchlistRepository.delete(entryId) }
        }

    @Test
    fun `moveEntryToMeeting throws BadRequestException for a series entry`(): Unit = runBlocking {
        val entryId = Uuid.random()
        val seriesEntry = entry(id = entryId, type = SERIES)

        every { watchlistRepository.findById(entryId) } returns seriesEntry
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> { watchlistService.moveEntryToMeeting(entryId, Uuid.random(), memberId) }
        verify(exactly = 0) { watchlistRepository.delete(any()) }
    }

    @Test
    fun `moveEntryToMeeting throws NotFoundException when entry is missing`(): Unit = runBlocking {
        val entryId = Uuid.random()
        every { watchlistRepository.findById(entryId) } returns null

        assertFailsWith<NotFoundException> { watchlistService.moveEntryToMeeting(entryId, Uuid.random(), memberId) }
    }

    @Test
    fun `listEntries returns entries visible to any club member, not just owners`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val entries = listOf(entry(memberId = Uuid.random()), entry(memberId = memberId))
        every { watchlistRepository.listByClub(clubId) } returns entries

        assertEquals(entries, watchlistService.listEntries(clubId, memberId))
    }

    @Test
    fun `listEntries fills in originalLanguage and translations from the movie catalog for a movie entry`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val movieEntry = entry(type = MOVIE)
        every { watchlistRepository.listByClub(clubId) } returns listOf(movieEntry)
        val info = CatalogTitleInfo("en", listOf(Translation("pt", "BR", "Portuguese", "Duna")))
        every { movieRepository.findCatalogTitleInfoByMediaItemIds(listOf(movieEntry.mediaItemId)) } returns
            mapOf(movieEntry.mediaItemId to info)

        val result = watchlistService.listEntries(clubId, memberId).single()

        assertEquals("en", result.originalLanguage)
        assertEquals(info.translations, result.translations)
    }

    @Test
    fun `listEntries fills in originalLanguage and translations from the series catalog for a series entry`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val seriesEntry = entry(type = SERIES)
        every { watchlistRepository.listByClub(clubId) } returns listOf(seriesEntry)
        val info = CatalogTitleInfo("ja", listOf(Translation("en", "US", "English", "Cowboy Bebop")))
        every { seriesRepository.findCatalogTitleInfoByMediaItemIds(listOf(seriesEntry.mediaItemId)) } returns
            mapOf(seriesEntry.mediaItemId to info)

        val result = watchlistService.listEntries(clubId, memberId).single()

        assertEquals("ja", result.originalLanguage)
        assertEquals(info.translations, result.translations)
    }

    /** Covers the actual bug the user hit in production: a Watchlist entry added before `fetchMovieMediaItem`/
     * `fetchSeriesMediaItem` started creating a real catalog row had a MediaItem but nothing in `Movies`, so
     * `resolveTitle` silently ignored the club's language preferences for it forever. `listEntries` now self-heals
     * this on the very next load instead of leaving it permanently blank. */
    @Test
    fun `listEntries backfills a missing catalog row on the fly and returns its language info`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val movieEntry = entry(type = MOVIE)
        every { watchlistRepository.listByClub(clubId) } returns listOf(movieEntry)
        val info = CatalogTitleInfo("en", listOf(Translation("pt", "BR", "Portuguese", "Duna")))
        every { movieRepository.findCatalogTitleInfoByMediaItemIds(listOf(movieEntry.mediaItemId)) } returnsMany
            listOf(emptyMap(), mapOf(movieEntry.mediaItemId to info))
        coEvery { movieService.refreshByImdbId(movieEntry.imdbId) } returns mediaItem()

        val result = watchlistService.listEntries(clubId, memberId).single()

        assertEquals("en", result.originalLanguage)
        assertEquals(info.translations, result.translations)
        coVerify { movieService.refreshByImdbId(movieEntry.imdbId) }
    }

    @Test
    fun `listEntries leaves originalLanguage null when the backfill attempt itself fails`(): Unit = runBlocking {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        val movieEntry = entry(type = MOVIE)
        every { watchlistRepository.listByClub(clubId) } returns listOf(movieEntry)
        every { movieRepository.findCatalogTitleInfoByMediaItemIds(listOf(movieEntry.mediaItemId)) } returns emptyMap()
        coEvery { movieService.refreshByImdbId(movieEntry.imdbId) } throws BadRequestException("Could not find TMDB metadata")

        val result = watchlistService.listEntries(clubId, memberId).single()

        assertEquals(null, result.originalLanguage)
        assertEquals(emptyList(), result.translations)
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
        type: MediaItemType = SERIES,
    ) = WatchlistEntryRow(
        id = id,
        clubId = clubId,
        memberId = memberId,
        mediaItemId = mediaItemId,
        type = type,
        title = "Dune",
        imdbId = "tt1160419",
        position = position,
        createdAt = Clock.System.now(),
    )
}
