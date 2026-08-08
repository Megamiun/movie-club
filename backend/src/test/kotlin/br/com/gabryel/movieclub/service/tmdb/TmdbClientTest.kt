package br.com.gabryel.movieclub.service.tmdb

import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import br.com.gabryel.movieclub.exception.BadRequestException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TmdbClientTest {
    @Test
    fun `parseImdbId accepts a bare id or a full url, rejects anything else`() {
        assertEquals("tt0180093", parseImdbId("tt0180093"))
        assertEquals("tt4857264", parseImdbId("https://www.imdb.com/title/tt4857264/"))
        assertFailsWith<BadRequestException> { parseImdbId("not-an-imdb-id") }
    }

    @Test
    fun `toRatingScale rounds a TMDB vote_average to one decimal place`() {
        assertEquals(BigDecimal("7.5"), 7.5.toRatingScale())
        assertEquals(BigDecimal("8.0"), 8.04.toRatingScale())
        assertEquals(BigDecimal("8.1"), 8.05.toRatingScale())
    }

    @Test
    fun `TmdbMovieDetails toMetadata pulls director from credits and year from release_date`() {
        val details = TmdbMovieDetails(
            originalTitle = "John Wick",
            title = "John Wick",
            releaseDate = "2014-10-24",
            runtime = 101,
            genres = listOf(TmdbGenre("Action"), TmdbGenre("Thriller")),
            originCountry = listOf("US"),
            productionCountries = listOf(TmdbProductionCountry("US", "United States of America")),
            voteAverage = 7.45,
            credits = TmdbCredits(
                crew = listOf(TmdbCrewMember("Chad Stahelski", "Director"), TmdbCrewMember("Someone Else", "Writer")),
            ),
            alternativeTitles = TmdbMovieAlternativeTitles(
                titles = listOf(TmdbAlternativeTitleEntry("BR", "De Volta ao Jogo", "")),
            ),
        )

        val metadata = details.toMetadata(tmdbId = 245891)

        assertEquals("245891", metadata.tmdbId)
        assertEquals("John Wick", metadata.originalTitle)
        assertEquals(2014, metadata.year)
        assertEquals("Chad Stahelski", metadata.director)
        assertEquals(101, metadata.runtimeMinutes)
        assertEquals(listOf("Action", "Thriller"), metadata.genre)
        assertEquals(listOf("US"), metadata.originCountry)
        assertEquals(listOf("United States of America"), metadata.productionCountries)
        assertEquals(BigDecimal("7.5"), metadata.tmdbRating)
        assertEquals(listOf(AlternativeTitle("BR", "De Volta ao Jogo")), metadata.alternativeTitles)
    }

    @Test
    fun `TmdbMovieDetails toMetadata is null-safe when credits and alternative_titles are absent`() {
        val details = TmdbMovieDetails(originalTitle = "Untitled", title = "Untitled")

        val metadata = details.toMetadata(tmdbId = 1)

        assertNull(metadata.director)
        assertNull(metadata.year)
        assertEquals(emptyList(), metadata.alternativeTitles)
    }

    @Test
    fun `TmdbTvDetails toMetadata pulls creator from created_by and year from first_air_date`() {
        val details = TmdbTvDetails(
            originalName = "Breaking Bad",
            name = "Breaking Bad",
            firstAirDate = "2008-01-20",
            genres = listOf(TmdbGenre("Drama")),
            originCountry = listOf("US"),
            productionCountries = listOf(TmdbProductionCountry("US", "United States of America")),
            voteAverage = 8.9,
            createdBy = listOf(TmdbCreator("Vince Gilligan")),
            alternativeTitles = TmdbTvAlternativeTitles(
                results = listOf(TmdbAlternativeTitleEntry("BR", "A Química do Mal", "working title")),
            ),
        )

        val metadata = details.toMetadata(tmdbId = 1396)

        assertEquals("1396", metadata.tmdbId)
        assertEquals("Breaking Bad", metadata.originalTitle)
        assertEquals(2008, metadata.year)
        assertEquals("Vince Gilligan", metadata.creator)
        assertEquals(listOf("Drama"), metadata.genre)
        assertEquals(listOf("United States of America"), metadata.productionCountries)
        assertEquals(
            listOf(AlternativeTitle("BR", "A Química do Mal", "working title")),
            metadata.alternativeTitles,
        )
    }

    @Test
    fun `TmdbEpisodeDetails toMetadata parses air_date and pulls director from crew`() {
        val details = TmdbEpisodeDetails(
            name = "Pilot",
            episodeNumber = 1,
            airDate = "2008-01-20",
            overview = "A chemistry teacher...",
            runtime = 59,
            voteAverage = 8.5,
            crew = listOf(TmdbCrewMember("Vince Gilligan", "Director")),
        )

        val metadata = details.toMetadata()

        assertEquals(LocalDate(2008, 1, 20), metadata.airDate)
        assertEquals("A chemistry teacher...", metadata.overview)
        assertEquals(59, metadata.runtimeMinutes)
        assertEquals("Vince Gilligan", metadata.director)
        assertEquals(BigDecimal("8.5"), metadata.tmdbRating)
    }

    @Test
    fun `TmdbEpisodeDetails toMetadata tolerates an unparseable air_date instead of throwing`() {
        val details = TmdbEpisodeDetails(name = "Pilot", episodeNumber = 1, airDate = "not-a-date")

        assertNull(details.toMetadata().airDate)
    }

    @Test
    fun `movie alternative_titles JSON nests under 'titles', tv nests under 'results'`() {
        val movieJson = """{"titles":[{"iso_3166_1":"BR","title":"De Volta ao Jogo","type":""}]}"""
        val tvJson = """{"results":[{"iso_3166_1":"BR","title":"A Química do Mal","type":""}]}"""

        val movieParsed = Json.decodeFromString<TmdbMovieAlternativeTitles>(movieJson)
        val tvParsed = Json.decodeFromString<TmdbTvAlternativeTitles>(tvJson)

        assertEquals("De Volta ao Jogo", movieParsed.titles.single().title)
        assertEquals("A Química do Mal", tvParsed.results.single().title)
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
