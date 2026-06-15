package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.strategy.ConflictAutoResolver
import com.nendo.argosy.data.sync.strategy.ReconcileAction
import com.nendo.argosy.data.sync.strategy.ReconcileOperation
import com.nendo.argosy.data.sync.strategy.ReconcilePlan
import com.nendo.argosy.data.sync.strategy.SaveSyncStrategy
import com.nendo.argosy.data.sync.strategy.SaveSyncStrategySelector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncCoordinatorApplyPlanTest {

    private lateinit var pendingSyncQueueDao: PendingSyncQueueDao
    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var gameDao: GameDao
    private lateinit var pendingConflictDao: PendingConflictDao
    private lateinit var conflictAutoResolver: ConflictAutoResolver
    private lateinit var strategySelector: SaveSyncStrategySelector
    private lateinit var fakeStrategy: SaveSyncStrategy
    private lateinit var mockSaveSyncRepository: SaveSyncRepository
    private lateinit var mockSaveCacheManager: com.nendo.argosy.data.repository.SaveCacheManager

    private lateinit var coordinator: SyncCoordinator

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
        strategySelector = mockk(relaxed = true)
        fakeStrategy = mockk(relaxed = true)

        val romM: RomMRepository = mockk(relaxed = true) {
            every { connectionState } returns MutableStateFlow(
                ConnectionState.Connected(
                    version = "4.9.0",
                    capabilities = RomMCapabilities.from("4.9.0")
                )
            )
        }
        val syncPrefs: SyncPreferencesRepository = mockk(relaxed = true) {
            every { preferences } returns MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
            coEvery { isSavePathCachePurged() } returns true
            coEvery { getLastNegotiateAt() } returns null
        }

        mockSaveSyncRepository = mockk(relaxed = true)
        coEvery { mockSaveSyncRepository.resolveEmulatorForGame(any()) } returns "mgba"
        mockSaveCacheManager = mockk(relaxed = true)
        val payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build())
        val effectApplier = ReconcileEffectApplier(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveSyncDao = saveSyncDao,
            gameDao = gameDao,
            pendingConflictDao = pendingConflictDao,
            conflictAutoResolver = conflictAutoResolver,
            saveSyncRepository = dagger.Lazy { mockSaveSyncRepository },
            saveCacheManager = dagger.Lazy { mockSaveCacheManager },
            payloadCodec = payloadCodec
        )
        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = mockk(relaxed = true),
            saveSyncDao = saveSyncDao,
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            romMRepository = dagger.Lazy { romM },
            saveSyncRepository = dagger.Lazy { mockSaveSyncRepository },
            saveCacheManager = dagger.Lazy { mockk<SaveCacheManager>(relaxed = true) },
            stateCacheManager = dagger.Lazy { mockk<StateCacheManager>(relaxed = true) },
            syncQueueManager = SyncQueueManager(),
            syncPreferencesRepository = syncPrefs,
            payloadCodec = payloadCodec,
            strategySelector = strategySelector,
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = effectApplier,
            saveRecoveryGate = mockk(relaxed = true)
        )

        every { strategySelector.current() } returns fakeStrategy
        coEvery { saveSyncDao.getAllWithLocalPath() } returns listOf(
            SaveSyncEntity(
                id = 1L,
                gameId = game.id,
                rommId = 100L,
                emulatorId = "mgba",
                localSavePath = null,
                syncStatus = SaveSyncEntity.STATUS_SYNCED
            )
        )
        coEvery { gameDao.getByRommId(100L) } returns game
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

    private suspend fun runWith(operations: List<ReconcileOperation>) {
        coEvery { fakeStrategy.planReconcile(any()) } returns ReconcilePlan(
            sessionId = 1L,
            operations = operations
        )
        coordinator.reconcileAll()
    }

    @Test
    fun `UPLOAD op queues a pending SAVE_FILE row`() = runTest {
        val captured = slot<PendingSyncQueueEntity>()
        coEvery { pendingSyncQueueDao.insert(capture(captured)) } returns 1L

        runWith(listOf(op(ReconcileAction.UPLOAD)))

        coVerify { pendingSyncQueueDao.deleteByGameAndType(game.id, SyncType.SAVE_FILE) }
        coVerify { pendingSyncQueueDao.insert(any()) }
        assert(captured.captured.gameId == game.id)
        assert(captured.captured.rommId == 100L)
        assert(captured.captured.syncType == SyncType.SAVE_FILE)
    }

    @Test
    fun `DOWNLOAD op upserts save_sync with STATUS_SERVER_NEWER and the saveId`() = runTest {
        val captured = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(captured)) } returns 1L
        coEvery { saveSyncDao.getByGameAndEmulator(game.id, "mgba") } returns null

        runWith(listOf(op(ReconcileAction.DOWNLOAD, saveId = 77L)))

        assert(captured.captured.gameId == game.id)
        assert(captured.captured.rommSaveId == 77L)
        assert(captured.captured.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER)
        assert(captured.captured.emulatorId == "mgba")
    }

    @Test
    fun `DOWNLOAD op for a rom with no local game is skipped`() = runTest {
        coEvery { gameDao.getByRommId(999L) } returns null

        runWith(listOf(op(ReconcileAction.DOWNLOAD, romId = 999L)))

        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `UPLOAD op for a rom with no local game is skipped`() = runTest {
        coEvery { gameDao.getByRommId(999L) } returns null

        runWith(listOf(op(ReconcileAction.UPLOAD, romId = 999L)))

        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
    }

    @Test
    fun `DOWNLOAD op for a game without localPath is skipped`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game.copy(localPath = null)

        runWith(listOf(op(ReconcileAction.DOWNLOAD)))

        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `CONFLICT auto-resolved KeepLocal queues an upload`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.KeepLocal("activeSaveApplied")

        runWith(listOf(op(ReconcileAction.CONFLICT)))

        coVerify { pendingSyncQueueDao.insert(match { it.syncType == SyncType.SAVE_FILE }) }
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }

    @Test
    fun `CONFLICT auto-resolved KeepServer marks save_sync SERVER_NEWER`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.KeepServer("local-unchanged")
        coEvery { saveSyncDao.getByGameAndEmulator(game.id, "mgba") } returns null

        runWith(listOf(op(ReconcileAction.CONFLICT)))

        coVerify { saveSyncDao.upsert(match { it.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER }) }
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }

    @Test
    fun `CONFLICT falling through to AsIs persists a PendingConflict row only`() = runTest {
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.AsIs

        val captured = slot<PendingConflictEntity>()
        coEvery { pendingConflictDao.upsert(capture(captured)) } returns 1L

        runWith(listOf(op(ReconcileAction.CONFLICT, saveId = 555L)))

        coVerify { pendingConflictDao.upsert(any()) }
        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
        assert(captured.captured.rommSaveId == 555L)
    }

    @Test
    fun `NO_OP does nothing`() = runTest {
        runWith(listOf(op(ReconcileAction.NO_OP)))

        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
        coVerify(exactly = 0) { pendingConflictDao.upsert(any()) }
    }

    @Test
    fun `mixed plan dispatches each op to its respective sink`() = runTest {
        coEvery { gameDao.getByRommId(100L) } returns game
        coEvery { gameDao.getByRommId(200L) } returns game.copy(id = 22L, rommId = 200L)
        coEvery { gameDao.getByRommId(300L) } returns game.copy(id = 33L, rommId = 300L)
        coEvery {
            conflictAutoResolver.classify(any(), any())
        } returns ConflictAutoResolver.Resolution.AsIs

        runWith(
            listOf(
                op(ReconcileAction.UPLOAD, romId = 100L),
                op(ReconcileAction.DOWNLOAD, romId = 200L, saveId = 222L),
                op(ReconcileAction.CONFLICT, romId = 300L, saveId = 333L),
                op(ReconcileAction.NO_OP, romId = 400L)
            )
        )

        coVerify(exactly = 1) { pendingSyncQueueDao.insert(match { it.gameId == 11L }) }
        coVerify(exactly = 1) { saveSyncDao.upsert(match { it.gameId == 22L && it.rommSaveId == 222L }) }
        coVerify(exactly = 1) { pendingConflictDao.upsert(match { it.gameId == 33L && it.rommSaveId == 333L }) }
    }

    // --- completeSession contract (post-Phase-4: fires from processQueue on drain, NOT reconcileAll) ---

    @Test
    fun `reconcileAll does not call completeSession after applyPlan`() = runTest {
        coEvery { conflictAutoResolver.classify(any(), any()) } returns ConflictAutoResolver.Resolution.AsIs

        runWith(listOf(
            op(ReconcileAction.UPLOAD),
            op(ReconcileAction.DOWNLOAD, saveId = 77L),
            op(ReconcileAction.CONFLICT, saveId = 88L)
        ))

        coVerify(exactly = 0) { fakeStrategy.completeSession(any(), any(), any()) }
    }

    @Test
    fun `reconcileAll stamps plan sessionId on queued upload rows`() = runTest {
        coEvery { conflictAutoResolver.classify(any(), any()) } returns ConflictAutoResolver.Resolution.AsIs

        runWith(listOf(op(ReconcileAction.UPLOAD)))

        coVerify { pendingSyncQueueDao.insert(match { it.sessionId == 1L && it.syncType == SyncType.SAVE_FILE }) }
    }

    // --- conflict entity hash propagation ---

    @Test
    fun `conflict entity carries current local file hash and serverHash from plan op`() = runTest {
        coEvery { conflictAutoResolver.classify(any(), any()) } returns ConflictAutoResolver.Resolution.AsIs
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(game.id, "mgba", "autosave") } returns SaveSyncEntity(
            gameId = game.id,
            rommId = 100L,
            emulatorId = "mgba",
            channelName = "autosave",
            localSavePath = "/storage/saves/g.srm",
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            lastUploadedHash = "server-anchor",
            localContentHash = "client-anchor"
        )
        coEvery { mockSaveCacheManager.calculateLocalSaveHash("/storage/saves/g.srm") } returns "live-local"
        val captured = slot<PendingConflictEntity>()
        coEvery { pendingConflictDao.upsert(capture(captured)) } returns 1L

        runWith(listOf(op(ReconcileAction.CONFLICT, saveId = 555L)))

        assertEquals("live-local", captured.captured.localHash)
        assertEquals("srv-hash", captured.captured.serverHash)
    }

    @Test
    fun `conflict entity has null localHash when no save_sync row exists yet`() = runTest {
        coEvery { conflictAutoResolver.classify(any(), any()) } returns ConflictAutoResolver.Resolution.AsIs
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(game.id, "mgba", "autosave") } returns null
        val captured = slot<PendingConflictEntity>()
        coEvery { pendingConflictDao.upsert(capture(captured)) } returns 1L

        runWith(listOf(op(ReconcileAction.CONFLICT, saveId = 555L)))

        assertEquals(null, captured.captured.localHash)
        assertEquals("srv-hash", captured.captured.serverHash)
    }

    // --- canonicalizeStaleEmulatorIds ---

    @Test
    fun `canonicalizeStaleEmulatorIds rekeyes default emulatorId rows to canonical id`() = runTest {
        val staleEntity = SaveSyncEntity(
            gameId = game.id,
            rommId = 100L,
            emulatorId = "default",
            syncStatus = SaveSyncEntity.STATUS_SYNCED
        )
        coEvery { saveSyncDao.getStaleDefaultEmulatorRows() } returns listOf(staleEntity)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { mockSaveSyncRepository.resolveEmulatorForGame(game) } returns "mgba"
        coEvery { fakeStrategy.planReconcile(any()) } returns ReconcilePlan(sessionId = null, operations = emptyList())

        coordinator.reconcileAll()

        coVerify { saveSyncDao.rekeyEmulatorForGame(game.id, "mgba") }
    }

    @Test
    fun `canonicalizeStaleEmulatorIds skips game when canonical emulator cannot be resolved`() = runTest {
        val staleEntity = SaveSyncEntity(
            gameId = game.id,
            rommId = 100L,
            emulatorId = "default",
            syncStatus = SaveSyncEntity.STATUS_SYNCED
        )
        coEvery { saveSyncDao.getStaleDefaultEmulatorRows() } returns listOf(staleEntity)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { mockSaveSyncRepository.resolveEmulatorForGame(game) } returns null
        coEvery { fakeStrategy.planReconcile(any()) } returns ReconcilePlan(sessionId = null, operations = emptyList())

        coordinator.reconcileAll()

        coVerify(exactly = 0) { saveSyncDao.rekeyEmulatorForGame(any(), any()) }
    }

    @Test
    fun `canonicalizeStaleEmulatorIds is no-op when no stale rows exist`() = runTest {
        coEvery { saveSyncDao.getStaleDefaultEmulatorRows() } returns emptyList()
        coEvery { fakeStrategy.planReconcile(any()) } returns ReconcilePlan(sessionId = null, operations = emptyList())

        coordinator.reconcileAll()

        coVerify(exactly = 0) { saveSyncDao.rekeyEmulatorForGame(any(), any()) }
        coVerify(exactly = 0) { gameDao.getById(any()) }
    }
}
