package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.PersonRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.db.repositories.WatchlistRepository
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeSearchRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeSearchSeriesTitle
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.ForbiddenException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import kotlin.uuid.Uuid

private val ENDED_STATUSES = setOf("Ended", "Canceled")

class EpisodeService(
    private val episodeRepository: EpisodeRepository,
    private val seasonRepository: SeasonRepository,
    private val seriesRepository: SeriesRepository,
    private val meetingRepository: MeetingRepository,
    private val clubService: ClubService,
    private val tmdbClient: TmdbClient,
    private val omdbClient: OmdbClient,
    private val watchlistRepository: WatchlistRepository,
    private val personRepository: PersonRepository,
) {
    /** Unlike [br.com.gabryel.movieclub.service.MovieService.addMovie]/`SeriesService.addSeries`, TMDB enrichment
     * here is always best-effort: an episode has no id of its own to look up by, only the parent series' `tmdbId`
     * (which may not exist yet if the series hasn't been matched), so a lookup failure never blocks creation.
     * [meetingId], if given, is assigned via [EpisodeRepository.assignToMeeting] right away -- a convenience for
     * the common "add and schedule in one step" case (e.g. CSV import). */
    suspend fun addEpisode(
        seasonId: Uuid,
        actingMemberId: Uuid,
        number: Int,
        title: String? = null,
        meetingId: Uuid? = null,
    ): EpisodeRow {
        requireClubSeriesForSeason(seasonId, actingMemberId)
        val episode = episodeRepository.create(seasonId, number, title)
        if (meetingId != null) assignToMeeting(episode.id, actingMemberId, meetingId)
        return runCatching { refreshMetadata(episode.id, actingMemberId) }.getOrDefault(episode)
    }

    /** Manual/explicit refresh -- unlike [addEpisode]'s best-effort enrichment, this throws when the series has no
     * `tmdbId` yet or TMDB has no matching episode, so callers who explicitly ask for a refresh learn why it failed. */
    suspend fun refreshMetadata(episodeId: Uuid, actingMemberId: Uuid): EpisodeRow {
        val episode = episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        val season = season(episode.seasonId)
        val series = requireClubSeriesForMember(season.seriesId, actingMemberId)
        val tmdbId = series.tmdbId?.toIntOrNull()
            ?: throw BadRequestException("Series has not been matched to TMDB yet")

        // episode.number can be OMDb's corrected number rather than TMDB's own (see
        // SeriesService.importSeasonsAndEpisodes) -- once the episode's own imdb_id is known, resolve TMDB's own
        // (season, episode) location for it directly instead of trusting the stored number as a TMDB path segment,
        // since the two numbering schemes can disagree for the same episode. Before the imdb_id is known yet (a
        // fresh episode OMDb had no data for), season.number/episode.number is still the only thing to go on --
        // same as before this existed, and correct for the common case where TMDB's own numbering was used as-is.
        val (tmdbSeasonNumber, tmdbEpisodeNumber) = episode.imdbId?.let { imdbId ->
            val found = tmdbClient.findEpisodeByImdbId(imdbId)
                ?: throw BadRequestException("Could not find TMDB metadata for $imdbId")
            found.seasonNumber to found.episodeNumber
        } ?: (season.number to episode.number)

        val details = tmdbClient.getEpisodeDetails(tmdbId, tmdbSeasonNumber, tmdbEpisodeNumber)
        val directorPersonId = resolveDirectorPersonId(details.director, details.directorTmdbId)
        val imdbRating = details.imdbId?.let { omdbClient.getImdbRating(it) }
        val metadata = details.toMetadata().copy(directorPersonId = directorPersonId, imdbRating = imdbRating)
        return episodeRepository.updateTmdbMetadata(episodeId, metadata)
    }

    /** Same best-effort IMDB-id-via-TMDB-person-lookup pattern as
     * [br.com.gabryel.movieclub.service.MovieService.resolveDirectorPersonId]. */
    private suspend fun resolveDirectorPersonId(directorName: String?, directorTmdbId: Int?): Uuid? {
        if (directorName == null) return null
        val directorImdbId = directorTmdbId?.let { runCatching { tmdbClient.getPersonExternalIds(it).imdbId }.getOrNull() }
        return personRepository.findOrCreate(directorName, directorTmdbId?.toString(), directorImdbId).id
    }

    fun assignToMeeting(episodeId: Uuid, actingMemberId: Uuid, meetingId: Uuid): EpisodeRow {
        episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        clubService.requireMembership(requireMeeting(meetingId).clubId, actingMemberId)

        if (episodeRepository.listByMeeting(meetingId).any { it.id == episodeId })
            throw BadRequestException("This episode has already been added to this meeting")

        episodeRepository.assignToMeeting(episodeId, meetingId)
        return episodeRepository.findById(episodeId)!!
    }

    fun unassignFromMeeting(episodeId: Uuid, actingMemberId: Uuid, meetingId: Uuid): EpisodeRow {
        episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        clubService.requireMembership(requireMeeting(meetingId).clubId, actingMemberId)
        episodeRepository.unassignFromMeeting(episodeId, meetingId)
        return episodeRepository.findById(episodeId)!!
    }

    fun listEpisodes(seasonId: Uuid, actingMemberId: Uuid): List<EpisodeRow> {
        requireClubSeriesForSeason(seasonId, actingMemberId)
        return episodeRepository.listBySeason(seasonId)
    }

    fun listEpisodesForMeeting(meetingId: Uuid, actingMemberId: Uuid): List<EpisodeRow> {
        clubService.requireMembership(requireMeeting(meetingId).clubId, actingMemberId)
        return episodeRepository.listByMeeting(meetingId)
    }

    fun searchEpisodes(clubId: Uuid, actingMemberId: Uuid, query: String): List<EpisodeSearchRow> {
        clubService.requireMembership(clubId, actingMemberId)
        if (query.isBlank()) return emptyList()
        return episodeRepository.searchByClub(clubId, query.trim())
    }

    /** One suggestion per series the club follows -- the earliest episode of that series it hasn't scheduled to
     * any meeting yet (see [EpisodeRepository.findNextUnscheduled]). Series with nothing left to suggest (every
     * known episode already scheduled, or none imported yet) are silently skipped rather than erroring, since this
     * is a convenience prompt, not something the caller picks a series for up front.
     *
     * Also skips a series that's no longer running (TMDB `status` "Ended"/"Canceled") unless the club is actively
     * watchlisting it -- an ended show with nothing new coming isn't worth nudging towards continuing, but a club
     * that's deliberately queued it up to catch up on clearly still wants the suggestion. A series with no `status`
     * yet (not refreshed since this field was added) is treated as still running rather than filtered out, since
     * "unknown" shouldn't silently hide an otherwise-valid suggestion. */
    fun listNextSuggestions(clubId: Uuid, actingMemberId: Uuid): List<EpisodeSearchRow> {
        clubService.requireMembership(clubId, actingMemberId)
        return seriesRepository.listByClub(clubId).mapNotNull { series ->
            if (series.status in ENDED_STATUSES && !watchlistRepository.existsByClubAndMediaItemImdbId(clubId, series.imdbId))
                return@mapNotNull null

            val next = episodeRepository.findNextUnscheduled(clubId, series.globalSeriesId) ?: return@mapNotNull null
            val season = seasonRepository.findById(next.seasonId) ?: return@mapNotNull null
            EpisodeSearchRow(
                episode = next,
                seasonNumber = season.number,
                series = EpisodeSearchSeriesTitle(
                    originalTitle = series.originalTitle,
                    originalLanguage = series.originalLanguage,
                    translations = series.translations,
                    customTitle = series.customTitle,
                    displayTitlePreference = series.displayTitlePreference,
                    displayLanguageCode = series.displayLanguageCode,
                ),
            )
        }
    }

    fun rate(
        episodeId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): EpisodeReviewRow {
        val episode = episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        val series = requireClubSeriesForMember(season(episode.seasonId).seriesId, actingMemberId)
        if (qualityOptionId != null) clubService.validateRatingOption(series.clubId, qualityOptionId, QUALITY)
        if (sentimentOptionId != null) clubService.validateRatingOption(series.clubId, sentimentOptionId, SENTIMENT)
        return episodeRepository.upsertReview(episodeId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    private fun season(seasonId: Uuid): SeasonRow =
        seasonRepository.findById(seasonId) ?: throw NotFoundException("Season not found")

    private fun requireMeeting(meetingId: Uuid) =
        meetingRepository.findById(meetingId) ?: throw NotFoundException("Meeting not found")

    /** Season has no club of its own -- it's shared across every club following the series -- so access is derived
     * by finding the acting member's own club's pick of the parent series. */
    private fun requireClubSeriesForSeason(seasonId: Uuid, actingMemberId: Uuid): SeriesRow =
        requireClubSeriesForMember(season(seasonId).seriesId, actingMemberId)

    private fun requireClubSeriesForMember(globalSeriesId: Uuid, actingMemberId: Uuid): SeriesRow =
        seriesRepository.findClubSeriesForMember(globalSeriesId, actingMemberId)
            ?: throw ForbiddenException("Not a member of a club following this series")
}
