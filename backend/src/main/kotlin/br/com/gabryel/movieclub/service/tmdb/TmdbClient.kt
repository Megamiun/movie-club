package br.com.gabryel.movieclub.service.tmdb

import br.com.gabryel.movieclub.db.repositories.dto.TmdbEpisodeMetadata
import br.com.gabryel.movieclub.db.repositories.dto.TmdbMovieMetadata
import br.com.gabryel.movieclub.db.repositories.dto.TmdbSeriesMetadata
import br.com.gabryel.movieclub.db.repositories.dto.Translation
import br.com.gabryel.movieclub.exception.BadRequestException
import br.com.gabryel.movieclub.exception.UpstreamServiceException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
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
import kotlin.time.Clock
import kotlin.time.Instant

private val IMDB_ID_REGEX = Regex("tt\\d{7,9}")

/** Tolerant of a bare `tt1234567` id or a full IMDB URL; used by every service that adds a movie/series by id. */
fun parseImdbId(input: String): String =
    IMDB_ID_REGEX.find(input)?.value ?: throw BadRequestException("Invalid IMDB id or URL: $input")

/** Expands a TMDB `poster_path` (e.g. `/abc123.jpg`) into a full, directly-loadable image URL at a size suited to
 * list/thumbnail display -- not the full-size original, which is unnecessarily large for that use. */
fun String.toTmdbPosterUrl(): String = "https://image.tmdb.org/t/p/w154$this"

@Serializable
data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbMovieSummary> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbTvSummary> = emptyList(),
    @SerialName("tv_episode_results") val tvEpisodeResults: List<TmdbEpisodeSummary> = emptyList(),
)

@Serializable
data class TmdbMovieSummary(
    val id: Int,
)

@Serializable
data class TmdbTvSummary(
    val id: Int,
)

/** TMDB's own (season, episode) location for an episode resolved by imdb id -- see
 * [TmdbClient.findEpisodeByImdbId]. Deliberately not reused as a stand-in for [TmdbEpisodeDetails]: this only
 * carries enough to address the episode by its true TMDB path, not its full metadata. */
@Serializable
data class TmdbEpisodeSummary(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_number") val episodeNumber: Int,
)

@Serializable
data class TmdbExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
)

/** One page of `/search/movie` results -- used by title search, distinct from [TmdbFindResponse]'s lookup-by-id. */
@Serializable
data class TmdbMovieSearchItem(
    val id: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
) {
    val year: Int? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}

@Serializable
data class TmdbTvSearchItem(
    val id: Int,
    val name: String,
    @SerialName("original_name") val originalName: String,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
) {
    val year: Int? get() = firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}

@Serializable
private data class TmdbMovieSearchResponse(val results: List<TmdbMovieSearchItem> = emptyList())

@Serializable
private data class TmdbTvSearchResponse(val results: List<TmdbTvSearchItem> = emptyList())

@Serializable
data class TmdbGenre(
    val name: String,
)

@Serializable
data class TmdbCrewMember(
    val name: String,
    val job: String,
    val id: Int? = null,
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

/** Movie's `data.title` and TV's `data.name` are the same concept (the translated title) under different keys --
 * a genuine inconsistency in TMDB's own API, not a typo here. Both are optional: many translation entries only
 * cover overview text, with no title override at all. */
@Serializable
data class TmdbTranslationData(
    val title: String? = null,
    val name: String? = null,
)

@Serializable
data class TmdbTranslationEntry(
    @SerialName("iso_3166_1") val countryCode: String,
    @SerialName("iso_639_1") val languageCode: String,
    @SerialName("english_name") val englishName: String,
    val data: TmdbTranslationData,
)

@Serializable
data class TmdbTranslations(
    val translations: List<TmdbTranslationEntry> = emptyList(),
) {
    /** Drops entries with no title override -- resolution only ever needs a language it can show a *title* in,
     * not a fully-translated overview. */
    fun toTranslations(): List<Translation> = translations.mapNotNull { entry ->
        (entry.data.title ?: entry.data.name)?.takeIf(String::isNotBlank)?.let { title ->
            Translation(entry.languageCode, entry.countryCode, entry.englishName, title)
        }
    }
}

@Serializable
data class TmdbMovieDetails(
    @SerialName("original_title") val originalTitle: String,
    val title: String,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TmdbProductionCountry> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    val credits: TmdbCredits? = null,
    val translations: TmdbTranslations? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
) {
    val year: Int? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    val director: String? get() = credits?.crew?.firstOrNull { it.job == "Director" }?.name
    val directorTmdbId: Int? get() = credits?.crew?.firstOrNull { it.job == "Director" }?.id

    fun toMetadata(tmdbId: Int, fetchedAt: Instant = Clock.System.now()) = TmdbMovieMetadata(
        tmdbId = tmdbId.toString(),
        originalTitle = originalTitle,
        originalLanguage = originalLanguage,
        translations = translations?.toTranslations().orEmpty(),
        year = year,
        runtimeMinutes = runtime,
        genre = genres.map { it.name },
        originCountry = originCountry,
        productionCountries = productionCountries.map { it.name },
        metadataFetchedAt = fetchedAt,
    )
}

@Serializable
data class TmdbCreator(
    val name: String,
    val id: Int? = null,
)

@Serializable
data class TmdbSeasonSummary(
    @SerialName("season_number") val seasonNumber: Int,
)

@Serializable
data class TmdbTvDetails(
    @SerialName("original_name") val originalName: String,
    val name: String,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TmdbProductionCountry> = emptyList(),
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("created_by") val createdBy: List<TmdbCreator> = emptyList(),
    val translations: TmdbTranslations? = null,
    val seasons: List<TmdbSeasonSummary> = emptyList(),
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
    val status: String? = null,
) {
    val year: Int? get() = firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
    val creator: String? get() = createdBy.firstOrNull()?.name
    val creatorTmdbId: Int? get() = createdBy.firstOrNull()?.id

    fun toMetadata(tmdbId: Int, fetchedAt: Instant = Clock.System.now()) = TmdbSeriesMetadata(
        tmdbId = tmdbId.toString(),
        originalTitle = originalName,
        originalLanguage = originalLanguage,
        translations = translations?.toTranslations().orEmpty(),
        year = year,
        genre = genres.map { it.name },
        originCountry = originCountry,
        productionCountries = productionCountries.map { it.name },
        status = status,
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
    val crew: List<TmdbCrewMember> = emptyList(),
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
) {
    val director: String? get() = crew.firstOrNull { it.job == "Director" }?.name
    val directorTmdbId: Int? get() = crew.firstOrNull { it.job == "Director" }?.id

    /** Only populated when fetched via [TmdbClient.getEpisodeDetails] (which requests it via
     * `append_to_response`) -- the bulk `/tv/{id}/season/{n}` listing [TmdbClient.getSeasonDetails] uses for
     * CSV/series import doesn't include per-episode external ids, so freshly-imported episodes get this filled in
     * only once something calls [EpisodeService][br.com.gabryel.movieclub.service.EpisodeService.refreshMetadata]
     * on them, same as the rest of an episode's best-effort enrichment. */
    val imdbId: String? get() = externalIds?.imdbId

    fun toMetadata(fetchedAt: Instant = Clock.System.now()) = TmdbEpisodeMetadata(
        airDate = airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        overview = overview,
        runtimeMinutes = runtime,
        imdbId = imdbId,
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

/** [engine] defaults to a real CIO engine but is overridable so tests can substitute a `MockEngine` -- this is the
 * only reason it's a constructor parameter rather than hardcoded, production callers never pass it. */
class TmdbClient(private val accessToken: String, engine: HttpClientEngine = CIO.create()) {
    private val http = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        // Without this, a non-2xx TMDB response (bad/expired API key, rate limit, outage) still gets decoded as if
        // it succeeded -- most of this file's response DTOs default their fields to empty/null for legitimate
        // "TMDB found nothing" cases, so an error body silently decodes into an empty result instead of a
        // deserialization failure, and callers report a confusing "not found" for what was actually e.g. an
        // Unauthorized. expectSuccess=true makes ktor throw on non-2xx instead of decoding the body at all; the
        // validator below turns that into a message that actually names the HTTP status.
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is ResponseException) {
                    val status = cause.response.status
                    throw UpstreamServiceException("TMDB request failed: ${status.value} ${status.description}")
                }
            }
        }
    }

    suspend fun findByImdbId(imdbId: String): TmdbMovieSummary? = find(imdbId).movieResults.firstOrNull()

    suspend fun findTvByImdbId(imdbId: String): TmdbTvSummary? = find(imdbId).tvResults.firstOrNull()

    /** Resolves an episode's own imdb id back to TMDB's *own* (season, episode) numbering -- needed because an
     * episode's stored [br.com.gabryel.movieclub.db.repositories.dto.EpisodeRow.number] can be OMDb's corrected
     * number rather than TMDB's own for shows where the two disagree (see
     * [br.com.gabryel.movieclub.service.SeriesService.importSeasonsAndEpisodes]), so it can't always be trusted
     * as a `/tv/{id}/season/{n}/episode/{m}` path segment directly. */
    suspend fun findEpisodeByImdbId(imdbId: String): TmdbEpisodeSummary? = find(imdbId).tvEpisodeResults.firstOrNull()

    suspend fun getMovieDetails(tmdbId: Int): TmdbMovieDetails =
        http.get("$BASE_URL/movie/$tmdbId") {
            authorized()
            parameter("append_to_response", "credits,external_ids,translations")
        }.body()

    suspend fun getTvDetails(tmdbId: Int): TmdbTvDetails =
        http.get("$BASE_URL/tv/$tmdbId") {
            authorized()
            parameter("append_to_response", "translations,external_ids")
        }.body()

    suspend fun getEpisodeDetails(tvId: Int, seasonNumber: Int, episodeNumber: Int): TmdbEpisodeDetails =
        http.get("$BASE_URL/tv/$tvId/season/$seasonNumber/episode/$episodeNumber") {
            authorized()
            parameter("append_to_response", "external_ids")
        }.body()

    suspend fun getSeasonDetails(tvId: Int, seasonNumber: Int): TmdbSeasonDetails =
        http.get("$BASE_URL/tv/$tvId/season/$seasonNumber") { authorized() }.body()

    /** Resolves a crew/cast member's TMDB person id to their IMDB id (`nm...`) -- used to link a movie/episode's
     * director to their IMDB page, since [TmdbCrewMember] itself only carries the person's TMDB id. */
    suspend fun getPersonExternalIds(personId: Int): TmdbExternalIds =
        http.get("$BASE_URL/person/$personId/external_ids") { authorized() }.body()

    suspend fun searchMovies(query: String): List<TmdbMovieSearchItem> =
        http.get("$BASE_URL/search/movie") {
            authorized()
            parameter("query", query)
        }.body<TmdbMovieSearchResponse>().results

    suspend fun searchTv(query: String): List<TmdbTvSearchItem> =
        http.get("$BASE_URL/search/tv") {
            authorized()
            parameter("query", query)
        }.body<TmdbTvSearchResponse>().results

    private suspend fun find(imdbId: String): TmdbFindResponse =
        http.get("$BASE_URL/find/$imdbId") {
            authorized()
            parameter("external_source", "imdb_id")
        }.body()

    private fun HttpRequestBuilder.authorized() {
        header("Authorization", "Bearer $accessToken")
    }
}
