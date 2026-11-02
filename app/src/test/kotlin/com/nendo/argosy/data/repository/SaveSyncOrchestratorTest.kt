package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SaveClaim
import com.nendo.argosy.data.sync.SaveLookup
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveSyncOrchestratorTest {

    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var pendingSyncQueueDao: PendingSyncQueueDao
    private lateinit var gameDao: GameDao
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var savePathResolver: SavePathResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var syncPreferencesRepository: SyncPreferencesRepository
    private lateinit var syncQueueManager: SyncQueueManager
    private lateinit var mockApiClient: SaveSyncApiClient
    private lateinit var apiClient: dagger.Lazy<SaveSyncApiClient>
    private lateinit var saveCacheDao: SaveCacheDao
    private lateinit var saveCacheManager: SaveCacheManager
    private lateinit var saveOwnershipTracker: SaveOwnershipTracker
    private lateinit var activeSaveRepository: ActiveSaveRepository
    private lateinit var orchestrator: SaveSyncOrchestrator
    private val fileAccessLayer = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true)

    private val testGame = GameEntity(
        id = 1L,
        title = "Pokemon Violet",
        sortTitle = "pokemon violet",
        platformId = 2L,
        platformSlug = "switch",
        rommId = 200L,
        igdbId = null,
        localPath = "/storage/roms/switch/Pokemon Violet.nsp",
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        saveSyncDao = mockk(relaxed = true)
        pendingSyncQueueDao = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        emulatorResolver = mockk(relaxed = true)
        savePathResolver = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        syncPreferencesRepository = mockk(relaxed = true)
        syncQueueManager = SyncQueueManager()
        mockApiClient = mockk(relaxed = true)
        apiClient = dagger.Lazy { mockApiClient }
        saveCacheDao = mockk(relaxed = true)
        saveCacheManager = mockk(relaxed = true)
        saveOwnershipTracker = mockk(relaxed = true)
        activeSaveRepository = mockk(relaxed = true)
        coEvery { activeSaveRepository.getActiveRow(any()) } returns null

        val prefs = UserPreferences(saveSyncEnabled = true)
        every { userPreferencesRepository.preferences } returns MutableStateFlow(prefs)

        coEvery { gameDao.getById(1L) } returns testGame

        orchestrator = SaveSyncOrchestrator(
            saveSyncDao = saveSyncDao,
            saveCacheDao = saveCacheDao,
            saveCacheManager = dagger.Lazy { saveCacheManager },
            activeSaveRepository = activeSaveRepository,
            serverSaveGate = ServerSaveGate(activeSaveRepository, saveSyncDao, syncPreferencesRepository),
            pendingSyncQueueDao = pendingSyncQueueDao,
            gameDao = gameDao,
            emulatorResolver = emulatorResolver,
            savePathResolver = savePathResolver,
            userPreferencesRepository = userPreferencesRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            syncQueueManager = syncQueueManager,
            apiClient = apiClient,
            payloadCodec = com.nendo.argosy.data.sync.SyncPayloadCodec(com.squareup.moshi.Moshi.Builder().build()),
            saveHandlerRegistry = mockk(relaxed = true),
            saveAccessNotices = com.nendo.argosy.data.sync.SaveAccessNotices(),
            saveOwnershipTracker = saveOwnershipTracker,
            accountSwitchMarkerStore = mockk(relaxed = true),
            fileAccessLayer = fileAccessLayer,
            gameArtDao = mockk(relaxed = true),
            diskJudge = ActiveSaveDiskJudge(saveCacheDao, saveSyncDao, saveOwnershipTracker)
        )
    }

    @Test
    fun `refreshCacheFromSystem finishes the cache write when its caller is cancelled`() = runTest {
        val saveFile = File.createTempFile("refresh", ".srm").apply {
            writeText("save")
            deleteOnExit()
        }
        coEvery {
            savePathResolver.discoverSavePathChecked(any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveLookup.Found(saveFile.path)
        coEvery { saveOwnershipTracker.claim(any(), any()) } returns SaveClaim.Unowned
        coEvery { saveCacheDao.getMostRecent(any(), any()) } returns null
        coEvery { saveCacheDao.getMostRecentInChannel(any(), any(), any()) } returns null
        val writeStarted = CompletableDeferred<Unit>()
        var writeFinished = false
        coEvery {
            saveCacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            writeStarted.complete(Unit)
            delay(50)
            writeFinished = true
            SaveCacheManager.CacheResult.Created(0L)
        }

        val refresh = launch(Dispatchers.IO) {
            orchestrator.refreshCacheFromSystem(1L, "retroarch", null, null)
        }
        writeStarted.await()
        refresh.cancelAndJoin()

        assertTrue(writeFinished)
    }

    // --- syncSavesForNewDownload ---

    @Test
    fun `syncSavesForNewDownload places only the newest autosave and caches the rest as history`() = runTest {
        val olderAutosave = makeServerSave(id = 1L, fileName = "Pok\u00e9mon Violet [2024-01-15 12-00-00].srm", updatedAt = "2025-01-10T00:00:00Z")
        val newestAutosave = makeServerSave(id = 2L, fileName = "Pok\u00e9mon Violet.srm", updatedAt = "2025-01-15T00:00:00Z")
        val checkpoint = makeServerSave(id = 3L, fileName = "checkpoint.srm", updatedAt = "2025-02-01T00:00:00Z")
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(olderAutosave, newestAutosave, checkpoint)
        coEvery { mockApiClient.downloadToCache(any(), any(), any()) } returns 10L
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify(exactly = 1) { mockApiClient.downloadSave(1L, "yuzu", "autosave", false, 2L) }
        coVerify(exactly = 1) { mockApiClient.downloadSave(any(), any(), any(), any(), any()) }
        coVerify { mockApiClient.downloadToCache(1L, 1L, "autosave") }
        coVerify { mockApiClient.downloadToCache(3L, 1L, "checkpoint") }
        coVerify(exactly = 0) { mockApiClient.downloadToCache(2L, any(), any()) }
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `syncSavesForNewDownload without an autosave places the newest save in its own slot`() = runTest {
        val serverSave = makeServerSave(id = 1L, fileName = "Ch\u00e9ckpoint.srm")
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(serverSave)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify(exactly = 1) { mockApiClient.downloadSave(1L, "yuzu", "Ch\u00e9ckpoint", false, 1L) }
    }

    @Test
    fun `syncSavesForNewDownload keeps history even when placing the head fails`() = runTest {
        val older = makeServerSave(id = 1L, fileName = "Pok\u00e9mon Violet [2024-01-15 12-00-00].srm", updatedAt = "2025-01-10T00:00:00Z")
        val head = makeServerSave(id = 2L, fileName = "Pok\u00e9mon Violet.srm", updatedAt = "2025-01-15T00:00:00Z")
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(older, head)
        coEvery { mockApiClient.downloadToCache(any(), any(), any()) } returns 10L
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Error("network error")

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify { mockApiClient.downloadToCache(1L, 1L, "autosave") }
    }

    @Test
    fun `syncSavesForNewDownload save sync disabled skips entirely`() = runTest {
        val disabledPrefs = UserPreferences(saveSyncEnabled = false)
        every { userPreferencesRepository.preferences } returns MutableStateFlow(disabledPrefs)

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify(exactly = 0) { mockApiClient.checkSavesForGame(any(), any()) }
    }

    // --- downloadPendingServerSaves ---

    @Test
    fun `downloadPendingServerSaves downloads all pending entities`() = runTest {
        val entity1 = makeSyncEntity(id = 1L, gameId = 1L)
        val entity2 = makeSyncEntity(id = 2L, gameId = 1L, channelName = "slot1")
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(entity1, entity2)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(2, result)
    }

    @Test
    fun `a disk holding the local form of the last transfer is not a local change`() = runTest {
        val entity = makeSyncEntity(id = 1L, gameId = 1L).copy(
            localSavePath = "/saves/game.srm",
            lastUploadedHash = "server-form",
            localContentHash = "local-form"
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(entity)
        every { fileAccessLayer.exists("/saves/game.srm") } returns true
        coEvery { saveCacheManager.calculateLocalSaveHash("/saves/game.srm", 1L, "yuzu") } returns "local-form"
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(1, result)
        coVerify(exactly = 0) { saveSyncDao.upsert(match { it.syncStatus == SaveSyncEntity.STATUS_CONFLICT }) }
    }

    @Test
    fun `a disk that moved since the last transfer is left as a conflict`() = runTest {
        val entity = makeSyncEntity(id = 1L, gameId = 1L).copy(
            localSavePath = "/saves/game.srm",
            lastUploadedHash = "server-form",
            localContentHash = "local-form"
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(entity)
        every { fileAccessLayer.exists("/saves/game.srm") } returns true
        coEvery { saveCacheManager.calculateLocalSaveHash("/saves/game.srm", 1L, "yuzu") } returns "new-progress"

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
        coVerify(exactly = 0) { mockApiClient.downloadSave(any(), any(), any(), any(), any()) }
    }

    private fun activeAutosave(cachedAt: String) = com.nendo.argosy.data.local.entity.SaveCacheEntity(
        id = 50L,
        gameId = 1L,
        emulatorId = "yuzu",
        cachedAt = java.time.Instant.parse(cachedAt),
        saveSize = 10,
        cachePath = "x/save.zip",
        channelName = "autosave"
    )

    @Test
    fun `an older save in the ROM-named slot is not placed over a newer active autosave`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(1L) } returns activeAutosave("2026-10-27T07:39:57Z")
        val legacySlot = makeSyncEntity(id = 7L, channelName = "Pokemon Violet").copy(
            serverUpdatedAt = java.time.Instant.parse("2026-06-01T00:01:24Z")
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(legacySlot)

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
        coVerify(exactly = 0) { mockApiClient.downloadSave(any(), any(), any(), any(), any()) }
        coVerify { saveSyncDao.upsert(match { it.id == 7L && it.syncStatus == SaveSyncEntity.STATUS_SYNCED }) }
    }

    @Test
    fun `a pending save with no server time is downloaded, never settled from its local time`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(1L) } returns activeAutosave("2026-10-27T07:39:57Z")
        val unknownTime = makeSyncEntity(id = 7L).copy(
            localUpdatedAt = java.time.Instant.parse("2026-06-01T00:01:24Z")
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(unknownTime)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

        assertEquals(1, orchestrator.downloadPendingServerSaves())
        coVerify(exactly = 0) { saveSyncDao.upsert(match { it.id == 7L && it.syncStatus == SaveSyncEntity.STATUS_SYNCED }) }
    }

    @Test
    fun `a superseded save still awaiting a conflict decision keeps its conflict`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(1L) } returns activeAutosave("2026-10-27T07:39:57Z")
        val conflicted = makeSyncEntity(id = 7L).copy(
            serverUpdatedAt = java.time.Instant.parse("2026-06-01T00:01:24Z"),
            syncStatus = SaveSyncEntity.STATUS_CONFLICT
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(conflicted)

        assertEquals(0, orchestrator.downloadPendingServerSaves())
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `a newer save in the ROM-named slot replaces the active autosave`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(1L) } returns activeAutosave("2026-06-01T00:00:00Z")
        val legacySlot = makeSyncEntity(id = 7L, channelName = "Pokemon Violet").copy(
            serverUpdatedAt = java.time.Instant.parse("2026-10-27T07:39:57Z")
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(legacySlot)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Success()

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(1, result)
    }

    @Test
    fun `a server save from the same moment as the active save is not placed again`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(1L) } returns activeAutosave("2026-10-27T07:39:57Z")
        val autosave = makeSyncEntity(id = 7L).copy(
            serverUpdatedAt = java.time.Instant.parse("2026-10-27T07:39:57Z")
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(autosave)

        assertEquals(0, orchestrator.downloadPendingServerSaves())
        coVerify(exactly = 0) { mockApiClient.downloadSave(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a pending save for a slot that is not active stays on the server`() = runTest {
        coEvery { activeSaveRepository.getActiveRow(1L) } returns activeAutosave("2026-06-01T00:00:00Z")
        val otherSlot = makeSyncEntity(id = 8L, channelName = "checkpoint").copy(
            serverUpdatedAt = java.time.Instant.parse("2026-10-27T07:39:57Z")
        )
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(otherSlot)

        assertEquals(0, orchestrator.downloadPendingServerSaves())
        coVerify(exactly = 0) { mockApiClient.downloadSave(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    @Test
    fun `downloadPendingServerSaves download failure does not count as success`() = runTest {
        val entity = makeSyncEntity(id = 1L, gameId = 1L)
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(entity)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns SaveSyncResult.Error("failed")

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
    }

    @Test
    fun `downloadPendingServerSaves empty pending list is no-op`() = runTest {
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns emptyList()

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
        coVerify(exactly = 0) { mockApiClient.downloadSave(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `downloadPendingServerSaves NeedsHardcoreResolution parks row and drops from queue`() = runTest {
        val entity = makeSyncEntity(id = 1L, gameId = 1L)
        coEvery { saveSyncDao.getPendingDownloads(any()) } returns listOf(entity)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any(), any()) } returns
            SaveSyncResult.NeedsHardcoreResolution(
                tempFilePath = "/tmp/save",
                gameId = 1L,
                gameName = testGame.title,
                emulatorId = "yuzu",
                targetPath = "/storage/saves/save.srm",
                isFolderBased = false,
                channelName = null
            )

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
        assertEquals(emptyList<com.nendo.argosy.data.sync.SyncOperation>(),
            syncQueueManager.state.value.operations.filter { it.gameId == 1L })
        coVerify { saveSyncDao.upsert(match { it.id == 1L && it.syncStatus == SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION }) }
    }

    // --- helpers ---

    private fun makeServerSave(
        id: Long,
        fileName: String,
        updatedAt: String = "2025-01-15T12:00:00Z"
    ) = RomMSave(
        id = id,
        romId = 200L,
        userId = 1L,
        emulator = "yuzu",
        fileName = fileName,
        updatedAt = updatedAt
    )

    private fun makeSyncEntity(
        id: Long = 1L,
        gameId: Long = 1L,
        channelName: String? = null
    ) = SaveSyncEntity(
        id = id,
        gameId = gameId,
        rommId = 200L,
        emulatorId = "yuzu",
        channelName = channelName,
        rommSaveId = id,
        syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
    )

    private fun makePendingItem(
        id: Long,
        gameId: Long
    ) = PendingSyncQueueEntity(
        id = id,
        gameId = gameId,
        rommId = 200L,
        syncType = SyncType.SAVE_FILE,
        priority = SyncPriority.SAVE_FILE,
        payloadJson = """{"emulatorId":"yuzu"}"""
    )
}
