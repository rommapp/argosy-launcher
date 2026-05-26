package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SaveSyncEntityManagerTest {

    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)

    private lateinit var manager: SaveSyncEntityManager

    @Before
    fun setUp() {
        manager = SaveSyncEntityManager(saveSyncDao, saveCacheDao, syncQueueManager)
    }

    @Test
    fun `createOrUpdateSyncEntity inserts PENDING_UPLOAD when no row exists`() = runTest {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        val entity = manager.createOrUpdateSyncEntity(
            gameId = 1L, rommId = 100L, emulatorId = "eden",
            localPath = "/p", localUpdatedAt = Instant.ofEpochMilli(1_000_000),
            channelName = null,
        )

        assertEquals(SaveSyncEntity.STATUS_PENDING_UPLOAD, entity.syncStatus)
        assertEquals(SaveSyncEntity.STATUS_PENDING_UPLOAD, captured.captured.syncStatus)
        assertEquals(1L, captured.captured.gameId)
        assertEquals("eden", captured.captured.emulatorId)
        assertEquals("/p", captured.captured.localSavePath)
    }

    @Test
    fun `createOrUpdateSyncEntity preserves existing status (does not downgrade SYNCED)`() = runTest {
        val existing = SaveSyncEntity(
            id = 42L, gameId = 1L, rommId = 100L, emulatorId = "eden",
            channelName = null, rommSaveId = 5L,
            localSavePath = "/old", localUpdatedAt = Instant.ofEpochMilli(500),
            serverUpdatedAt = Instant.ofEpochMilli(600), lastSyncedAt = Instant.ofEpochMilli(700),
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
        )
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(1L, "eden", any()) } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 42L

        manager.createOrUpdateSyncEntity(
            gameId = 1L, rommId = 100L, emulatorId = "eden",
            localPath = "/new", localUpdatedAt = Instant.ofEpochMilli(1_000),
            channelName = null,
        )

        val written = captured.captured
        assertEquals("SYNCED status must survive a path update", SaveSyncEntity.STATUS_SYNCED, written.syncStatus)
        assertEquals("Existing id must be preserved for an in-place update", 42L, written.id)
        assertEquals("Existing rommSaveId must be preserved", 5L, written.rommSaveId)
        assertEquals("/new", written.localSavePath)
    }

    @Test
    fun `createOrUpdateSyncEntity keeps existing localPath when caller passes null`() = runTest {
        val existing = SaveSyncEntity(
            id = 7L, gameId = 1L, rommId = 100L, emulatorId = "eden",
            channelName = null, localSavePath = "/preserve_me",
            localUpdatedAt = Instant.ofEpochMilli(1_000),
            syncStatus = SaveSyncEntity.STATUS_LOCAL_NEWER,
        )
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 7L

        manager.createOrUpdateSyncEntity(
            gameId = 1L, rommId = 100L, emulatorId = "eden",
            localPath = null, localUpdatedAt = null,
            channelName = null,
        )

        assertEquals("/preserve_me", captured.captured.localSavePath)
        assertEquals(Instant.ofEpochMilli(1_000), captured.captured.localUpdatedAt)
        assertEquals(SaveSyncEntity.STATUS_LOCAL_NEWER, captured.captured.syncStatus)
    }

    @Test
    fun `createOrUpdateSyncEntity uses channel-keyed lookup when channelName provided`() = runTest {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(1L, "eden", "manual") } returns null
        coEvery { saveSyncDao.upsert(any()) } returns 1L

        manager.createOrUpdateSyncEntity(
            gameId = 1L, rommId = 100L, emulatorId = "eden",
            localPath = "/p", localUpdatedAt = null,
            channelName = "manual",
        )

        coVerify(exactly = 1) { saveSyncDao.getByGameEmulatorAndChannel(1L, "eden", "manual") }
        coVerify(exactly = 0) { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) }
    }

    @Test
    fun `markRestored writes SYNCED with new path and timestamps`() = runTest {
        coEvery { saveSyncDao.getByGameAndEmulator(1L, "eden") } returns null
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L

        manager.markRestored(
            gameId = 1L, rommId = 100L, emulatorId = "eden",
            channelName = null, localPath = "/restored",
            rommSaveId = 99L, serverTimestamp = Instant.ofEpochMilli(2_000),
            contentHash = "deadbeef",
        )

        val written = captured.captured
        assertEquals(SaveSyncEntity.STATUS_SYNCED, written.syncStatus)
        assertEquals("/restored", written.localSavePath)
        assertEquals(99L, written.rommSaveId)
        assertEquals("deadbeef", written.lastUploadedHash)
    }

    @Test
    fun `markRestored preserves existing rommSaveId when caller passes null`() = runTest {
        val existing = SaveSyncEntity(
            id = 3L, gameId = 1L, rommId = 100L, emulatorId = "eden",
            channelName = null, rommSaveId = 77L,
            localSavePath = "/old", localUpdatedAt = Instant.ofEpochMilli(1_000),
            syncStatus = SaveSyncEntity.STATUS_LOCAL_NEWER,
        )
        coEvery { saveSyncDao.getByGameAndEmulator(1L, "eden") } returns existing
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 3L

        manager.markRestored(
            gameId = 1L, rommId = 100L, emulatorId = "eden",
            channelName = null, localPath = "/new",
            rommSaveId = null, serverTimestamp = null, contentHash = null,
        )

        assertEquals("Existing rommSaveId must be preserved when caller passes null", 77L, captured.captured.rommSaveId)
    }
}
