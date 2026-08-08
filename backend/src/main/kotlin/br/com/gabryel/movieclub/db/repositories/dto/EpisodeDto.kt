package br.com.gabryel.movieclub.db.repositories.dto

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Global episode catalog row, deduplicated by ([seasonId], [number]) -- shared by every club following that
 * series. Meeting assignment isn't a field here -- see `EpisodeRepository.assignToMeeting`. */
data class EpisodeRow(
    val id: Uuid,
    val seasonId: Uuid,
    val number: Int,
    val title: String? = null,
    val airDate: LocalDate? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val director: String? = null,
    val tmdbRating: BigDecimal? = null,
    val metadataFetchedAt: Instant? = null,
)

/** One [EpisodeRow] plus the series/season context needed to label it in a search result -- the club-scoped
 * series title (custom, if the club set one, else TMDB's original) and the season's number. */
data class EpisodeSearchRow(
    val episode: EpisodeRow,
    val seasonNumber: Int,
    val seriesTitle: String,
)

data class EpisodeReviewRow(
    val episodeId: Uuid,
    val memberId: Uuid,
    val qualityOptionId: Uuid? = null,
    val sentimentOptionId: Uuid? = null,
    val comment: String? = null,
)

/** Every non-user-entered field the `episodes` table stores -- same rationale as `TmdbMovieMetadata`, but purely
 * additive: unlike Movie/Series, an episode has no separate original/english/custom title split, so `title` stays
 * whatever the user/CSV entered and TMDB only fills in the fields it uniquely knows about. See
 * [br.com.gabryel.movieclub.service.tmdb.TmdbEpisodeDetails.toMetadata] for how TMDB's response becomes one. */
data class TmdbEpisodeMetadata(
    val airDate: LocalDate? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val director: String? = null,
    val tmdbRating: BigDecimal? = null,
    val metadataFetchedAt: Instant? = null,
)
