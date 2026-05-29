package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.every
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

    private lateinit var repo: SaveSyncRepository

    private val gameId = 1L
    private val rommId = 100L
    private val emulatorId = "retroarch"

    @Before
    fun setUp() {
        repo = SaveSyncRepository(
            apiClient, conflictResolver, orchestrator, entityManager,
            stateCacheManager, syncQueueManager, saveSyncDao, saveCacheDao,
        )
        every { apiClient.getDeviceId() } returns "device-1"
        coEvery { apiClient.checkSavesForGame(any(), any()) } returns emptyList()
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
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

    @Test
    fun `no deviceId returns NoConnection without an API call`() = runTest {
        every { apiClient.getDeviceId() } returns null

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.NoConnection)
    }

    @Test
    fun `userSelectedRestorePoint=true short-circuits to LocalIsNewer without API call`() = runTest {
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave")
        } returns SaveSyncEntity(
            id = 5L, gameId = gameId, rommId = rommId, emulatorId = emulatorId,
            channelName = "autosave",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            userSelectedRestorePoint = true,
            userSelectedRestorePointAt = java.time.Instant.now()
        )

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
        io.mockk.coVerify(exactly = 0) { apiClient.checkSavesForGame(any(), any()) }
    }

    @Test
    fun `userSelectedRestorePoint expired beyond TTL is cleared and pre-launch evaluates server state`() = runTest {
        val staleAt = java.time.Instant.now().minusMillis(SaveSyncEntity.USER_PIN_TTL_MS + 60_000L)
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave")
        } returns SaveSyncEntity(
            id = 5L, gameId = gameId, rommId = rommId, emulatorId = emulatorId,
            channelName = "autosave",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            userSelectedRestorePoint = true,
            userSelectedRestorePointAt = staleAt
        )
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.ServerIsNewer)
        io.mockk.coVerify { saveSyncDao.clearUserSelectedRestorePoint(5L) }
    }

    @Test
    fun `no server save and no local dirty returns NoServerSave`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns emptyList()
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.NoServerSave)
    }

    @Test
    fun `no server save but local dirty returns LocalIsNewer (queue uploads later)`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns emptyList()
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `server isCurrent=true and not dirty returns LocalIsNewer (no_op)`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `server isCurrent=true and dirty returns LocalIsNewer (no_op)`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns true

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

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

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

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

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue("Expected LocalModified, got $result", result is PreLaunchSyncResult.LocalModified)
        assertEquals(99L, (result as PreLaunchSyncResult.LocalModified).serverSaveId)
    }

    @Test
    fun `device has no sync entry on server save defaults to serverHasNewer=true`() = runTest {
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(
            makeServerSave(deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-other", isCurrent = true)))
        )
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue("Expected ServerIsNewer, got $result", result is PreLaunchSyncResult.ServerIsNewer)
    }

    @Test
    fun `checkSavesForGame failure returns NoConnection`() = runTest {
        coEvery { apiClient.checkSavesForGame(any(), any()) } throws RuntimeException("transport")

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

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

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = "slot1")

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

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.ServerIsNewer)
    }

    @Test
    fun `state-shaped server saves are filtered out before slot lookup`() = runTest {
        val state = makeServerSave(id = 50L, slot = "state_1").copy(fileName = "state.zip")
        coEvery { apiClient.checkSavesForGame(gameId, rommId) } returns listOf(state)
        coEvery { saveCacheDao.hasNeedingRemoteSync(gameId, null) } returns false

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue("Expected NoServerSave (state-shaped row filtered), got $result", result is PreLaunchSyncResult.NoServerSave)
    }

    @Test
    fun `LocalModified carries existing localSavePath from save_sync row when present`() = runTest {
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "autosave")
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

        val result = repo.preLaunchSyncForGame(gameId, rommId, emulatorId, channelName = null)

        assertTrue(result is PreLaunchSyncResult.LocalModified)
        assertEquals("/persisted/save.srm", (result as PreLaunchSyncResult.LocalModified).localSavePath)
    }
}
