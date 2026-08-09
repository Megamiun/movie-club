package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.DisplayTitlePreference
import br.com.gabryel.movieclub.db.DisplayTitlePreference.CUSTOM
import br.com.gabryel.movieclub.db.DisplayTitlePreference.LANGUAGE
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.RatingScaleType.QUALITY
import br.com.gabryel.movieclub.db.RatingScaleType.SENTIMENT
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.MeetingRepository
import br.com.gabryel.movieclub.db.repositories.MovieRepository
import br.com.gabryel.movieclub.db.repositories.dto.MeetingRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.MovieRow
import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.NotFoundException
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbMovieDetails
import br.com.gabryel.movieclub.service.tmdb.TmdbMovieSearchItem
import br.com.gabryel.movieclub.service.tmdb.parseImdbId
import br.com.gabryel.movieclub.service.tmdb.toTmdbPosterUrl
import java.math.BigDecimal
import kotlin.uuid.Uuid

class MovieService(
    private val movieRepository: MovieRepository,
    private val meetingRepository: MeetingRepository,
    private val clubService: ClubService,
    private val mediaItemRepository: MediaItemRepository,
    private val tmdbClient: TmdbClient,
    private val omdbClient: OmdbClient,
) {
    suspend fun searchMovies(query: String): List<TmdbMovieSearchItem> {
        if (query.isBlank()) return emptyList()
        return tmdbClient.searchMovies(query.trim())
    }

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

        return createFromTmdb(meeting.id, actingMemberId, summary.id, watchLink)
    }

    suspend fun addMovieByTmdbId(
        meetingId: Uuid,
        actingMemberId: Uuid,
        tmdbId: Int,
        watchLink: String? = null,
    ): MovieRow {
        val meeting = requireMeetingAccess(meetingId, actingMemberId)
        return createFromTmdb(meeting.id, actingMemberId, tmdbId, watchLink)
    }

    private suspend fun createFromTmdb(meetingId: Uuid, actingMemberId: Uuid, tmdbId: Int, watchLink: String?): MovieRow {
        val details = tmdbClient.getMovieDetails(tmdbId)
        val imdbId = details.externalIds?.imdbId
            ?: throw BadRequestException("TMDB movie $tmdbId has no linked IMDB id")

        if (movieRepository.findByMeetingAndImdbId(meetingId, imdbId) != null)
            throw BadRequestException("This movie has already been added to this meeting")

        val imdbRating = omdbClient.getImdbRating(imdbId)
        val directorImdbId = resolveDirectorImdbId(details.directorTmdbId)
        val metadata = details.toMetadata(tmdbId).copy(imdbRating = imdbRating, directorImdbId = directorImdbId)
        val mediaItem = linkMediaItem(details, tmdbId, imdbId, metadata, imdbRating)
        return movieRepository.create(meetingId, actingMemberId, imdbId, metadata, mediaItem, watchLink)
    }

    suspend fun refreshMetadata(movieId: Uuid, actingMemberId: Uuid): MovieRow {
        val movie = requireMovieAccess(movieId, actingMemberId)
        val summary = tmdbClient.findByImdbId(movie.imdbId)
            ?: throw BadRequestException("Could not find TMDB metadata for ${movie.imdbId}")

        val details = tmdbClient.getMovieDetails(summary.id)
        val imdbRating = omdbClient.getImdbRating(movie.imdbId)
        val directorImdbId = resolveDirectorImdbId(details.directorTmdbId)
        val metadata = details.toMetadata(summary.id).copy(imdbRating = imdbRating, directorImdbId = directorImdbId)
        val mediaItem = linkMediaItem(details, summary.id, movie.imdbId, metadata, imdbRating)

        return movieRepository.updateTmdbMetadata(movieId, metadata, mediaItem)
    }

    /** Best-effort, like [OmdbClient.getImdbRating] -- a person lookup failing (rate limit, no linked IMDB page,
     * etc.) should never block adding/refreshing the movie itself. */
    private suspend fun resolveDirectorImdbId(directorTmdbId: Int?): String? =
        directorTmdbId?.let { runCatching { tmdbClient.getPersonExternalIds(it).imdbId }.getOrNull() }

    private fun linkMediaItem(
        details: TmdbMovieDetails,
        tmdbId: Int,
        imdbId: String,
        metadata: TmdbMovieMetadata,
        imdbRating: BigDecimal?,
    ): Uuid = mediaItemRepository.findOrCreate(
        type = MOVIE,
        imdbId = imdbId,
        title = details.originalTitle,
        tmdbId = tmdbId.toString(),
        year = details.year,
        posterUrl = details.posterPath?.toTmdbPosterUrl(),
        tmdbRating = metadata.tmdbRating,
        imdbRating = imdbRating,
    ).id

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
        languageCode: String? = null,
    ): MovieRow {
        requireMovieAccess(movieId, actingMemberId)
        if (preference == CUSTOM && customTitle.isNullOrBlank())
            throw BadRequestException("customTitle is required when preference is CUSTOM")
        if (preference == LANGUAGE && languageCode.isNullOrBlank())
            throw BadRequestException("languageCode is required when preference is LANGUAGE")

        return movieRepository.updateDisplayTitle(movieId, customTitle, preference, languageCode)
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
