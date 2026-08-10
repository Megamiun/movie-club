package br.com.gabryel.movieclub.service.tmdb

import br.com.gabryel.movieclub.db.repositories.dto.Translation
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.UpstreamServiceException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TmdbClientTest {
    @Test
    fun `a non-2xx TMDB response throws UpstreamServiceException naming the status, instead of silently decoding as empty`() {
        val engine = MockEngine { respond("Invalid API key", HttpStatusCode.Unauthorized) }
        val client = TmdbClient(accessToken = "bad-token", engine = engine)

        val exception = assertFailsWith<UpstreamServiceException> {
            runBlocking { client.getMovieDetails(438631) }
        }
        assertEquals("TMDB request failed: 401 Unauthorized", exception.message)
    }

    @Test
    fun `findByImdbId still resolves normally on a 2xx response`() {
        val engine = MockEngine {
            respond(
                """{"movie_results":[{"id":438631}],"tv_results":[]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = TmdbClient(accessToken = "token", engine = engine)

        val result = runBlocking { client.findByImdbId("tt1160419") }

        assertEquals(438631, result?.id)
    }

    @Test
    fun `parseImdbId accepts a bare id or a full url, rejects anything else`() {
        assertEquals("tt0180093", parseImdbId("tt0180093"))
        assertEquals("tt4857264", parseImdbId("https://www.imdb.com/title/tt4857264/"))
        assertFailsWith<BadRequestException> { parseImdbId("not-an-imdb-id") }
    }

    @Test
    fun `TmdbMovieDetails toMetadata pulls year from release_date -- director comes from the details getter directly`() {
        val details = TmdbMovieDetails(
            originalTitle = "John Wick",
            title = "John Wick",
            originalLanguage = "en",
            releaseDate = "2014-10-24",
            runtime = 101,
            genres = listOf(TmdbGenre("Action"), TmdbGenre("Thriller")),
            originCountry = listOf("US"),
            productionCountries = listOf(TmdbProductionCountry("US", "United States of America")),
            credits = TmdbCredits(
                crew = listOf(TmdbCrewMember("Chad Stahelski", "Director"), TmdbCrewMember("Someone Else", "Writer")),
            ),
            translations = TmdbTranslations(
                translations = listOf(
                    TmdbTranslationEntry("BR", "pt", "Portuguese", TmdbTranslationData(title = "De Volta ao Jogo")),
                ),
            ),
        )

        val metadata = details.toMetadata(tmdbId = 245891)

        assertEquals("245891", metadata.tmdbId)
        assertEquals("John Wick", metadata.originalTitle)
        assertEquals("en", metadata.originalLanguage)
        assertEquals(2014, metadata.year)
        assertEquals("Chad Stahelski", details.director)
        assertEquals(101, metadata.runtimeMinutes)
        assertEquals(listOf("Action", "Thriller"), metadata.genre)
        assertEquals(listOf("US"), metadata.originCountry)
        assertEquals(listOf("United States of America"), metadata.productionCountries)
        assertEquals(listOf(Translation("pt", "BR", "Portuguese", "De Volta ao Jogo")), metadata.translations)
    }

    @Test
    fun `TmdbMovieDetails directorTmdbId reads the Director crew member's own TMDB person id`() {
        val details = TmdbMovieDetails(
            originalTitle = "John Wick",
            title = "John Wick",
            credits = TmdbCredits(
                crew = listOf(
                    TmdbCrewMember("Chad Stahelski", "Director", id = 12891),
                    TmdbCrewMember("Someone Else", "Writer", id = 999),
                ),
            ),
        )

        assertEquals(12891, details.directorTmdbId)
    }

    @Test
    fun `TmdbMovieDetails toMetadata is null-safe when credits and translations are absent`() {
        val details = TmdbMovieDetails(originalTitle = "Untitled", title = "Untitled")

        val metadata = details.toMetadata(tmdbId = 1)

        assertNull(details.director)
        assertNull(metadata.year)
        assertEquals(emptyList(), metadata.translations)
    }

    @Test
    fun `TmdbTvDetails toMetadata pulls year from first_air_date -- creator comes from the details getter directly`() {
        val details = TmdbTvDetails(
            originalName = "Breaking Bad",
            name = "Breaking Bad",
            originalLanguage = "en",
            firstAirDate = "2008-01-20",
            genres = listOf(TmdbGenre("Drama")),
            originCountry = listOf("US"),
            productionCountries = listOf(TmdbProductionCountry("US", "United States of America")),
            createdBy = listOf(TmdbCreator("Vince Gilligan")),
            translations = TmdbTranslations(
                translations = listOf(
                    TmdbTranslationEntry("BR", "pt", "Portuguese", TmdbTranslationData(name = "A Química do Mal")),
                ),
            ),
        )

        val metadata = details.toMetadata(tmdbId = 1396)

        assertEquals("1396", metadata.tmdbId)
        assertEquals("Breaking Bad", metadata.originalTitle)
        assertEquals("en", metadata.originalLanguage)
        assertEquals(2008, metadata.year)
        assertEquals("Vince Gilligan", details.creator)
        assertEquals(listOf("Drama"), metadata.genre)
        assertEquals(listOf("United States of America"), metadata.productionCountries)
        assertEquals(
            listOf(Translation("pt", "BR", "Portuguese", "A Química do Mal")),
            metadata.translations,
        )
    }

    @Test
    fun `TmdbEpisodeDetails toMetadata parses air_date and imdbId from external_ids -- director comes from the crew getter directly`() {
        val details = TmdbEpisodeDetails(
            name = "Pilot",
            episodeNumber = 1,
            airDate = "2008-01-20",
            overview = "A chemistry teacher...",
            runtime = 59,
            crew = listOf(TmdbCrewMember("Vince Gilligan", "Director")),
            externalIds = TmdbExternalIds(imdbId = "tt0959621"),
        )

        val metadata = details.toMetadata()

        assertEquals(LocalDate(2008, 1, 20), metadata.airDate)
        assertEquals("A chemistry teacher...", metadata.overview)
        assertEquals(59, metadata.runtimeMinutes)
        assertEquals("Vince Gilligan", details.director)
        assertEquals("tt0959621", metadata.imdbId)
    }

    @Test
    fun `TmdbEpisodeDetails toMetadata leaves imdbId null when external_ids is absent`() {
        val details = TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1)

        assertNull(details.toMetadata().imdbId)
    }

    @Test
    fun `TmdbEpisodeDetails directorTmdbId reads the Director crew member's own TMDB person id`() {
        val details = TmdbEpisodeDetails(
            name = "Pilot",
            episodeNumber = 1,
            crew = listOf(TmdbCrewMember("Vince Gilligan", "Director", id = 66633)),
        )

        assertEquals(66633, details.directorTmdbId)
    }

    @Test
    fun `TmdbEpisodeDetails toMetadata tolerates an unparseable air_date instead of throwing`() {
        val details = TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1, airDate = "not-a-date")

        assertNull(details.toMetadata().airDate)
    }

    @Test
    fun `toTranslations reads movie's data-title or tv's data-name, dropping entries with neither`() {
        val translations = TmdbTranslations(
            translations = listOf(
                TmdbTranslationEntry("BR", "pt", "Portuguese", TmdbTranslationData(title = "De Volta ao Jogo")),
                TmdbTranslationEntry("US", "en", "English", TmdbTranslationData(name = "John Wick")),
                TmdbTranslationEntry("FR", "fr", "French", TmdbTranslationData()),
            ),
        )

        assertEquals(
            listOf(
                Translation("pt", "BR", "Portuguese", "De Volta ao Jogo"),
                Translation("en", "US", "English", "John Wick"),
            ),
            translations.toTranslations(),
        )
    }

    @Test
    fun `TmdbTvDetails seasons and TmdbSeasonDetails episodes parse season_number and episode_number`() {
        val tvJson = """{"original_name":"Breaking Bad","name":"Breaking Bad",
            "seasons":[{"season_number":1},{"season_number":2}]}"""
        val seasonJson = """{"season_number":1,"episodes":[{"name":"Pilot","episode_number":1}]}"""

        val tvParsed = Json.decodeFromString<TmdbTvDetails>(tvJson)
        val seasonParsed = Json.decodeFromString<TmdbSeasonDetails>(seasonJson)

        assertEquals(listOf(1, 2), tvParsed.seasons.map { it.seasonNumber })
        assertEquals(1, seasonParsed.seasonNumber)
        assertEquals(1, seasonParsed.episodes.single().episodeNumber)
    }
}
