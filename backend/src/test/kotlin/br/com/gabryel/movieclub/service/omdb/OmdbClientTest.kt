package br.com.gabryel.movieclub.service.omdb

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull

class OmdbClientTest {
    @Test
    fun `getImdbRating returns null without calling OMDb when no api key is configured`() = runBlocking {
        assertNull(OmdbClient("").getImdbRating("tt0903747"))
    }
}
