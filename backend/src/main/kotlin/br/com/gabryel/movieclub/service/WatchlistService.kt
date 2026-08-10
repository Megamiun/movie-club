package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.db.MediaItemType.EPISODE
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.WatchlistEntryRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.toTmdbPosterUrl
import kotlin.uuid.Uuid

enum class MoveDirection { UP, DOWN }

class WatchlistService(
    private val watchlistRepository: WatchlistRepository,
    private val clubService: ClubService,
    private val mediaItemRepository: MediaItemRepository,
    private val tmdbClient: TmdbClient,
    private val omdbClient: OmdbClient,
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

        return watchlistRepository.create(clubId, actingMemberId, mediaItem.id)
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

        return watchlistRepository.create(clubId, actingMemberId, mediaItem.id)
    }

    private suspend fun fetchMovieMediaItem(tmdbId: Int): MediaItemRow {
        val details = tmdbClient.getMovieDetails(tmdbId)
        val imdbId = details.externalIds?.imdbId
            ?: throw BadRequestException("TMDB movie $tmdbId has no linked IMDB id")

        return mediaItemRepository.findOrCreate(
            type = MOVIE,
            imdbId = imdbId,
            title = details.originalTitle,
            tmdbId = tmdbId.toString(),
            year = details.year,
            posterUrl = details.posterPath?.toTmdbPosterUrl(),
            imdbRating = omdbClient.getImdbRating(imdbId),
        )
    }

    private suspend fun fetchSeriesMediaItem(tmdbId: Int): MediaItemRow {
        val details = tmdbClient.getTvDetails(tmdbId)
        val imdbId = details.externalIds?.imdbId
            ?: throw BadRequestException("TMDB series $tmdbId has no linked IMDB id")

        return mediaItemRepository.findOrCreate(
            type = SERIES,
            imdbId = imdbId,
            title = details.originalName,
            tmdbId = tmdbId.toString(),
            year = details.year,
            posterUrl = details.posterPath?.toTmdbPosterUrl(),
            imdbRating = omdbClient.getImdbRating(imdbId),
        )
    }

    fun listEntries(clubId: Uuid, actingMemberId: Uuid): List<WatchlistEntryRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return watchlistRepository.listByClub(clubId)
    }

    /** Swaps [entryId] with whichever entry is immediately adjacent to it within its own owner's column -- among
     * entries of the *same* [WatchlistEntryRow.type] *and* the same [WatchlistEntryRow.memberId] only, since the UI
     * shows one column per member (movies and series as separate boards, see `WatchlistPage`). A no-op at either
     * edge of that column. Unlike [deleteEntry], any club member may reorder -- reordering was already documented
     * as not owner-restricted before per-member columns existed (a shared, collaboratively prioritized list), and
     * that's preserved here even though it now means reordering someone else's own column. */
    fun moveEntry(entryId: Uuid, actingMemberId: Uuid, direction: MoveDirection): WatchlistEntryRow {
        val entry = watchlistRepository.findById(entryId) ?: throw NotFoundException("Watchlist entry not found")
        clubService.requireMembership(entry.clubId, actingMemberId)

        val siblings = watchlistRepository.listByClub(entry.clubId)
            .filter { it.type == entry.type && it.memberId == entry.memberId }
            .sortedBy { it.position }
        val index = siblings.indexOfFirst { it.id == entryId }
        val targetIndex = if (direction == MoveDirection.UP) index - 1 else index + 1
        val target = siblings.getOrNull(targetIndex) ?: return entry

        watchlistRepository.updatePosition(entry.id, target.position)
        watchlistRepository.updatePosition(target.id, entry.position)
        return watchlistRepository.findById(entryId)!!
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
