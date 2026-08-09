package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import kotlin.uuid.Uuid

interface WatchlistRepository {
    /** [WatchlistEntryRow.position] is assigned automatically -- one past the highest existing position for
     * ([clubId], the MediaItem's type), so a new entry always lands at the end of its own type's list. */
    fun create(clubId: Uuid, memberId: Uuid, mediaItemId: Uuid, notes: String? = null): WatchlistEntryRow

    fun findById(id: Uuid): WatchlistEntryRow?

    fun findByClubMemberAndMediaItem(clubId: Uuid, memberId: Uuid, mediaItemId: Uuid): WatchlistEntryRow?

    fun listByClub(clubId: Uuid): List<WatchlistEntryRow>

    fun update(id: Uuid, notes: String? = null): WatchlistEntryRow

    fun updatePosition(id: Uuid, position: Int): WatchlistEntryRow

    fun delete(id: Uuid)

    /** Whether any member of [clubId] is watchlisting the MediaItem with this [imdbId] -- keyed by `imdb_id`
     * rather than a `mediaItemId` so callers like `SeriesRow` (which has no `mediaItemId` field of its own) don't
     * need one just for this check. */
    fun existsByClubAndMediaItemImdbId(clubId: Uuid, imdbId: String): Boolean
}
