package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedSaveEntry.Source
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * #18 consistency fix: restoring a SERVER-only save must also create a local cache entry (so the
 * unified view sees it as BOTH afterwards, not SERVER-only) while the layout-aware live restore
 * (downloadSaveById) is preserved.
 */
class RestoreCachedSaveServerCachingTest {
    private val gameId = 7L
    private val serverSaveId = 42L

    private lateinit var saveCacheManager: SaveCacheManager
    private lateinit var saveSyncRepository: SaveSyncRepository
    private lateinit var gameDao: GameDao
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var useCase: RestoreCachedSaveUseCase

    private val game = GameEntity(
        id = gameId,
        platformId = 1L,
        title = "Test Game",
        sortTitle = "test game",
        localPath = "/roms/test.gba",
        rommId = 100L,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    private fun serverEntry() = UnifiedSaveEntry(
        serverSaveId = serverSaveId,
        timestamp = Instant.now(),
        size = 100,
        channelName = null,
        source = Source.SERVER
    )

    @Before
    fun setup() {
        saveCacheManager = mockk(relaxed = true)
        saveSyncRepository = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        emulatorResolver = mockk(relaxed = true)
        useCase = RestoreCachedSaveUseCase(saveCacheManager, saveSyncRepository, gameDao, emulatorResolver)

        coEvery { gameDao.getById(gameId) } returns game
        coEvery {
            saveSyncRepository.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any())
        } returns "/saves/test.srm"
        coEvery { saveSyncRepository.clearSavesForTitle(any(), any(), any()) } returns true
    }

    @Test
    fun `successful server restore also caches the save`() = runTest {
        coEvery {
            saveSyncRepository.downloadSaveById(any(), any(), any(), any(), any(), any())
        } returns true

        useCase(serverEntry(), gameId, emulatorId = "vbam", syncToServer = false)

        coVerify(exactly = 1) { saveSyncRepository.downloadAndCacheSave(serverSaveId, gameId, null) }
    }

    @Test
    fun `failed server download does not cache`() = runTest {
        coEvery {
            saveSyncRepository.downloadSaveById(any(), any(), any(), any(), any(), any())
        } returns false

        useCase(serverEntry(), gameId, emulatorId = "vbam", syncToServer = false)

        coVerify(exactly = 0) { saveSyncRepository.downloadAndCacheSave(any(), any(), any()) }
    }
}
