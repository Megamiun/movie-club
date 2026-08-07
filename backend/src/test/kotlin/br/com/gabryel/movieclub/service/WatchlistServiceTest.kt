package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.ClubRole.MEMBER
import br.com.gabryel.movieclub.db.repositories.ClubMembershipRow
import br.com.gabryel.movieclub.db.repositories.WatchlistEntryRow
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class WatchlistServiceTest {
    private val watchlistRepository = mockk<WatchlistRepository>()
    private val clubService = mockk<ClubService>()
    private val watchlistService = WatchlistService(watchlistRepository, clubService)

    private val clubId = Uuid.random()
    private val memberId = Uuid.random()

    @Test
    fun `addEntry throws BadRequestException for a blank title`() {
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<BadRequestException> { watchlistService.addEntry(clubId, memberId, "   ") }
        verify(exactly = 0) { watchlistRepository.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `addEntry creates an entry owned by the acting member`() {
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val expected = entry()
        every { watchlistRepository.create(clubId, memberId, "Dune", null, null) } returns expected

        assertEquals(expected, watchlistService.addEntry(clubId, memberId, "Dune"))
    }

    @Test
    fun `updateEntry throws ForbiddenException when acting member is not the owner`() {
        val entryId = Uuid.random()
        val ownerId = Uuid.random()
        every { watchlistRepository.findById(entryId) } returns entry(id = entryId, memberId = ownerId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()

        assertFailsWith<ForbiddenException> { watchlistService.updateEntry(entryId, memberId, title = "New title") }
        verify(exactly = 0) { watchlistRepository.update(any(), any(), any(), any()) }
    }

    @Test
    fun `updateEntry succeeds for the owner`() {
        val entryId = Uuid.random()
        every { watchlistRepository.findById(entryId) } returns entry(id = entryId, memberId = memberId)
        every { clubService.requireMembership(clubId, memberId) } returns membership()
        val updated = entry(id = entryId, memberId = memberId, title = "New title")
        every { watchlistRepository.update(entryId, "New title", null, null) } returns updated

        assertEquals(updated, watchlistService.updateEntry(entryId, memberId, title = "New title"))
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

    private fun entry(
        id: Uuid = Uuid.random(),
        memberId: Uuid = this.memberId,
        title: String = "Dune",
    ) = WatchlistEntryRow(id, clubId, memberId, title, null, null, Clock.System.now())
}
