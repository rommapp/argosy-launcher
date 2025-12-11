package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.RomMRom
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadGameUseCaseTest {

    private lateinit var gameDao: GameDao
    private lateinit var gameDiscDao: GameDiscDao
    private lateinit var romMRepository: RomMRepository
    private lateinit var downloadManager: DownloadManager
    private lateinit var useCase: DownloadGameUseCase

    @Before
    fun setup() {
        gameDao = mockk(relaxed = true)
        gameDiscDao = mockk(relaxed = true)
        romMRepository = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        useCase = DownloadGameUseCase(gameDao, gameDiscDao, romMRepository, downloadManager)
    }

    @Test
    fun `invoke returns error when game not found`() = runTest {
        coEvery { gameDao.getById(any()) } returns null

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Game not found", (result as DownloadResult.Error).message)
    }

    @Test
    fun `invoke returns error when rommId is null`() = runTest {
        val game = createGameEntity(rommId = null)
        coEvery { gameDao.getById(123L) } returns game

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Game not synced from RomM", (result as DownloadResult.Error).message)
    }

    @Test
    fun `invoke returns error when getRom fails`() = runTest {
        val game = createGameEntity(rommId = 456L)
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Error("Network error")

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Failed to get ROM info: Network error", (result as DownloadResult.Error).message)
    }

    @Test
    fun `invoke returns error for invalid file extensions`() = runTest {
        val game = createGameEntity(rommId = 456L)
        val rom = createRom(fileName = "game.png")
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Invalid ROM file type: .png", (result as DownloadResult.Error).message)
    }

    @Test
    fun `invoke returns error for html file type`() = runTest {
        val game = createGameEntity(rommId = 456L)
        val rom = createRom(fileName = "game.html")
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals("Invalid ROM file type: .html", (result as DownloadResult.Error).message)
    }

    @Test
    fun `invoke enqueues download successfully`() = runTest {
        val game = createGameEntity(rommId = 456L, title = "Test Game", coverPath = "/cover.jpg")
        val rom = createRom(fileName = "game.nes", platformSlug = "nes", fileSize = 1024L)
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Queued)
        coVerify {
            downloadManager.enqueueDownload(
                gameId = 123L,
                rommId = 456L,
                fileName = "game.nes",
                gameTitle = "Test Game",
                platformSlug = "nes",
                coverPath = "/cover.jpg",
                expectedSizeBytes = 1024L
            )
        }
    }

    @Test
    fun `invoke uses fallback filename when fileName is null`() = runTest {
        val game = createGameEntity(rommId = 456L, title = "My Game")
        val rom = createRom(fileName = null)
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Queued)
        coVerify {
            downloadManager.enqueueDownload(
                gameId = 123L,
                rommId = 456L,
                fileName = "My Game.rom",
                gameTitle = any(),
                platformSlug = any(),
                coverPath = any(),
                expectedSizeBytes = any()
            )
        }
    }

    @Test
    fun `invoke accepts valid rom extensions`() = runTest {
        val validExtensions = listOf("nes", "smc", "z64", "iso", "gba", "nds", "3ds")

        for (ext in validExtensions) {
            val game = createGameEntity(rommId = 456L)
            val rom = createRom(fileName = "game.$ext")
            coEvery { gameDao.getById(123L) } returns game
            coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

            val result = useCase(123L)
            assertTrue("Expected Queued for .$ext but got $result", result is DownloadResult.Queued)
        }
    }

    private fun createGameEntity(
        id: Long = 123L,
        rommId: Long? = 456L,
        title: String = "Test Game",
        coverPath: String? = null
    ) = GameEntity(
        id = id,
        platformId = "nes",
        title = title,
        sortTitle = title.lowercase(),
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = GameSource.ROMM_SYNCED,
        coverPath = coverPath
    )

    private fun createRom(
        fileName: String? = "game.nes",
        platformSlug: String = "nes",
        fileSize: Long = 1024L
    ) = RomMRom(
        id = 456L,
        platformId = 1L,
        platformSlug = platformSlug,
        platformName = "Nintendo Entertainment System",
        name = "Test Game",
        slug = "test-game",
        fileName = fileName,
        fileSize = fileSize,
        filePath = "/roms/nes/game.nes",
        igdbId = null,
        mobyId = null,
        summary = null,
        metadatum = null,
        launchboxMetadata = null,
        coverSmall = null,
        coverLarge = null,
        coverUrl = null,
        regions = null,
        languages = null,
        revision = null,
        screenshotPaths = null
    )
}
