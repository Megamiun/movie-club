package br.com.gabryel.movieclub.service.csvimport

import org.apache.commons.csv.CSVFormat
import java.io.InputStream
import java.io.InputStreamReader

data class CommentsCsvRow(
    val rowNumber: Int,
    val movieTitle: String,
    val commentsByDisplayName: Map<String, String>,
)

/**
 * Parses a `Movie Club - Movies <year> - Comments.csv` file -- a "Movie" title column followed by one column per
 * member, each holding that member's free-text comment for that movie (blank when they didn't leave one). Unlike
 * [MoviesCsvParser], there's no IMDB id or `Choice` column at all: a row is matched to an already-imported movie
 * pick purely by title (fuzzy, same [br.com.gabryel.movieclub.service.episodeTitleSimilarity] this codebase
 * already uses to match a CSV's informal title against TMDB's own -- see
 * [ImportService.importComments]), and every member column is a bare display name directly, not the
 * "<Name>'s Rating" suffix convention [detectRatingColumnPairs] looks for (this file only ever carries comments,
 * never a quality/sentiment pair).
 */
object CommentsCsvParser {
    private const val TITLE_COLUMN = "Movie"

    fun parse(input: InputStream): List<CommentsCsvRow> {
        val parser = CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(InputStreamReader(input, Charsets.UTF_8))

        val memberColumns = parser.headerNames.filterNot { it == TITLE_COLUMN }

        return parser.records.mapNotNull { record ->
            val title = naToNull(record.getOrEmpty(TITLE_COLUMN)) ?: return@mapNotNull null
            CommentsCsvRow(
                rowNumber = record.recordNumber.toInt(),
                movieTitle = title,
                commentsByDisplayName = memberColumns.mapNotNull { column ->
                    naToNull(record.getOrEmpty(column))?.let { column to it }
                }.toMap(),
            )
        }
    }
}
