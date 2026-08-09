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
}
