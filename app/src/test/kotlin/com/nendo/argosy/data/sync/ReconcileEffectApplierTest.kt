package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.strategy.ConflictAutoResolver
import com.nendo.argosy.data.sync.strategy.ReconcileAction
import com.nendo.argosy.data.sync.strategy.ReconcileOperation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReconcileEffectApplierTest {

    private lateinit var pendingSyncQueueDao: PendingSyncQueueDao
    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var gameDao: GameDao
    private lateinit var pendingConflictDao: PendingConflictDao
    private lateinit var conflictAutoResolver: ConflictAutoResolver
    private lateinit var saveSyncRepository: SaveSyncRepository
    private lateinit var applier: ReconcileEffectApplier

    private val game = GameEntity(
        id = 11L,
        title = "Game",
        sortTitle = "game",
        platformId = 1L,
        platformSlug = "gba",
        localPath = "/storage/roms/g.gba",
        rommId = 100L,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        pendingSyncQueueDao = mockk(relaxed = true)
        saveSyncDao = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        pendingConflictDao = mockk(relaxed = true)
        conflictAutoResolver = mockk(relaxed = true)
        saveSyncRepository = mockk(relaxed = true)
        coEvery { saveSyncRepository.resolveEmulatorForGame(any()) } returns "mgba"
        coEvery { gameDao.getByRommId(100L) } returns game

        applier = ReconcileEffectApplier(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveSyncDao = saveSyncDao,
            gameDao = gameDao,
            pendingConflictDao = pendingConflictDao,
            conflictAutoResolver = conflictAutoResolver,
            saveSyncRepository = dagger.Lazy { saveSyncRepository },
            saveCacheManager = dagger.Lazy { mockk(relaxed = true) },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build())
        )
    }

    private fun op(
        action: ReconcileAction,
        romId: Long = 100L,
        saveId: Long? = 42L,
        slot: String? = "autosave",
        emulator: String? = "mgba"
    ) = ReconcileOperation(
        action = action,
        romId = romId,
        saveId = saveId,
        fileName = "save.sav",
        slot = slot,
        emulator = emulator,
        reason = "test",
        serverUpdatedAt = "2026-05-17T12:00:00Z",
        serverContentHash = "srv-hash"
    )

    @Test
    fun `NO_OP returns NONE without side effects`() = runTest {
        val outcome = applier.apply(op(ReconcileAction.NO_OP), sessionId = 1L)

        assertEquals(0, outcome.applied)
        assertEquals(0, outcome.conflicts)
        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }

    @Test
    fun `UPLOAD queues a SAVE_FILE row and returns applied=1`() = runTest {
        val captured = slot<PendingSyncQueueEntity>()
        coEvery { pendingSyncQueueDao.insert(capture(captured)) } returns 1L

        val outcome = applier.apply(op(ReconcileAction.UPLOAD), sessionId = 9L)

        assertEquals(1, outcome.applied)
        assertEquals(0, outcome.conflicts)
        coVerify { pendingSyncQueueDao.deleteByGameAndType(game.id, SyncType.SAVE_FILE) }
        assertEquals(game.id, captured.captured.gameId)
        assertEquals(SyncType.SAVE_FILE, captured.captured.syncType)
        assertEquals(9L, captured.captured.sessionId)
    }

    @Test
    fun `UPLOAD with no local game returns NONE`() = runTest {
        coEvery { gameDao.getByRommId(999L) } returns null

        val outcome = applier.apply(op(ReconcileAction.UPLOAD, romId = 999L), sessionId = null)

        assertEquals(0, outcome.applied)
        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
    }

    @Test
    fun `DOWNLOAD marks save_sync STATUS_SERVER_NEWER with the server saveId`() = runTest {
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val outcome = applier.apply(op(ReconcileAction.DOWNLOAD, saveId = 77L), sessionId = null)

        assertEquals(1, outcome.applied)
        assertEquals(SaveSyncEntity.STATUS_SERVER_NEWER, captured.captured.syncStatus)
        assertEquals(77L, captured.captured.rommSaveId)
    }

    @Test
    fun `DOWNLOAD with no local game returns NONE`() = runTest {
        coEvery { gameDao.getByRommId(999L) } returns null

        val outcome = applier.apply(op(ReconcileAction.DOWNLOAD, romId = 999L), sessionId = null)

        assertEquals(0, outcome.applied)
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `DOWNLOAD with no local ROM is skipped`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game.copy(localPath = null)

        val outcome = applier.apply(op(ReconcileAction.DOWNLOAD), sessionId = null)

        assertEquals(0, outcome.applied)
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `CONFLICT auto-resolved KeepLocal queues an upload`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.KeepLocal("activeSaveApplied")

        val outcome = applier.apply(op(ReconcileAction.CONFLICT), sessionId = null)

        assertEquals(1, outcome.applied)
        assertEquals(0, outcome.conflicts)
        coVerify { pendingSyncQueueDao.insert(match { it.syncType == SyncType.SAVE_FILE }) }
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }

    @Test
    fun `CONFLICT auto-resolved KeepServer marks save_sync SERVER_NEWER`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.KeepServer("local-unchanged")
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val outcome = applier.apply(op(ReconcileAction.CONFLICT), sessionId = null)

        assertEquals(1, outcome.applied)
        coVerify { saveSyncDao.upsert(match { it.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER }) }
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }

    @Test
    fun `CONFLICT AsIs persists a PendingConflict row and returns conflicts=1`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.AsIs
        val captured = slot<PendingConflictEntity>()
        coEvery { pendingConflictDao.upsert(capture(captured)) } returns 1L

        val outcome = applier.apply(op(ReconcileAction.CONFLICT, saveId = 555L), sessionId = null)

        assertEquals(0, outcome.applied)
        assertEquals(1, outcome.conflicts)
        assertEquals(555L, captured.captured.rommSaveId)
    }

    @Test
    fun `CONFLICT AsIs previously dismissed unchanged is suppressed`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.AsIs
        val existing = PendingConflictEntity(
            gameId = game.id,
            rommSaveId = 555L,
            fileName = "save.sav",
            slot = "autosave",
            emulator = "mgba",
            localUpdatedAt = null,
            serverUpdatedAt = java.time.Instant.parse("2026-05-17T12:00:00Z"),
            localHash = null,
            serverHash = "srv-hash",
            reason = "test",
            dismissed = true
        )
        coEvery { pendingConflictDao.findByGameAndSave(game.id, 555L) } returns existing

        val outcome = applier.apply(op(ReconcileAction.CONFLICT, saveId = 555L), sessionId = null)

        assertEquals(0, outcome.conflicts)
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }
}
