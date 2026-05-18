package com.nendo.argosy.ui.screens.savesync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.ConflictResolutionService
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncOperation
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncQueueState
import com.nendo.argosy.data.sync.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SaveSyncViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var pendingConflictDao: PendingConflictDao
    private lateinit var syncQueueManager: SyncQueueManager
    private lateinit var gameDao: GameDao
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var romMRepository: RomMRepository
    private lateinit var conflictResolutionService: ConflictResolutionService

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        saveSyncDao = mockk(relaxed = true)
        pendingConflictDao = mockk(relaxed = true)
        syncQueueManager = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        romMRepository = mockk(relaxed = true)
        conflictResolutionService = mockk(relaxed = true)

        every { saveSyncDao.observeAll() } returns flowOf(emptyList())
        every { saveSyncDao.observeSaveCountsByDevice() } returns flowOf(emptyList())
        every { pendingConflictDao.observeOpenConflicts() } returns flowOf(emptyList())
        every { syncQueueManager.state } returns MutableStateFlow(SyncQueueState())
        every { preferencesRepository.preferences } returns flowOf(UserPreferences())
        every { romMRepository.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("4.9.0", RomMCapabilities.from("4.9.0"))
        )
        coEvery { romMRepository.getRegisteredDevices() } returns emptyList()
        coEvery { gameDao.getByIds(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combine produces device card with connected = true when rommDeviceId set`() = runTest(testDispatcher) {
        val deviceId = "abcd-1234-5678-90ef"
        every { preferencesRepository.preferences } returns flowOf(UserPreferences(rommDeviceId = deviceId))
        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.deviceCard.isConnected)
        assertEquals(deviceId.takeLast(8), state.deviceCard.deviceIdShort)
    }

    @Test
    fun `device card reports connected on older RomM without a device id`() = runTest(testDispatcher) {
        every { preferencesRepository.preferences } returns flowOf(UserPreferences(rommDeviceId = null))
        every { romMRepository.connectionState } returns MutableStateFlow(
            ConnectionState.Connected("4.8.0", RomMCapabilities.from("4.8.0"))
        )
        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.deviceCard.isConnected)
        assertEquals(null, state.deviceCard.deviceIdShort)
    }

    @Test
    fun `device card reports not connected when RomM is disconnected`() = runTest(testDispatcher) {
        every { preferencesRepository.preferences } returns flowOf(UserPreferences(rommDeviceId = "abc-12345678"))
        every { romMRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(false, state.deviceCard.isConnected)
        assertEquals(null, state.deviceCard.deviceIdShort)
    }

    @Test
    fun `attention rows are produced from open conflicts and include game title`() = runTest(testDispatcher) {
        val game = makeGame(id = 42, title = "Persona 5")
        every { pendingConflictDao.observeOpenConflicts() } returns flowOf(
            listOf(
                PendingConflictEntity(
                    id = 7,
                    gameId = 42,
                    rommSaveId = 99,
                    fileName = "save.dat",
                    slot = "Slot 1",
                    emulator = "duckstation",
                    localUpdatedAt = Instant.parse("2024-01-01T10:00:00Z"),
                    serverUpdatedAt = Instant.parse("2024-01-01T09:00:00Z"),
                    reason = "hash"
                )
            )
        )
        coEvery { gameDao.getByIds(any()) } returns listOf(game)

        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.attentionRows.size)
        val row = state.attentionRows.single()
        assertEquals("Persona 5", row.title)
        assertEquals("Slot 1", row.channelName)
        assertTrue(row.isLocalNewer)
    }

    @Test
    fun `game rows are produced from save_sync rows joined with games`() = runTest(testDispatcher) {
        val game = makeGame(id = 1L, title = "Tekken")
        every { saveSyncDao.observeAll() } returns flowOf(
            listOf(
                SaveSyncEntity(
                    id = 100L,
                    gameId = 1L,
                    rommId = 1L,
                    emulatorId = "ppsspp",
                    channelName = null,
                    syncStatus = SaveSyncEntity.STATUS_SYNCED,
                    lastSyncedAt = Instant.now(),
                    lastSyncDeviceId = "device-A",
                    lastSyncDeviceName = "Retroid Pocket 5"
                )
            )
        )
        coEvery { gameDao.getByIds(listOf(1L)) } returns listOf(game)

        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.gameRows.size)
        val row = state.gameRows.single()
        assertEquals("Tekken", row.title)
        assertEquals("Retroid Pocket 5", row.lastSyncDeviceName)
        assertTrue(row.isJustSynced)
    }

    @Test
    fun `in progress rows surface PENDING and IN_PROGRESS ops`() = runTest(testDispatcher) {
        val queueState = MutableStateFlow(
            SyncQueueState(
                operations = listOf(
                    SyncOperation(
                        gameId = 1, gameName = "A", coverPath = null,
                        direction = SyncDirection.UPLOAD, status = SyncStatus.IN_PROGRESS, progress = 0.5f
                    ),
                    SyncOperation(
                        gameId = 2, gameName = "B", coverPath = null,
                        direction = SyncDirection.DOWNLOAD, status = SyncStatus.COMPLETED, progress = 1f
                    )
                )
            )
        )
        every { syncQueueManager.state } returns queueState
        coEvery { gameDao.getByIds(listOf(1L, 2L)) } returns listOf(makeGame(1, "A"), makeGame(2, "B"))

        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.inProgressRows.size)
        assertEquals(SyncDirection.UPLOAD, state.inProgressRows.single().direction)
    }

    @Test
    fun `confirm on focused attention row dispatches SKIP by default`() = runTest(testDispatcher) {
        val conflict = PendingConflictEntity(
            id = 7, gameId = 42, rommSaveId = 99, fileName = "save.dat",
            slot = "Slot 1", emulator = "duckstation",
            localUpdatedAt = Instant.now(), serverUpdatedAt = Instant.now()
        )
        every { pendingConflictDao.observeOpenConflicts() } returns flowOf(listOf(conflict))
        coEvery { pendingConflictDao.getById(7L) } returns conflict
        coEvery { gameDao.getByIds(any()) } returns listOf(makeGame(42, "P5"))

        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        var navTarget: Long? = null
        val handler = vm.createInputHandler(onBack = {}, onNavigateToGame = { navTarget = it })
        handler.onConfirm()
        advanceUntilIdle()

        coVerify { conflictResolutionService.resolve(conflict, ConflictResolution.SKIP) }
        assertEquals(null, navTarget)
    }

    @Test
    fun `D-pad left cycles Skip to Keep Server then Keep Local then clamps`() = runTest(testDispatcher) {
        val conflict = PendingConflictEntity(
            id = 7, gameId = 42, rommSaveId = 99, fileName = "save.dat",
            slot = "Slot 1", emulator = "duckstation",
            localUpdatedAt = Instant.now(), serverUpdatedAt = Instant.now()
        )
        every { pendingConflictDao.observeOpenConflicts() } returns flowOf(listOf(conflict))
        coEvery { pendingConflictDao.getById(7L) } returns conflict
        coEvery { gameDao.getByIds(any()) } returns listOf(makeGame(42, "P5"))

        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val handler = vm.createInputHandler(onBack = {}, onNavigateToGame = {})
        assertEquals(AttentionAction.SKIP, vm.uiState.value.attentionAction)
        handler.onLeft()
        advanceUntilIdle()
        assertEquals(AttentionAction.KEEP_SERVER, vm.uiState.value.attentionAction)
        handler.onLeft()
        advanceUntilIdle()
        assertEquals(AttentionAction.KEEP_LOCAL, vm.uiState.value.attentionAction)
        // Clamped at the left edge — stays on KEEP_LOCAL
        handler.onLeft()
        advanceUntilIdle()
        assertEquals(AttentionAction.KEEP_LOCAL, vm.uiState.value.attentionAction)

        handler.onConfirm()
        advanceUntilIdle()
        coVerify { conflictResolutionService.resolve(conflict, ConflictResolution.KEEP_LOCAL) }
    }

    @Test
    fun `confirm on focused synced game row navigates to game`() = runTest(testDispatcher) {
        every { saveSyncDao.observeAll() } returns flowOf(
            listOf(
                SaveSyncEntity(
                    id = 1L, gameId = 5L, rommId = 5L, emulatorId = "e", channelName = null,
                    syncStatus = SaveSyncEntity.STATUS_SYNCED
                )
            )
        )
        coEvery { gameDao.getByIds(listOf(5L)) } returns listOf(makeGame(5, "X"))

        val vm = build()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        var navTarget: Long? = null
        val handler = vm.createInputHandler(onBack = {}, onNavigateToGame = { navTarget = it })
        handler.onConfirm()
        advanceUntilIdle()

        assertEquals(5L, navTarget)
    }

    private fun build(): SaveSyncViewModel = SaveSyncViewModel(
        saveSyncDao = saveSyncDao,
        pendingConflictDao = pendingConflictDao,
        syncQueueManager = syncQueueManager,
        gameDao = gameDao,
        preferencesRepository = preferencesRepository,
        romMRepository = romMRepository,
        conflictResolutionService = conflictResolutionService
    )

    private fun makeGame(id: Long, title: String): GameEntity = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "psp",
        title = title,
        sortTitle = title,
        localPath = "/tmp/$title.iso",
        rommId = id,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )
}
