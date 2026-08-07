package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import org.apache.commons.csv.CSVRecord

private val IMDB_ID_REGEX = Regex("tt\\d{7,9}")
private val DD_MM_YYYY_REGEX = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")
private val RATING_COLUMN_REGEX = Regex("^(.+)'s Rating$")
private val LIKED_COLUMN_REGEX = Regex("^(.+) - Liked\\?$")

data class RatingColumnPair(
    val displayName: String,
    val qualityColumn: String,
    val sentimentColumn: String,
)

/** Blank / unparseable dates (including the literal `#VALUE!` spreadsheet-formula-error string) become null. */
fun parseDdMmYyyyOrNull(value: String): LocalDate? {
    val match = DD_MM_YYYY_REGEX.find(value.trim()) ?: return null
    val (day, month, year) = match.destructured
    return runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/** The literal string "N/A" (used throughout the sample CSVs for unknown numeric fields) maps to null, like a blank cell. */
fun naToNull(value: String): String? = value.trim().takeUnless { it.isEmpty() || it.equals("N/A", ignoreCase = true) }

/** Tolerant of a bare `tt1234567` id or a full IMDB URL; returns null (not an exception) when nothing matches, since the
 * importer treats a missing id as a skip-and-warn case rather than a fatal error. */
fun parseImdbIdOrNull(value: String): String? = IMDB_ID_REGEX.find(value)?.value

/**
 * Finds "<Name>'s Rating" / "<Name> - Liked?" column pairs by common display-name prefix, in header order.
 * Not hardcoded to "Gabryel"/"Camila" so the importer keeps working if the club's members change.
 */
fun detectRatingColumnPairs(header: List<String>): List<RatingColumnPair> {
    val ratingColumns =
        header.mapNotNull { column ->
            RATING_COLUMN_REGEX
                .find(column)
                ?.groupValues
                ?.get(1)
                ?.let { it to column }
        }
    val likedColumns =
        header
            .mapNotNull { column ->
                LIKED_COLUMN_REGEX
                    .find(column)
                    ?.groupValues
                    ?.get(1)
                    ?.let { it to column }
            }.toMap()
    return ratingColumns.mapNotNull { (displayName, qualityColumn) ->
        likedColumns[displayName]?.let { sentimentColumn ->
            RatingColumnPair(
                displayName,
                qualityColumn,
                sentimentColumn,
            )
        }
    }
}

/** [CSVRecord.get] throws if the column isn't in this file's header; used for columns that drift/are optional across files. */
fun CSVRecord.getOrEmpty(column: String): String = if (isMapped(column)) get(column) else ""
