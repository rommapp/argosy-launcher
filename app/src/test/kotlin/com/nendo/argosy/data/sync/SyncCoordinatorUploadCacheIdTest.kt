package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
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
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SyncCoordinatorUploadCacheIdTest {

    private val pendingSyncQueueDao = mockk<PendingSyncQueueDao>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val emulatorSaveConfigDao = mockk<EmulatorSaveConfigDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val romMRepository = mockk<RomMRepository>(relaxed = true)
    private val saveSyncRepository = mockk<SaveSyncRepository>(relaxed = true)
    private val saveCacheManager = mockk<SaveCacheManager>(relaxed = true)
    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val syncPreferencesRepository = mockk<SyncPreferencesRepository>(relaxed = true)
    private val strategySelector = mockk<SaveSyncStrategySelector>(relaxed = true)
    private val conflictAutoResolver = mockk<ConflictAutoResolver>(relaxed = true)
    private val pendingConflictDao = mockk<PendingConflictDao>(relaxed = true)

    private val payloadCodec = SyncPayloadCodec(Moshi.Builder().build())
    private val syncQueueManager = SyncQueueManager()
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
        coEvery { saveSyncRepository.checkForConflict(any(), any(), any()) } returns null
        coEvery { saveSyncRepository.flushPendingDeviceSync(any()) } returns Unit
        coEvery { saveSyncRepository.uploadSave(any(), any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

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
    fun `non-channel dirty cache forwards uploadedCacheId equal to cache id (audit Bug A2)`() = runTest {
        val nonChannelCache = SaveCacheEntity(
            id = 7777L,
            gameId = 1L,
            emulatorId = "snes9x",
            cachedAt = Instant.parse("2026-05-20T12:00:00Z"),
            saveSize = 1024L,
            cachePath = "1/2026/save.srm",
            channelName = null,
            needsRemoteSync = true,
            contentHash = "hash-non-channel",
        )
        coEvery { saveCacheDao.getNeedingRemoteSync() } returns listOf(nonChannelCache)

        coordinator.processQueue()

        coVerify(exactly = 1) {
            saveSyncRepository.uploadSave(
                gameId = 1L,
                emulatorId = "snes9x",
                channelName = null,
                forceOverwrite = any(),
                isHardcore = any(),
                uploadedCacheId = 7777L,
            )
        }
    }
}
