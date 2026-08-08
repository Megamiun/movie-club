package br.com.gabryel.movieclub.routing.series

import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeasonRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesReviewRow
import br.com.gabryel.movieclub.db.repositories.dto.SeriesRow
import br.com.gabryel.movieclub.routing.actingMemberId
import br.com.gabryel.movieclub.routing.movie.AlternativeTitleResponse
import br.com.gabryel.movieclub.routing.toDisplayTitlePreferenceOrBadRequest
import br.com.gabryel.movieclub.routing.toUuidOrBadRequest
import br.com.gabryel.movieclub.routing.uuidPathParam
import br.com.gabryel.movieclub.service.EpisodeService
import br.com.gabryel.movieclub.service.SeasonService
import br.com.gabryel.movieclub.service.SeriesService
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.seriesRoutes(seriesService: SeriesService, seasonService: SeasonService, episodeService: EpisodeService) {
    authenticate("auth-jwt") {
        post("/clubs/{clubId}/series") {
            val body = call.receive<AddSeriesRequest>()
            val series = seriesService.addSeries(call.uuidPathParam("clubId"), call.actingMemberId(), body.imdbUrlOrId)
            call.respond(Created, series.toResponse())
        }

        get("/clubs/{clubId}/series") {
            val series = seriesService.listSeries(call.uuidPathParam("clubId"), call.actingMemberId())
            call.respond(series.map { it.toResponse() })
        }

        get("/series/{seriesId}") {
            val series = seriesService.getSeries(call.uuidPathParam("seriesId"), call.actingMemberId())
            call.respond(series.toResponse())
        }

        patch("/series/{seriesId}") {
            val body = call.receive<UpdateSeriesRequest>()
            val series = seriesService.updateDisplayTitle(
                call.uuidPathParam("seriesId"),
                call.actingMemberId(),
                body.customTitle,
                body.preference.toDisplayTitlePreferenceOrBadRequest(),
            )
            call.respond(series.toResponse())
        }

        post("/series/{seriesId}/refresh-metadata") {
            val series = seriesService.refreshMetadata(call.uuidPathParam("seriesId"), call.actingMemberId())
            call.respond(series.toResponse())
        }

        put("/series/{seriesId}/review") {
            val body = call.receive<RateRequest>()
            val review = seriesService.rate(
                call.uuidPathParam("seriesId"),
                call.actingMemberId(),
                body.qualityOptionId?.toUuidOrBadRequest(),
                body.sentimentOptionId?.toUuidOrBadRequest(),
                body.comment,
            )
            call.respond(review.toResponse())
        }

        post("/series/{seriesId}/seasons") {
            val body = call.receive<AddSeasonRequest>()
            val season =
                seasonService.addSeason(call.uuidPathParam("seriesId"), call.actingMemberId(), body.number, body.title)
            call.respond(Created, season.toResponse())
        }

        get("/series/{seriesId}/seasons") {
            val seasons = seasonService.listSeasons(call.uuidPathParam("seriesId"), call.actingMemberId())
            call.respond(seasons.map { it.toResponse() })
        }

        put("/seasons/{seasonId}/review") {
            val body = call.receive<RateRequest>()
            val review = seasonService.rate(
                call.uuidPathParam("seasonId"),
                call.actingMemberId(),
                body.qualityOptionId?.toUuidOrBadRequest(),
                body.sentimentOptionId?.toUuidOrBadRequest(),
                body.comment,
            )
            call.respond(review.toResponse())
        }

        post("/seasons/{seasonId}/episodes") {
            val body = call.receive<AddEpisodeRequest>()
            val episode = episodeService.addEpisode(
                call.uuidPathParam("seasonId"),
                call.actingMemberId(),
                body.number,
                body.title,
                body.meetingId?.toUuidOrBadRequest(),
            )
            call.respond(Created, episode.toResponse())
        }

        get("/seasons/{seasonId}/episodes") {
            val episodes = episodeService.listEpisodes(call.uuidPathParam("seasonId"), call.actingMemberId())
            call.respond(episodes.map { it.toResponse() })
        }

        post("/episodes/{episodeId}/refresh-metadata") {
            val episode = episodeService.refreshMetadata(call.uuidPathParam("episodeId"), call.actingMemberId())
            call.respond(episode.toResponse())
        }

        post("/episodes/{episodeId}/meetings/{meetingId}") {
            val episode = episodeService.assignToMeeting(
                call.uuidPathParam("episodeId"),
                call.actingMemberId(),
                call.uuidPathParam("meetingId"),
            )
            call.respond(episode.toResponse())
        }

        delete("/episodes/{episodeId}/meetings/{meetingId}") {
            val episode = episodeService.unassignFromMeeting(
                call.uuidPathParam("episodeId"),
                call.actingMemberId(),
                call.uuidPathParam("meetingId"),
            )
            call.respond(episode.toResponse())
        }

        put("/episodes/{episodeId}/review") {
            val body = call.receive<RateRequest>()
            val review = episodeService.rate(
                call.uuidPathParam("episodeId"),
                call.actingMemberId(),
                body.qualityOptionId?.toUuidOrBadRequest(),
                body.sentimentOptionId?.toUuidOrBadRequest(),
                body.comment,
            )
            call.respond(review.toResponse())
        }

        get("/meetings/{meetingId}/episodes") {
            val episodes = episodeService.listEpisodesForMeeting(call.uuidPathParam("meetingId"), call.actingMemberId())
            call.respond(episodes.map { it.toResponse() })
        }
    }
}

private fun SeriesRow.toResponse() = SeriesResponse(
    id = id.toString(),
    clubId = clubId.toString(),
    chosenById = chosenById.toString(),
    imdbId = imdbId,
    tmdbId = tmdbId,
    originalTitle = originalTitle,
    alternativeTitles = alternativeTitles.map { it.toResponse() },
    customTitle = customTitle,
    displayTitlePreference = displayTitlePreference.name,
    year = year,
    genre = genre,
    originCountry = originCountry,
    productionCountries = productionCountries,
    tmdbRating = tmdbRating?.toPlainString(),
    creator = creator,
    posterS3Key = posterS3Key,
)

private fun AlternativeTitle.toResponse() = AlternativeTitleResponse(isoCode, title, type)

private fun SeasonRow.toResponse() = SeasonResponse(id.toString(), seriesId.toString(), number, title)

private fun EpisodeRow.toResponse() = EpisodeResponse(
    id = id.toString(),
    seasonId = seasonId.toString(),
    number = number,
    title = title,
    airDate = airDate?.toString(),
    overview = overview,
    runtimeMinutes = runtimeMinutes,
    director = director,
    tmdbRating = tmdbRating?.toPlainString(),
)

private fun SeriesReviewRow.toResponse() = SeriesReviewResponse(
    seriesId.toString(),
    memberId.toString(),
    qualityOptionId?.toString(),
    sentimentOptionId?.toString(),
    comment,
)

private fun SeasonReviewRow.toResponse() = SeasonReviewResponse(
    seasonId.toString(),
    memberId.toString(),
    qualityOptionId?.toString(),
    sentimentOptionId?.toString(),
    comment,
)

private fun EpisodeReviewRow.toResponse() = EpisodeReviewResponse(
    episodeId.toString(),
    memberId.toString(),
    qualityOptionId?.toString(),
    sentimentOptionId?.toString(),
    comment,
)
