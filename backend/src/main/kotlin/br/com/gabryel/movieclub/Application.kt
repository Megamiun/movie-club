package br.com.gabryel.movieclub

import br.com.gabryel.movieclub.db.repositories.exposed.ExposedClubRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedEpisodeRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMediaItemRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMeetingRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMemberRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedMovieRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedPersonRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedRatingScaleRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeasonRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedSeriesRepository
import br.com.gabryel.movieclub.db.repositories.exposed.ExposedWatchlistRepository
import br.com.gabryel.movieclub.plugins.configureAuthentication
import br.com.gabryel.movieclub.plugins.configureCORS
import br.com.gabryel.movieclub.plugins.configureCallLogging
import br.com.gabryel.movieclub.plugins.configureDatabase
import br.com.gabryel.movieclub.plugins.configureErrors
import br.com.gabryel.movieclub.plugins.configureMetrics
import br.com.gabryel.movieclub.plugins.configureRouting
import br.com.gabryel.movieclub.plugins.configureSerialization
import br.com.gabryel.movieclub.service.AdminService
import br.com.gabryel.movieclub.service.ClubService
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.MediaItemService
import br.com.gabryel.movieclub.service.MeetingService
import br.com.gabryel.movieclub.service.MemberService
import br.com.gabryel.movieclub.service.MetadataRefreshJob
import br.com.gabryel.movieclub.service.MovieService
import br.com.gabryel.movieclub.service.SeasonService
import br.com.gabryel.movieclub.service.SeriesService
import br.com.gabryel.movieclub.service.WatchlistService
import br.com.gabryel.movieclub.service.auth.Argon2PasswordService
import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.service.csvimport.ImportService
import br.com.gabryel.movieclub.service.omdb.OmdbClient
import br.com.gabryel.movieclub.service.storage.S3StorageClient
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val config = environment.config

    val jwtService = JwtService(
        secret = config.property("jwt.secret").getString(),
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
    )

    configureDatabase()

    val s3StorageClient = S3StorageClient(
        accessKeyId = config.propertyOrNull("aws.accessKeyId")?.getString(),
        secretAccessKey = config.propertyOrNull("aws.secretAccessKey")?.getString(),
        region = config.property("aws.region").getString(),
        bucketName = config.propertyOrNull("aws.bucketName")?.getString().orEmpty(),
        endpointUrl = config.propertyOrNull("aws.endpointUrl")?.getString(),
        publicBaseUrl = config.propertyOrNull("aws.publicBaseUrl")?.getString(),
    )
    val memberRepository = ExposedMemberRepository()
    val memberService = MemberService(memberRepository, Argon2PasswordService(), s3StorageClient)
    val ratingScaleRepository = ExposedRatingScaleRepository()
    val meetingRepository = ExposedMeetingRepository()
    val movieRepository = ExposedMovieRepository()
    val seriesRepository = ExposedSeriesRepository()
    val seasonRepository = ExposedSeasonRepository()
    val episodeRepository = ExposedEpisodeRepository()
    val clubRepository = ExposedClubRepository()
    val clubService = ClubService(
        clubRepository,
        ratingScaleRepository,
        memberRepository,
        movieRepository,
        seriesRepository,
        seasonRepository,
        episodeRepository,
        memberService,
    )
    val mediaItemRepository = ExposedMediaItemRepository()
    val personRepository = ExposedPersonRepository()
    val tmdbClient = TmdbClient(config.propertyOrNull("tmdb.accessToken")?.getString().orEmpty())
    val omdbClient = OmdbClient(config.propertyOrNull("omdb.apiKey")?.getString().orEmpty())
    val meetingService =
        MeetingService(meetingRepository, movieRepository, episodeRepository, seriesRepository, clubService, clubRepository)
    val movieService =
        MovieService(movieRepository, meetingRepository, clubService, mediaItemRepository, tmdbClient, omdbClient, personRepository)
    val seriesService = SeriesService(
        seriesRepository,
        clubService,
        mediaItemRepository,
        tmdbClient,
        omdbClient,
        seasonRepository,
        episodeRepository,
        personRepository,
    )
    val seasonService = SeasonService(seasonRepository, seriesRepository, clubService)
    val watchlistRepository = ExposedWatchlistRepository()
    val episodeService = EpisodeService(
        episodeRepository,
        seasonRepository,
        seriesRepository,
        meetingRepository,
        clubService,
        tmdbClient,
        omdbClient,
        watchlistRepository,
        personRepository,
        mediaItemRepository,
    )
    val watchlistService = WatchlistService(
        watchlistRepository,
        clubService,
        movieRepository,
        seriesRepository,
        tmdbClient,
        movieService,
        seriesService,
    )
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
    val metadataRefreshJob =
        MetadataRefreshJob(movieRepository, seriesRepository, episodeRepository, movieService, seriesService, episodeService)
    val adminService = AdminService(memberRepository, mediaItemRepository, metadataRefreshJob)
    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    scheduleNightlyMetadataRefresh(metadataRefreshJob)

    configureCallLogging()
    configureSerialization()
    configureCORS()
    configureErrors()
    configureAuthentication(jwtService)
    configureMetrics(meterRegistry)
    val mediaItemService = MediaItemService(mediaItemRepository, episodeRepository, movieService, seriesService, episodeService)
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
        tmdbClient,
        mediaItemService,
        meterRegistry,
    )
}

/** Runs [job] once a day at [hourUtc] (04:00 UTC by default -- an hour after the nightly DB backup's own 03:00
 * UTC timer, `infra/templates/user_data.sh.tpl`, so the two don't compete for the instance's modest resources at
 * the same moment). `Application` is itself a [CoroutineScope] tied to the server's own lifecycle (its
 * `coroutineContext` wraps a `SupervisorJob`), so `launch` here is automatically cancelled on shutdown -- no
 * separate cleanup needed. A failed run is logged and swallowed rather than crashing the loop, so one bad night
 * (a TMDB/OMDb outage, say) doesn't cancel every future scheduled run. */
private fun Application.scheduleNightlyMetadataRefresh(job: MetadataRefreshJob, hourUtc: Int = 4) {
    launch {
        while (isActive) {
            val now = Clock.System.now()
            val nowDateTime = now.toLocalDateTime(TimeZone.UTC)
            val nextRunDate = if (nowDateTime.hour < hourUtc) nowDateTime.date else nowDateTime.date.plus(1, DateTimeUnit.DAY)
            val nextRun = LocalDateTime(nextRunDate, LocalTime(hourUtc, 0)).toInstant(TimeZone.UTC)
            delay(nextRun - now)
            runCatching { job.run() }
                .onFailure { environment.log.error("Nightly metadata refresh failed", it) }
        }
    }
}
