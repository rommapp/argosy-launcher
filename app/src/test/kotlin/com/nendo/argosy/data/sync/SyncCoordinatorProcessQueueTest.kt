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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            emulatorSaveConfigDao = emulatorSaveConfigDao,
            gameDao = gameDao,
            romMRepository = Lazy { romMRepository },
            saveSyncRepository = Lazy { saveSyncRepository },
            saveCacheManager = Lazy { saveCacheManager },
            stateCacheManager = Lazy { stateCacheManager },
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = syncPreferencesRepository,
            payloadCodec = payloadCodec,
            strategySelector = strategySelector,
            conflictAutoResolver = conflictAutoResolver,
            pendingConflictDao = pendingConflictDao,
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

    @Test
    fun `returns NotConnected when RomM is not connected`() = runTest {
        connectionState.value = ConnectionState.Disconnected

        val result = coordinator.processQueue()

        assertTrue(result is SyncCoordinator.ProcessResult.NotConnected)
        coVerify(exactly = 0) { pendingSyncQueueDao.promoteEligibleFailedToPending(any()) }
    }

    private fun saveFileRow(
        id: Long,
        sessionId: Long?,
        channel: String? = null,
    ): PendingSyncQueueEntity {
        val payload = SaveFilePayload(emulatorId = "snes9x", channelName = channel)
        return PendingSyncQueueEntity(
            id = id, gameId = 1L, rommId = 100L,
            syncType = SyncType.SAVE_FILE, priority = SyncPriority.SAVE_FILE,
            payloadJson = payloadCodec.encode(payload),
            status = SyncStatus.PENDING,
            createdAt = Instant.now(), updatedAt = Instant.now(),
            sessionId = sessionId,
        )
    }
}
