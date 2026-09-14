package br.com.gabryel.movieclub.service.csvimport

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsCsvParserTest {
    @Test
    fun `each member column becomes an entry keyed by their display name`() {
        val csv =
            """
            Movie,Gabryel,Camila
            Millenium Actress,Great anime,
            Exorcist,"Scary, but good","Good, but scary"
            """.trimIndent()

        val rows = CommentsCsvParser.parse(csv.byteInputStream())

        assertEquals(
            listOf(
                CommentsCsvRow(1, "Millenium Actress", mapOf("Gabryel" to "Great anime")),
                CommentsCsvRow(2, "Exorcist", mapOf("Gabryel" to "Scary, but good", "Camila" to "Good, but scary")),
            ),
            rows,
        )
    }

    @Test
    fun `a row with no title is dropped entirely`() {
        val csv =
            """
            Movie,Gabryel
            ,Some comment
            Dogville,Liked it
            """.trimIndent()

        val rows = CommentsCsvParser.parse(csv.byteInputStream())

        assertEquals(listOf(CommentsCsvRow(2, "Dogville", mapOf("Gabryel" to "Liked it"))), rows)
    }

    @Test
    fun `a row with every comment blank still parses, just with an empty map`() {
        val csv =
            """
            Movie,Gabryel,Camila
            Eyes Wide Shut,,
            """.trimIndent()

        val rows = CommentsCsvParser.parse(csv.byteInputStream())

        assertEquals(listOf(CommentsCsvRow(1, "Eyes Wide Shut", emptyMap())), rows)
    }
}
