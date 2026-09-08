package br.com.gabryel.movieclub.db.repositories

import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeSearchRow
import br.com.gabryel.movieclub.db.repositories.dto.RefreshCandidateRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface EpisodeRepository {
    /** Finds the global episode for ([seasonId], [number]) if any club has already added it, otherwise creates it. */
    fun create(seasonId: Uuid, number: Int, title: String? = null): EpisodeRow

    fun findById(id: Uuid): EpisodeRow?

    fun listBySeason(seasonId: Uuid): List<EpisodeRow>

    fun listByMeeting(meetingId: Uuid): List<EpisodeRow>

    /** Batched form of [listByMeeting] for multiple meetings at once. Unlike [MovieRepository]'s equivalent,
     * [EpisodeRow] carries no `meetingId` of its own (Episode is genuinely global, only the `MeetingEpisodes` join
     * table ties one to a meeting -- see CLAUDE.md's Series → Season → Episode section), so the grouping has to
     * happen here, before that join column is dropped mapping into [EpisodeRow]. */
    fun listByMeetings(meetingIds: List<Uuid>): Map<Uuid, List<EpisodeRow>>

    /** Matches by episode title or by the parent series' title, scoped to series [clubId] follows -- used to pick
     * an existing episode to assign to a meeting instead of requiring its raw id. */
    fun searchByClub(clubId: Uuid, query: String, limit: Int = 20): List<EpisodeSearchRow>

    /** The earliest (by season, then episode number) episode of [globalSeriesId] that [clubId] hasn't scheduled to
     * any of its own meetings yet -- null if every known episode is already scheduled, or the series has none.
     * Used to suggest "what's next" when assigning an episode to a meeting. */
    fun findNextUnscheduled(clubId: Uuid, globalSeriesId: Uuid): EpisodeRow?

    /** The `imdb_id` of the episode's parent (global) series -- lets a caller resolve a club's own pick of that
     * series (e.g. via `SeriesRepository.findByClubAndImdbId`) without `EpisodeRepository` depending on
     * `SeriesRepository` itself. Used by `MeetingService` to show/group a meeting's episodes by series. */
    fun findSeriesImdbId(episodeId: Uuid): String?

    /** Batched form of [findSeriesImdbId] for multiple episodes at once, keyed by episode id. */
    fun findSeriesImdbIds(episodeIds: List<Uuid>): Map<Uuid, String>

    /** Schedules the global episode for [meetingId] -- idempotent, since multiple clubs (or repeated calls) may
     * each want the same episode assigned to their own meeting. */
    fun assignToMeeting(episodeId: Uuid, meetingId: Uuid)

    /** Removes this specific meeting's assignment only -- other clubs' assignments of the same episode, if any,
     * are untouched. */
    fun unassignFromMeeting(episodeId: Uuid, meetingId: Uuid)

    fun updateTmdbMetadata(episodeId: Uuid, metadata: TmdbEpisodeMetadata, mediaItemId: Uuid? = null): EpisodeRow

    /** The episode whose [EpisodeRow.mediaItemId] is [mediaItemId], if any -- lets `MediaItemService`'s
     * consolidated refresh endpoint resolve a MediaItem back to the episode it links to (Movie/Series don't need
     * this: their own `imdbId`/`tmdbId` are already cached on the MediaItem itself, but an episode's refresh needs
     * its *own* row, season, and parent series to resolve TMDB's per-episode endpoint -- see
     * `EpisodeService.refreshCatalogMetadata`). */
    fun findIdByMediaItemId(mediaItemId: Uuid): Uuid?

    /** Candidates for the nightly metadata-refresh job (`MetadataRefreshJob`), ordered: not-yet-released rows
     * ([today] before their own release date) sort *last* regardless of everything else -- no rating to
     * meaningfully refresh yet, so spending budget on an already-released row comes first. Among the rest,
     * no-rating-first then oldest-fetched-first. Eligible rows are never-fetched ones, rows released on/after
     * [recentReleaseSince] (still-moving ratings, always eligible regardless of [staleBefore]), or rows fetched
     * before [staleBefore]. */
    fun findRefreshCandidates(limit: Int, today: LocalDate, staleBefore: Instant, recentReleaseSince: LocalDate): List<RefreshCandidateRow>

    /** Total episode count, refreshable or not -- `MetadataRefreshJob` computes its nightly budget as a percentage
     * of the combined Movie/Series/Episode total, so every row (even one with no `imdb_id` yet) counts toward it. */
    fun count(): Long

    fun upsertReview(
        episodeId: Uuid,
        memberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): EpisodeReviewRow

    fun findReview(episodeId: Uuid, memberId: Uuid): EpisodeReviewRow?

    /** Sets *only* [qualityOptionId] on this member's review, creating it if it doesn't exist yet -- unlike
     * [upsertReview], the update statement never touches `sentimentOptionId`/`comment` at all, so a concurrent
     * update to either of those can never be clobbered by this call (or vice versa). `null` unambiguously clears
     * the quality rating, since there's no other field it could be mistaken for "leave unchanged". */
    fun updateReviewQuality(episodeId: Uuid, memberId: Uuid, qualityOptionId: Uuid?): EpisodeReviewRow

    /** Same as [updateReviewQuality], for `sentimentOptionId` instead. */
    fun updateReviewSentiment(episodeId: Uuid, memberId: Uuid, sentimentOptionId: Uuid?): EpisodeReviewRow

    fun listReviews(episodeId: Uuid): List<EpisodeReviewRow>

    /** Batched form of [listReviews] for multiple episodes at once -- each [EpisodeReviewRow] already carries its
     * own `episodeId`, so the caller groups the flat result itself. */
    fun listReviewsByEpisodes(episodeIds: List<Uuid>): List<EpisodeReviewRow>

    /** Repoints every review currently using [oldOptionId] (as either its quality or sentiment choice, whichever
     * applies) to [newOptionId] instead -- used when a rating option is deleted, so existing reviews aren't left
     * dangling. */
    fun reassignRatingOption(oldOptionId: Uuid, newOptionId: Uuid)
}
