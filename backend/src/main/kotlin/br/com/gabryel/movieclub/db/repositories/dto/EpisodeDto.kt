package br.com.gabryel.movieclub.db.repositories.dto

import br.com.gabryel.movieclub.db.DisplayTitlePreference
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
    val directorImdbId: String? = null,
    val imdbId: String? = null,
    val imdbRating: BigDecimal? = null,
    val metadataFetchedAt: Instant? = null,
)

/** Just the fields a client needs to resolve the *display* title of [EpisodeSearchRow]'s series (mirrors the
 * frontend's `resolveTitle`/`TitledMedia`) -- deliberately not the full [SeriesRow], since this DTO only ever
 * needs to label a search result/suggestion, not carry the series' whole catalog data. Resolution stays
 * client-side for the same reason [SeriesRow]'s own title fields do (see [SeriesRow] doc) -- this used to be
 * pre-resolved server-side as `customTitle ?: originalTitle`, which silently skipped the club's
 * language-preference/ignored-language resolution that every other title display already goes through. */
data class EpisodeSearchSeriesTitle(
    val originalTitle: String,
    val originalLanguage: String? = null,
    val translations: List<Translation>,
    val customTitle: String? = null,
    val displayTitlePreference: DisplayTitlePreference,
    val displayLanguageCode: String? = null,
)

/** One [EpisodeRow] plus the series/season context needed to label it in a search result -- the season's number
 * and enough of the series to resolve its display title client-side. */
data class EpisodeSearchRow(
    val episode: EpisodeRow,
    val seasonNumber: Int,
    val series: EpisodeSearchSeriesTitle,
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
    val directorPersonId: Uuid? = null,
    val imdbId: String? = null,
    val imdbRating: BigDecimal? = null,
    val metadataFetchedAt: Instant? = null,
)
