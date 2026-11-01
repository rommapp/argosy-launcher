package com.nendo.argosy.data.download

import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerAutoStartTest {

    @get:Rule
    val storage = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var downloadQueueDao: DownloadQueueDao
    private lateinit var romMRepository: RomMRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var mediaDownloadManager: MediaDownloadManager
    private val inserted = slot<DownloadQueueEntity>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        downloadQueueDao = mockk(relaxed = true)
        romMRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        mediaDownloadManager = mockk(relaxed = true)
        every { romMRepository.isConnected() } returns false
        every { romMRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        coEvery { downloadQueueDao.getByGameId(any()) } returns null
        coEvery { downloadQueueDao.getById(any()) } returns null
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns emptyList()
        coEvery { downloadQueueDao.insert(capture(inserted)) } returns INSERTED_ID
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun manager(autoStart: Boolean): DownloadManager {
        every { preferencesRepository.userPreferences } returns flowOf(
            UserPreferences(
                autoStartDownloads = autoStart,
                romStoragePath = storage.root.absolutePath
            )
        )
        val gameDao = mockk<GameDao>(relaxed = true)
        coEvery { gameDao.getById(any()) } returns null
        val platformDao = mockk<PlatformDao>(relaxed = true)
        return DownloadManager(
            gameDao = gameDao,
            gameDiscDao = mockk(relaxed = true),
            gameFileDao = mockk(relaxed = true),
            downloadQueueDao = downloadQueueDao,
            platformDao = platformDao,
            romMRepository = romMRepository,
            preferencesRepository = preferencesRepository,
            soundManager = mockk(relaxed = true),
            m3uManager = mockk(relaxed = true),
            thermalManager = dagger.Lazy { mockk(relaxed = true) },
            mediaDownloadManager = dagger.Lazy { mediaDownloadManager },
            musicDirectoryManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            homeTileRepository = mockk(relaxed = true),
            homeTilePromptQueue = mockk(relaxed = true),
            customGridShapeStore = mockk(relaxed = true),
            extContentOrganizer = mockk(relaxed = true),
            romStagingManager = mockk(relaxed = true),
            platformFolderResolver = PlatformFolderResolver(
                context = mockk(relaxed = true),
                platformDao = platformDao,
                gameDao = gameDao,
                preferencesRepository = preferencesRepository
            ),
            scope = CoroutineScope(testDispatcher)
        )
    }

    private suspend fun DownloadManager.enqueueRom() = enqueueDownload(
        gameId = GAME_ID,
        rommId = ROMM_ID,
        fileName = "game.nes",
        gameTitle = "Test Game",
        platformSlug = "nes",
        coverPath = null
    )

    @Test
    fun `with auto start off a new download is paused`() = runTest {
        val manager = manager(autoStart = false)

        manager.enqueueRom()

        assertEquals(DownloadState.PAUSED.name, inserted.captured.state)
        assertEquals(DownloadState.PAUSED, manager.state.value.queue.single().state)
    }

    @Test
    fun `with auto start on a new download is queued`() = runTest {
        val manager = manager(autoStart = true)

        manager.enqueueRom()

        assertEquals(DownloadState.QUEUED.name, inserted.captured.state)
        assertEquals(DownloadState.QUEUED, manager.state.value.queue.single().state)
    }

    @Test
    fun `with auto start off a disc download is paused`() = runTest {
        val manager = manager(autoStart = false)

        manager.enqueueDiscDownload(
            gameId = GAME_ID,
            discId = 7L,
            discNumber = 1,
            rommId = ROMM_ID,
            fileName = "disc1.chd",
            gameTitle = "Test Game",
            platformSlug = "psx",
            coverPath = null
        )

        assertEquals(DownloadState.PAUSED.name, inserted.captured.state)
    }

    @Test
    fun `a retried rom starts immediately with auto start off`() = runTest {
        val manager = manager(autoStart = false)

        manager.requeueFailed(failed())

        assertEquals(DownloadState.QUEUED.name, inserted.captured.state)
        assertEquals(DownloadState.QUEUED, manager.state.value.queue.single().state)
    }

    @Test
    fun `a retried disc starts immediately with auto start off`() = runTest {
        val manager = manager(autoStart = false)

        manager.requeueFailed(failed().copy(discId = 7L, discNumber = 1, fileName = "disc1.chd"))

        assertEquals(DownloadState.QUEUED.name, inserted.captured.state)
    }

    @Test
    fun `a retried game file starts immediately with auto start off`() = runTest {
        val manager = manager(autoStart = false)

        manager.requeueFailed(failed().copy(gameFileId = 8L, fileCategory = "update"))

        assertEquals(DownloadState.QUEUED.name, inserted.captured.state)
    }

    private fun failed() = DownloadProgress(
        id = 5L,
        gameId = GAME_ID,
        rommId = ROMM_ID,
        fileName = "game.nes",
        gameTitle = "Test Game",
        platformSlug = "nes",
        coverPath = null,
        bytesDownloaded = 0L,
        totalBytes = 0L,
        state = DownloadState.FAILED
    )

    @Test
    fun `resume all paused resumes only paused downloads`() = runTest {
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns listOf(
            queued(id = 1L, gameId = 11L, state = DownloadState.PAUSED),
            queued(id = 2L, gameId = 12L, state = DownloadState.WAITING_FOR_STORAGE),
            queued(id = 3L, gameId = 13L, state = DownloadState.PAUSED),
            queued(id = 4L, gameId = 14L, state = DownloadState.QUEUED)
        )
        val manager = manager(autoStart = false)
        manager.state.first { it.queue.size == 4 }

        manager.resumeAllPaused()

        val states = manager.state.value.queue.associate { it.id to it.state }
        assertEquals(DownloadState.QUEUED, states[1L])
        assertEquals(DownloadState.WAITING_FOR_STORAGE, states[2L])
        assertEquals(DownloadState.QUEUED, states[3L])
        assertEquals(DownloadState.QUEUED, states[4L])

        advanceUntilIdle()
        coVerify(exactly = 1) { downloadQueueDao.updateState(1L, DownloadState.QUEUED.name, any()) }
        coVerify(exactly = 1) { downloadQueueDao.updateState(3L, DownloadState.QUEUED.name, any()) }
        coVerify(exactly = 0) { downloadQueueDao.updateState(2L, any(), any()) }
        coVerify(exactly = 0) { downloadQueueDao.updateState(4L, any(), any()) }
        verify(exactly = 0) { mediaDownloadManager.resumeDownload(any()) }
    }

    @Test
    fun `resume all paused with nothing paused changes nothing`() = runTest {
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns listOf(
            queued(id = 2L, gameId = 12L, state = DownloadState.WAITING_FOR_STORAGE)
        )
        val manager = manager(autoStart = false)
        manager.state.first { it.queue.size == 1 }

        manager.resumeAllPaused()
        advanceUntilIdle()

        assertEquals(DownloadState.WAITING_FOR_STORAGE, manager.state.value.queue.single().state)
        coVerify(exactly = 0) { downloadQueueDao.updateState(any(), any(), any()) }
    }

    private fun queued(id: Long, gameId: Long, state: DownloadState) = DownloadQueueEntity(
        id = id,
        gameId = gameId,
        rommId = gameId + 1000L,
        fileName = "game$gameId.nes",
        gameTitle = "Game $gameId",
        platformSlug = "nes",
        coverPath = null,
        bytesDownloaded = 0L,
        totalBytes = 0L,
        state = state.name,
        errorReason = null,
        tempFilePath = null
    )

    @Test
    fun `restart preserves failed staging and retries the same owned row`() = runTest {
        val part = java.io.File(storage.newFolder("staging"), "game.nes.tmp")
        part.writeBytes(ByteArray(2300))
        val row = queued(5L, GAME_ID, DownloadState.FAILED).copy(
            tempFilePath = part.absolutePath,
            bytesDownloaded = 17L,
            totalBytes = 9000L,
            ownerUserId = 42L,
            selectedFileIds = "8,9",
            resumeValidator = "\"version-1\"",
            responseTotalBytes = 9000L,
            requestKey = "synthetic"
        )
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns listOf(row)
        coEvery { downloadQueueDao.getById(row.id) } returns row
        val manager = manager(autoStart = false)
        manager.state.first { it.completed.size == 1 }
        assertEquals(2300L, manager.state.value.completed.single().bytesDownloaded)

        manager.retryDownload(row.id)
        val retried = manager.state.first { it.queue.size == 1 }.queue.single()

        assertEquals(row.id, retried.id)
        assertEquals(listOf(8L, 9L), retried.selectedFileIds)
        assertEquals(2300L, retried.bytesDownloaded)
        assertEquals(2300L, part.length())
        coVerify(exactly = 0) { downloadQueueDao.clearFailed() }
        coVerify(exactly = 0) { downloadQueueDao.deleteById(any()) }
        coVerify(exactly = 0) { downloadQueueDao.insert(any()) }
        coVerify(exactly = 0) { downloadQueueDao.updateTransferCheckpoint(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `restart reconciles paused owner separated path with disk length`() = runTest {
        val part = java.io.File(storage.newFolder("owner", "addons"), "game.nes.tmp")
        part.writeBytes(ByteArray(4096))
        val row = queued(5L, GAME_ID, DownloadState.PAUSED).copy(
            tempFilePath = part.absolutePath, bytesDownloaded = 9000L,
            resumeValidator = "\"version-1\"", responseTotalBytes = 12000L
        )
        coEvery { downloadQueueDao.getRecoverableDownloads() } returns listOf(row)
        val manager = manager(autoStart = false)
        val restored = manager.state.first { it.queue.size == 1 }.queue.single()
        assertEquals(4096L, restored.bytesDownloaded)
        assertEquals(DownloadState.PAUSED, restored.state)
        assertEquals(4096L, part.length())
    }

    @Test
    fun `concurrent admissions reserve one queue row`() = runTest {
        val manager = manager(autoStart = true)
        List(8) { async { manager.enqueueRom() } }.awaitAll()
        assertEquals(1, manager.state.value.queue.size)
        coVerify(exactly = 1) { downloadQueueDao.insert(any()) }
    }

    private companion object {
        const val GAME_ID = 123L
        const val ROMM_ID = 456L
        const val INSERTED_ID = 99L
    }
}
