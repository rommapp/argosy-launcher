package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.ScreenshotUploader
import com.nendo.argosy.data.repository.StateCacheManager
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class SyncCoordinatorQueueRomTargetTest {

    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)
    private val saveCacheDao: SaveCacheDao = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val romM: RomMRepository = mockk(relaxed = true)
    private val syncRepo: SaveSyncRepository = mockk(relaxed = true)
    private val cacheManager: SaveCacheManager = mockk(relaxed = true)
    private val stateManager: StateCacheManager = mockk(relaxed = true)
    private val screenshotUploader: ScreenshotUploader = mockk(relaxed = true)
    private val api: RomMApi = mockk(relaxed = true)
    private val codec = SyncPayloadCodec(Moshi.Builder().build())
    private val tempFiles = mutableListOf<File>()

    private val game = GameEntity(
        id = GAME_ID,
        title = "Blue",
        sortTitle = "blue",
        platformId = 1L,
        platformSlug = "gb",
        rommId = CURRENT_ROM,
        igdbId = null,
        localPath = "/storage/roms/blue.gb",
        source = GameSource.ROMM_SYNCED
    )

    private val state = StateCacheEntity(
        id = STATE_ID,
        gameId = GAME_ID,
        platformSlug = "gb",
        emulatorId = "retroarch",
        slotNumber = 1,
        cachedAt = Instant.EPOCH,
        stateSize = 8L,
        cachePath = "states/1.state"
    )

    @Before
    fun setup() {
        every { romM.connectionState } returns MutableStateFlow(ConnectionState.Connected("4.0.0"))
        every { syncRepo.getApi() } returns api
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(any()) } returns emptyList()
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { stateManager.getStateById(STATE_ID) } returns state
        coEvery { stateManager.uploadStateToRomM(any(), any(), any(), any()) } returns
            StateCacheManager.StateCloudResult.Success
        coEvery { screenshotUploader.upload(any(), any()) } returns ScreenshotUploader.Result.Success
    }

    @After
    fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    @Test
    fun `a state row keyed to another rom stays local and is not uploaded`() = runTest {
        queue(SyncPriority.SAVE_STATE, stateRow(rommId = STALE_ROM))

        coordinator().processQueue()

        coVerify(exactly = 0) { stateManager.uploadStateToRomM(any(), any(), any(), any()) }
        coVerify(exactly = 0) { stateManager.deleteState(any()) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(ROW_ID, any(), any()) }
    }

    @Test
    fun `a state row keyed to the game's rom still uploads to that rom`() = runTest {
        queue(SyncPriority.SAVE_STATE, stateRow(rommId = CURRENT_ROM))

        coordinator().processQueue()

        coVerify(exactly = 1) { stateManager.uploadStateToRomM(state, CURRENT_ROM, "blue", api) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
    }

    @Test
    fun `a state row whose state was detached from the game is not uploaded`() = runTest {
        coEvery { stateManager.getStateById(STATE_ID) } returns state.copy(gameId = -GAME_ID)
        queue(SyncPriority.SAVE_STATE, stateRow(rommId = CURRENT_ROM))

        coordinator().processQueue()

        coVerify(exactly = 0) { stateManager.uploadStateToRomM(any(), any(), any(), any()) }
        coVerify(exactly = 0) { stateManager.deleteState(any()) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
    }

    @Test
    fun `a state row whose game has no rom any more is not uploaded`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game.copy(rommId = null)
        queue(SyncPriority.SAVE_STATE, stateRow(rommId = CURRENT_ROM))

        coordinator().processQueue()

        coVerify(exactly = 0) { stateManager.uploadStateToRomM(any(), any(), any(), any()) }
    }

    @Test
    fun `a pinned save row keyed to another rom is not uploaded to the game's rom`() = runTest {
        pinCache()
        queue(SyncPriority.SAVE_FILE, saveRow(rommId = STALE_ROM))

        coordinator().processQueue()

        coVerify(exactly = 0) {
            syncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { syncRepo.uploadSave(any(), any(), any(), any(), any(), any()) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
    }

    @Test
    fun `a pinned save row keyed to the game's rom still uploads`() = runTest {
        pinCache()
        queue(SyncPriority.SAVE_FILE, saveRow(rommId = CURRENT_ROM))

        coordinator().processQueue()

        coVerify(exactly = 1) {
            syncRepo.uploadCacheEntry(GAME_ID, CURRENT_ROM, any(), "Slot 1", any(), any(), any(), CACHE_ID, any())
        }
    }

    @Test
    fun `a screenshot row keyed to another rom is not uploaded`() = runTest {
        val shot = tempFile(".png")
        queue(SyncPriority.PROPERTY, screenshotRow(rommId = STALE_ROM, path = shot.absolutePath))

        coordinator().processQueue()

        coVerify(exactly = 0) { screenshotUploader.upload(any(), any()) }
        coVerify { pendingSyncQueueDao.deleteById(ROW_ID) }
    }

    @Test
    fun `a screenshot row keyed to the game's rom still uploads`() = runTest {
        val shot = tempFile(".png")
        queue(SyncPriority.PROPERTY, screenshotRow(rommId = CURRENT_ROM, path = shot.absolutePath))

        coordinator().processQueue()

        coVerify(exactly = 1) { screenshotUploader.upload(shot, CURRENT_ROM) }
        assertFalse(shot.exists())
    }

    private fun queue(priority: Int, row: PendingSyncQueueEntity) {
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(priority) } returns listOf(row)
    }

    private fun pinCache() {
        val cache = SaveCacheEntity(
            id = CACHE_ID,
            gameId = GAME_ID,
            emulatorId = "retroarch",
            cachedAt = Instant.EPOCH,
            saveSize = 3L,
            cachePath = "1/save.zip",
            channelName = "Slot 1",
            contentHash = "hash"
        )
        coEvery { saveCacheDao.getById(CACHE_ID) } returns cache
        every { cacheManager.getCacheFile(cache) } returns tempFile(".zip")
        coEvery {
            syncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Success(rommSaveId = 42L)
    }

    private fun tempFile(suffix: String): File =
        File.createTempFile("queue_rom_target", suffix).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            tempFiles += this
        }

    private fun stateRow(rommId: Long) = PendingSyncQueueEntity(
        id = ROW_ID,
        gameId = GAME_ID,
        rommId = rommId,
        syncType = SyncType.SAVE_STATE,
        priority = SyncPriority.SAVE_STATE,
        payloadJson = codec.encode(SaveStatePayload(STATE_ID, "retroarch"))
    )

    private fun saveRow(rommId: Long) = PendingSyncQueueEntity(
        id = ROW_ID,
        gameId = GAME_ID,
        rommId = rommId,
        syncType = SyncType.SAVE_FILE,
        priority = SyncPriority.SAVE_FILE,
        payloadJson = codec.encode(SaveFilePayload(emulatorId = "retroarch", channelName = "Slot 1")),
        cacheId = CACHE_ID
    )

    private fun screenshotRow(rommId: Long, path: String) = PendingSyncQueueEntity(
        id = ROW_ID,
        gameId = GAME_ID,
        rommId = rommId,
        syncType = SyncType.SCREENSHOT,
        priority = SyncPriority.PROPERTY,
        payloadJson = codec.encode(ScreenshotPayload(path))
    )

    private fun coordinator() = SyncCoordinator(
        pendingSyncQueueDao = pendingSyncQueueDao,
        saveCacheDao = saveCacheDao,
        saveSyncDao = mockk(relaxed = true),
        emulatorSaveConfigDao = mockk(relaxed = true),
        gameDao = gameDao,
        activeSaveRepository = mockk(relaxed = true),
        romMRepository = dagger.Lazy { romM },
        saveSyncRepository = dagger.Lazy { syncRepo },
        saveCacheManager = dagger.Lazy { cacheManager },
        stateCacheManager = dagger.Lazy { stateManager },
        syncQueueManager = SyncQueueManager(),
        syncPreferencesRepository = mockk(relaxed = true) {
            every { preferences } returns MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
        },
        payloadCodec = codec,
        savePathResolver = mockk(relaxed = true),
        strategySelector = mockk(relaxed = true),
        pendingConflictDao = mockk(relaxed = true),
        reconcileEffectApplier = mockk(relaxed = true),
        saveRecoveryGate = mockk(relaxed = true),
        screenshotUploads = ScreenshotUploadQueue(
            context = mockk(relaxed = true) { every { filesDir } returns File(System.getProperty("java.io.tmpdir")) },
            pendingSyncQueueDao = pendingSyncQueueDao,
            payloadCodec = codec,
            uploader = screenshotUploader,
            ledger = mockk(relaxed = true),
            notices = mockk(relaxed = true)
        ),
        rommApiProvider = mockk(relaxed = true),
        accountSwitchMarkerStore = mockk(relaxed = true),
        syncStatesOnSessionEndUseCase = mockk(relaxed = true),
        negotiateInventory = mockk(relaxed = true),
        gameArtDao = mockk(relaxed = true),
        snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
    )

    private companion object {
        const val GAME_ID = 1L
        const val ROW_ID = 50L
        const val STATE_ID = 60L
        const val CACHE_ID = 70L
        const val CURRENT_ROM = 100L
        const val STALE_ROM = 200L
    }
}
