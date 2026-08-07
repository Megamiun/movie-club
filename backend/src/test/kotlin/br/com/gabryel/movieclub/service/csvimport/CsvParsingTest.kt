package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvParsingTest {
    @Test
    fun `parseDdMmYyyyOrNull parses a valid date`() {
        assertEquals(LocalDate(2025, 1, 5), parseDdMmYyyyOrNull("05/01/2025"))
    }

    @Test
    fun `parseDdMmYyyyOrNull returns null for blank`() {
        assertNull(parseDdMmYyyyOrNull(""))
    }

    @Test
    fun `parseDdMmYyyyOrNull returns null for a spreadsheet formula error`() {
        assertNull(parseDdMmYyyyOrNull("#VALUE!"))
    }

    @Test
    fun `naToNull maps the literal N-A to null`() {
        assertNull(naToNull("N/A"))
        assertNull(naToNull(""))
        assertNull(naToNull("   "))
        assertEquals("102 min", naToNull("102 min"))
    }

    @Test
    fun `parseImdbIdOrNull extracts a tt id from a bare id or a full url`() {
        assertEquals("tt0180093", parseImdbIdOrNull("tt0180093"))
        assertEquals("tt4857264", parseImdbIdOrNull("https://www.imdb.com/title/tt4857264/"))
        assertNull(parseImdbIdOrNull(""))
    }

    @Test
    fun `detectRatingColumnPairs finds pairs by common display name, not hardcoded members`() {
        val header = listOf(
            "Choice",
            "Movie",
            "When?",
            "Gabryel's Rating",
            "Gabryel - Liked?",
            "Camila's Rating",
            "Camila - Liked?",
            "Year",
        )

        val pairs = detectRatingColumnPairs(header)

        assertEquals(
            listOf(
                RatingColumnPair("Gabryel", "Gabryel's Rating", "Gabryel - Liked?"),
                RatingColumnPair("Camila", "Camila's Rating", "Camila - Liked?"),
            ),
            pairs,
        )
    }

    @Test
    fun `detectRatingColumnPairs ignores an unpaired rating column`() {
        val header = listOf("Choice", "Movie", "Gabryel's Rating")

        assertEquals(emptyList(), detectRatingColumnPairs(header))
    }
}
