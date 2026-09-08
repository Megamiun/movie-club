package br.com.gabryel.movieclub.service

import br.com.gabryel.movieclub.db.MediaItemType.EPISODE
import br.com.gabryel.movieclub.db.MediaItemType.MOVIE
import br.com.gabryel.movieclub.db.MediaItemType.SERIES
import br.com.gabryel.movieclub.db.repositories.EpisodeRepository
import br.com.gabryel.movieclub.db.repositories.MediaItemRepository
import br.com.gabryel.movieclub.db.repositories.dto.MediaItemRow
import br.com.gabryel.movieclub.exception.NotFoundException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MediaItemServiceTest {
    private val mediaItemRepository = mockk<MediaItemRepository>()
    private val episodeRepository = mockk<EpisodeRepository>()
    private val movieService = mockk<MovieService>()
    private val seriesService = mockk<SeriesService>()
    private val episodeService = mockk<EpisodeService>()
    private val mediaItemService = MediaItemService(mediaItemRepository, episodeRepository, movieService, seriesService, episodeService)

    @Test
    fun `refreshMetadata throws NotFoundException when the media item doesn't exist`(): Unit = runBlocking {
        val mediaItemId = Uuid.random()
        every { mediaItemRepository.findById(mediaItemId) } returns null

        assertFailsWith<NotFoundException> { mediaItemService.refreshMetadata(mediaItemId) }
    }

    @Test
    fun `refreshMetadata dispatches a MOVIE item to MovieService, by its cached imdbId and tmdbId`(): Unit = runBlocking {
        val mediaItemId = Uuid.random()
        val item = mediaItem(mediaItemId, MOVIE, tmdbId = "278")
        every { mediaItemRepository.findById(mediaItemId) } returns item
        coEvery { movieService.refreshByImdbId(item.imdbId, "278") } returns item

        val result = mediaItemService.refreshMetadata(mediaItemId)

        assertEquals(item, result)
        coVerify { movieService.refreshByImdbId(item.imdbId, "278") }
    }

    @Test
    fun `refreshMetadata dispatches a SERIES item to SeriesService, by its cached imdbId and tmdbId`(): Unit = runBlocking {
        val mediaItemId = Uuid.random()
        val item = mediaItem(mediaItemId, SERIES, tmdbId = "1396")
        every { mediaItemRepository.findById(mediaItemId) } returns item
        coEvery { seriesService.refreshByImdbId(item.imdbId, "1396") } returns item

        mediaItemService.refreshMetadata(mediaItemId)

        coVerify { seriesService.refreshByImdbId(item.imdbId, "1396") }
    }

    @Test
    fun `refreshMetadata resolves an EPISODE item back to its own episode row, unlike Movie or Series`(): Unit = runBlocking {
        val mediaItemId = Uuid.random()
        val episodeId = Uuid.random()
        val item = mediaItem(mediaItemId, EPISODE)
        every { mediaItemRepository.findById(mediaItemId) } returns item
        every { episodeRepository.findIdByMediaItemId(mediaItemId) } returns episodeId
        coEvery { episodeService.refreshCatalogMetadata(episodeId) } returns mockk()

        mediaItemService.refreshMetadata(mediaItemId)

        coVerify { episodeService.refreshCatalogMetadata(episodeId) }
    }

    @Test
    fun `refreshMetadata throws NotFoundException when an EPISODE media item has no episode linked to it`(): Unit = runBlocking {
        val mediaItemId = Uuid.random()
        every { mediaItemRepository.findById(mediaItemId) } returns mediaItem(mediaItemId, EPISODE)
        every { episodeRepository.findIdByMediaItemId(mediaItemId) } returns null

        assertFailsWith<NotFoundException> { mediaItemService.refreshMetadata(mediaItemId) }
    }

    private fun mediaItem(id: Uuid, type: br.com.gabryel.movieclub.db.MediaItemType, tmdbId: String? = null) = MediaItemRow(
        id = id,
        type = type,
        imdbId = "tt0111161",
        tmdbId = tmdbId,
        title = "Some Title",
        createdAt = Clock.System.now(),
    )
}
