package br.com.gabryel.movieclub.service.tmdb

import br.com.gabryel.movieclub.db.repositories.dto.AlternativeTitle
import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.exception.BadRequestException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Clock
import kotlin.time.Instant

private val IMDB_ID_REGEX = Regex("tt\\d{7,9}")

/** Tolerant of a bare `tt1234567` id or a full IMDB URL; used by every service that adds a movie/series by id. */
fun parseImdbId(input: String): String =
    IMDB_ID_REGEX.find(input)?.value ?: throw BadRequestException("Invalid IMDB id or URL: $input")

/** Maps a TMDB `vote_average` (0-10, arbitrary precision) onto our `tmdb_rating DECIMAL(4,1)` column. */
fun Double.toRatingScale(): BigDecimal = BigDecimal.valueOf(this).setScale(1, RoundingMode.HALF_UP)

@Serializable
data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbMovieSummary> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbTvSummary> = emptyList(),
)

@Serializable
data class TmdbMovieSummary(
    val id: Int,
)

@Serializable
data class TmdbTvSummary(
    val id: Int,
)

@Serializable
data class TmdbGenre(
    val name: String,
)

@Serializable
data class TmdbCrewMember(
    val name: String,
    val job: String,
)

@Serializable
data class TmdbCredits(
    val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbProductionCountry(
    @SerialName("iso_3166_1") val isoCode: String,
    val name: String,
)

@Serializable
data class TmdbAlternativeTitleEntry(
    @SerialName("iso_3166_1") val isoCode: String,
    val title: String,
    val type: String = "",
)

/** Movie alternative-titles responses nest their list under `"titles"`; TV's nests under `"results"` (see
 * [TmdbTvAlternativeTitles]) -- a genuine inconsistency in TMDB's own API, not a typo here. */
@Serializable
data class TmdbMovieAlternativeTitles(
    val titles: List<TmdbAlternativeTitleEntry> = emptyList(),
) {
    fun toAlternativeTitles(): List<AlternativeTitle> = titles.toAlternativeTitles()
}

@Serializable
data class TmdbTvAlternativeTitles(
    val results: List<TmdbAlternativeTitleEntry> = emptyList(),
) {
    fun toAlternativeTitles(): List<AlternativeTitle> = results.toAlternativeTitles()
}

private fun List<TmdbAlternativeTitleEntry>.toAlternativeTitles(): List<AlternativeTitle> =
    map { AlternativeTitle(it.isoCode, it.title, it.type.takeIf(String::isNotBlank)) }

@Serializable
data class TmdbMovieDetails(
    @SerialName("original_title") val originalTitle: String,
    val title: String,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TmdbProductionCountry> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val credits: TmdbCredits? = null,
    @SerialName("alternative_titles") val alternativeTitles: TmdbMovieAlternativeTitles? = null,
) {
    val year: Int? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    val director: String? get() = credits?.crew?.firstOrNull { it.job == "Director" }?.name

    fun toMetadata(tmdbId: Int, fetchedAt: Instant = Clock.System.now()) = TmdbMovieMetadata(
        tmdbId = tmdbId.toString(),
        originalTitle = originalTitle,
        alternativeTitles = alternativeTitles?.toAlternativeTitles().orEmpty(),
        year = year,
        director = director,
        runtimeMinutes = runtime,
        genre = genres.map { it.name },
        originCountry = originCountry,
        productionCountries = productionCountries.map { it.name },
        tmdbRating = voteAverage?.toRatingScale(),
        metadataFetchedAt = fetchedAt,
    )
}

@Serializable
data class TmdbCreator(
    val name: String,
)

@Serializable
data class TmdbSeasonSummary(
    @SerialName("season_number") val seasonNumber: Int,
)

@Serializable
data class TmdbTvDetails(
    @SerialName("original_name") val originalName: String,
    val name: String,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TmdbProductionCountry> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("created_by") val createdBy: List<TmdbCreator> = emptyList(),
    @SerialName("alternative_titles") val alternativeTitles: TmdbTvAlternativeTitles? = null,
    val seasons: List<TmdbSeasonSummary> = emptyList(),
) {
    val year: Int? get() = firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    val creator: String? get() = createdBy.firstOrNull()?.name

    fun toMetadata(tmdbId: Int, fetchedAt: Instant = Clock.System.now()) = TmdbSeriesMetadata(
        tmdbId = tmdbId.toString(),
        originalTitle = originalName,
        alternativeTitles = alternativeTitles?.toAlternativeTitles().orEmpty(),
        year = year,
        creator = creator,
        genre = genres.map { it.name },
        originCountry = originCountry,
        productionCountries = productionCountries.map { it.name },
        tmdbRating = voteAverage?.toRatingScale(),
        metadataFetchedAt = fetchedAt,
    )
}

@Serializable
data class TmdbEpisodeDetails(
    val name: String,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("air_date") val airDate: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val crew: List<TmdbCrewMember> = emptyList(),
) {
    val director: String? get() = crew.firstOrNull { it.job == "Director" }?.name

    fun toMetadata(fetchedAt: Instant = Clock.System.now()) = TmdbEpisodeMetadata(
        airDate = airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        overview = overview,
        runtimeMinutes = runtime,
        director = director,
        tmdbRating = voteAverage?.toRatingScale(),
        metadataFetchedAt = fetchedAt,
    )
}

/** `/tv/{id}/season/{n}` -- the full episode list for one season, used to bulk-import a series' entire catalog
 * rather than looking up one already-known episode at a time (see [TmdbClient.getSeasonDetails]). */
@Serializable
data class TmdbSeasonDetails(
    @SerialName("season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisodeDetails> = emptyList(),
)

private const val BASE_URL = "https://api.themoviedb.org/3"

class TmdbClient(private val accessToken: String) {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun findByImdbId(imdbId: String): TmdbMovieSummary? = find(imdbId).movieResults.firstOrNull()

    suspend fun findTvByImdbId(imdbId: String): TmdbTvSummary? = find(imdbId).tvResults.firstOrNull()

    suspend fun getMovieDetails(tmdbId: Int): TmdbMovieDetails =
        http.get("$BASE_URL/movie/$tmdbId") {
            authorized()
            parameter("append_to_response", "credits,external_ids,alternative_titles")
        }.body()

    suspend fun getTvDetails(tmdbId: Int): TmdbTvDetails =
        http.get("$BASE_URL/tv/$tmdbId") {
            authorized()
            parameter("append_to_response", "alternative_titles")
        }.body()

    suspend fun getEpisodeDetails(tvId: Int, seasonNumber: Int, episodeNumber: Int): TmdbEpisodeDetails =
        http.get("$BASE_URL/tv/$tvId/season/$seasonNumber/episode/$episodeNumber") { authorized() }.body()

    suspend fun getSeasonDetails(tvId: Int, seasonNumber: Int): TmdbSeasonDetails =
        http.get("$BASE_URL/tv/$tvId/season/$seasonNumber") { authorized() }.body()

    private suspend fun find(imdbId: String): TmdbFindResponse =
        http.get("$BASE_URL/find/$imdbId") {
            authorized()
            parameter("external_source", "imdb_id")
        }.body()

    private fun HttpRequestBuilder.authorized() {
        header("Authorization", "Bearer $accessToken")
    }
}
