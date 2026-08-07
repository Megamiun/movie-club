package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoviesCsvParserTest {
    @Test
    fun `blank When cell inherits the nearest preceding non-blank date`() {
        val csv =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,IMDB Id
            G,John Wick,23/02/2025,Bom,Gostei!,Bom,Gostei!,2014,101 min,Chad Stahelski,7.5,"Action, Crime, Thriller",United States,tt2911666
            G,John Wick: Chapter 2,,Muito bom,Gostei!,Regular,Desgostei,2017,122 min,Chad Stahelski,7.4,"Action, Crime, Thriller","United States, Italy",tt4425200
            """.trimIndent()

        val rows = MoviesCsvParser.parse(csv.byteInputStream())

        assertEquals(LocalDate(2025, 2, 23), rows[0].date)
        assertEquals(LocalDate(2025, 2, 23), rows[1].date)
    }

    @Test
    fun `watch link column is absent in the 2025 shape without breaking the parse`() {
        val csv =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,IMDB Id
            C,Requiem for a Dream,05/01/2025,Muito bom,Gostei!,Muito bom,Gostei!,2000,102 min,Darren Aronofsky,8.3,Drama,United States,tt0180093
            """.trimIndent()

        val rows = MoviesCsvParser.parse(csv.byteInputStream())

        assertNull(rows.single().watchLink)
        assertEquals("tt0180093", rows.single().imdbId)
    }

    @Test
    fun `watch link resolves from either Link or Where to watch column name`() {
        val link2026 =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,Link,IMDB Id
            G,Exorcist,17/01/2026,Bom,Gostei!,Muito bom,Desgostei,1973,122 min,William Friedkin,8.1,Horror,United States,HBO,tt0070047
            """.trimIndent()
        val whereToWatch2027 =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,Where to watch?,IMDB Id
            C,The Wind Rises,09/01/2027,,,,,2013,126 min,Hayao Miyazaki,7.8,"Animation, Biography, Drama",Japan,https://www.youtube.com/watch?v=x,tt2013293
            """.trimIndent()

        assertEquals("HBO", MoviesCsvParser.parse(link2026.byteInputStream()).single().watchLink)
        assertEquals(
            "https://www.youtube.com/watch?v=x",
            MoviesCsvParser.parse(whereToWatch2027.byteInputStream()).single().watchLink,
        )
    }

    @Test
    fun `a row with no IMDB id still parses with a null imdbId, not an exception`() {
        val csv =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,Where to watch?,IMDB Id
            C,The Tale of the Princess Kaguya,23/01/2027,,,,,,,,,,,,
            """.trimIndent()

        val row = MoviesCsvParser.parse(csv.byteInputStream()).single()

        assertNull(row.imdbId)
    }

    @Test
    fun `a bare skeleton row with no movie still parses as an empty-slot row, not skipped by the parser`() {
        val csv =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,Where to watch?,IMDB Id
            G,,02/01/2027,,,,,,,,,,,,
            """.trimIndent()

        val row = MoviesCsvParser.parse(csv.byteInputStream()).single()

        assertNull(row.imdbId)
        assertEquals(LocalDate(2027, 1, 2), row.date)
    }

    @Test
    fun `ratings are keyed by display name, independent for quality and sentiment`() {
        val csv =
            """
            Choice,Movie,When?,Gabryel's Rating,Gabryel - Liked?,Camila's Rating,Camila - Liked?,Year,Duration,Director,IMDB Rating,Genre,Country,IMDB Id
            C,Dogville,24/01/2026,Muito bom,Gostei!,Horrível,Detestei,2003,171 min,Lars von Trier,8.0,"Crime, Drama",Denmark,tt0276919
            """.trimIndent()

        val row = MoviesCsvParser.parse(csv.byteInputStream()).single()

        assertEquals(RatingPair("Muito bom", "Gostei!"), row.ratingsByDisplayName["Gabryel"])
        assertEquals(RatingPair("Horrível", "Detestei"), row.ratingsByDisplayName["Camila"])
    }

    @Test
    fun `real sample files parse end-to-end without throwing`() {
        for (year in listOf(2025, 2026, 2027)) {
            val file = File("../samples/Movie Club - Movies $year.csv")
            assertTrue(file.exists(), "expected fixture at ${file.absolutePath}")
            val rows = MoviesCsvParser.parse(file.inputStream())
            assertTrue(rows.isNotEmpty(), "$year should have parsed at least one row")
        }
    }
}
