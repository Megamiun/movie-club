package br.com.gabryel.movieclub.service.csvimport

import kotlinx.datetime.LocalDate
import org.apache.commons.csv.CSVFormat
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvParsingTest {
    @Test
    fun `getOrEmpty returns empty for a trailing column this row is too short to have, instead of throwing`() {
        // "A,Twin Peaks" is short one field -- no trailing "IMDB Id" value at all, not even a blank one. A
        // spreadsheet trims trailing empty cells on save, so this is the shape real ragged rows actually take.
        val csv = "Choice,Movie,IMDB Id\nA,Twin Peaks\n"
        val record = CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(StringReader(csv))
            .records
            .single()

        assertEquals("", record.getOrEmpty("IMDB Id"))
    }

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
            "Person A's Rating",
            "Person A - Liked?",
            "Person B's Rating",
            "Person B - Liked?",
            "Year",
        )

        val pairs = detectRatingColumnPairs(header)

        assertEquals(
            listOf(
                RatingColumnPair("Person A", "Person A's Rating", "Person A - Liked?"),
                RatingColumnPair("Person B", "Person B's Rating", "Person B - Liked?"),
            ),
            pairs,
        )
    }

    @Test
    fun `detectRatingColumnPairs ignores an unpaired rating column`() {
        val header = listOf("Choice", "Movie", "Person A's Rating")

        assertEquals(emptyList(), detectRatingColumnPairs(header))
    }
}
