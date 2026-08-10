package br.com.gabryel.movieclub.service.csvimport

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReserveCsvParserTest {
    @Test
    fun `positional columns map to the right category and display name`() {
        val csv =
            """
            Movies,,Series,
            Person A,Person B,Person A,Person B
            Anne+,Deception,Dark,Game of Thrones
            """.trimIndent()

        val rows = ReserveCsvParser.parse(csv.byteInputStream())

        assertEquals(
            listOf(
                ReserveCsvRow(WatchlistCategory.MOVIE, "Person A", "Anne+"),
                ReserveCsvRow(WatchlistCategory.MOVIE, "Person B", "Deception"),
                ReserveCsvRow(WatchlistCategory.SERIES, "Person A", "Dark"),
                ReserveCsvRow(WatchlistCategory.SERIES, "Person B", "Game of Thrones"),
            ),
            rows,
        )
    }

    @Test
    fun `ragged rows are fine -- a blank cell in a shorter column is simply no entry`() {
        val csv =
            """
            Movies,,Series,
            Person A,Person B,Person A,Person B
            Anne+,,Dark,
            Wolf Walkers,Erin Brockovich,,
            """.trimIndent()

        val rows = ReserveCsvParser.parse(csv.byteInputStream())

        assertEquals(
            listOf(
                ReserveCsvRow(WatchlistCategory.MOVIE, "Person A", "Anne+"),
                ReserveCsvRow(WatchlistCategory.SERIES, "Person A", "Dark"),
                ReserveCsvRow(WatchlistCategory.MOVIE, "Person A", "Wolf Walkers"),
                ReserveCsvRow(WatchlistCategory.MOVIE, "Person B", "Erin Brockovich"),
            ),
            rows,
        )
    }

    @Test
    fun `titles are trimmed of stray whitespace`() {
        val csv =
            """
            Movies,,Series,
            Person A,Person B,Person A,Person B
            Wolf Walkers ,Reservoir dogs ,,
            """.trimIndent()

        val rows = ReserveCsvParser.parse(csv.byteInputStream())

        assertEquals("Wolf Walkers", rows[0].title)
        assertEquals("Reservoir dogs", rows[1].title)
    }
}
