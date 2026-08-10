package br.com.gabryel.movieclub.service.omdb

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OmdbClientTest {
    @Test
    fun `getImdbRating returns null without calling OMDb when no api key is configured`() = runBlocking {
        assertNull(OmdbClient("").getImdbRating("tt0903747"))
    }

    @Test
    fun `getSeasonEpisodes returns null without calling OMDb when no api key is configured`() = runBlocking {
        assertNull(OmdbClient("").getSeasonEpisodes("tt0213338", 1))
    }

    @Test
    fun `getSeasonEpisodes parses OMDb's own numbering, distinct from TMDB's`() = runBlocking {
        val engine = MockEngine {
            respond(
                """{"Title":"Cowboy Bebop","Season":"1","Episodes":[
                    {"Title":"Asteroid Blues","Released":"2001-09-02","Episode":"1","imdbRating":"8.2","imdbID":"tt0618963"},
                    {"Title":"Gateway Shuffle","Released":"2001-09-09","Episode":"4","imdbRating":"7.7","imdbID":"tt0618968"}
                ],"Response":"True"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = OmdbClient("key", engine)

        val episodes = client.getSeasonEpisodes("tt0213338", 1)

        assertEquals(
            listOf(
                OmdbSeasonEpisode("Asteroid Blues", "1", "tt0618963", "8.2"),
                OmdbSeasonEpisode("Gateway Shuffle", "4", "tt0618968", "7.7"),
            ),
            episodes,
        )
        assertEquals(4, episodes?.get(1)?.number)
    }

    @Test
    fun `getSeasonEpisodes returns null when OMDb has no data for this season`() = runBlocking {
        val engine = MockEngine {
            respond(
                """{"Response":"False","Error":"Series or season not found!"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = OmdbClient("key", engine)

        assertNull(client.getSeasonEpisodes("tt0213338", 99))
    }

    @Test
    fun `getSeasonEpisodes returns null on a network failure instead of throwing`() = runBlocking {
        val engine = MockEngine { throw RuntimeException("timeout") }
        val client = OmdbClient("key", engine)

        assertNull(client.getSeasonEpisodes("tt0213338", 1))
    }

    @Test
    fun `OmdbSeasonEpisode number is null for a non-numeric Episode value`() {
        assertNull(OmdbSeasonEpisode("Special", "SPECIAL", "tt0000000").number)
    }
}
