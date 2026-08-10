package br.com.gabryel.movieclub.service.omdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

@Serializable
data class OmdbResponse(
    @SerialName("imdbRating") val imdbRating: String? = null,
)

/** One episode entry from OMDb's `Season` listing (`?i={seriesImdbId}&Season={n}`) -- OMDb's own `Episode` number
 * reflects IMDB's canonical numbering, which some shows' TMDB entries number differently (e.g. by original
 * broadcast order instead of the internationally-known release/session order -- see [SeriesService][
 * br.com.gabryel.movieclub.service.SeriesService.importSeasonsAndEpisodes] for how this is used to correct TMDB's
 * own episode numbers). */
@Serializable
data class OmdbSeasonEpisode(
    @SerialName("Title") val title: String,
    @SerialName("Episode") val episode: String,
    @SerialName("imdbID") val imdbId: String,
    @SerialName("imdbRating") val imdbRating: String? = null,
) {
    /** Null for the rare non-numeric `Episode` value (e.g. a special) -- callers skip those rather than guessing. */
    val number: Int? get() = episode.toIntOrNull()
}

@Serializable
private data class OmdbSeasonResponse(
    @SerialName("Episodes") val episodes: List<OmdbSeasonEpisode> = emptyList(),
)

private const val BASE_URL = "https://www.omdbapi.com/"

/** Best-effort lookup of IMDB's own rating/episode data by imdb id -- OMDb is the only free source for that
 * number, since TMDB's API only ever exposes its own `vote_average`. No-ops (returns null) when [apiKey] is blank
 * rather than throwing, since -- unlike TMDB -- OMDb access always requires a registered key, and a missing one
 * shouldn't block every movie/series add or refresh. A network failure, timeout, or non-2xx OMDb response is caught
 * here too, for the same reason -- callers never wrap this call themselves (see Movie/Series/Watchlist/
 * EpisodeService), so the "never blocks" guarantee has to live in the client itself rather than being every
 * caller's responsibility. [engine] defaults to a real CIO engine but is overridable so tests can substitute a
 * `MockEngine`, same reason as [br.com.gabryel.movieclub.service.tmdb.TmdbClient]'s own [engine] parameter. */
class OmdbClient(private val apiKey: String, engine: HttpClientEngine = CIO.create()) {
    private val http = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getImdbRating(imdbId: String): BigDecimal? {
        if (apiKey.isBlank()) return null

        return runCatching {
            val response = http.get(BASE_URL) {
                parameter("i", imdbId)
                parameter("apikey", apiKey)
            }.body<OmdbResponse>()
            response.imdbRating?.toBigDecimalOrNull()
        }.getOrNull()
    }

    /** OMDb's own episode list for one season of [imdbId] (a series), or null if it has nothing usable (blank
     * [apiKey], network failure, or a season/series OMDb doesn't know about) -- callers fall back to their own
     * default numbering in that case, same "never blocks" contract as [getImdbRating]. */
    suspend fun getSeasonEpisodes(imdbId: String, seasonNumber: Int): List<OmdbSeasonEpisode>? {
        if (apiKey.isBlank()) return null

        return runCatching {
            http.get(BASE_URL) {
                parameter("i", imdbId)
                parameter("Season", seasonNumber)
                parameter("apikey", apiKey)
            }.body<OmdbSeasonResponse>().episodes.ifEmpty { null }
        }.getOrNull()
    }
}
