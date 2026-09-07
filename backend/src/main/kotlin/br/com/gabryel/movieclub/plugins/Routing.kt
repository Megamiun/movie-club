package br.com.gabryel.movieclub.plugins

import br.com.gabryel.movieclub.routing.admin.adminRoutes
import br.com.gabryel.movieclub.routing.auth.authRoutes
import br.com.gabryel.movieclub.routing.club.clubRoutes
import br.com.gabryel.movieclub.routing.import.importRoutes
import br.com.gabryel.movieclub.routing.mediaitem.mediaItemRoutes
import br.com.gabryel.movieclub.routing.meeting.meetingRoutes
import br.com.gabryel.movieclub.routing.member.memberRoutes
import br.com.gabryel.movieclub.routing.movie.movieRoutes
import br.com.gabryel.movieclub.routing.series.seriesRoutes
import br.com.gabryel.movieclub.routing.watchlist.watchlistRoutes
import br.com.gabryel.movieclub.service.AdminService
import br.com.gabryel.movieclub.service.ClubService
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.MeetingService
import br.com.gabryel.movieclub.service.MemberService
import br.com.gabryel.movieclub.service.MovieService
import br.com.gabryel.movieclub.service.SeasonService
import br.com.gabryel.movieclub.service.SeriesService
import br.com.gabryel.movieclub.service.WatchlistService
import br.com.gabryel.movieclub.service.auth.JwtService
import br.com.gabryel.movieclub.service.csvimport.ImportService
import br.com.gabryel.movieclub.service.tmdb.TmdbClient
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Application.configureRouting(
    jwtService: JwtService,
    memberService: MemberService,
    clubService: ClubService,
    meetingService: MeetingService,
    movieService: MovieService,
    seriesService: SeriesService,
    seasonService: SeasonService,
    episodeService: EpisodeService,
    watchlistService: WatchlistService,
    importService: ImportService,
    adminService: AdminService,
    tmdbClient: TmdbClient,
    meterRegistry: PrometheusMeterRegistry,
) {
    routing {
        get("/health") {
            call.respondText("OK")
        }

        // Deliberately unauthenticated, like /health above -- request-rate/timing metrics aren't user data, and
        // this is a single-operator instance with no monitoring stack to route auth through yet. Prometheus text
        // format either way (`registry.scrape()`), scrapable directly once something's actually watching it.
        get("/metrics") {
            call.respondText(meterRegistry.scrape())
        }

        authRoutes(jwtService, memberService)
        memberRoutes(memberService)
        clubRoutes(clubService)
        meetingRoutes(meetingService)
        movieRoutes(movieService, watchlistService)
        seriesRoutes(seriesService, seasonService, episodeService)
        watchlistRoutes(watchlistService)
        importRoutes(importService)
        adminRoutes(adminService)
        mediaItemRoutes(tmdbClient)
    }
}
