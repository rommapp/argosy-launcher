package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.RomMRom
import com.nendo.argosy.data.remote.romm.RomMRomFile
import com.nendo.argosy.data.repository.GameRepository
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
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var downloadQueueDao: DownloadQueueDao
    private lateinit var gameRepository: GameRepository
    private lateinit var gameFileDao: GameFileDao
    private lateinit var useCase: DownloadGameUseCase

    @Before
    fun setup() {
        gameDao = mockk(relaxed = true)
        gameDiscDao = mockk(relaxed = true)
        romMRepository = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        downloadQueueDao = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        useCase = DownloadGameUseCase(gameDao, gameDiscDao, gameFileDao, romMRepository, downloadManager, emulatorConfigDao, downloadQueueDao, gameRepository, mockk(relaxed = true))
    }

    @Test
    fun `sibling disc and file extraction failures do not prompt base recovery`() = runTest {
        coEvery { gameDao.getById(123L) } returns createGameEntity()
        coEvery { romMRepository.getRom(any()) } returns RomMResult.Success(createRom(fileName = "game.nes"))
        val failed = failedExtractionEntry()
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns listOf(
            failed.copy(discId = 1L), failed.copy(id = 2L, gameFileId = 2L)
        )

        assertEquals(DownloadResult.Queued, useCase(123L))
        coVerify(exactly = 0) { downloadManager.getDownloadPath(any(), any(), any()) }
    }

    @Test
    fun `ambiguous base extraction failures do not prompt recovery`() = runTest {
        coEvery { gameDao.getById(123L) } returns createGameEntity()
        coEvery { romMRepository.getRom(any()) } returns RomMResult.Success(createRom(fileName = "game.nes"))
        val failed = failedExtractionEntry()
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns listOf(failed, failed.copy(id = 2L))

        assertEquals(DownloadResult.Queued, useCase(123L))
        coVerify(exactly = 0) { downloadManager.getDownloadPath(any(), any(), any()) }
    }

    private fun failedExtractionEntry() = DownloadQueueEntity(
        id = 1L, gameId = 123L, rommId = 456L, fileName = "game.zip", gameTitle = "Game",
        platformSlug = "nes", coverPath = null, bytesDownloaded = 1L, totalBytes = 1L,
        state = "FAILED", errorReason = "EXTRACTION_FAILED:bad archive", tempFilePath = null
    )

    @Test
    fun `a folder disc downloads only its own file`() = runTest {
        givenFolderDiscGame()

        assertTrue(useCase(123L) is DownloadResult.MultiDiscQueued)

        coVerify(exactly = 1) { downloadManager.enqueueGameFileDownload(gameId = 123L, gameFileId = 11L, rommFileId = 7702L, fileName = DISC_1, category = "game", gameTitle = any(), platformSlug = "psx", coverPath = any(), expectedSizeBytes = any(), gameFolderName = any()) }
        coVerify(exactly = 1) { downloadManager.enqueueGameFileDownload(gameId = 123L, gameFileId = 12L, rommFileId = 7701L, fileName = DISC_2, category = "game", gameTitle = any(), platformSlug = "psx", coverPath = any(), expectedSizeBytes = any(), gameFolderName = any()) }
        coVerify(exactly = 0) { downloadManager.enqueueDiscDownload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a chosen folder disc is the only one queued`() = runTest {
        givenFolderDiscGame()

        assertTrue(useCase(123L, selectedFileIds = listOf(7701L)) is DownloadResult.MultiDiscQueued)

        coVerify(exactly = 1) { downloadManager.enqueueGameFileDownload(gameId = 123L, gameFileId = 12L, rommFileId = 7701L, fileName = DISC_2, category = any(), gameTitle = any(), platformSlug = any(), coverPath = any(), expectedSizeBytes = any(), gameFolderName = any()) }
        coVerify(exactly = 0) { downloadManager.enqueueGameFileDownload(gameId = any(), gameFileId = 11L, rommFileId = any(), fileName = any(), category = any(), gameTitle = any(), platformSlug = any(), coverPath = any(), expectedSizeBytes = any(), gameFolderName = any()) }
    }

    private fun givenFolderDiscGame() {
        coEvery { gameDao.getById(123L) } returns createGameEntity(platformSlug = "psx").copy(isMultiDisc = true)
        coEvery { gameDiscDao.getDiscsForGame(123L) } returns listOf(
            GameDiscEntity(id = 1L, gameId = 123L, discNumber = 1, rommId = 456L, fileName = DISC_1, localPath = null, fileSize = 10L, parentRommId = 456L),
            GameDiscEntity(id = 2L, gameId = 123L, discNumber = 2, rommId = 456L, fileName = DISC_2, localPath = null, fileSize = 10L, parentRommId = 456L)
        )
        coEvery { gameFileDao.getFilesForGame(123L) } returns listOf(
            GameFileEntity(id = 11L, gameId = 123L, rommFileId = 7702L, fileName = DISC_1, filePath = "roms/psx/Game", category = "game", fileSize = 10L),
            GameFileEntity(id = 12L, gameId = 123L, rommFileId = 7701L, fileName = DISC_2, filePath = "roms/psx/Game", category = "game", fileSize = 10L)
        )
    }

    @Test
    fun `invoke returns error when game not found`() = runTest {
        coEvery { gameDao.getById(any()) } returns null

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals(DownloadGameFailureReason.GameNotFound, (result as DownloadResult.Error).reason)
    }

    @Test
    fun `invoke returns error when rommId is null`() = runTest {
        val game = createGameEntity(rommId = null)
        coEvery { gameDao.getById(123L) } returns game

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals(DownloadGameFailureReason.GameNotSynced, (result as DownloadResult.Error).reason)
    }

    @Test
    fun `invoke returns error when getRom fails`() = runTest {
        val game = createGameEntity(rommId = 456L)
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Error("Network error")

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals(
            DownloadGameFailureReason.RomInfoFetchFailed("Network error"),
            (result as DownloadResult.Error).reason
        )
    }

    @Test
    fun `invoke returns error for invalid file extensions`() = runTest {
        val game = createGameEntity(rommId = 456L)
        val rom = createRom(fileName = "game.png")
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals(
            DownloadGameFailureReason.InvalidFileType("png"),
            (result as DownloadResult.Error).reason
        )
    }

    @Test
    fun `invoke returns error for html file type`() = runTest {
        val game = createGameEntity(rommId = 456L)
        val rom = createRom(fileName = "game.html")
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        val result = useCase(123L)

        assertTrue(result is DownloadResult.Error)
        assertEquals(
            DownloadGameFailureReason.InvalidFileType("html"),
            (result as DownloadResult.Error).reason
        )
    }

    @Test
    fun `invoke enqueues download successfully`() = runTest {
        val game = createGameEntity(rommId = 456L, title = "Test Game")
        val rom = createRom(fileName = "game.nes", platformSlug = "nes", fileSize = 1024L)
        coEvery { gameDao.getById(123L) } returns game
        coEvery { gameRepository.getArt(123L) } returns ResolvedGameArt(coverPath = "/cover.jpg")
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

    @Test
    fun `a folder rom holding one file is downloaded raw`() = runTest {
        val game = createGameEntity(rommId = 456L, title = "Baten Kaitos", platformSlug = "gc")
        val rom = createRom(fileName = "Baten Kaitos", platformSlug = "gc").copy(
            hasNestedSingleFile = true,
            files = listOf(createRomFile(fileName = "Baten Kaitos.iso", filePath = "roms/gc/Baten Kaitos"))
        )
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        assertTrue(useCase(123L) is DownloadResult.Queued)
        coVerify {
            downloadManager.enqueueDownload(
                gameId = 123L,
                rommId = 456L,
                fileName = "Baten Kaitos.iso",
                gameTitle = any(),
                platformSlug = any(),
                coverPath = any(),
                expectedSizeBytes = any(),
                isMultiFileRom = false,
                selectedFileIds = null
            )
        }
    }

    @Test
    fun `a folder rom holding several files is downloaded as a server built zip`() = runTest {
        val game = createGameEntity(rommId = 456L, title = "Skies of Arcadia", platformSlug = "gc")
        val rom = createRom(fileName = "Skies of Arcadia", platformSlug = "gc").copy(
            hasMultipleFiles = true,
            files = listOf(
                createRomFile(id = 1L, fileName = "disc1.iso", filePath = "roms/gc/Skies of Arcadia"),
                createRomFile(id = 2L, fileName = "disc2.iso", filePath = "roms/gc/Skies of Arcadia")
            )
        )
        coEvery { gameDao.getById(123L) } returns game
        coEvery { romMRepository.getRom(456L) } returns RomMResult.Success(rom)

        assertTrue(useCase(123L) is DownloadResult.Queued)
        coVerify {
            downloadManager.enqueueDownload(
                gameId = 123L,
                rommId = 456L,
                fileName = "Skies of Arcadia.zip",
                gameTitle = any(),
                platformSlug = any(),
                coverPath = any(),
                expectedSizeBytes = any(),
                isMultiFileRom = true,
                selectedFileIds = null
            )
        }
    }

    private fun createRomFile(
        id: Long = 1L,
        fileName: String,
        filePath: String
    ) = RomMRomFile(
        id = id,
        romId = 456L,
        fileName = fileName,
        filePath = filePath,
        fileSizeBytes = 2048L,
        fullPath = "$filePath/$fileName"
    )

    private fun createGameEntity(
        id: Long = 123L,
        rommId: Long? = 456L,
        title: String = "Test Game",
        platformSlug: String = "nes"
    ) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = platformSlug,
        title = title,
        sortTitle = title.lowercase(),
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
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

    private companion object {
        const val DISC_1 = "Game (Disc 1).chd"
        const val DISC_2 = "Game (Disc 2).chd"
    }
}
