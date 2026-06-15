package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictAutoResolverTest {

    private val gameDao: GameDao = mockk()
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)

    private val resolver = ConflictAutoResolver(gameDao, saveSyncDao, pendingSyncQueueDao)

    private fun game(id: Long = 1L, rommId: Long = 100L, activeApplied: Boolean = false) = GameEntity(
        id = id,
        title = "Test",
        sortTitle = "test",
        platformId = 1L,
        platformSlug = "snes",
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = GameSource.LOCAL_ONLY,
        activeSaveApplied = activeApplied
    )

    private fun op(
        action: ReconcileAction = ReconcileAction.CONFLICT,
        romId: Long = 100L,
        emulator: String? = "mgba",
        serverHash: String? = "server-hash"
    ) = ReconcileOperation(
        action = action,
        romId = romId,
        fileName = "save.sav",
        emulator = emulator,
        reason = "test",
        serverContentHash = serverHash
    )

    @Test
    fun `non-conflict actions pass through as-is`() = runTest {
        val result = resolver.classify(op(action = ReconcileAction.UPLOAD))
        assertEquals(ConflictAutoResolver.Resolution.AsIs, result)
    }

    @Test
    fun `rule 1 keeps local when activeSaveApplied is true`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game(activeApplied = true)

        val result = resolver.classify(op())

        assertTrue(result is ConflictAutoResolver.Resolution.KeepLocal)
        assertEquals("user-restored", (result as ConflictAutoResolver.Resolution.KeepLocal).ruleId)
    }

    @Test
    fun `rule 2 keeps local when an upload is already queued`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game()
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns listOf(
            PendingSyncQueueEntity(
                gameId = 1L,
                rommId = 100L,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = "{}"
            )
        )

        val result = resolver.classify(op())

        assertTrue(result is ConflictAutoResolver.Resolution.KeepLocal)
        assertEquals("queued-upload", (result as ConflictAutoResolver.Resolution.KeepLocal).ruleId)
    }

    @Test
    fun `rule 3 picks server when local hash matches lastUploaded but server differs`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game()
        coEvery { pendingSyncQueueDao.getByGameId(any()) } returns emptyList()
        coEvery { saveSyncDao.getByGameAndEmulator(1L, "mgba") } returns SaveSyncEntity(
            gameId = 1L,
            rommId = 100L,
            emulatorId = "mgba",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            lastUploadedHash = "anchor-hash",
            localContentHash = "anchor-hash"
        )

        val result = resolver.classify(op(serverHash = "server-hash-newer"), clientHash = "anchor-hash")

        assertTrue(result is ConflictAutoResolver.Resolution.KeepServer)
    }

    @Test
    fun `rule 4 picks local when server hash matches lastUploaded but local differs`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game()
        coEvery { pendingSyncQueueDao.getByGameId(any()) } returns emptyList()
        coEvery { saveSyncDao.getByGameAndEmulator(1L, "mgba") } returns SaveSyncEntity(
            gameId = 1L,
            rommId = 100L,
            emulatorId = "mgba",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            lastUploadedHash = "anchor-hash",
            localContentHash = "anchor-hash"
        )

        val result = resolver.classify(op(serverHash = "anchor-hash"), clientHash = "local-changed")

        assertTrue(result is ConflictAutoResolver.Resolution.KeepLocal)
        assertEquals("server-unchanged", (result as ConflictAutoResolver.Resolution.KeepLocal).ruleId)
    }

    @Test
    fun `genuine conflict with no rule match returns AsIs`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game()
        coEvery { pendingSyncQueueDao.getByGameId(any()) } returns emptyList()
        coEvery { saveSyncDao.getByGameAndEmulator(1L, "mgba") } returns SaveSyncEntity(
            gameId = 1L,
            rommId = 100L,
            emulatorId = "mgba",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            lastUploadedHash = "anchor-hash",
            localContentHash = "anchor-hash"
        )

        val result = resolver.classify(op(serverHash = "different-server"), clientHash = "different-local")

        assertEquals(ConflictAutoResolver.Resolution.AsIs, result)
    }

    @Test
    fun `rule 1 takes precedence over rule 2`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game(activeApplied = true)
        coEvery { pendingSyncQueueDao.getByGameId(1L) } returns listOf(
            PendingSyncQueueEntity(
                gameId = 1L,
                rommId = 100L,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = "{}"
            )
        )

        val result = resolver.classify(op())

        assertEquals("user-restored", (result as ConflictAutoResolver.Resolution.KeepLocal).ruleId)
    }
}
