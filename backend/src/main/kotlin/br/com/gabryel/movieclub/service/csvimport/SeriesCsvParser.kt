package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.InputStream
import java.io.InputStreamReader

data class SeriesHeaderRow(
    val rowNumber: Int,
    val choiceInitial: String,
    val title: String,
    val imdbId: String?,
    val ratingsByDisplayName: Map<String, RatingPair>,
)

data class SeasonHeaderRow(
    val rowNumber: Int,
    val number: Int,
    val ratingsByDisplayName: Map<String, RatingPair>,
)

data class EpisodeCsvRow(
    val rowNumber: Int,
    val number: Int,
    val title: String?,
    val date: LocalDate?,
    val ratingsByDisplayName: Map<String, RatingPair>,
)

data class StandaloneFilmRow(
    val rowNumber: Int,
    val title: String,
    val date: LocalDate?,
)

data class SeasonBlock(
    val header: SeasonHeaderRow,
    val episodes: List<EpisodeCsvRow>,
)

data class SeriesBlock(
    val header: SeriesHeaderRow,
    val seasons: List<SeasonBlock>,
    val standaloneFilms: List<StandaloneFilmRow>,
)

private val SEASON_TITLE_REGEX = Regex("^Season (\\d+)$")
private val EPISODE_NUMBER_REGEX = Regex("^\\d+$")

/**
 * Parses `Movie Club - Series.csv`: a flat encoding of a 3-level series -> season -> episode hierarchy where the
 * `Choice` cell's content determines the row's meaning: a member initial starts a new series, blank + "Season N"
 * starts a new season within it, a bare number is an episode within the current season, "Film" is a standalone
 * movie tied to the current series (e.g. a companion film), and anything else (blank, or a member initial with a
 * blank title -- a "next pick not yet decided" placeholder) is a separator/no-op, not an error.
 *
 * `IMDB Id` is read by header name like the Movies parser's watch-link column -- it doesn't exist in the file yet,
 * but the user plans to add it, and this parser needs zero changes once it's there.
 */
object SeriesCsvParser {
    fun parse(input: InputStream): List<SeriesBlock> {
        val parser = CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(InputStreamReader(input, Charsets.UTF_8))
        val header = parser.headerNames
        val ratingColumnPairs = detectRatingColumnPairs(header)
        val imdbIdColumn = header.firstOrNull { it == "IMDB Id" }

        val results = mutableListOf<SeriesBlock>()
        var currentSeries: SeriesBuilder? = null
        var currentSeason: SeasonBuilder? = null
        var lastDate: LocalDate? = null

        fun ratingsFor(record: CSVRecord) =
            ratingColumnPairs.associate { pair ->
                pair.displayName to RatingPair(
                    naToNull(record.get(pair.qualityColumn)),
                    naToNull(record.get(pair.sentimentColumn)),
                )
            }

        fun flushSeason() {
            val season = currentSeason ?: return
            currentSeries?.seasons?.add(SeasonBlock(season.header, season.episodes.toList()))
            currentSeason = null
        }

        fun flushSeries() {
            flushSeason()
            val series = currentSeries ?: return
            results.add(SeriesBlock(series.header, series.seasons.toList(), series.standaloneFilms.toList()))
            currentSeries = null
        }

        for (record in parser) {
            val choice = record.get("Choice").trim()
            val title = naToNull(record.get("Movie"))
            val dateCell = record.get("When?")

            when {
                choice.isEmpty() && title != null && SEASON_TITLE_REGEX.matches(title) -> {
                    flushSeason()
                    val number = SEASON_TITLE_REGEX.find(title)!!.groupValues[1].toInt()
                    currentSeason =
                        SeasonBuilder(SeasonHeaderRow(record.recordNumber.toInt(), number, ratingsFor(record)))
                }

                EPISODE_NUMBER_REGEX.matches(choice) -> {
                    // Some series (e.g. The Peripheral, Cowboy Bebop) never have a "Season N" row at all --
                    // their episodes follow the series header directly. Treat that as an implicit season 1
                    // rather than silently dropping the episodes.
                    if (currentSeason == null) {
                        currentSeason = SeasonBuilder(SeasonHeaderRow(record.recordNumber.toInt(), 1, emptyMap()))
                    }
                    val date = parseDdMmYyyyOrNull(dateCell) ?: lastDate
                    lastDate = date ?: lastDate
                    currentSeason?.episodes?.add(
                        EpisodeCsvRow(
                            record.recordNumber.toInt(),
                            choice.toInt(),
                            title,
                            date,
                            ratingsFor(record),
                        ),
                    )
                }

                choice == "Film" -> {
                    if (title != null) {
                        currentSeries?.standaloneFilms?.add(
                            StandaloneFilmRow(
                                record.recordNumber.toInt(),
                                title,
                                parseDdMmYyyyOrNull(dateCell),
                            ),
                        )
                    }
                }

                choice.isNotEmpty() && title != null -> {
                    flushSeries()
                    val imdbId = imdbIdColumn?.let { naToNull(record.getOrEmpty(it)) }
                    currentSeries = SeriesBuilder(
                        SeriesHeaderRow(
                            record.recordNumber.toInt(),
                            choice,
                            title,
                            imdbId,
                            ratingsFor(record),
                        ),
                    )
                }

                else -> Unit // separator row, or a dangling "next pick not decided yet" placeholder -- skip, not an error
            }
        }
        flushSeries()
        return results
    }

    private class SeasonBuilder(
        val header: SeasonHeaderRow,
    ) {
        val episodes = mutableListOf<EpisodeCsvRow>()
    }

    private class SeriesBuilder(
        val header: SeriesHeaderRow,
    ) {
        val seasons = mutableListOf<SeasonBlock>()
        val standaloneFilms = mutableListOf<StandaloneFilmRow>()
    }
}
