package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRepository
import br.com.gabryel.movieclub.db.repositories.SeasonRow
import br.com.gabryel.movieclub.db.repositories.SeriesRepository
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.toMetadata
import kotlin.uuid.Uuid

class EpisodeService(
    private val episodeRepository: EpisodeRepository,
    private val seasonRepository: SeasonRepository,
    private val seriesRepository: SeriesRepository,
    private val meetingRepository: MeetingRepository,
    private val clubService: ClubService,
    private val tmdbClient: TmdbClient,
) {
    /** Unlike [br.com.gabryel.movieclub.service.MovieService.addMovie]/`SeriesService.addSeries`, TMDB enrichment
     * here is always best-effort: an episode has no id of its own to look up by, only the parent series' `tmdbId`
     * (which may not exist yet if the series hasn't been matched), so a lookup failure never blocks creation. */
    suspend fun addEpisode(
        seasonId: Uuid,
        actingMemberId: Uuid,
        number: Int,
        title: String? = null,
        meetingId: Uuid? = null,
    ): EpisodeRow {
        val clubId = clubIdForSeason(seasonId, actingMemberId)
        if (meetingId != null) requireMeetingInClub(meetingId, clubId)
        val episode = episodeRepository.create(seasonId, number, title, meetingId)
        return runCatching { refreshMetadata(episode.id, actingMemberId) }.getOrDefault(episode)
    }

    /** Manual/explicit refresh -- unlike [addEpisode]'s best-effort enrichment, this throws when the series has no
     * `tmdbId` yet or TMDB has no matching episode, so callers who explicitly ask for a refresh learn why it failed. */
    suspend fun refreshMetadata(episodeId: Uuid, actingMemberId: Uuid): EpisodeRow {
        val episode = episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        val season = season(episode.seasonId)
        val series = seriesRepository.findById(season.seriesId) ?: throw NotFoundException("Series not found")
        clubService.requireMembership(series.clubId, actingMemberId)
        val tmdbId = series.tmdbId?.toIntOrNull()
            ?: throw BadRequestException("Series has not been matched to TMDB yet")

        val details = tmdbClient.getEpisodeDetails(tmdbId, season.number, episode.number)
        return episodeRepository.updateTmdbMetadata(episodeId, details.toMetadata())
    }

    fun assignToMeeting(episodeId: Uuid, actingMemberId: Uuid, meetingId: Uuid?): EpisodeRow {
        val episode = episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        val clubId = clubIdForSeason(episode.seasonId, actingMemberId)
        if (meetingId != null) requireMeetingInClub(meetingId, clubId)
        return episodeRepository.updateMeeting(episodeId, meetingId)
    }

    fun listEpisodes(seasonId: Uuid, actingMemberId: Uuid): List<EpisodeRow> {
        clubIdForSeason(seasonId, actingMemberId)
        return episodeRepository.listBySeason(seasonId)
    }

    fun listEpisodesForMeeting(meetingId: Uuid, actingMemberId: Uuid): List<EpisodeRow> {
        val meeting = meetingRepository.findById(meetingId) ?: throw NotFoundException("Meeting not found")
        clubService.requireMembership(meeting.clubId, actingMemberId)
        return episodeRepository.listByMeeting(meetingId)
    }

    fun rate(
        episodeId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid?,
        sentimentOptionId: Uuid?,
        comment: String?,
    ): EpisodeReviewRow {
        val episode = episodeRepository.findById(episodeId) ?: throw NotFoundException("Episode not found")
        val clubId = clubIdForSeason(episode.seasonId, actingMemberId)
        qualityOptionId?.let { clubService.validateRatingOption(clubId, it, QUALITY) }
        sentimentOptionId?.let { clubService.validateRatingOption(clubId, it, SENTIMENT) }
        return episodeRepository.upsertReview(episodeId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    private fun season(seasonId: Uuid): SeasonRow =
        seasonRepository.findById(seasonId) ?: throw NotFoundException("Season not found")

    private fun clubIdForSeason(seasonId: Uuid, actingMemberId: Uuid): Uuid {
        val series = seriesRepository.findById(season(seasonId).seriesId) ?: throw NotFoundException("Series not found")
        clubService.requireMembership(series.clubId, actingMemberId)
        return series.clubId
    }

    private fun requireMeetingInClub(meetingId: Uuid, clubId: Uuid) {
        val meeting = meetingRepository.findById(meetingId) ?: throw NotFoundException("Meeting not found")
        if (meeting.clubId != clubId) throw BadRequestException("Meeting must belong to the same club as the series")
    }
}
