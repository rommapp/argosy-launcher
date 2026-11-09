package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.ScreenshotUploader
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.strategy.ConflictAutoResolver
import com.nendo.argosy.data.sync.strategy.SaveSyncStrategySelector
import com.squareup.moshi.Moshi
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class SyncCoordinatorProcessQueueTest {

    private val pendingSyncQueueDao = mockk<PendingSyncQueueDao>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val emulatorSaveConfigDao = mockk<EmulatorSaveConfigDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)
    private val saveCacheManager = mockk<SaveCacheManager>(relaxed = true)
    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
    private val syncPreferencesRepository = mockk<SyncPreferencesRepository>(relaxed = true)
    private val strategySelector = mockk<SaveSyncStrategySelector>(relaxed = true)
    private val conflictAutoResolver = mockk<ConflictAutoResolver>(relaxed = true)
    private val pendingConflictDao = mockk<PendingConflictDao>(relaxed = true)
    private val screenshotUploader = mockk<ScreenshotUploader>()
    private val screenshotLedger = mockk<ScreenshotUploadLedger>(relaxed = true)
    private val screenshotNotices = mockk<ScreenshotUploadNotices>(relaxed = true)
    private val tempFilesDir: File = Files.createTempDirectory("process_queue").toFile()

    private val payloadCodec = SyncPayloadCodec(Moshi.Builder().build())
    private val connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected(version = "4.9.0", capabilities = mockk(relaxed = true))
    )

    private lateinit var coordinator: SyncCoordinator

    private val game = GameEntity(
        id = 1L, platformId = 10L, platformSlug = "snes",
        title = "Game", sortTitle = "game",
        localPath = "/roms/g.smc", rommId = 100L,
        igdbId = null, source = GameSource.ROMM_SYNCED,
    )

    @Before
    fun setUp() {
        every { romMRepository.connectionState } returns connectionState
        every { syncPreferencesRepository.preferences } returns flowOf(SyncPreferences(saveSyncEnabled = true))
        coEvery { syncPreferencesRepository.isSavePathCachePurged() } returns true
        coEvery { gameDao.getById(any()) } returns game
        coEvery { pendingSyncQueueDao.promoteEligibleFailedToPending() } returns 0
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(any()) } returns emptyList()
        coEvery { saveSyncRepository.rekeySaveSyncToLocalEmulators() } returns 0
        coEvery { saveSyncDao.deleteDuplicateRows() } returns 0
        coEvery { saveSyncRepository.downloadPendingServerSaves() } returns 0
        coEvery { pendingSyncQueueDao.distinctSessions() } returns emptyList()

        val effectApplier = ReconcileEffectApplier(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveSyncDao = saveSyncDao,
            gameDao = gameDao,
            pendingConflictDao = pendingConflictDao,
            conflictAutoResolver = conflictAutoResolver,
            saveSyncRepository = Lazy { saveSyncRepository },
            saveCacheManager = Lazy { saveCacheManager },
            payloadCodec = payloadCodec,
            syncPreferencesRepository = syncPreferencesRepository,
            serverSaveGate = noHoldGate()
        )
        val screenshotUploads = ScreenshotUploadQueue(
            context = mockk(relaxed = true) { every { filesDir } returns tempFilesDir },
            pendingSyncQueueDao = pendingSyncQueueDao,
            payloadCodec = payloadCodec,
            uploader = screenshotUploader,
            ledger = screenshotLedger,
            notices = screenshotNotices
        )
        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            emulatorSaveConfigDao = emulatorSaveConfigDao,
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = Lazy { romMRepository },
            saveSyncRepository = Lazy { saveSyncRepository },
            saveCacheManager = Lazy { saveCacheManager },
            stateCacheManager = Lazy { stateCacheManager },
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = syncPreferencesRepository,
            payloadCodec = payloadCodec,
            savePathResolver = mockk(relaxed = true),
            strategySelector = strategySelector,
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = effectApplier,
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = screenshotUploads,
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter(),
        )
    }

    @Test
    fun `processQueue calls promoteEligibleFailedToPending at start`() = runTest {
        coordinator.processQueue()
        coVerify(exactly = 1) { pendingSyncQueueDao.promoteEligibleFailedToPending(any()) }
    }

    @Test
    fun `successful SAVE_FILE without sessionId deletes the queue row`() = runTest {
        val row = saveFileRow(id = 10L, sessionId = null)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)
        coEvery {
            saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Success()

        val result = coordinator.processQueue()

        assertTrue(result is SyncCoordinator.ProcessResult.Completed)
        assertEquals(1, (result as SyncCoordinator.ProcessResult.Completed).processed)
        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(10L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markCompleted(any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(any(), any(), any()) }
    }

    @Test
    fun `successful SAVE_FILE with sessionId calls markCompleted instead of delete`() = runTest {
        val row = saveFileRow(id = 11L, sessionId = 42L)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)
        coEvery {
            saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Success()

        coordinator.processQueue()

        coVerify(exactly = 1) { pendingSyncQueueDao.markCompleted(11L, any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
    }

    @Test
    fun `failed upload calls markFailed (does not delete row)`() = runTest {
        val row = saveFileRow(id = 12L, sessionId = null)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)
        coEvery {
            saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Error("network down")

        val result = coordinator.processQueue()

        assertEquals(1, (result as SyncCoordinator.ProcessResult.Completed).failed)
        coVerify(exactly = 1) { pendingSyncQueueDao.markFailed(12L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(any()) }
    }

    @Test
    fun `connection loss mid-loop stops processing remaining items`() = runTest {
        val rowA = saveFileRow(id = 20L, sessionId = null)
        val rowB = saveFileRow(id = 21L, sessionId = null)
        val rowC = saveFileRow(id = 22L, sessionId = null)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(rowA, rowB, rowC)
        coEvery {
            saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any())
        } coAnswers {
            connectionState.value = ConnectionState.Disconnected
            SaveSyncResult.Success()
        }

        coordinator.processQueue()

        coVerify(exactly = 1) { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(21L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(22L) }
    }

    /**
     * A named channel's bytes live in its cache. A row that names one but pins nothing would
     * upload whatever is on the live path under that name, which is how a deleted slot comes back.
     */
    @Test
    fun `a named-channel row with no pinned cache is refused and never reads the live path`() = runTest {
        val row = saveFileRow(id = 30L, sessionId = null, channel = "slot1")
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)

        coordinator.processQueue()

        coVerify(exactly = 0) { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveSyncRepository.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { pendingSyncQueueDao.markFailed(30L, any(), any()) }
    }

    @Test
    fun `a named-channel row whose pinned cache row is gone is refused`() = runTest {
        val row = saveFileRow(id = 31L, sessionId = null, channel = "slot1", cacheId = 93L)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)
        coEvery { saveCacheDao.getById(93L) } returns null

        coordinator.processQueue()

        coVerify(exactly = 0) { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { pendingSyncQueueDao.markFailed(31L, any(), any()) }
    }

    @Test
    fun `an autosave row with no pinned cache still uploads from the live path`() = runTest {
        val row = saveFileRow(id = 32L, sessionId = null, channel = "autosave")
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)
        coEvery {
            saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Success()

        coordinator.processQueue()

        coVerify(exactly = 1) { saveSyncRepository.uploadSave(1L, "snes9x", "autosave", any(), any(), any()) }
        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(32L) }
    }

    @Test
    fun `a COMPLETION row pushes only the completion field to RomM`() = runTest {
        val row = propertyRow(id = 40L, syncType = SyncType.COMPLETION, intValue = 45)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns listOf(row)
        coEvery { romMRepository.updateRomUserProps(any(), any(), any(), any(), any(), any()) } returns true

        coordinator.processQueue()

        coVerify(exactly = 1) {
            romMRepository.updateRomUserProps(
                rommId = 100L,
                userRating = null,
                userDifficulty = null,
                completion = 45,
                userStatus = null,
                hidden = null
            )
        }
        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(40L) }
    }

    @Test
    fun `a COMPLETION row of zero clears completion on RomM`() = runTest {
        val row = propertyRow(id = 41L, syncType = SyncType.COMPLETION, intValue = 0)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns listOf(row)
        coEvery { romMRepository.updateRomUserProps(any(), any(), any(), any(), any(), any()) } returns true

        coordinator.processQueue()

        coVerify(exactly = 1) {
            romMRepository.updateRomUserProps(100L, null, null, 0, null, null)
        }
    }

    @Test
    fun `a RATING row never carries a completion value`() = runTest {
        val row = propertyRow(id = 42L, syncType = SyncType.RATING, intValue = 7)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns listOf(row)
        coEvery { romMRepository.updateRomUserProps(any(), any(), any(), any(), any(), any()) } returns true

        coordinator.processQueue()

        coVerify(exactly = 1) {
            romMRepository.updateRomUserProps(100L, 7, null, null, null, null)
        }
    }

    @Test
    fun `a rejected COMPLETION write keeps the row queued`() = runTest {
        val row = propertyRow(id = 43L, syncType = SyncType.COMPLETION, intValue = 50)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns listOf(row)
        coEvery { romMRepository.updateRomUserProps(any(), any(), any(), any(), any(), any()) } returns false

        coordinator.processQueue()

        coVerify(exactly = 1) { pendingSyncQueueDao.markFailed(43L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(43L) }
    }

    @Test
    fun `an ACHIEVEMENT row is left for the RetroAchievements drain, never deleted or failed here`() = runTest {
        val row = PendingSyncQueueEntity(
            id = 44L, gameId = 1L, rommId = 0L,
            syncType = SyncType.ACHIEVEMENT, priority = SyncPriority.PROPERTY,
            payloadJson = payloadCodec.encode(AchievementPayload(achievementRaId = 9L, forHardcoreMode = false, earnedAt = 1L)),
            status = SyncStatus.PENDING,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        )
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns listOf(row)

        coordinator.processQueue()

        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(44L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markInProgress(44L, any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(44L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markCompleted(44L, any()) }
    }

    @Test
    fun `a screenshot upload that fails transiently stays queued past the retry limit`() = runTest {
        val copy = pendingCopy("Screenshot_1.png")
        val row = screenshotRow(id = 50L, copy = copy).copy(retryCount = 3, maxRetries = 3)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns listOf(row)
        coEvery { screenshotUploader.upload(any(), any()) } returns ScreenshotUploader.Result.Failed("HTTP 503")

        coordinator.processQueue()

        coVerify(exactly = 1) { pendingSyncQueueDao.recordRetryableFailure(50L, "HTTP 503", any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(50L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.deleteById(50L) }
        assertTrue(copy.exists())
        coVerify(exactly = 0) { screenshotLedger.record(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a screenshot upload the uploader cannot reach the server for stays queued`() = runTest {
        val copy = pendingCopy("Screenshot_2.png")
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns
            listOf(screenshotRow(id = 51L, copy = copy))
        coEvery { screenshotUploader.upload(any(), any()) } returns ScreenshotUploader.Result.NotConnected

        coordinator.processQueue()

        coVerify(exactly = 1) { pendingSyncQueueDao.recordRetryableFailure(51L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(51L, any(), any()) }
        assertTrue(copy.exists())
    }

    @Test
    fun `a screenshot the server rejects for good is dropped at once and announced`() = runTest {
        val copy = pendingCopy("Screenshot_3.png")
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns
            listOf(screenshotRow(id = 52L, copy = copy))
        coEvery { screenshotUploader.upload(any(), any()) } returns ScreenshotUploader.Result.Rejected("HTTP 413")

        coordinator.processQueue()

        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(52L) }
        coVerify(exactly = 0) { pendingSyncQueueDao.markFailed(52L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.recordRetryableFailure(52L, any(), any()) }
        assertFalse(copy.exists())
        coVerify(exactly = 1) { screenshotNotices.drainFinished(0, 1) }
    }

    @Test
    fun `an uploaded screenshot is deleted, remembered and announced`() = runTest {
        val copy = pendingCopy("Screenshot_4.png")
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.PROPERTY) } returns
            listOf(screenshotRow(id = 53L, copy = copy))
        coEvery { screenshotUploader.upload(any(), any()) } returns ScreenshotUploader.Result.Success

        coordinator.processQueue()

        coVerify(exactly = 1) { screenshotUploader.upload(copy, 100L) }
        coVerify(exactly = 1) { pendingSyncQueueDao.deleteById(53L) }
        assertFalse(copy.exists())
        coVerify(exactly = 1) { screenshotLedger.record(null, 100L, "/sdcard/DCIM/Screenshots/Screenshot_4.png", 1_000L, any()) }
        coVerify(exactly = 1) { screenshotNotices.drainFinished(1, 0) }
    }

    @Test
    fun `a failed save upload still spends its retry budget and never takes the screenshot path`() = runTest {
        val row = saveFileRow(id = 13L, sessionId = null)
        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(SyncPriority.SAVE_FILE) } returns listOf(row)
        coEvery {
            saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Error("HTTP 503")

        coordinator.processQueue()

        coVerify(exactly = 1) { pendingSyncQueueDao.markFailed(13L, any(), any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.recordRetryableFailure(any(), any(), any()) }
        coVerify(exactly = 0) { screenshotUploader.upload(any(), any()) }
    }

    @Test
    fun `returns NotConnected when RomM is not connected`() = runTest {
        connectionState.value = ConnectionState.Disconnected

        val result = coordinator.processQueue()

        assertTrue(result is SyncCoordinator.ProcessResult.NotConnected)
        coVerify(exactly = 0) { pendingSyncQueueDao.promoteEligibleFailedToPending(any()) }
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
    }

    private fun pendingCopy(name: String): File {
        val slot = File(PendingScreenshotStore.root(mockk(relaxed = true) { every { filesDir } returns tempFilesDir }), "1000_ab")
        slot.mkdirs()
        return File(slot, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    private fun screenshotRow(id: Long, copy: File): PendingSyncQueueEntity =
        PendingSyncQueueEntity(
            id = id, gameId = 1L, rommId = 100L,
            syncType = SyncType.SCREENSHOT, priority = SyncPriority.PROPERTY,
            payloadJson = payloadCodec.encode(
                ScreenshotPayload(
                    localPath = copy.absolutePath,
                    sourcePath = "/sdcard/DCIM/Screenshots/${copy.name}",
                    sourceModifiedAt = 1_000L
                )
            ),
            status = SyncStatus.PENDING,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        )

    private fun propertyRow(id: Long, syncType: SyncType, intValue: Int): PendingSyncQueueEntity =
        PendingSyncQueueEntity(
            id = id, gameId = 1L, rommId = 100L,
            syncType = syncType, priority = SyncPriority.PROPERTY,
            payloadJson = payloadCodec.encode(PropertyPayload(intValue = intValue)),
            status = SyncStatus.PENDING,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        )

    private fun saveFileRow(
        id: Long,
        sessionId: Long?,
        channel: String? = null,
        cacheId: Long? = null,
    ): PendingSyncQueueEntity {
        val payload = SaveFilePayload(emulatorId = "snes9x", channelName = channel)
        return PendingSyncQueueEntity(
            id = id, gameId = 1L, rommId = 100L,
            syncType = SyncType.SAVE_FILE, priority = SyncPriority.SAVE_FILE,
            payloadJson = payloadCodec.encode(payload),
            status = SyncStatus.PENDING,
            createdAt = Instant.now(), updatedAt = Instant.now(),
            sessionId = sessionId,
            cacheId = cacheId,
        )
    }
}
