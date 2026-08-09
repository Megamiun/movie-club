package br.com.gabryel.movieclub.plugins

import br.com.gabryel.movieclub.routing.admin.adminRoutes
import br.com.gabryel.movieclub.routing.auth.authRoutes
import br.com.gabryel.movieclub.routing.club.clubRoutes
import br.com.gabryel.movieclub.routing.import.importRoutes
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
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

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
) {
    routing {
        get("/health") {
            call.respondText("OK")
        }

        authRoutes(jwtService, memberService)
        memberRoutes(memberService)
        clubRoutes(clubService)
        meetingRoutes(meetingService)
        movieRoutes(movieService)
        seriesRoutes(seriesService, seasonService, episodeService)
        watchlistRoutes(watchlistService)
        importRoutes(importService)
        adminRoutes(adminService)
    }
}
