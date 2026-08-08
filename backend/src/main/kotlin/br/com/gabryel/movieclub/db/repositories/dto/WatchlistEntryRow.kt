package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.MediaItemType
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The externally-visible "watchlist entry" -- a flattened join of the `WatchlistEntries` pick row (`notes`,
 * `position`) and the [MediaItemRow] it references, same shape convention as [MovieRow]/[SeriesRow]. [id] is the
 * entry's own id; [mediaItemId] is the referenced [MediaItemRow]'s id, needed to check "is this already on the
 * watchlist" without a second lookup. */
data class WatchlistEntryRow(
    val id: Uuid,
    val clubId: Uuid,
    val memberId: Uuid,
    val mediaItemId: Uuid,
    val type: MediaItemType,
    val title: String,
    val imdbId: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val tmdbRating: BigDecimal? = null,
    val imdbRating: BigDecimal? = null,
    val notes: String? = null,
    val position: Int,
    val createdAt: Instant,
)
