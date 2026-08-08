package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import kotlin.uuid.Uuid

interface WatchlistRepository {
    fun create(
        clubId: Uuid,
        memberId: Uuid,
        title: String,
        imdbUrl: String? = null,
        notes: String? = null,
    ): WatchlistEntryRow

    fun findById(id: Uuid): WatchlistEntryRow?

    fun listByClub(clubId: Uuid): List<WatchlistEntryRow>

    fun update(id: Uuid, title: String? = null, imdbUrl: String? = null, notes: String? = null): WatchlistEntryRow

    fun delete(id: Uuid)
}
