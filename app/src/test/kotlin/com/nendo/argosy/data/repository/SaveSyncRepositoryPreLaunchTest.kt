package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.strategy.LegacySaveSyncStrategy
import com.nendo.argosy.data.sync.strategy.NegotiatorSaveSyncStrategy
import com.nendo.argosy.data.sync.strategy.ReconcileAction
import com.nendo.argosy.data.sync.strategy.ReconcileOperation
import com.nendo.argosy.data.sync.strategy.ReconcilePlan
import com.nendo.argosy.data.sync.strategy.SaveSyncStrategySelector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.async
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SaveSyncRepositoryPreLaunchTest {

    private val apiClient = mockk<SaveSyncApiClient>(relaxed = true)
    private val conflictResolver = mockk<SaveSyncConflictResolver>(relaxed = true)
    private val orchestrator = mockk<SaveSyncOrchestrator>(relaxed = true)
    private val entityManager = mockk<SaveSyncEntityManager>(relaxed = true)
    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val strategySelector = mockk<SaveSyncStrategySelector>()
    private val negotiator = mockk<NegotiatorSaveSyncStrategy>()

    private lateinit var repo: SaveSyncRepository

    private val gameId = 1L
    private val rommId = 100L
    private val emulatorId = "retroarch"
    private val recoveryGate = com.nendo.argosy.data.sync.SaveRecoveryGate().apply { markComplete() }

    @Test
    fun `pre-launch does not touch the disk while a session end is still capturing the save`() = runTest {
        recoveryGate.tryClaimSessionEnd()
        val launch = async { repo.preLaunchSyncForGame(gameId, rommId, emulatorId, null, secureSaves = true) }
        kotlinx.coroutines.yield()

        coVerify(exactly = 0) { orchestrator.checkDiskAgainstActive(any<Long>(), any(), any(), any(), any()) }

        recoveryGate.releaseSessionEnd()
        launch.await()
        coVerify(exactly = 1) { orchestrator.checkDiskAgainstActive(any<Long>(), any(), any(), any(), any()) }
    }

    @Before
    fun setUp() {
        repo = SaveSyncRepository(
            apiClient, conflictResolver, orchestrator, entityManager,
            stateCacheManager, syncQueueManager, saveSyncDao, saveCacheDao,
            mockk(relaxed = true), strategySelector, mockk(relaxed = true), recoveryGate,
            com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter(),
            com.nendo.argosy.data.sync.fixtures.cachedPathAsStored(),
        )
        every { strategySelector.current() } returns mockk<LegacySaveSyncStrategy>(relaxed = true)
        every { apiClient.getDeviceId() } returns "device-1"
        coEvery { apiClient.checkSavesForGame(any(), any()) } returns emptyList()
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { saveCacheDao.hasNeedingRemoteSync(any(), any()) } returns false
    }

    private fun makeServerSave(
        id: Long = 10L,
        slot: String? = "autosave",
        deviceSyncs: List<RomMDeviceSync>? = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
    ) = RomMSave(
        id = id,
        romId = rommId,
        userId = 1L,
        fileName = "save.srm",
        downloadPath = "/saves/save.srm",
        emulator = emulatorId,
        updatedAt = "2025-01-15T12:00:00Z",
        slot = slot,
        fileNameNoExt = "save",
        deviceSyncs = deviceSyncs
    )

    private fun negotiateAnswers(vararg ops: ReconcileOperation) {
        every { strategySelector.current() } returns negotiator
        coEvery { negotiator.planForGame(any(), rommId) } returns ReconcilePlan(sessionId = 1L, operations = ops.toList())
    }

    private fun op(action: ReconcileAction, slot: String, saveId: Long = 70L) = ReconcileOperation(
        action = action,
        romId = rommId,
        saveId = saveId,
        fileName = "save.srm",
        slot = slot,
        serverUpdatedAt = "2026-10-01T00:00:00Z"
    )

    @Test
    fun `negotiate download for the launch slot pulls that save`() = runTest {
        negotiateAnswers(op(ReconcileAction.NO_OP, "slot1", 1L), op(ReconcileAction.DOWNLOAD, "autosave", 70L))

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertEquals(70L, (result as PreLaunchSyncResult.ServerIsNewer).serverSaveId)
        io.mockk.coVerify(exactly = 0) { apiClient.checkSavesForGame(any(), any()) }
    }

    private fun storedRow(status: String) = SaveSyncEntity(
        gameId = gameId, rommId = rommId, emulatorId = emulatorId, channelName = "autosave",
        rommSaveId = 60L, localSavePath = "/saves/save.srm", syncStatus = status
    )

    @Test
    fun `a save that could not be restored asks which save to use instead of launching`() = runTest {
        coEvery { orchestrator.checkDiskAgainstActive(any<Long>(), any(), any(), any(), any()) } returns
            SaveSyncOrchestrator.DiskCheck.Failed
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns
            storedRow(SaveSyncEntity.STATUS_SYNCED)

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        val prompt = result as PreLaunchSyncResult.LocalModified
        assertEquals("/saves/save.srm", prompt.localSavePath)
        assertTrue(prompt.restoreFailed)
    }

    @Test
    fun `an unreadable save folder launches without touching sync`() = runTest {
        coEvery { orchestrator.checkDiskAgainstActive(any<Long>(), any(), any(), any(), any()) } returns
            SaveSyncOrchestrator.DiskCheck.Unreadable("/saves")

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `a download over a conflict reconcile already flagged asks the user`() = runTest {
        negotiateAnswers(op(ReconcileAction.DOWNLOAD, "autosave", 72L))
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns
            storedRow(SaveSyncEntity.STATUS_CONFLICT)

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertEquals(72L, (result as PreLaunchSyncResult.LocalModified).serverSaveId)
    }

    @Test
    fun `negotiate conflict asks the user instead of pulling`() = runTest {
        negotiateAnswers(op(ReconcileAction.CONFLICT, "autosave", 71L))

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertEquals(71L, (result as PreLaunchSyncResult.LocalModified).serverSaveId)
    }

    @Test
    fun `negotiate no-op launches with the local save, whatever the client clocks say`() = runTest {
        negotiateAnswers(op(ReconcileAction.NO_OP, "autosave"))
        coEvery { saveCacheDao.hasNeedingRemoteSync(any(), any()) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `a download for another slot does not pull over the launch slot`() = runTest {
        negotiateAnswers(op(ReconcileAction.DOWNLOAD, "slot1"))

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `an unanswered negotiate is unknown server state, never no saves`() = runTest {
        every { strategySelector.current() } returns negotiator
        coEvery { negotiator.planForGame(any(), any()) } returns null

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.NoConnection)
    }

    @Test
    fun `no deviceId returns NoConnection without an API call`() = runTest {
        every { apiClient.getDeviceId() } returns null

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.NoConnection)
    }

    @Test
    fun `userSelectedRestorePoint=true short-circuits to LocalIsNewer without API call`() = runTest {
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave", any())
        } returns SaveSyncEntity(
            id = 5L, gameId = gameId, rommId = rommId, emulatorId = emulatorId,
            channelName = "autosave",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            userSelectedRestorePoint = true
        )

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
        io.mockk.coVerify(exactly = 0) { apiClient.checkSavesForGame(any(), any()) }
    }

    @Test
    fun `userSelectedRestorePoint=true persists across long idle until cleared by upload`() = runTest {
        val pinnedLongAgo = java.time.Instant.now().minusMillis(30L * 24 * 60 * 60 * 1000)
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave", any())
        } returns SaveSyncEntity(
            id = 5L, gameId = gameId, rommId = rommId, emulatorId = emulatorId,
            channelName = "autosave",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            userSelectedRestorePoint = true,
            userSelectedRestorePointAt = pinnedLongAgo
        )

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
        io.mockk.coVerify(exactly = 0) { apiClient.checkSavesForGame(any(), any()) }
        io.mockk.coVerify(exactly = 0) { saveSyncDao.clearUserSelectedRestorePoint(any()) }
    }

    @Test
    fun `no server save and no local dirty returns NoServerSave`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns emptyList()
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.NoServerSave)
    }

    @Test
    fun `no server save but local dirty returns LocalIsNewer (queue uploads later)`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns emptyList()
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `server isCurrent=true and not dirty returns LocalIsNewer (no_op)`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `server isCurrent=true and dirty returns LocalIsNewer (no_op)`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `server has newer and local not dirty returns ServerIsNewer (download)`() = runTest {
        val server = makeServerSave(
            id = 77L,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(server)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue("Expected ServerIsNewer, got $result", result is PreLaunchSyncResult.ServerIsNewer)
        assertEquals(77L, (result as PreLaunchSyncResult.ServerIsNewer).serverSaveId)
    }

    @Test
    fun `server has newer and local dirty returns LocalModified (conflict)`() = runTest {
        val server = makeServerSave(
            id = 99L,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(server)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue("Expected LocalModified, got $result", result is PreLaunchSyncResult.LocalModified)
        assertEquals(99L, (result as PreLaunchSyncResult.LocalModified).serverSaveId)
    }

    @Test
    fun `default launch sees dirty rows stored under the autosave channel`() = runTest {
        val server = makeServerSave(
            id = 55L,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(server)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, "autosave") } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue("Expected LocalModified, got $result", result is PreLaunchSyncResult.LocalModified)
    }

    @Test
    fun `device has no sync entry on server save defaults to serverHasNewer=true`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-other", isCurrent = true)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue("Expected ServerIsNewer, got $result", result is PreLaunchSyncResult.ServerIsNewer)
    }

    @Test
    fun `checkSavesForGame failure returns NoConnection`() = runTest {
        coEvery { apiClient.checkSavesForGame(any(), any()) } throws RuntimeException("transport")

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.NoConnection)
    }

    @Test
    fun `explicit channelName uses that slot for server-side selection`() = runTest {
        val target = makeServerSave(
            id = 11L,
            slot = "slot1",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        val unrelated = makeServerSave(
            id = 22L,
            slot = "autosave",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(target, unrelated)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, "slot1") } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = "slot1", secureSaves = true)

        assertTrue("Expected ServerIsNewer for slot1, got $result", result is PreLaunchSyncResult.ServerIsNewer)
        assertEquals(11L, (result as PreLaunchSyncResult.ServerIsNewer).serverSaveId)
    }

    @Test
    fun `null channelName resolves to autosave for server save selection`() = runTest {
        val server = makeServerSave(
            id = 33L,
            slot = "AUTOSAVE",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(server)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.ServerIsNewer)
    }

    @Test
    fun `state-shaped server saves are filtered out before slot lookup`() = runTest {
        val state = makeServerSave(id = 50L, slot = "state_1").copy(fileName = "state.zip")
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(state)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue("Expected NoServerSave (state-shaped row filtered), got $result", result is PreLaunchSyncResult.NoServerSave)
    }

    @Test
    fun `LocalModified carries existing localSavePath from save_sync row when present`() = runTest {
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave", any())
        } returns SaveSyncEntity(
            id = 7L, gameId = gameId, rommId = rommId, emulatorId = emulatorId,
            channelName = "autosave",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            localSavePath = "/persisted/save.srm"
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null, secureSaves = true)

        assertTrue(result is PreLaunchSyncResult.LocalModified)
        assertEquals("/persisted/save.srm", (result as PreLaunchSyncResult.LocalModified).localSavePath)
    }
}
