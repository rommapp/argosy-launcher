package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.StateCacheManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class SyncCoordinatorChannelConflictTest {

    private lateinit var pendingSyncQueueDao: PendingSyncQueueDao
    private lateinit var saveCacheDao: SaveCacheDao
    private lateinit var gameDao: GameDao
    private lateinit var romMRepository: dagger.Lazy<RomMRepository>
    private lateinit var saveSyncRepository: dagger.Lazy<SaveSyncRepository>
    private lateinit var saveCacheManager: dagger.Lazy<SaveCacheManager>
    private lateinit var stateCacheManager: dagger.Lazy<StateCacheManager>
    private lateinit var syncQueueManager: SyncQueueManager
    private lateinit var pendingConflictDao: com.nendo.argosy.data.local.dao.PendingConflictDao

    private lateinit var mockRomM: RomMRepository
    private lateinit var mockSyncRepo: SaveSyncRepository
    private lateinit var mockCacheManager: SaveCacheManager

    private lateinit var coordinator: SyncCoordinator

    private val testGame = GameEntity(
        id = 1L,
        title = "Test Game",
        sortTitle = "test game",
        platformId = 1L,
        platformSlug = "gba",
        rommId = 100L,
        igdbId = null,
        localPath = "/storage/roms/test.gba",
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        pendingSyncQueueDao = mockk(relaxed = true)
        saveCacheDao = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        syncQueueManager = SyncQueueManager()
        pendingConflictDao = mockk(relaxed = true)
        coEvery { pendingConflictDao.findByGameSaveAndOwner(any(), any(), any(), any()) } returns null
        coEvery { pendingConflictDao.upsert(any()) } returns STORED_CONFLICT_ID

        mockRomM = mockk(relaxed = true)
        mockSyncRepo = mockk(relaxed = true)
        mockCacheManager = mockk(relaxed = true)

        romMRepository = dagger.Lazy { mockRomM }
        saveSyncRepository = dagger.Lazy { mockSyncRepo }
        saveCacheManager = dagger.Lazy { mockCacheManager }
        stateCacheManager = dagger.Lazy { mockk(relaxed = true) }

        every { mockRomM.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("3.7.0")
        )

        coEvery { pendingSyncQueueDao.getPendingByPriorityTier(any()) } returns emptyList()
        coEvery { gameDao.getById(1L) } returns testGame
    }

    @Test
    fun `channel dirty cache with conflict prompts user and skips upload`() = runTest {
        val cacheFile = File.createTempFile("test_cache", ".zip").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val dirtyCache = makeDirtyChannelCache("slot1")
        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(dirtyCache)
        every { mockCacheManager.getCacheFile(dirtyCache) } returns cacheFile

        val conflictInfo = ConflictInfo(
            gameId = 1L,
            gameName = "Test Game",
            channelName = "slot1",
            localTimestamp = Instant.parse("2025-01-14T12:00:00Z"),
            serverTimestamp = Instant.parse("2025-01-15T12:00:00Z"),
            isHashConflict = true
        )
        coEvery {
            mockSyncRepo.checkForConflict(1L, "retroarch", "slot1")
        } returns conflictInfo

        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = romMRepository,
            saveSyncRepository = saveSyncRepository,
            saveCacheManager = saveCacheManager,
            stateCacheManager = stateCacheManager,
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = mockk(relaxed = true) {
                every { preferences } returns kotlinx.coroutines.flow.MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
            },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            savePathResolver = mockk(relaxed = true),
            strategySelector = mockk(relaxed = true),
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = mockk(relaxed = true),
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
        )

        coordinator.processQueue()

        coVerify {
            saveCacheDao.clearDirtyFlagForChannel(1L, any(), "slot1", excludeId = -1)
        }
        coVerify(exactly = 0) {
            mockSyncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify { pendingConflictDao.upsert(match { it.gameId == 1L && it.slot == "slot1" }) }
        org.junit.Assert.assertEquals(STORED_CONFLICT_ID, syncQueueManager.pendingConflicts.value.single().conflictId)

        cacheFile.delete()
    }

    @Test
    fun `channel dirty cache without conflict uploads normally`() = runTest {
        val cacheFile = File.createTempFile("test_cache", ".zip").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val dirtyCache = makeDirtyChannelCache("slot1")
        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(dirtyCache)
        every { mockCacheManager.getCacheFile(dirtyCache) } returns cacheFile
        coEvery {
            mockSyncRepo.checkForConflict(1L, "retroarch", "slot1")
        } returns null
        coEvery {
            mockSyncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Success(rommSaveId = 42L)

        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = romMRepository,
            saveSyncRepository = saveSyncRepository,
            saveCacheManager = saveCacheManager,
            stateCacheManager = stateCacheManager,
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = mockk(relaxed = true) {
                every { preferences } returns kotlinx.coroutines.flow.MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
            },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            savePathResolver = mockk(relaxed = true),
            strategySelector = mockk(relaxed = true),
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = mockk(relaxed = true),
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
        )

        coordinator.processQueue()

        coVerify {
            mockSyncRepo.uploadCacheEntry(
                gameId = 1L,
                rommId = 100L,
                emulatorId = "retroarch",
                channelName = "slot1",
                cacheFile = cacheFile,
                contentHash = "hash123",
                overwrite = false,
                uploadedCacheId = 10L
            )
        }

        cacheFile.delete()
    }

    @Test
    fun `channel dirty cache upload returns 409 conflict handled`() = runTest {
        val cacheFile = File.createTempFile("test_cache", ".zip").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val dirtyCache = makeDirtyChannelCache("slot1")
        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(dirtyCache)
        every { mockCacheManager.getCacheFile(dirtyCache) } returns cacheFile
        coEvery {
            mockSyncRepo.checkForConflict(1L, "retroarch", "slot1")
        } returns null
        coEvery {
            mockSyncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Conflict(
            gameId = 1L,
            localTimestamp = Instant.now(),
            serverTimestamp = Instant.now()
        )

        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = romMRepository,
            saveSyncRepository = saveSyncRepository,
            saveCacheManager = saveCacheManager,
            stateCacheManager = stateCacheManager,
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = mockk(relaxed = true) {
                every { preferences } returns kotlinx.coroutines.flow.MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
            },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            savePathResolver = mockk(relaxed = true),
            strategySelector = mockk(relaxed = true),
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = mockk(relaxed = true),
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
        )

        coordinator.processQueue()

        coVerify {
            saveCacheDao.clearDirtyFlagForChannel(1L, any(), "slot1", excludeId = -1)
        }
        coVerify { pendingConflictDao.upsert(match { it.gameId == 1L && it.slot == "slot1" }) }
        org.junit.Assert.assertEquals(STORED_CONFLICT_ID, syncQueueManager.pendingConflicts.value.single().conflictId)

        cacheFile.delete()
    }

    @Test
    fun `processQueue skips save work when saveSyncEnabled is false`() = runTest {
        val cacheFile = File.createTempFile("test_cache", ".zip").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val dirtyCache = makeDirtyChannelCache("slot1")
        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(dirtyCache)
        every { mockCacheManager.getCacheFile(dirtyCache) } returns cacheFile

        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = romMRepository,
            saveSyncRepository = saveSyncRepository,
            saveCacheManager = saveCacheManager,
            stateCacheManager = stateCacheManager,
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = mockk(relaxed = true) {
                every { preferences } returns kotlinx.coroutines.flow.MutableStateFlow(SyncPreferences(saveSyncEnabled = false))
            },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            savePathResolver = mockk(relaxed = true),
            strategySelector = mockk(relaxed = true),
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = mockk(relaxed = true),
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
        )

        coordinator.processQueue()

        coVerify(exactly = 0) { saveCacheDao.getNeedingRemoteSync() }
        coVerify(exactly = 0) { mockSyncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { mockSyncRepo.downloadPendingServerSaves() }
        coVerify(exactly = 0) { mockSyncRepo.rekeySaveSyncToLocalEmulators() }

        cacheFile.delete()
    }

    @Test
    fun `dirty rows in one slot upload oldest first so the newest save wins the slot`() = runTest {
        val olderFile = File.createTempFile("older_cache", ".zip").apply {
            writeBytes(byteArrayOf(1))
            deleteOnExit()
        }
        val newerFile = File.createTempFile("newer_cache", ".zip").apply {
            writeBytes(byteArrayOf(2))
            deleteOnExit()
        }

        val older = SaveCacheEntity(
            id = 10L,
            gameId = 1L,
            emulatorId = "retroarch",
            cachedAt = Instant.parse("2025-01-14T12:00:00Z"),
            saveSize = 1024L,
            cachePath = "1/20250114_120000/save.zip",
            channelName = "slot1",
            needsRemoteSync = true,
            contentHash = "hash-older"
        )
        val newer = older.copy(
            id = 11L,
            cachedAt = Instant.parse("2025-01-15T12:00:00Z"),
            cachePath = "1/20250115_120000/save.zip",
            contentHash = "hash-newer"
        )

        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(newer, older)
        every { mockCacheManager.getCacheFile(older) } returns olderFile
        every { mockCacheManager.getCacheFile(newer) } returns newerFile
        coEvery { mockSyncRepo.checkForConflict(1L, "retroarch", "slot1") } returns null
        coEvery {
            mockSyncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveSyncResult.Success(rommSaveId = 42L)

        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = romMRepository,
            saveSyncRepository = saveSyncRepository,
            saveCacheManager = saveCacheManager,
            stateCacheManager = stateCacheManager,
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = mockk(relaxed = true) {
                every { preferences } returns kotlinx.coroutines.flow.MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
            },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            savePathResolver = mockk(relaxed = true),
            strategySelector = mockk(relaxed = true),
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = mockk(relaxed = true),
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
        )

        coordinator.processQueue()

        coVerifyOrder {
            mockSyncRepo.uploadCacheEntry(
                gameId = 1L,
                rommId = 100L,
                emulatorId = "retroarch",
                channelName = "slot1",
                cacheFile = olderFile,
                contentHash = "hash-older",
                overwrite = false,
                uploadedCacheId = 10L
            )
            mockSyncRepo.uploadCacheEntry(
                gameId = 1L,
                rommId = 100L,
                emulatorId = "retroarch",
                channelName = "slot1",
                cacheFile = newerFile,
                contentHash = "hash-newer",
                overwrite = false,
                uploadedCacheId = 11L
            )
        }

        olderFile.delete()
        newerFile.delete()
    }

    @Test
    fun `offline versions of one slot raise one conflict for the whole chain`() = runTest {
        val first = makeDirtyChannelCache("slot1")
        val second = first.copy(id = 11L, cachedAt = Instant.parse("2025-01-14T13:00:00Z"), contentHash = "hash456")
        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(second, first)
        every { mockCacheManager.getCacheFile(any()) } returns File.createTempFile("chain", ".zip").apply { deleteOnExit() }
        coEvery { mockSyncRepo.checkForConflict(1L, "retroarch", "slot1") } returns ConflictInfo(
            gameId = 1L,
            gameName = "Test Game",
            channelName = "slot1",
            localTimestamp = first.cachedAt,
            serverTimestamp = Instant.parse("2025-01-15T12:00:00Z"),
            isHashConflict = true
        )
        coordinator = SyncCoordinator(
            pendingSyncQueueDao = pendingSyncQueueDao,
            saveCacheDao = saveCacheDao,
            saveSyncDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            gameDao = gameDao,
            activeSaveRepository = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true),
            romMRepository = romMRepository,
            saveSyncRepository = saveSyncRepository,
            saveCacheManager = saveCacheManager,
            stateCacheManager = stateCacheManager,
            syncQueueManager = syncQueueManager,
            syncPreferencesRepository = mockk(relaxed = true) {
                every { preferences } returns kotlinx.coroutines.flow.MutableStateFlow(SyncPreferences(saveSyncEnabled = true))
            },
            payloadCodec = SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            savePathResolver = mockk(relaxed = true),
            strategySelector = mockk(relaxed = true),
            pendingConflictDao = pendingConflictDao,
            reconcileEffectApplier = mockk(relaxed = true),
            saveRecoveryGate = mockk(relaxed = true),
            screenshotUploads = mockk(relaxed = true),
            rommApiProvider = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            syncStatesOnSessionEndUseCase = mockk(relaxed = true),
            negotiateInventory = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true),
            snapshotRouter = com.nendo.argosy.data.sync.fixtures.legacySnapshotRouter()
        )

        coordinator.processQueue()

        coVerify(exactly = 1) { mockSyncRepo.checkForConflict(1L, "retroarch", "slot1") }
        org.junit.Assert.assertEquals(1, syncQueueManager.pendingConflicts.value.size)
        coVerify(exactly = 0) { mockSyncRepo.uploadCacheEntry(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun makeDirtyChannelCache(channelName: String) = SaveCacheEntity(
        id = 10L,
        gameId = 1L,
        emulatorId = "retroarch",
        cachedAt = Instant.parse("2025-01-14T12:00:00Z"),
        saveSize = 1024L,
        cachePath = "1/20250114_120000/save.zip",
        channelName = channelName,
        needsRemoteSync = true,
        contentHash = "hash123"
    )

    private companion object {
        const val STORED_CONFLICT_ID = 77L
    }
}
