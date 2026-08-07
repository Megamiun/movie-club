package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import org.apache.commons.csv.CSVFormat
import java.io.InputStream
import java.io.InputStreamReader

data class RatingPair(
    val qualityLabel: String?,
    val sentimentLabel: String?,
)

data class MovieCsvRow(
    val rowNumber: Int,
    val choiceInitial: String,
    val date: LocalDate?,
    val ratingsByDisplayName: Map<String, RatingPair>,
    val watchLink: String?,
    val imdbId: String?,
)

/**
 * Parses one of the `Movie Club - Movies <year>.csv` files. Descriptive columns (title, year, director, etc.) are
 * TMDB's job -- the best-effort refresh in [br.com.gabryel.movieclub.service.csvimport.ImportService] fetches all
 * of that from the IMDB id, so only the fields TMDB can't supply are parsed here: who chose it, the ratings, and
 * the id itself. The watch-link column drifts by year (absent in 2025, called "Link" in 2026, and "Where to
 * watch?" in 2027) so it's resolved by name, tolerating the column not existing at all. A blank `When?` cell
 * inherits the nearest preceding non-blank date -- this is how consecutive rows collapse into movies sharing one
 * Meeting.
 */
object MoviesCsvParser {
    fun parse(input: InputStream): List<MovieCsvRow> {
        val parser = CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(InputStreamReader(input, Charsets.UTF_8))
        val header = parser.headerNames
        val watchLinkColumn = header.firstOrNull { it == "Link" || it == "Where to watch?" }
        val ratingColumnPairs = detectRatingColumnPairs(header)

        var lastDate: LocalDate? = null
        return parser.records.map { record ->
            val date = parseDdMmYyyyOrNull(record.get("When?")) ?: lastDate
            lastDate = date ?: lastDate

            MovieCsvRow(
                rowNumber = record.recordNumber.toInt(),
                choiceInitial = record.get("Choice").trim(),
                date = date,
                ratingsByDisplayName = ratingColumnPairs.associate { pair ->
                    pair.displayName to RatingPair(
                        naToNull(record.get(pair.qualityColumn)),
                        naToNull(record.get(pair.sentimentColumn)),
                    )
                },
                watchLink = watchLinkColumn?.let { naToNull(record.getOrEmpty(it)) },
                imdbId = parseImdbIdOrNull(record.get("IMDB Id")),
            )
        }
    }
}
