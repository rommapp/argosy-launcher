package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SaveSyncOrchestratorTest {

    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var pendingSyncQueueDao: PendingSyncQueueDao
    private lateinit var gameDao: GameDao
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var savePathResolver: SavePathResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var syncQueueManager: SyncQueueManager
    private lateinit var mockApiClient: SaveSyncApiClient
    private lateinit var apiClient: dagger.Lazy<SaveSyncApiClient>
    private lateinit var orchestrator: SaveSyncOrchestrator

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
        syncQueueManager = SyncQueueManager()
        mockApiClient = mockk(relaxed = true)
        apiClient = dagger.Lazy { mockApiClient }

        val prefs = UserPreferences(saveSyncEnabled = true)
        every { userPreferencesRepository.preferences } returns MutableStateFlow(prefs)

        coEvery { gameDao.getById(1L) } returns testGame

        orchestrator = SaveSyncOrchestrator(
            saveSyncDao = saveSyncDao,
            pendingSyncQueueDao = pendingSyncQueueDao,
            gameDao = gameDao,
            emulatorResolver = emulatorResolver,
            savePathResolver = savePathResolver,
            userPreferencesRepository = userPreferencesRepository,
            syncQueueManager = syncQueueManager,
            apiClient = apiClient
        )
    }

    // --- syncSavesForNewDownload ---

    @Test
    fun `syncSavesForNewDownload accented latest save creates entity with null channelName`() = runTest {
        val serverSave = makeServerSave(
            id = 1L,
            fileName = "Pok\u00e9mon Violet.srm",
            updatedAt = "2025-01-15T12:00:00Z"
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(serverSave)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any()) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify { saveSyncDao.upsert(match { it.channelName == null && it.gameId == 1L }) }
    }

    @Test
    fun `syncSavesForNewDownload accented channel save preserves accented channelName`() = runTest {
        val serverSave = makeServerSave(
            id = 1L,
            fileName = "Ch\u00e9ckpoint.srm",
            updatedAt = "2025-01-15T12:00:00Z"
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(serverSave)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any()) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify { saveSyncDao.upsert(match { it.channelName == "Ch\u00e9ckpoint" }) }
    }

    @Test
    fun `syncSavesForNewDownload accented filename with timestamp tag creates null channelName`() = runTest {
        val serverSave = makeServerSave(
            id = 1L,
            fileName = "Pok\u00e9mon Violet [2024-01-15 12-00-00].srm",
            updatedAt = "2025-01-15T12:00:00Z"
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(serverSave)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any()) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify { saveSyncDao.upsert(match { it.channelName == null }) }
    }

    @Test
    fun `syncSavesForNewDownload multiple server saves all get sync entities`() = runTest {
        val latestSave = makeServerSave(id = 1L, fileName = "Pok\u00e9mon Violet.srm")
        val channel1 = makeServerSave(id = 2L, fileName = "checkpoint.srm")
        val channel2 = makeServerSave(id = 3L, fileName = "backup.srm")
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(latestSave, channel1, channel2)
        coEvery { mockApiClient.downloadSave(any(), any(), any(), any()) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify(exactly = 3) { saveSyncDao.upsert(any()) }
        coVerify(exactly = 3) { mockApiClient.downloadSave(any(), any(), any(), any()) }
    }

    @Test
    fun `syncSavesForNewDownload download failure mid-loop continues to next save`() = runTest {
        val save1 = makeServerSave(id = 1L, fileName = "Pok\u00e9mon Violet.srm")
        val save2 = makeServerSave(id = 2L, fileName = "checkpoint.srm")
        coEvery { mockApiClient.checkSavesForGame(1L, 200L) } returns listOf(save1, save2)
        coEvery { mockApiClient.downloadSave(1L, "yuzu", null, skipBackup = true) } returns SaveSyncResult.Error("network error")
        coEvery { mockApiClient.downloadSave(1L, "yuzu", "checkpoint", skipBackup = true) } returns SaveSyncResult.Success()

        orchestrator.syncSavesForNewDownload(1L, 200L, "yuzu")

        coVerify(exactly = 2) { saveSyncDao.upsert(any()) }
        coVerify(exactly = 2) { mockApiClient.downloadSave(any(), any(), any(), any()) }
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
        coEvery { saveSyncDao.getPendingDownloads() } returns listOf(entity1, entity2)
        coEvery { mockApiClient.downloadSave(any(), any(), any()) } returns SaveSyncResult.Success()

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(2, result)
    }

    @Test
    fun `downloadPendingServerSaves download failure does not count as success`() = runTest {
        val entity = makeSyncEntity(id = 1L, gameId = 1L)
        coEvery { saveSyncDao.getPendingDownloads() } returns listOf(entity)
        coEvery { mockApiClient.downloadSave(any(), any(), any()) } returns SaveSyncResult.Error("failed")

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
    }

    @Test
    fun `downloadPendingServerSaves empty pending list is no-op`() = runTest {
        coEvery { saveSyncDao.getPendingDownloads() } returns emptyList()

        val result = orchestrator.downloadPendingServerSaves()

        assertEquals(0, result)
        coVerify(exactly = 0) { mockApiClient.downloadSave(any(), any(), any()) }
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
