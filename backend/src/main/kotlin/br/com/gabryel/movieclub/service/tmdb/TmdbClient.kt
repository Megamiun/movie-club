package br.com.gabryel.movieclub.service.tmdb

import br.com.gabryel.movieclub.db.repositories.TmdbEpisodeMetadata
import br.com.gabryel.movieclub.db.repositories.TmdbMovieMetadata
import br.com.gabryel.movieclub.db.repositories.TmdbSeriesMetadata
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
data class TmdbMovieDetails(
    @SerialName("original_title") val originalTitle: String,
    val title: String,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val credits: TmdbCredits? = null,
) {
    val year: Int? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    val director: String? get() = credits?.crew?.firstOrNull { it.job == "Director" }?.name
}

fun TmdbMovieDetails.toMetadata(tmdbId: Int, fetchedAt: Instant = Clock.System.now()): TmdbMovieMetadata =
    TmdbMovieMetadata(
        tmdbId = tmdbId.toString(),
        originalTitle = originalTitle,
        englishTitle = title,
        year = year,
        director = director,
        runtimeMinutes = runtime,
        genre = genres.map { it.name },
        country = originCountry,
        tmdbRating = voteAverage?.toRatingScale(),
        metadataFetchedAt = fetchedAt,
    )

@Serializable
data class TmdbCreator(
    val name: String,
)

@Serializable
data class TmdbTvDetails(
    @SerialName("original_name") val originalName: String,
    val name: String,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("created_by") val createdBy: List<TmdbCreator> = emptyList(),
) {
    val year: Int? get() = firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    val creator: String? get() = createdBy.firstOrNull()?.name
}

fun TmdbTvDetails.toMetadata(tmdbId: Int, fetchedAt: Instant = Clock.System.now()): TmdbSeriesMetadata =
    TmdbSeriesMetadata(
        tmdbId = tmdbId.toString(),
        originalTitle = originalName,
        englishTitle = name,
        year = year,
        creator = creator,
        genre = genres.map { it.name },
        country = originCountry,
        tmdbRating = voteAverage?.toRatingScale(),
        metadataFetchedAt = fetchedAt,
    )

@Serializable
data class TmdbEpisodeDetails(
    val name: String,
    @SerialName("air_date") val airDate: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val crew: List<TmdbCrewMember> = emptyList(),
) {
    val director: String? get() = crew.firstOrNull { it.job == "Director" }?.name
}

fun TmdbEpisodeDetails.toMetadata(fetchedAt: Instant = Clock.System.now()): TmdbEpisodeMetadata =
    TmdbEpisodeMetadata(
        airDate = airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        overview = overview,
        runtimeMinutes = runtime,
        director = director,
        tmdbRating = voteAverage?.toRatingScale(),
        metadataFetchedAt = fetchedAt,
    )

private const val BASE_URL = "https://api.themoviedb.org/3"

class TmdbClient(
    private val accessToken: String,
) {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun findByImdbId(imdbId: String): TmdbMovieSummary? = find(imdbId).movieResults.firstOrNull()

    suspend fun findTvByImdbId(imdbId: String): TmdbTvSummary? = find(imdbId).tvResults.firstOrNull()

    suspend fun getMovieDetails(tmdbId: Int): TmdbMovieDetails =
        http
            .get("$BASE_URL/movie/$tmdbId") {
                authorized()
                parameter("append_to_response", "credits,external_ids")
            }.body()

    suspend fun getTvDetails(tmdbId: Int): TmdbTvDetails =
        http.get("$BASE_URL/tv/$tmdbId") { authorized() }.body()

    suspend fun getEpisodeDetails(tvId: Int, seasonNumber: Int, episodeNumber: Int): TmdbEpisodeDetails =
        http.get("$BASE_URL/tv/$tvId/season/$seasonNumber/episode/$episodeNumber") { authorized() }.body()

    private suspend fun find(imdbId: String): TmdbFindResponse =
        http
            .get("$BASE_URL/find/$imdbId") {
                authorized()
                parameter("external_source", "imdb_id")
            }.body()

    private fun HttpRequestBuilder.authorized() {
        header("Authorization", "Bearer $accessToken")
    }
}
