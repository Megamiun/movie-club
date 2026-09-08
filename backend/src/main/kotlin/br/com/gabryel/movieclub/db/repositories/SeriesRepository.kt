package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.repositories.dto.CatalogTitleInfo
import br.com.gabryel.movieclub.db.repositories.dto.RefreshCandidateRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface SeriesRepository {
    fun create(clubId: Uuid, chosenById: Uuid, imdbId: String, metadata: TmdbSeriesMetadata, mediaItemId: Uuid? = null): SeriesRow

    /** Same idea as [MovieRepository.findOrCreateCatalogEntry] -- ensures the shared, deduplicated Series catalog
     * row exists/is refreshed, without picking it to any club (e.g. adding straight to a Watchlist, see
     * `WatchlistService`/`SeriesService.findOrCreateCatalogMediaItem`). */
    fun findOrCreateCatalogEntry(imdbId: String, metadata: TmdbSeriesMetadata, mediaItemId: Uuid?): Uuid

    fun findById(id: Uuid): SeriesRow?

    fun listByClub(clubId: Uuid): List<SeriesRow>

    fun findByClubAndImdbId(clubId: Uuid, imdbId: String): SeriesRow?

    /** Batched form of [findByClubAndImdbId] for multiple imdb ids at once -- each [SeriesRow] already carries its
     * own `imdbId`, so the caller does its own `associateBy`/lookup on the flat result. */
    fun findByClubAndImdbIds(clubId: Uuid, imdbIds: List<String>): List<SeriesRow>

    /** Given the id of a *global* series, finds the acting member's own club's pick of it (if any) -- used to
     * authorize and resolve rating-scale context for Season/Episode, which no longer have one club of their own
     * to check directly since they're shared across every club following the series. */
    fun findClubSeriesForMember(seriesId: Uuid, memberId: Uuid): SeriesRow?

    /** The global series catalog row's own `imdbId`/`tmdbId`, looked up by its *global* id directly -- unlike
     * [findClubSeriesForMember] and [findById] (both club/pick-scoped), this needs no member or pick context.
     * Used by `EpisodeService.refreshCatalogMetadata`, which needs the parent series' `tmdbId` to resolve TMDB's
     * per-episode endpoint but has no acting member for the new system-triggered refresh paths (the nightly job,
     * `MediaItemService`'s consolidated endpoint). */
    fun findGlobalCatalogById(globalSeriesId: Uuid): RefreshCandidateRow?

    fun updateDisplayTitle(
        seriesId: Uuid,
        customTitle: String? = null,
        preference: DisplayTitlePreference,
        displayLanguageCode: String? = null,
    ): SeriesRow

    fun updateTmdbMetadata(seriesId: Uuid, metadata: TmdbSeriesMetadata, mediaItemId: Uuid? = null): SeriesRow

    /** Same as [MovieRepository.findCatalogTitleInfoByMediaItemIds], for the Series catalog (`Series.mediaItemId`)
     * instead. */
    fun findCatalogTitleInfoByMediaItemIds(mediaItemIds: List<Uuid>): Map<Uuid, CatalogTitleInfo>

    /** Candidates for the nightly metadata-refresh job (`MetadataRefreshJob`), ordered: not-yet-released rows
     * ([today] before their own release date) sort *last* regardless of everything else -- no rating to
     * meaningfully refresh yet, so spending budget on an already-released row comes first. Among the rest,
     * no-rating-first then oldest-fetched-first. Eligible rows are never-fetched ones, rows released on/after
     * [recentReleaseSince] (still-moving ratings, always eligible regardless of [staleBefore]), or rows fetched
     * before [staleBefore]. */
    fun findRefreshCandidates(limit: Int, today: LocalDate, staleBefore: Instant, recentReleaseSince: LocalDate): List<RefreshCandidateRow>

    /** Total catalog row count -- `MetadataRefreshJob` computes its nightly budget as a percentage of the combined
     * Movie/Series/Episode total. */
    fun count(): Long

    /** Same as [MovieRepository.findWatchlistOnlyCandidates], for the Series catalog instead. */
    fun findWatchlistOnlyCandidates(limit: Int): List<RefreshCandidateRow>

    fun upsertReview(
        seriesId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): SeriesReviewRow

    fun findReview(seriesId: Uuid, memberId: Uuid): SeriesReviewRow?

    fun listReviews(seriesId: Uuid): List<SeriesReviewRow>

    /** Repoints every review currently using [oldOptionId] (as either its quality or sentiment choice, whichever
     * applies) to [newOptionId] instead -- used when a rating option is deleted, so existing reviews aren't left
     * dangling. */
    fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid)
}
