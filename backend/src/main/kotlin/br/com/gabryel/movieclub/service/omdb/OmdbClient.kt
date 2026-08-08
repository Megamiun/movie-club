package br.com.gabryel.movieclub.service.omdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

private const val BASE_URL = "https://www.omdbapi.com/"

/** Best-effort lookup of IMDB's own rating by imdb id -- OMDb is the only free source for that number, since TMDB's
 * API only ever exposes its own `vote_average`. No-ops (returns null) when [apiKey] is blank rather than throwing,
 * since -- unlike TMDB -- OMDb access always requires a registered key, and a missing one shouldn't block every
 * movie/series add or refresh. */
class OmdbClient(private val apiKey: String) {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getImdbRating(imdbId: String): BigDecimal? {
        if (apiKey.isBlank()) return null

        val response = http.get(BASE_URL) {
            parameter("i", imdbId)
            parameter("apikey", apiKey)
        }.body<OmdbResponse>()

        return response.imdbRating?.toBigDecimalOrNull()
    }
}
