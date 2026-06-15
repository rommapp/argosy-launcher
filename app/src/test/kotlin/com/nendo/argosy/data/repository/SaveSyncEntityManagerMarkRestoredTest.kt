package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SaveSyncEntityManagerMarkRestoredTest {

    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)

    private val manager = SaveSyncEntityManager(
        saveSyncDao = saveSyncDao,
        saveCacheDao = mockk(relaxed = true),
        syncQueueManager = SyncQueueManager()
    )

    @Test
    fun `markRestored creates a fresh SYNCED row when none exists`() = runTest {
        coEvery { saveSyncDao.getByGameAndEmulator(any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        val serverTs = Instant.parse("2026-05-15T10:00:00Z")
        manager.markRestored(
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "slot1",
            localPath = "/storage/saves/g.srm",
            rommSaveId = 555L,
            serverTimestamp = serverTs
        )

        assertEquals(0L, captured.captured.id)
        assertEquals(7L, captured.captured.gameId)
        assertEquals(100L, captured.captured.rommId)
        assertEquals("mgba", captured.captured.emulatorId)
        assertEquals("slot1", captured.captured.channelName)
        assertEquals("/storage/saves/g.srm", captured.captured.localSavePath)
        assertEquals(555L, captured.captured.rommSaveId)
        assertEquals(serverTs, captured.captured.serverUpdatedAt)
        assertNull("Fresh row has no server-verified hash to carry forward", captured.captured.lastUploadedHash)
        assertEquals(SaveSyncEntity.STATUS_SYNCED, captured.captured.syncStatus)
        assert(captured.captured.lastSyncedAt != null)
        assert(captured.captured.localUpdatedAt != null)
    }

    @Test
    fun `markRestored updates existing row in place when channel matches`() = runTest {
        val existing = SaveSyncEntity(
            id = 42L,
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "slot1",
            rommSaveId = 999L,
            syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
            lastUploadedHash = "old-hash"
        )
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(7L, "mgba", "slot1")
        } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        manager.markRestored(
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "slot1",
            localPath = "/storage/saves/g.srm",
            rommSaveId = 555L,
            serverTimestamp = null
        )

        assertEquals(42L, captured.captured.id)
        assertEquals(555L, captured.captured.rommSaveId)
        assertEquals(SaveSyncEntity.STATUS_SYNCED, captured.captured.syncStatus)
        assertEquals("Existing server-verified hash must be carried forward", "old-hash", captured.captured.lastUploadedHash)
    }

    @Test
    fun `markRestored records restored content hash as client anchor and preserves server hash`() = runTest {
        val existing = SaveSyncEntity(
            id = 42L,
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "slot1",
            syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
            lastUploadedHash = "old-hash"
        )
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(7L, "mgba", "slot1")
        } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        manager.markRestored(
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "slot1",
            localPath = "/storage/saves/g.srm",
            rommSaveId = 555L,
            serverTimestamp = null,
            contentHash = "restored-hash"
        )

        assertEquals("restored-hash", captured.captured.localContentHash)
        assertEquals("old-hash", captured.captured.lastUploadedHash)
        assertEquals(SaveSyncEntity.STATUS_SYNCED, captured.captured.syncStatus)
    }

    @Test
    fun `markRestored preserves existing rommSaveId when caller passes null`() = runTest {
        val existing = SaveSyncEntity(
            id = 42L,
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = null,
            rommSaveId = 999L,
            syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
        )
        coEvery { saveSyncDao.getByGameAndEmulator(7L, "mgba") } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        manager.markRestored(
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = null,
            localPath = "/storage/saves/g.srm",
            rommSaveId = null,
            serverTimestamp = null
        )

        assertEquals(999L, captured.captured.rommSaveId)
    }

    @Test
    fun `markRestored falls back to channel-default lookup when channelName has no exact row`() = runTest {
        val existing = SaveSyncEntity(
            id = 42L,
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = null,
            syncStatus = SaveSyncEntity.STATUS_LOCAL_NEWER
        )
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(7L, "mgba", "slot1")
        } returns null
        coEvery {
            saveSyncDao.getByGameAndEmulatorWithDefault(7L, "mgba", "slot1")
        } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        manager.markRestored(
            gameId = 7L,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "slot1",
            localPath = "/storage/saves/g.srm",
            rommSaveId = 555L,
            serverTimestamp = null
        )

        assertEquals(42L, captured.captured.id)
        assertEquals("slot1", captured.captured.channelName)
        assertEquals(SaveSyncEntity.STATUS_SYNCED, captured.captured.syncStatus)
    }
}
