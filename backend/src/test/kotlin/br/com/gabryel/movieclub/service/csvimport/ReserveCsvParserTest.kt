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
            Gabryel,Camila,Gabryel,Camila
            Anne+,Deception,Dark,Game of Thrones
            """.trimIndent()

        val rows = ReserveCsvParser.parse(csv.byteInputStream())

        assertEquals(
            listOf(
                ReserveCsvRow(WatchlistCategory.MOVIE, "Gabryel", "Anne+"),
                ReserveCsvRow(WatchlistCategory.MOVIE, "Camila", "Deception"),
                ReserveCsvRow(WatchlistCategory.SERIES, "Gabryel", "Dark"),
                ReserveCsvRow(WatchlistCategory.SERIES, "Camila", "Game of Thrones"),
            ),
            rows,
        )
    }

    @Test
    fun `ragged rows are fine -- a blank cell in a shorter column is simply no entry`() {
        val csv =
            """
            Movies,,Series,
            Gabryel,Camila,Gabryel,Camila
            Anne+,,Dark,
            Wolf Walkers,Erin Brockovich,,
            """.trimIndent()

        val rows = ReserveCsvParser.parse(csv.byteInputStream())

        assertEquals(
            listOf(
                ReserveCsvRow(WatchlistCategory.MOVIE, "Gabryel", "Anne+"),
                ReserveCsvRow(WatchlistCategory.SERIES, "Gabryel", "Dark"),
                ReserveCsvRow(WatchlistCategory.MOVIE, "Gabryel", "Wolf Walkers"),
                ReserveCsvRow(WatchlistCategory.MOVIE, "Camila", "Erin Brockovich"),
            ),
            rows,
        )
    }

    @Test
    fun `titles are trimmed of stray whitespace`() {
        val csv =
            """
            Movies,,Series,
            Gabryel,Camila,Gabryel,Camila
            Wolf Walkers ,Reservoir dogs ,,
            """.trimIndent()

        val rows = ReserveCsvParser.parse(csv.byteInputStream())

        assertEquals("Wolf Walkers", rows[0].title)
        assertEquals("Reservoir dogs", rows[1].title)
    }

    @Test
    fun `real sample file parses end-to-end without throwing`() {
        val file = File("../samples/Movie Club - Reserve.csv")
        assertTrue(file.exists(), "expected fixture at ${file.absolutePath}")

        val rows = ReserveCsvParser.parse(file.inputStream())

        assertTrue(rows.isNotEmpty())
        assertTrue(rows.any { it.category == WatchlistCategory.MOVIE && it.csvDisplayName == "Gabryel" })
        assertTrue(rows.any { it.category == WatchlistCategory.SERIES && it.csvDisplayName == "Camila" })
    }
}
