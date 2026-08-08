package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeriesCsvParserTest {
    private val header = "Choice,Movie,When?,Person A's Rating,Person A - Liked?,Person B's Rating,Person B - Liked?"

    @Test
    fun `parses a series with two seasons and episodes`() {
        val csv =
            """
            $header
            A,Twin Peaks,,Muito bom,Gostei!,,
            ,Season 1,,Muito bom,Gostei!,,
            1,Northwest Passage,06/09/2025,,,,
            2,Traces to Nowhere,,,,,
            ,,,,,,
            ,Season 2,,Bom,Gostei!,,
            1,May the Giant Be with You,13/09/2025,,,,
            """.trimIndent()

        val blocks = SeriesCsvParser.parse(csv.byteInputStream())

        val series = blocks.single()

        assertEquals("Twin Peaks", series.header.title)
        assertEquals("A", series.header.choiceInitial)
        assertEquals(2, series.seasons.size)
        assertEquals(1, series.seasons[0].header.number)
        assertEquals(2, series.seasons[0].episodes.size)
        assertEquals("Northwest Passage", series.seasons[0].episodes[0].title)
        // blank When? on episode 2 inherits episode 1's date
        assertEquals(LocalDate(2025, 9, 6), series.seasons[0].episodes[1].date)
        assertEquals(2, series.seasons[1].header.number)
        assertEquals(1, series.seasons[1].episodes.size)
    }

    @Test
    fun `episode-level ratings are captured when present, unlike series that never rate episodes`() {
        val csv =
            """
            $header
            B,The Peripheral,,Muito bom,Gostei!,Excepcional!,Adorei
            1,The Pilot,06/06/2026,Muito bom,Gostei!,Excepcional!,Adorei
            """.trimIndent()

        val episode = SeriesCsvParser
            .parse(csv.byteInputStream()).single()
            .seasons.single()
            .episodes.single()

        assertEquals(RatingPair("Muito bom", "Gostei!"), episode.ratingsByDisplayName["Person A"])
        assertEquals(RatingPair("Excepcional!", "Adorei"), episode.ratingsByDisplayName["Person B"])
    }

    @Test
    fun `a trailing dangling row with a member initial but no title is skipped, not a broken series`() {
        val csv =
            """
            $header
            A,Cowboy Bebop,,,,,
            1,Asteroid Blues,25/07/2026,,,,
            ,,,,,,
            B,,,,,,
            """.trimIndent()

        val blocks = SeriesCsvParser.parse(csv.byteInputStream())

        assertEquals(1, blocks.size)
        assertEquals("Cowboy Bebop", blocks.single().header.title)
    }

    @Test
    fun `imdbId is read by header name and is null when the column does not exist yet`() {
        val withoutColumn = SeriesCsvParser.parse("$header\nA,Twin Peaks,,,,,".byteInputStream()).single()
        assertNull(withoutColumn.header.imdbId)

        val withColumn = SeriesCsvParser.parse(
            "$header,IMDB Id\nA,Twin Peaks,,,,,,tt0098936".byteInputStream(),
        ).single()
        assertEquals("tt0098936", withColumn.header.imdbId)
    }
}
