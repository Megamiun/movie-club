package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.db.MediaItemType.EPISODE
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
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
    private val movieRepository: MovieRepository,
    private val seriesRepository: SeriesRepository,
    private val tmdbClient: TmdbClient,
    private val omdbClient: OmdbClient,
    private val movieService: MovieService,
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
        return enrichCatalogTitles(watchlistRepository.listByClub(clubId))
    }

    /** Swaps [entryId] with whichever entry is immediately adjacent to it within its own owner's list -- among
     * entries of the same [WatchlistEntryRow.memberId] only (movies and series share one mixed, ordered list per
     * member, not separate ones by type -- see `WatchlistPage`). A no-op at either edge of that list. Unlike
     * [deleteEntry], any club member may reorder -- reordering was already documented as not owner-restricted
     * before per-member lists existed (a shared, collaboratively prioritized list), and that's preserved here
     * even though it now means reordering someone else's own list. */
    fun moveEntry(entryId: Uuid, actingMemberId: Uuid, direction: MoveDirection): WatchlistEntryRow {
        val entry = watchlistRepository.findById(entryId) ?: throw NotFoundException("Watchlist entry not found")
        clubService.requireMembership(entry.clubId, actingMemberId)

        val siblings = watchlistRepository.listByClub(entry.clubId)
            .filter { it.memberId == entry.memberId }
            .sortedBy { it.position }
        val index = siblings.indexOfFirst { it.id == entryId }
        val targetIndex = if (direction == MoveDirection.UP) index - 1 else index + 1
        val target = siblings.getOrNull(targetIndex) ?: return entry

        watchlistRepository.updatePosition(entry.id, target.position)
        watchlistRepository.updatePosition(target.id, entry.position)
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
     * whole club's watchlist. An entry whose MediaItem has no matching catalog row (not yet backfilled, or
     * [MediaItemType.EPISODE] -- watchlist entries can't be episodes yet) is left with its default `null`/empty
     * values, which `resolveTitle` treats the same as "no translation data available". */
    private fun enrichCatalogTitles(entries: List<WatchlistEntryRow>): List<WatchlistEntryRow> {
        val movieMediaItemIds = entries.filter { it.type == MOVIE }.map { it.mediaItemId }
        val seriesMediaItemIds = entries.filter { it.type == SERIES }.map { it.mediaItemId }
        val movieCatalogInfo = movieRepository.findCatalogTitleInfoByMediaItemIds(movieMediaItemIds)
        val seriesCatalogInfo = seriesRepository.findCatalogTitleInfoByMediaItemIds(seriesMediaItemIds)

        return entries.map { entry ->
            val info = when (entry.type) {
                MOVIE -> movieCatalogInfo[entry.mediaItemId]
                SERIES -> seriesCatalogInfo[entry.mediaItemId]
                EPISODE -> null
            } ?: return@map entry
            entry.copy(originalLanguage = info.originalLanguage, translations = info.translations)
        }
    }

    private fun enrichCatalogTitle(entry: WatchlistEntryRow): WatchlistEntryRow = enrichCatalogTitles(listOf(entry)).single()
}
