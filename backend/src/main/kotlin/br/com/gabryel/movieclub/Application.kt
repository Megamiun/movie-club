package br.com.gabryel.movieclub

import br.com.gabryel.movieclub.db.repositories.exposed.ExposedClubRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedEpisodeRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMediaItemRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMeetingRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMemberRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMovieRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedRatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeasonRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeriesRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedWatchlistRepository
import br.com.gabryel.movieclub.plugins.configureAuthentication
import br.com.gabryel.movieclub.plugins.configureCORS
import br.com.gabryel.movieclub.plugins.configureCallLogging
import br.com.gabryel.movieclub.plugins.configureDatabase
import br.com.gabryel.movieclub.plugins.configureErrors
import br.com.gabryel.movieclub.plugins.configureRouting
import br.com.gabryel.movieclub.plugins.configureSerialization
import br.com.gabryel.movieclub.service.AdminService
import br.com.gabryel.movieclub.service.ClubService
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.MeetingService
import br.com.gabryel.movieclub.service.MemberService
import br.com.gabryel.movieclub.service.MovieService
import br.com.gabryel.movieclub.service.SeasonService
import br.com.gabryel.movieclub.service.SeriesService
import br.com.gabryel.movieclub.service.WatchlistService
import br.com.gabryel.movieclub.service.auth.Argon2PasswordService
import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.service.csvimport.ImportService
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val config = environment.config

    val jwtService = JwtService(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
    )

    configureDatabase()

    val memberRepository = ExposedMemberRepository()
    val memberService = MemberService(memberRepository, Argon2PasswordService())
    val clubService = ClubService(ExposedClubRepository(), ExposedRatingScaleRepository(), memberRepository)
    val ratingScaleRepository = ExposedRatingScaleRepository()
    val meetingRepository = ExposedMeetingRepository()
    val movieRepository = ExposedMovieRepository()
    val seriesRepository = ExposedSeriesRepository()
    val seasonRepository = ExposedSeasonRepository()
    val episodeRepository = ExposedEpisodeRepository()
    val mediaItemRepository = ExposedMediaItemRepository()
    val tmdbClient = TmdbClient(config.propertyOrNull("tmdb.accessToken")?.getString().orEmpty())
    val omdbClient = OmdbClient(config.propertyOrNull("omdb.apiKey")?.getString().orEmpty())
    val meetingService = MeetingService(meetingRepository, movieRepository, episodeRepository, seriesRepository, clubService)
    val movieService = MovieService(movieRepository, meetingRepository, clubService, mediaItemRepository, tmdbClient, omdbClient)
    val seriesService = SeriesService(
        seriesRepository,
        clubService,
        mediaItemRepository,
        tmdbClient,
        omdbClient,
        seasonRepository,
        episodeRepository,
    )
    val seasonService = SeasonService(seasonRepository, seriesRepository, clubService)
    val episodeService = EpisodeService(
        episodeRepository,
        seasonRepository,
        seriesRepository,
        meetingRepository,
        clubService,
        tmdbClient,
    )
    val watchlistRepository = ExposedWatchlistRepository()
    val watchlistService = WatchlistService(watchlistRepository, clubService, mediaItemRepository, tmdbClient, omdbClient)
    val importService = ImportService(
        clubService,
        meetingRepository,
        movieRepository,
        movieService,
        seriesRepository,
        seriesService,
        seasonRepository,
        episodeRepository,
        episodeService,
        watchlistRepository,
        watchlistService,
        ratingScaleRepository,
    )
    val adminService = AdminService(memberRepository, mediaItemRepository)

    configureCallLogging()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureAuthentication(jwtService)
    configureRouting(
        jwtService,
        memberService,
        clubService,
        meetingService,
        movieService,
        seriesService,
        seasonService,
        episodeService,
        watchlistService,
        importService,
        adminService,
    )
}
