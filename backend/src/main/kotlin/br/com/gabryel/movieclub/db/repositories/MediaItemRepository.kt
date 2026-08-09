package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.MediaItemType
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import java.math.BigDecimal
import kotlin.uuid.Uuid

interface MediaItemRepository {
    /** Finds the [MediaItemRow] for [imdbId] if one already exists (from any type, any caller), refreshing it with
     * the given fields; otherwise creates it. Mirrors the Movie/Series catalog's own find-or-create-by-`imdbId`
     * pattern -- same `imdbId`, same canonical TMDB response either way, so overwriting on a repeat call is
     * harmless. */
    fun findOrCreate(
        type: MediaItemType,
        imdbId: String,
        title: String,
        tmdbId: String? = null,
        year: Int? = null,
        posterUrl: String? = null,
        imdbRating: BigDecimal? = null,
    ): MediaItemRow

    fun findById(id: Uuid): MediaItemRow?

    /** Every MediaItem site-wide, regardless of which club(s) reference it -- used by the site-admin panel to show
     * every movie/series ever imported via TMDB in one list, since MediaItem is already the one place both types
     * are deduplicated to a single row (see [br.com.gabryel.movieclub.db.tables.MediaItems]). */
    fun listAll(): List<MediaItemRow>
}
