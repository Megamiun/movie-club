package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlin.uuid.Uuid

class WatchlistService(private val watchlistRepository: WatchlistRepository, private val clubService: ClubService) {
    fun addEntry(
        clubId: Uuid,
        actingMemberId: Uuid,
        title: String,
        imdbUrl: String? = null,
        notes: String? = null,
    ): WatchlistEntryRow {
        clubService.requireMembership(clubId, actingMemberId)
        if (title.isBlank())
            throw BadRequestException("title must not be blank")

        return watchlistRepository.create(clubId, actingMemberId, title, imdbUrl, notes)
    }

    fun listEntries(clubId: Uuid, actingMemberId: Uuid): List<WatchlistEntryRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return watchlistRepository.listByClub(clubId)
    }

    fun updateEntry(
        entryId: Uuid,
        actingMemberId: Uuid,
        title: String? = null,
        imdbUrl: String? = null,
        notes: String? = null,
    ): WatchlistEntryRow {
        val entry = requireOwnedEntry(entryId, actingMemberId)
        return watchlistRepository.update(entry.id, title, imdbUrl, notes)
    }

    fun deleteEntry(entryId: Uuid, actingMemberId: Uuid) {
        val entry = requireOwnedEntry(entryId, actingMemberId)
        watchlistRepository.delete(entry.id)
    }

    private fun requireOwnedEntry(entryId: Uuid, actingMemberId: Uuid): WatchlistEntryRow {
        val entry = watchlistRepository.findById(entryId)
            ?: throw NotFoundException("Watchlist entry not found")

        clubService.requireMembership(entry.clubId, actingMemberId)
        if (entry.memberId != actingMemberId)
            throw ForbiddenException("Only the owner can modify this watchlist entry")

        return entry
    }
}
