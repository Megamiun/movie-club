package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.db.MediaItemType.EPISODE
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

class WatchlistService(
    private val watchlistRepository: WatchlistRepository,
    private val clubService: ClubService,
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val tmdbClient: TmdbClient,
    private val movieService: MovieService,
    private val seriesService: SeriesService,
) {
    /** Adding is always by [tmdbId] -- there's no freeform title entry, since a MediaItem only ever exists from a
     * successful TMDB lookup (see [br.com.gabryel.movieclub.db.tables.MediaItems]). */
    suspend fun addEntry(
        clubId: Uuid,
        actingMemberId: Uuid,
        type: MediaItemType,
        tmdbId: String,
    ): WatchlistEntryRow {
        clubService.requireMembership(clubId, actingMemberId)
        val id = tmdbId.toIntOrNull() ?: throw BadRequestException("Invalid tmdbId")

        val mediaItem = when (type) {
            MOVIE -> fetchMovieMediaItem(id)
            SERIES -> fetchSeriesMediaItem(id)
            EPISODE -> throw BadRequestException("Episodes cannot be added to the watchlist yet")
        }

        if (watchlistRepository.findByClubMemberAndMediaItem(clubId, actingMemberId, mediaItem.id) != null)
            throw BadRequestException("This is already in your watchlist")

        return enrichCatalogTitle(watchlistRepository.create(clubId, actingMemberId, mediaItem.id))
    }

    /** Best-effort variant for CSV import, which only ever has a bare title (the Reserve CSV has no id column at
     * all -- see `ReserveCsvParser`), never a `tmdbId` to look up directly. Takes the *top* TMDB search result as
     * a best guess; returns null (caller should skip-with-warning, same convention as the CSV importers'
     * best-effort TMDB refreshes elsewhere) when TMDB has no match whatsoever for the title. */
    suspend fun addEntryByTitleSearch(
        clubId: Uuid,
        actingMemberId: Uuid,
        type: MediaItemType,
        title: String,
    ): WatchlistEntryRow? {
        clubService.requireMembership(clubId, actingMemberId)

        val mediaItem = when (type) {
            MOVIE -> tmdbClient.searchMovies(title).firstOrNull()?.id?.let { fetchMovieMediaItem(it) }
            SERIES -> tmdbClient.searchTv(title).firstOrNull()?.id?.let { fetchSeriesMediaItem(it) }
            EPISODE -> throw BadRequestException("Episodes cannot be added to the watchlist yet")
        } ?: return null

        return enrichCatalogTitle(watchlistRepository.create(clubId, actingMemberId, mediaItem.id))
    }

    /** Delegates to [MovieService]/[SeriesService] instead of a bare `mediaItemRepository.findOrCreate` (the
     * latter is all this used to do) so a Watchlist-only add also gets a real Movie/Series *catalog* row, not just
     * a MediaItem -- without one, the entry had no `originalLanguage`/`translations` for [enrichCatalogTitles] to
     * find, so `resolveTitle` (frontend `utils/title.ts`) always fell back to the raw original title, silently
     * ignoring the club's language preferences entirely for anything added straight to the Watchlist rather than
     * picked to a meeting/added to the club's series list first. */
    private suspend fun fetchMovieMediaItem(tmdbId: Int): MediaItemRow = movieService.findOrCreateCatalogMediaItem(tmdbId)

    private suspend fun fetchSeriesMediaItem(tmdbId: Int): MediaItemRow = seriesService.findOrCreateCatalogMediaItem(tmdbId)

    suspend fun listEntries(clubId: Uuid, actingMemberId: Uuid): List<WatchlistEntryRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return enrichCatalogTitles(watchlistRepository.listByClub(clubId))
    }

    /** Moves [entryId] directly to [targetPosition] (a 0-based index into its own owner's list, clamped to that
     * list's bounds) in one call -- among entries of the same [WatchlistEntryRow.memberId] only (movies and series
     * share one mixed, ordered list per member, not separate ones by type -- see `WatchlistPage`). Every sibling
     * between the entry's old and new position shifts by one slot to make room, the same "these ids, in this
     * order, become positions 0..N-1" idiom `ClubService.assignContiguousPositions` already uses for rating-option
     * reorder -- replaced an earlier adjacent-only swap (`MoveDirection` UP/DOWN) that made the frontend replay one
     * call per slot to drag an entry any real distance. Unlike [deleteEntry], any club member may reorder --
     * reordering was already documented as not owner-restricted before per-member lists existed (a shared,
     * collaboratively prioritized list), and that's preserved here even though it now means reordering someone
     * else's own list. */
    suspend fun moveEntry(entryId: Uuid, actingMemberId: Uuid, targetPosition: Int): WatchlistEntryRow {
        val entry = watchlistRepository.findById(entryId) ?: throw NotFoundException("Watchlist entry not found")
        clubService.requireMembership(entry.clubId, actingMemberId)

        val siblings = watchlistRepository.listByClub(entry.clubId)
            .filter { it.memberId == entry.memberId }
            .sortedBy { it.position }
            .toMutableList()

        val currentIndex = siblings.indexOfFirst { it.id == entryId }
        val newIndex = targetPosition.coerceIn(0, siblings.lastIndex)
        if (currentIndex != newIndex) {
            siblings.add(newIndex, siblings.removeAt(currentIndex))
            siblings.forEachIndexed { index, sibling ->
                if (sibling.position != index) watchlistRepository.updatePosition(sibling.id, index)
            }
        }
        return enrichCatalogTitle(watchlistRepository.findById(entryId)!!)
    }

    fun deleteEntry(entryId: Uuid, actingMemberId: Uuid) {
        val entry = requireOwnedEntry(entryId, actingMemberId)
        watchlistRepository.delete(entry.id)
    }

    /** Adds [entryId]'s movie straight to [meetingId], then removes the watchlist entry -- unlike [deleteEntry],
     * deliberately *not* owner-restricted: any club member may schedule a movie sitting in someone else's
     * Watchlist onto a meeting, the same way any member can already add a movie to a meeting from scratch. Done
     * as one atomic service call (unlike the owner's own watchlist-to-meeting move, which the frontend still
     * composes from separate add + delete calls) specifically because a non-owner's delete would otherwise hit
     * [requireOwnedEntry]'s ownership check on the second call -- there's no clean way to compose this one from
     * the existing per-call endpoints without either loosening [deleteEntry] itself (dropping the ownership
     * check for every caller, not just this flow) or adding a bypass flag a client could otherwise abuse. */
    suspend fun moveEntryToMeeting(entryId: Uuid, meetingId: Uuid, actingMemberId: Uuid): MovieRow {
        val entry = watchlistRepository.findById(entryId) ?: throw NotFoundException("Watchlist entry not found")
        clubService.requireMembership(entry.clubId, actingMemberId)
        if (entry.type != MOVIE) throw BadRequestException("Only movies can be moved to a meeting")

        val movie = movieService.addMovie(meetingId, actingMemberId, entry.imdbId)
        watchlistRepository.delete(entry.id)
        return movie
    }

    private fun requireOwnedEntry(entryId: Uuid, actingMemberId: Uuid): WatchlistEntryRow {
        val entry = watchlistRepository.findById(entryId)
            ?: throw NotFoundException("Watchlist entry not found")

        clubService.requireMembership(entry.clubId, actingMemberId)
        if (entry.memberId != actingMemberId)
            throw ForbiddenException("Only the owner can modify this watchlist entry")

        return entry
    }

    /** Fills in [WatchlistEntryRow.originalLanguage]/[WatchlistEntryRow.translations] from the entry's underlying
     * Movie/Series catalog row (see [MovieRepository.findCatalogTitleInfoByMediaItemIds]) -- neither the
     * `WatchlistEntries`/`MediaItems` join `ExposedWatchlistRepository` itself does can supply these (repositories
     * don't depend on each other), so this cross-entity composition lives here, same as any other in this
     * codebase. Batched across every entry at once rather than per-entry, to avoid a query per row when listing a
     * whole club's watchlist.
     *
     * An entry whose MediaItem has no matching catalog row yet ([MediaItemType.EPISODE] always, or a MOVIE/SERIES
     * entry added before [fetchMovieMediaItem]/[fetchSeriesMediaItem] started creating one) is *not* just left
     * blank -- [backfillMissingCatalogRows] creates the missing row on the spot (best-effort, same find-or-create
     * [addEntry] itself uses), so the watchlist always shows real language-resolved data on the very next load
     * rather than staying permanently broken until someone happens to remove and re-add the entry. */
    private suspend fun enrichCatalogTitles(entries: List<WatchlistEntryRow>): List<WatchlistEntryRow> {
        val movieMediaItemIds = entries.filter { it.type == MOVIE }.map { it.mediaItemId }
        val seriesMediaItemIds = entries.filter { it.type == SERIES }.map { it.mediaItemId }
        var movieCatalogInfo = movieRepository.findCatalogTitleInfoByMediaItemIds(movieMediaItemIds)
        var seriesCatalogInfo = seriesRepository.findCatalogTitleInfoByMediaItemIds(seriesMediaItemIds)

        val missing = entries.filter { entry ->
            when (entry.type) {
                MOVIE -> entry.mediaItemId !in movieCatalogInfo
                SERIES -> entry.mediaItemId !in seriesCatalogInfo
                EPISODE -> false
            }
        }
        if (missing.isNotEmpty()) {
            backfillMissingCatalogRows(missing)
            movieCatalogInfo = movieRepository.findCatalogTitleInfoByMediaItemIds(movieMediaItemIds)
            seriesCatalogInfo = seriesRepository.findCatalogTitleInfoByMediaItemIds(seriesMediaItemIds)
        }

        return entries.map { entry ->
            val info = when (entry.type) {
                MOVIE -> movieCatalogInfo[entry.mediaItemId]
                SERIES -> seriesCatalogInfo[entry.mediaItemId]
                EPISODE -> null
            } ?: return@map entry
            entry.copy(originalLanguage = info.originalLanguage, translations = info.translations)
        }
    }

    /** Runs [fetchMovieMediaItem]/[fetchSeriesMediaItem] again for each entry in [missing] purely for its
     * find-or-create side effect (a fresh catalog row) -- concurrently, since a full club watchlist can have
     * dozens of these at once (the pre-fix backlog) and each is its own TMDB/OMDb round trip; a serial loop would
     * make one page load take as long as backfilling the whole club. Best-effort per entry (`runCatching`), same
     * posture as every other TMDB call in this app that must never block on a transient upstream hiccup -- a
     * failure here just leaves that one entry blank for this load, same as before this method existed, and it's
     * retried the next time anyone loads the watchlist. */
    private suspend fun backfillMissingCatalogRows(missing: List<WatchlistEntryRow>) = coroutineScope {
        missing.map { entry ->
            async {
                runCatching {
                    when (entry.type) {
                        MOVIE -> movieService.refreshByImdbId(entry.imdbId)
                        SERIES -> seriesService.refreshByImdbId(entry.imdbId)
                        EPISODE -> Unit
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun enrichCatalogTitle(entry: WatchlistEntryRow): WatchlistEntryRow = enrichCatalogTitles(listOf(entry)).single()
}
