package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.MediaItemType.EPISODE
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.exception.NotFoundException
import kotlin.uuid.Uuid

/** Consolidates "refresh this thing's TMDB/OMDb metadata" onto one shared entry point instead of duplicating a
 * near-identical version per type -- Movie and Series each used to have their own `POST .../refresh-metadata`
 * route, replaced by this one. MediaItem is already this app's universal cross-type handle (see CLAUDE.md's
 * MediaItem section), so an operation that behaves the same way regardless of type belongs here rather than on
 * each type-specific service/route. Deliberately not club-access-checked, unlike the routes this replaces (each
 * gated by a specific pick's club membership) -- a MediaItem is global, shared by every club that's ever
 * referenced it, so "which club's membership" isn't a well-defined question here; `MediaItemRoutes` gates this
 * behind plain authentication only, the same posture `GET /movies/search`/`GET /series/search` already use for
 * other TMDB-touching, non-club-specific operations.
 *
 * Episode's own `POST /episodes/{episodeId}/refresh-metadata` route deliberately still exists alongside this one,
 * not consolidated -- an episode's `media_item_id` is only populated as the *result* of a successful refresh (see
 * `EpisodeService.refreshCatalogMetadata`), so a never-yet-refreshed episode has no MediaItem for this endpoint to
 * be addressed by. Movie/Series don't have that bootstrapping problem: their MediaItem is created synchronously
 * when the pick is first added (see MediaItem's own CLAUDE.md section), so it always exists by the time a refresh
 * could be requested. Once an episode *has* been refreshed at least once, refreshing it again works through this
 * endpoint too, via its now-populated `mediaItemId`. */
class MediaItemService(
    private val mediaItemRepository: MediaItemRepository,
    private val episodeRepository: EpisodeRepository,
    private val movieService: MovieService,
    private val seriesService: SeriesService,
    private val episodeService: EpisodeService,
) {
    suspend fun refreshMetadata(mediaItemId: Uuid): MediaItemRow {
        val mediaItem = mediaItemRepository.findById(mediaItemId) ?: throw NotFoundException("Media item not found")
        when (mediaItem.type) {
            // refreshByImdbId re-resolves a tmdbId via TMDB search when the cached one is missing/blank, rather
            // than failing outright -- a MediaItem predating reliable tmdbId tracking, or one only ever created
            // via the imdbId path, can have a null tmdbId here.
            MOVIE -> movieService.refreshByImdbId(mediaItem.imdbId, mediaItem.tmdbId)
            SERIES -> seriesService.refreshByImdbId(mediaItem.imdbId, mediaItem.tmdbId)
            // Movie/Series can refresh from the MediaItem's own cached imdbId/tmdbId alone -- an episode's TMDB
            // fetch needs its own row, season, and parent series (see EpisodeService.refreshCatalogMetadata's
            // doc), so this resolves back to that row instead of duplicating the fetch logic here.
            EPISODE -> episodeService.refreshCatalogMetadata(
                episodeRepository.findIdByMediaItemId(mediaItemId)
                    ?: throw NotFoundException("Episode not found for this media item"),
            )
        }
        return mediaItemRepository.findById(mediaItemId)!!
    }
}
