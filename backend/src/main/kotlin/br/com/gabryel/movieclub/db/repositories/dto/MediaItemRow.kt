package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.MediaItemType
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Universal handle for anything sourced from TMDB -- see [br.com.gabryel.movieclub.db.tables.MediaItems] for the
 * full rationale. Always constructed as a whole from a successful TMDB lookup, never assembled field by field. */
data class MediaItemRow(
    val id: Uuid,
    val type: MediaItemType,
    val imdbId: String,
    val tmdbId: String? = null,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val imdbRating: BigDecimal? = null,
    val createdAt: Instant,
)
