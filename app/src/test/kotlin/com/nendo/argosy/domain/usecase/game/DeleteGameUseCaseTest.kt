package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.TexturePackInstaller
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeleteGameUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a texture pack that could not be removed keeps its saved path when the game is deleted`() = runTest {
        val gameDao = mockk<GameDao>(relaxed = true)
        val gameFileDao = mockk<GameFileDao>(relaxed = true)
        val gameRepository = mockk<GameRepository>(relaxed = true)
        val musicDirectoryManager = mockk<MusicDirectoryManager>(relaxed = true)
        val texturePackInstaller = mockk<TexturePackInstaller>(relaxed = true)
        val platformDir = tempFolder.newFolder("ngc")
        val musicDir = tempFolder.newFolder("music")
        val game = GameEntity(
            id = 1L,
            platformId = 10L,
            platformSlug = "ngc",
            title = "Cars",
            sortTitle = "cars",
            localPath = File(platformDir, "missing.iso").absolutePath,
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED
        )
        coEvery { gameDao.getById(1L) } returns game
        coEvery { gameRepository.getDownloadDirForPlatformId(10L) } returns platformDir
        coEvery { musicDirectoryManager.resolveMusicDir() } returns musicDir
        coEvery { texturePackInstaller.uninstallForGame(game) } returns listOf(85L)

        val useCase = DeleteGameUseCase(
            gameDao = gameDao,
            gameRepository = gameRepository,
            downloadQueueDao = mockk(relaxed = true),
            gameFileDao = gameFileDao,
            gameDiscDao = mockk(relaxed = true),
            saveCacheManager = mockk(relaxed = true),
            stateCacheManager = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            orphanedFileDao = mockk(relaxed = true),
            steamRepository = mockk(relaxed = true),
            payloadCodec = mockk(relaxed = true),
            musicDirectoryManager = musicDirectoryManager,
            attributionRepository = mockk(relaxed = true),
            texturePackInstaller = texturePackInstaller
        )

        assertTrue(useCase(1L))
        coVerify {
            gameFileDao.clearLocalPathsByGameIdExcludingPrefix(1L, musicDir.absolutePath + File.separator, listOf(85L))
        }
    }
}
