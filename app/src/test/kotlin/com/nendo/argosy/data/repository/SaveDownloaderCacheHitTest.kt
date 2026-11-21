package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SavePathResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class SaveDownloaderCacheHitTest {
    private val gameId = 7L
    private val serverSaveId = 42L
    private val cachedId = 900L
    private val savePath = "/saves/test.sav"
    private val serverHash = "server-hash"

    private val api: RomMApi = mockk(relaxed = true)
    private val apiClient: SaveSyncApiClient = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val saveCacheDao: com.nendo.argosy.data.local.dao.SaveCacheDao = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val activeSaveRepository: ActiveSaveRepository = mockk(relaxed = true)
    private val fal: FileAccessLayer = mockk(relaxed = true)
    private val savePathResolver: SavePathResolver = mockk(relaxed = true)

    private val downloader = SaveDownloader(
        context = mockk(relaxed = true),
        saveSyncDao = saveSyncDao,
        saveCacheDao = saveCacheDao,
        emulatorResolver = mockk(relaxed = true),
        gameDao = gameDao,
        activeSaveRepository = activeSaveRepository,
        titleDbRepository = mockk(relaxed = true),
        titleIdExtractor = mockk(relaxed = true),
        saveArchiver = mockk(relaxed = true),
        savePathResolver = savePathResolver,
        syncPreferencesRepository = mockk(relaxed = true),
        saveCacheManager = dagger.Lazy { saveCacheManager },
        fal = fal,
        switchSaveHandler = mockk(relaxed = true),
        gciSaveHandler = mockk(relaxed = true),
        apiClient = dagger.Lazy { apiClient },
        saveUploader = dagger.Lazy { mockk(relaxed = true) },
        emulatorSaveConfigRepository = mockk(relaxed = true),
        unitSaveHandler = mockk(relaxed = true),
        saveUnitResolver = mockk(relaxed = true),
        sigilSaveHandler = com.nendo.argosy.data.sync.fixtures.notRoutedSigil()
    )

    private fun cachedRow(isHardcore: Boolean) = SaveCacheEntity(
        id = cachedId,
        gameId = gameId,
        emulatorId = "mgba",
        cachedAt = Instant.EPOCH,
        saveSize = 1,
        cachePath = "x/test.sav",
        contentHash = serverHash,
        isHardcore = isHardcore
    )

    @Before
    fun setup() {
        every { apiClient.getApi() } returns api
        every { apiClient.getDeviceId() } returns "device-abc"
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId,
            platformId = 1L,
            title = "Test Game",
            sortTitle = "test game",
            localPath = "/roms/test.gba",
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED,
            platformSlug = "gba"
        )
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(gameId, "mgba", any(), any()) } returns SaveSyncEntity(
            gameId = gameId,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
            rommSaveId = serverSaveId,
            localSavePath = savePath,
            syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
        )
        every { fal.exists(any()) } returns true
        coEvery { api.getSaveWithDevice(serverSaveId, "device-abc") } returns Response.success(
            RomMSave(
                id = serverSaveId, romId = 100L, userId = 1L, emulator = "mgba",
                fileName = "test.sav", updatedAt = "2026-10-01T00:00:00Z", contentHash = serverHash
            )
        )
        coEvery { saveCacheManager.findCachedByHash(gameId, serverHash) } returns cachedRow(isHardcore = false)
        coEvery { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns true
        coEvery { saveCacheManager.restoreSave(any(), any(), any()) } returns true
    }

    @Test
    fun `cache hit protects the disk, restores, and marks the active save unapplied`() = runTest {
        val result = downloader.downloadSave(gameId, "mgba")

        assertTrue(result is SaveSyncResult.Success)
        coVerify { saveCacheManager.protectBeforeOverwrite(gameId, "mgba", savePath) }
        coVerify { saveCacheManager.restoreSave(cachedId, savePath, "mgba") }
        coVerify { activeSaveRepository.activateCache(gameId, cachedId) }
        coVerify { activeSaveRepository.setActiveSaveApplied(gameId, false) }
    }

    @Test
    fun `the save the server named is fetched even when the sync row still points at an older one`() = runTest {
        val newerId = 43L
        coEvery { api.getSaveWithDevice(newerId, "device-abc") } returns Response.success(
            RomMSave(
                id = newerId, romId = 100L, userId = 1L, emulator = "mgba",
                fileName = "test-newer.sav", updatedAt = "2026-10-02T00:00:00Z", contentHash = "newer-hash"
            )
        )

        downloader.downloadSave(gameId, "mgba", knownServerSaveId = newerId)

        coVerify { api.getSaveWithDevice(newerId, "device-abc") }
        coVerify(exactly = 0) { api.getSaveWithDevice(serverSaveId, any()) }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(cachedId, any(), any()) }
    }

    @Test
    fun `a cached copy from another channel is never restored or activated for a named channel`() = runTest {
        downloader.downloadSave(gameId, "mgba", channelName = "Main Game")

        coVerify(exactly = 0) { saveCacheManager.restoreSave(cachedId, any(), any()) }
        coVerify(exactly = 0) { activeSaveRepository.activateCache(gameId, cachedId) }
    }

    @Test
    fun `cache hit refuses to overwrite when the disk save cannot be protected`() = runTest {
        coEvery { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns false

        val result = downloader.downloadSave(gameId, "mgba")

        assertTrue(result is SaveSyncResult.Error)
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), any(), any()) }
    }

    @Test
    fun `cache hit is skipped when a local hardcore save would be replaced by a casual one`() = runTest {
        coEvery { saveCacheManager.hasHardcoreSave(gameId) } returns true

        downloader.downloadSave(gameId, "mgba")

        coVerify(exactly = 0) { saveCacheManager.restoreSave(cachedId, any(), any()) }
    }

    @Test
    fun `caching a server save already held returns its row without fetching it again`() = runTest {
        coEvery { saveCacheDao.getByGameAndOwner(gameId, any()) } returns listOf(cachedRow(isHardcore = false).copy(rommSaveId = serverSaveId))

        val cacheId = downloader.downloadToCache(serverSaveId, gameId, "autosave", activate = false)

        assertEquals(cachedId, cacheId)
        coVerify(exactly = 0) { api.getSaveWithDevice(any(), any()) }
        coVerify(exactly = 0) { saveCacheManager.cacheServerDownload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `two downloads of one game never run at the same time`() = runTest {
        val inflight = java.util.concurrent.atomic.AtomicInteger(0)
        val maxInflight = java.util.concurrent.atomic.AtomicInteger(0)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { saveCacheManager.restoreSave(any(), any(), any()) } coAnswers {
            maxInflight.updateAndGet { maxOf(it, inflight.incrementAndGet()) }
            gate.await()
            inflight.decrementAndGet()
            true
        }

        val first = launch { downloader.downloadSave(gameId, "mgba") }
        val second = launch { downloader.downloadSave(gameId, "mgba") }
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, maxInflight.get())
    }

    @Test
    fun `cache hit still applies a hardcore cached save over a local hardcore save`() = runTest {
        coEvery { saveCacheManager.hasHardcoreSave(gameId) } returns true
        coEvery { saveCacheManager.findCachedByHash(gameId, serverHash) } returns cachedRow(isHardcore = true)

        downloader.downloadSave(gameId, "mgba")

        coVerify { saveCacheManager.restoreSave(cachedId, savePath, "mgba") }
    }
}
