package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.parseImdbId
import kotlin.uuid.Uuid

class MovieService(
    private val movieRepository: MovieRepository,
    private val meetingRepository: MeetingRepository,
    private val clubService: ClubService,
    private val tmdbClient: TmdbClient,
) {
    suspend fun addMovie(
        meetingId: Uuid,
        actingMemberId: Uuid,
        imdbUrlOrId: String,
        watchLink: String? = null,
    ): MovieRow {
        val meeting = requireMeetingAccess(meetingId, actingMemberId)
        val imdbId = parseImdbId(imdbUrlOrId)

        val summary = tmdbClient.findByImdbId(imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for $imdbId")

        val metadata = tmdbClient.getMovieDetails(summary.id).toMetadata(summary.id)

        return movieRepository.create(meeting.id, actingMemberId, imdbId, metadata, watchLink)
    }

    suspend fun refreshMetadata(movieId: Uuid, actingMemberId: Uuid): MovieRow {
        val movie = requireMovieAccess(movieId, actingMemberId)
        val summary = tmdbClient.findByImdbId(movie.imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for ${movie.imdbId}")

        val metadata = tmdbClient.getMovieDetails(summary.id).toMetadata(summary.id)

        return movieRepository.updateTmdbMetadata(movieId, metadata)
    }

    fun listMovies(meetingId: Uuid, actingMemberId: Uuid): List<MovieRow> {
        requireMeetingAccess(meetingId, actingMemberId)
        return movieRepository.listByMeeting(meetingId)
    }

    fun getMovie(movieId: Uuid, actingMemberId: Uuid): MovieRow = requireMovieAccess(movieId, actingMemberId)

    fun updateDisplayTitle(
        movieId: Uuid,
        actingMemberId: Uuid,
        customTitle: String? = null,
        preference: DisplayTitlePreference,
    ): MovieRow {
        requireMovieAccess(movieId, actingMemberId)
        if (preference == CUSTOM && customTitle.isNullOrBlank())
            throw BadRequestException("customTitle is required when preference is CUSTOM")

        return movieRepository.updateDisplayTitle(movieId, customTitle, preference)
    }

    fun updateWatchLink(movieId: Uuid, actingMemberId: Uuid, watchLink: String? = null): MovieRow {
        requireMovieAccess(movieId, actingMemberId)
        return movieRepository.updateWatchLink(movieId, watchLink)
    }

    fun deleteMovie(movieId: Uuid, actingMemberId: Uuid) {
        requireMovieAccess(movieId, actingMemberId)
        movieRepository.delete(movieId)
    }

    fun rate(
        movieId: Uuid,
        actingMemberId: Uuid,
        qualityOptionId: Uuid? = null,
        sentimentOptionId: Uuid? = null,
        comment: String? = null,
    ): MovieReviewRow {
        val movie = requireMovieAccess(movieId, actingMemberId)
        val meeting = meetingRepository.findById(movie.meetingId) ?: throw NotFoundException("Meeting not found")

        if (qualityOptionId != null) clubService.validateRatingOption(meeting.clubId, qualityOptionId, QUALITY)
        if (sentimentOptionId != null) clubService.validateRatingOption(meeting.clubId, sentimentOptionId, SENTIMENT)

        return movieRepository.upsertReview(movieId, actingMemberId, qualityOptionId, sentimentOptionId, comment)
    }

    fun listReviews(movieId: Uuid, actingMemberId: Uuid): List<MovieReviewRow> {
        requireMovieAccess(movieId, actingMemberId)
        return movieRepository.listReviews(movieId)
    }

    private fun requireMeetingAccess(meetingId: Uuid, actingMemberId: Uuid): MeetingRow {
        val meeting = meetingRepository.findById(meetingId) ?: throw NotFoundException("Meeting not found")
        clubService.requireMembership(meeting.clubId, actingMemberId)
        return meeting
    }

    private fun requireMovieAccess(movieId: Uuid, actingMemberId: Uuid): MovieRow {
        val movie = movieRepository.findById(movieId) ?: throw NotFoundException("Movie not found")
        requireMeetingAccess(movie.meetingId, actingMemberId)
        return movie
    }
}
