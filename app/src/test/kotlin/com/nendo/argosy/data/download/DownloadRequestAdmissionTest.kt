package com.nendo.argosy.data.download

import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadRequestAdmissionTest {
    @get:Rule val storage = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val rows = linkedMapOf<Long, DownloadQueueEntity>()
    private var nextId = 1L
    private val dao = mockk<DownloadQueueDao>(relaxed = true)
    private val repository = mockk<RomMRepository>(relaxed = true)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.isConnected() } returns false
        every { repository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        coEvery { dao.getRecoverableDownloads() } answers { rows.values.toList() }
        coEvery { dao.getById(any()) } answers { rows[firstArg()] }
        coEvery { dao.insert(any()) } answers {
            val id = nextId++
            rows[id] = firstArg<DownloadQueueEntity>().copy(id = id)
            id
        }
        coEvery { dao.updateRetainedPart(any(), any(), any()) } answers {
            val id = firstArg<Long>()
            rows[id]?.let { rows[id] = it.copy(tempFilePath = arg(1), bytesDownloaded = arg(2)) }
            Unit
        }
        coEvery { dao.updateState(any(), any(), any()) } answers {
            val id = firstArg<Long>()
            rows[id]?.let { rows[id] = it.copy(state = secondArg(), errorReason = thirdArg()) }
            Unit
        }
        coEvery { dao.deleteById(any()) } answers { rows.remove(firstArg()); Unit }
    }

    @After fun teardown() = Dispatchers.resetMain()

    private fun TestScope.manager(ownerUserId: Long? = null, autoStart: Boolean = false): DownloadManager {
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userPreferences } returns flowOf(UserPreferences(
            autoStartDownloads = autoStart, romStoragePath = storage.root.absolutePath
        ))
        val games = mockk<GameDao>(relaxed = true)
        coEvery { games.getById(any()) } returns null
        val platforms = mockk<PlatformDao>(relaxed = true)
        val staging = mockk<RomStagingManager>(relaxed = true)
        every { staging.stagingRoot } returns File(storage.root, "staging")
        every { staging.list() } returns emptyList()
        val syncPreferences = mockk<SyncPreferencesRepository>(relaxed = true)
        coEvery { syncPreferences.getRommUserId() } returns ownerUserId
        return DownloadManager(
            gameDao = games, gameDiscDao = mockk(relaxed = true), gameFileDao = mockk(relaxed = true),
            downloadQueueDao = dao, platformDao = platforms, romMRepository = repository,
            preferencesRepository = preferences, soundManager = mockk(relaxed = true),
            m3uManager = mockk(relaxed = true), thermalManager = dagger.Lazy { mockk(relaxed = true) },
            mediaDownloadManager = dagger.Lazy { mockk(relaxed = true) },
            musicDirectoryManager = mockk(relaxed = true), attributionRepository = mockk(relaxed = true),
            syncPreferencesRepository = syncPreferences, homeTileRepository = mockk(relaxed = true),
            homeTilePromptQueue = mockk(relaxed = true), customGridShapeStore = mockk(relaxed = true),
            extContentOrganizer = mockk(relaxed = true), romStagingManager = staging,
            platformFolderResolver = PlatformFolderResolver(mockk(relaxed = true), platforms, games, preferences),
            scope = backgroundScope
        )
    }

    private fun retained(): DownloadQueueEntity {
        val part = File(storage.root, "retained/game.zip.tmp")
        part.parentFile!!.mkdirs()
        part.writeBytes(ByteArray(2048) { 17 })
        val row = DownloadQueueEntity(
            id = nextId++, gameId = 1, rommId = 10, fileName = "game.zip", gameTitle = "Game",
            platformSlug = "nes", coverPath = null, bytesDownloaded = part.length(), totalBytes = 8192,
            state = DownloadState.FAILED.name, errorReason = null, tempFilePath = part.absolutePath,
            selectedFileIds = "3,9", isMultiFileRom = true,
            requestKey = "original?files=3,9", resumeValidator = "etag", validatedBytes = 2048
        )
        rows[row.id] = row
        return row
    }

    private suspend fun DownloadManager.enqueue(selection: List<Long> = listOf(3, 9)) =
        enqueueDownload(1, 10, "game.zip", "Game", "nes", null, selectedFileIds = selection)

    @Test fun `concurrent base duplicates insert once`() = runTest {
        val manager = manager()
        List(12) { async { manager.enqueue() } }.awaitAll()
        assertEquals(1, rows.size)
        assertEquals(1, manager.state.value.queue.size)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test fun `concurrent disc duplicates insert once`() = runTest {
        val manager = manager()
        List(12) { async {
            manager.enqueueDiscDownload(1, 80, 1, 10, "disc.chd", "Game", platformSlug = "psx", coverPath = null)
        } }.awaitAll()
        assertEquals(1, rows.size)
        assertEquals(80L, rows.values.single().discId)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test fun `concurrent game file duplicates insert once`() = runTest {
        val manager = manager()
        List(12) { async {
            manager.enqueueGameFileDownload(1, 90, 10, "addon.bin", "dlc", "Game", "nes", null)
        } }.awaitAll()
        assertEquals(1, rows.size)
        assertEquals(90L, rows.values.single().gameFileId)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test fun `same failed request retains row bytes validator and request key`() = runTest {
        val retained = retained()
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        manager.enqueue()
        assertEquals(retained.copy(state = DownloadState.PAUSED.name), rows[retained.id])
        assertEquals(2048L, File(retained.tempFilePath!!).length())
        assertEquals(retained.id, manager.state.value.queue.single().id)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test fun `second owner preserves failed row checkpoint and partial`() = runTest {
        val retained = retained().copy(ownerUserId = 42)
        rows[retained.id] = retained
        val part = File(retained.tempFilePath!!)
        val originalBytes = part.readBytes()
        val manager = manager(ownerUserId = 84)
        manager.state.first { it.completed.size == 1 }

        manager.enqueue()

        assertEquals(retained, rows[retained.id])
        assertArrayEquals(originalBytes, part.readBytes())
        assertEquals(retained.id, manager.state.value.completed.single().id)
        val replacement = rows.values.single { it.id != retained.id }
        assertEquals(84L, replacement.ownerUserId)
        assertEquals(replacement.id, manager.state.value.queue.single().id)
        assertNull(replacement.requestKey)
        assertNull(replacement.resumeValidator)
        coVerify(exactly = 0) { dao.deleteById(any()) }
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test fun `cancelling second owner preserves first owners allocated partial`() = runTest {
        cancelSecondOwner { it.enqueueDownload(1, 10, "game.bin", "Game", "nes", null) }
    }

    @Test fun `cancelling second owner disc preserves first owners allocated partial`() = runTest {
        cancelSecondOwner {
            it.enqueueDiscDownload(1, 80, 1, 10, "disc.chd", "Game", platformSlug = "psx", coverPath = null)
        }
    }

    @Test fun `cancelling second owner game file preserves first owners allocated partial`() = runTest {
        cancelSecondOwner { it.enqueueGameFileDownload(1, 90, 10, "addon.bin", "dlc", "Game", "nes", null) }
    }

    private suspend fun TestScope.cancelSecondOwner(enqueue: suspend (DownloadManager) -> Unit) {
        val firstOwner = manager(ownerUserId = 42)
        enqueue(firstOwner)
        firstOwner.state.first { it.queue.size == 1 }
        val allocated = rows.values.single()
        val part = File(allocated.tempFilePath!!)
        part.parentFile!!.mkdirs()
        val originalBytes = ByteArray(2048) { 17 }
        part.writeBytes(originalBytes)
        val retained = allocated.copy(
            state = DownloadState.FAILED.name, bytesDownloaded = part.length(), totalBytes = 8192,
            resumeValidator = "etag", validatedBytes = part.length(), requestKey = "original/${allocated.fileName}"
        )
        rows[retained.id] = retained
        val secondOwner = manager(ownerUserId = 84)
        secondOwner.state.first { it.completed.any { item -> item.id == retained.id } }

        enqueue(secondOwner)
        val replacement = rows.values.single { it.ownerUserId == 84L }
        assertTrue(replacement.id != retained.id)
        assertTrue(replacement.tempFilePath != retained.tempFilePath)
        secondOwner.cancelDownload(10)
        runCurrent()
        secondOwner.state.first { it.queue.none { item -> item.id == replacement.id } }

        assertEquals(retained, rows[retained.id])
        assertTrue("Cancelling another owner's request must preserve the retained partial", part.isFile)
        assertArrayEquals(originalBytes, part.readBytes())
    }

    @Test fun `identical failed enqueue stays paused when auto start is disabled`() = runTest {
        val retained = retained()
        val manager = manager()
        manager.state.first { it.completed.size == 1 }

        manager.enqueue()

        assertEquals(DownloadState.PAUSED.name, rows.getValue(retained.id).state)
        assertEquals(DownloadState.PAUSED, manager.state.value.queue.single().state)
    }

    @Test fun `identical failed enqueue queues when auto start is enabled`() = runTest {
        val retained = retained()
        val manager = manager(autoStart = true)
        manager.state.first { it.completed.size == 1 }

        manager.enqueue()

        assertEquals(DownloadState.QUEUED.name, rows.getValue(retained.id).state)
        assertEquals(DownloadState.QUEUED, manager.state.value.queue.single().state)
    }

    @Test fun `start now queues identical failed request with auto start disabled`() = runTest {
        val retained = retained()
        val manager = manager()
        manager.state.first { it.completed.size == 1 }

        manager.enqueueDownload(1, 10, "game.zip", "Game", "nes", null,
            selectedFileIds = listOf(3, 9), startNow = true)

        assertEquals(DownloadState.QUEUED.name, rows.getValue(retained.id).state)
        assertEquals(DownloadState.QUEUED, manager.state.value.queue.single().state)
    }

    @Test fun `explicit retry queues identical failed request with auto start disabled`() = runTest {
        val retained = retained()
        val manager = manager()
        manager.state.first { it.completed.size == 1 }

        manager.retryDownload(retained.id)
        val retried = manager.state.first { it.queue.any { item -> item.id == retained.id } }.queue.single()

        assertEquals(DownloadState.QUEUED.name, rows.getValue(retained.id).state)
        assertEquals(DownloadState.QUEUED, retried.state)
        assertEquals(retained.requestKey, rows.getValue(retained.id).requestKey)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test fun `base retry preserves failure when matching paused or queued work exists`() = runTest {
        retryWithDuplicate()
    }

    @Test fun `disc retry preserves failure when matching paused or queued work exists`() = runTest {
        retryWithDuplicate(discId = 80)
    }

    @Test fun `game file retry preserves failure when matching paused or queued work exists`() = runTest {
        retryWithDuplicate(gameFileId = 90)
    }

    private suspend fun TestScope.retryWithDuplicate(discId: Long? = null, gameFileId: Long? = null) {
        for (state in listOf(DownloadState.PAUSED, DownloadState.QUEUED)) {
            rows.clear()
            val base = discId == null && gameFileId == null
            val retained = retained().copy(
                ownerUserId = 42, discId = discId, discNumber = discId?.let { 1 },
                gameFileId = gameFileId, fileCategory = gameFileId?.let { "dlc" },
                selectedFileIds = if (base) "3,9" else null, isMultiFileRom = base
            )
            rows[retained.id] = retained
            val originalBytes = File(retained.tempFilePath!!).readBytes()
            val manager = manager(ownerUserId = 84)
            manager.state.first { it.completed.any { item -> item.id == retained.id } }
            val startNow = state == DownloadState.QUEUED
            when {
                discId != null -> manager.enqueueDiscDownload(
                    1, discId, 1, 10, "game.zip", "Game", platformSlug = "nes", coverPath = null,
                    startNow = startNow
                )
                gameFileId != null -> manager.enqueueGameFileDownload(
                    1, gameFileId, 10, "game.zip", "dlc", "Game", "nes", null, startNow = startNow
                )
                else -> manager.enqueueDownload(
                    1, 10, "game.zip", "Game", "nes", null,
                    selectedFileIds = listOf(3, 9), startNow = startNow
                )
            }
            val duplicate = manager.state.value.queue.single()

            manager.requeueFailed(manager.state.value.completed.single())

            assertEquals(DownloadState.FAILED.name, rows.getValue(retained.id).state)
            assertEquals(retained, rows.getValue(retained.id))
            assertArrayEquals(originalBytes, File(retained.tempFilePath).readBytes())
            assertEquals(retained.id, manager.state.value.completed.single().id)
            assertEquals(listOf(duplicate.id), manager.state.value.queue.map { it.id })
            assertEquals(state, manager.state.value.queue.single().state)
            assertTrue(manager.state.value.activeDownloads.isEmpty())
            assertEquals(2, rows.size)
        }
    }

    @Test fun `changed selection starts without old bytes or checkpoint`() = runTest {
        val retained = retained()
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        manager.enqueue(listOf(4, 9))
        val replacement = rows.values.single()
        assertEquals("4,9", replacement.selectedFileIds)
        assertEquals(0L, replacement.bytesDownloaded)
        assertNull(replacement.resumeValidator)
        assertNull(replacement.requestKey)
        assertNull(replacement.validatedBytes)
        assertFalse(File(retained.tempFilePath!!).exists())
        assertFalse(rows.containsKey(retained.id))
        coVerify(exactly = 1) { dao.insert(any()) }
    }
}
