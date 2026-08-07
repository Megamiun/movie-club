package br.com.gabryel.movieclub.service.csvimport

import org.apache.commons.csv.CSVFormat
import java.io.InputStream
import java.io.InputStreamReader

enum class WatchlistCategory { MOVIE, SERIES }

data class ReserveCsvRow(
    val category: WatchlistCategory,
    val csvDisplayName: String,
    val title: String,
)

/**
 * Parses `Movie Club - Reserve.csv`. Unlike the other two files, ownership here is by fixed COLUMN POSITION, not
 * a `Choice` cell: a 2-row merged header (`Movies,,Series,` then `Gabryel,Camila,Gabryel,Camila`) defines 4 ragged
 * columns, each just a bare title per line -- no IMDB id, no notes, no date. Blank cells are simply "no entry" for
 * that member at that row, not an error.
 */
object ReserveCsvParser {
    fun parse(input: InputStream): List<ReserveCsvRow> {
        // No header configuration here on purpose: the file's first two rows are a 2-row MERGED header
        // ("Movies,,Series," then "Gabryel,Camila,Gabryel,Camila"), not the single-header-row shape
        // CSVFormat's header support expects. Every row, including those two, is read purely by position.
        val records = CSVFormat.DEFAULT.parse(InputStreamReader(input, Charsets.UTF_8)).records
        require(records.size >= 2) { "Reserve CSV must have a 2-row merged header" }

        val displayNameRow = records[1]
        val columns = (0 until displayNameRow.size()).mapNotNull { index ->
            val displayName = naToNull(displayNameRow.get(index)) ?: return@mapNotNull null
            val category = if (index < 2) WatchlistCategory.MOVIE else WatchlistCategory.SERIES
            index to (category to displayName)
        }

        return records.drop(2).flatMap { record ->
            columns.mapNotNull { (index, categoryAndName) ->
                if (index >= record.size()) return@mapNotNull null
                val title = naToNull(record.get(index))?.trim() ?: return@mapNotNull null
                val (category, displayName) = categoryAndName
                ReserveCsvRow(category, displayName, title)
            }
        }
    }
}
