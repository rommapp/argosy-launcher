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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        coEvery { dao.getRecoverableDownloads() } answers {
            rows.values.filter { it.state in recoverableStates }.sortedBy { it.createdAt }
        }
        coEvery { dao.clearCompleted() } answers {
            rows.entries.removeAll { it.value.state == DownloadState.COMPLETED.name }
            Unit
        }
        coEvery { dao.getById(any()) } answers { rows[firstArg()] }
        coEvery { dao.insert(any()) } answers {
            val row = firstArg<DownloadQueueEntity>()
            val id = maxOf(nextId, (rows.keys.maxOrNull() ?: 0L) + 1)
            nextId = id + 1
            rows[id] = row.copy(id = id)
            id
        }
        coEvery { dao.update(any()) } answers {
            val row = firstArg<DownloadQueueEntity>()
            if (rows.containsKey(row.id)) rows[row.id] = row
        }
        coEvery { dao.updateState(any(), any(), any()) } answers {
            val id = firstArg<Long>()
            rows[id]?.let { rows[id] = it.copy(state = secondArg(), errorReason = thirdArg()) }
            Unit
        }
        coEvery { dao.deleteById(any()) } answers { rows.remove(firstArg()); Unit }
    }

    @After fun teardown() = Dispatchers.resetMain()

    private fun TestScope.manager(): DownloadManager {
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userPreferences } returns flowOf(UserPreferences(
            autoStartDownloads = false, romStoragePath = storage.root.absolutePath
        ))
        val games = mockk<GameDao>(relaxed = true)
        coEvery { games.getById(any()) } returns null
        val platforms = mockk<PlatformDao>(relaxed = true)
        val staging = mockk<RomStagingManager>(relaxed = true)
        every { staging.stagingRoot } returns File(storage.root, "staging")
        every { staging.list() } returns emptyList()
        return DownloadManager(
            gameDao = games, gameDiscDao = mockk(relaxed = true), gameFileDao = mockk(relaxed = true),
            downloadQueueDao = dao, platformDao = platforms, romMRepository = repository,
            preferencesRepository = preferences, soundManager = mockk(relaxed = true),
            m3uManager = mockk(relaxed = true), thermalManager = dagger.Lazy { mockk(relaxed = true) },
            mediaDownloadManager = dagger.Lazy { mockk(relaxed = true) },
            musicDirectoryManager = mockk(relaxed = true), attributionRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true), homeTileRepository = mockk(relaxed = true),
            homeTilePromptQueue = mockk(relaxed = true), customGridShapeStore = mockk(relaxed = true),
            extContentOrganizer = mockk(relaxed = true), romStagingManager = staging,
            platformFolderResolver = PlatformFolderResolver(mockk(relaxed = true), platforms, games, preferences),
            scope = backgroundScope
        )
    }

    private fun row(id: Long = 7, state: DownloadState = DownloadState.FAILED): DownloadQueueEntity {
        nextId = maxOf(nextId, id + 1)
        val part = File(storage.root, "retained/$id/game.zip.tmp")
        part.parentFile!!.mkdirs()
        part.writeBytes(ByteArray(2048) { 17 })
        return DownloadQueueEntity(
            id = id, gameId = 1, rommId = 10, fileName = "game.zip", gameTitle = "Game",
            platformSlug = "nes", coverPath = null, bytesDownloaded = part.length(), totalBytes = 8192,
            state = state.name, errorReason = null, tempFilePath = part.absolutePath,
            selectedFileIds = "9,3,9", isMultiFileRom = true, ownerUserId = 42,
            requestKey = "original?files=9,3,9", resumeValidator = "etag", validatedBytes = 2048
        )
    }

    private suspend fun DownloadManager.enqueue(
        selection: List<Long>? = listOf(3, 9), gameId: Long = 1, fileName: String = "game.zip"
    ) = enqueueDownload(gameId, 10, fileName, "New title", "nes", null, selectedFileIds = selection)

    private val recoverableStates = setOf(
        "QUEUED", "PAUSED", "DOWNLOADING", "EXTRACTING", "MOVING", "WAITING_FOR_STORAGE",
        "RETRYING", "WAITING_FOR_NETWORK", "FAILED"
    )

    private suspend fun awaitGate(gate: CompletableDeferred<Unit>) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { gate.await() }
    }

    private suspend fun DownloadManager.enqueueExact(retained: DownloadQueueEntity): DownloadEnqueueResult =
        when {
            retained.discId != null -> enqueueDiscDownload(
                retained.gameId, retained.discId, retained.discNumber!!, retained.rommId,
                retained.fileName, retained.gameTitle, platformSlug = retained.platformSlug,
                coverPath = null, startNow = true
            )
            retained.gameFileId != null -> enqueueGameFileDownload(
                retained.gameId, retained.gameFileId, retained.rommId, retained.fileName,
                retained.fileCategory!!, retained.gameTitle, retained.platformSlug, null, startNow = true
            )
            else -> enqueueDownload(
                retained.gameId, retained.rommId, retained.fileName, retained.gameTitle,
                retained.platformSlug, null, selectedFileIds = listOf(3, 9), startNow = true
            )
        }

    private suspend fun TestScope.cancelBeforeReadmission(retained: DownloadQueueEntity) {
        rows[retained.id] = retained
        nextId = retained.id + 1
        val manager = manager()
        manager.state.first { it.queue.any { item -> item.id == retained.id } }
        val cleanupEntered = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        coEvery { dao.deleteById(retained.id) } coAnswers {
            cleanupEntered.complete(Unit)
            awaitGate(releaseCleanup)
            rows.remove(retained.id)
            Unit
        }
        manager.cancelDownload(retained.rommId)
        awaitGate(cleanupEntered)
        val admission = async(start = CoroutineStart.UNDISPATCHED) { manager.enqueueExact(retained) }
        runCurrent()
        assertFalse(admission.isCompleted)
        releaseCleanup.complete(Unit)
        val accepted = admission.await() as DownloadEnqueueResult.Accepted
        assertTrue(accepted.downloadId > retained.id)
        assertEquals(DownloadState.QUEUED.name, rows[accepted.downloadId]?.state)
        assertFalse(rows.containsKey(retained.id))
        assertEquals(listOf(accepted.downloadId), manager.state.value.queue.map { it.id })
        assertTrue(manager.state.value.activeDownloads.none { it.id == retained.id })
        coVerify(exactly = 1) { dao.deleteById(retained.id) }
    }

    @Test fun `base admission waits for paused cancellation cleanup`() = runTest {
        cancelBeforeReadmission(row(state = DownloadState.PAUSED))
    }

    @Test fun `disc admission waits for paused cancellation cleanup`() = runTest {
        cancelBeforeReadmission(row(state = DownloadState.PAUSED).copy(
            discId = 80, discNumber = 1, selectedFileIds = null, isMultiFileRom = false
        ))
    }

    @Test fun `game file admission waits for paused cancellation cleanup`() = runTest {
        cancelBeforeReadmission(row(state = DownloadState.PAUSED).copy(
            gameFileId = 90, fileCategory = "dlc", selectedFileIds = null, isMultiFileRom = false
        ))
    }

    @Test fun `recoverable snapshot disappearing before reuse creates a persisted replacement`() = runTest {
        val retained = row(state = DownloadState.PAUSED)
        rows[retained.id] = retained
        nextId = retained.id + 1
        val manager = manager()
        manager.state.first { it.queue.size == 1 }
        var snapshots = 0
        coEvery { dao.getRecoverableDownloads() } answers {
            snapshots++
            check(snapshots <= 4)
            val snapshot = rows.values.toList()
            rows.remove(retained.id)
            snapshot
        }
        val result = manager.enqueueExact(retained) as DownloadEnqueueResult.Accepted
        assertTrue(result.downloadId > retained.id)
        assertEquals(DownloadState.QUEUED.name, rows[result.downloadId]?.state)
        assertEquals(listOf(result.downloadId), manager.state.value.queue.map { it.id })
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test fun `memory only paused candidate cannot accept a missing persisted row`() = runTest {
        val retained = row(state = DownloadState.PAUSED)
        rows[retained.id] = retained
        nextId = retained.id + 1
        val manager = manager()
        manager.state.first { it.queue.size == 1 }
        rows.remove(retained.id)
        val result = manager.enqueueExact(retained) as DownloadEnqueueResult.Accepted
        assertTrue(result.downloadId > retained.id)
        assertEquals(DownloadState.QUEUED.name, rows[result.downloadId]?.state)
        assertEquals(listOf(result.downloadId), manager.state.value.queue.map { it.id })
    }

    private fun DownloadManager.removeHistory(id: Long, clear: Boolean) {
        if (clear) clearFinished() else removeFromCompleted(id)
    }

    private suspend fun TestScope.historyRemovalBeforeAdmission(clear: Boolean) {
        val retained = row()
        rows[retained.id] = retained
        nextId = retained.id + 1
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        val deletionEntered = CompletableDeferred<Unit>()
        val releaseDeletion = CompletableDeferred<Unit>()
        coEvery { dao.deleteById(retained.id) } coAnswers {
            deletionEntered.complete(Unit)
            awaitGate(releaseDeletion)
            rows.remove(retained.id)
            Unit
        }
        manager.removeHistory(retained.id, clear)
        awaitGate(deletionEntered)
        val admission = async(start = CoroutineStart.UNDISPATCHED) { manager.enqueueExact(retained) }
        runCurrent()
        assertFalse(admission.isCompleted)
        releaseDeletion.complete(Unit)
        val result = admission.await() as DownloadEnqueueResult.Accepted
        assertTrue(result.downloadId > retained.id)
        assertEquals(DownloadState.QUEUED.name, rows[result.downloadId]?.state)
        assertEquals(listOf(result.downloadId), manager.state.value.queue.map { it.id })
        assertTrue(manager.state.value.completed.isEmpty())
    }

    @Test fun `finished removal owns cleanup before identical admission`() = runTest {
        historyRemovalBeforeAdmission(clear = false)
    }

    @Test fun `finished clear owns cleanup before identical admission`() = runTest {
        historyRemovalBeforeAdmission(clear = true)
    }

    private suspend fun TestScope.reuseBeforeHistoryRemoval(clear: Boolean) {
        val retained = row()
        rows[retained.id] = retained
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        val updateEntered = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        coEvery { dao.updateState(retained.id, DownloadState.QUEUED.name, any()) } coAnswers {
            updateEntered.complete(Unit)
            awaitGate(releaseUpdate)
            rows[retained.id] = retained.copy(state = DownloadState.QUEUED.name)
        }
        val admission = async { manager.enqueueExact(retained) }
        awaitGate(updateEntered)
        manager.removeHistory(retained.id, clear)
        runCurrent()
        assertFalse(admission.isCompleted)
        releaseUpdate.complete(Unit)
        assertEquals(DownloadEnqueueResult.Accepted(retained.id), admission.await())
        runCurrent()
        manager.enqueue(gameId = 2, fileName = "barrier.zip")
        assertEquals(DownloadState.QUEUED.name, rows[retained.id]?.state)
        assertTrue(manager.state.value.queue.any { it.id == retained.id })
        coVerify(exactly = 0) { dao.deleteById(retained.id) }
    }

    @Test fun `stale finished removal cannot delete reused queued row`() = runTest {
        reuseBeforeHistoryRemoval(clear = false)
    }

    @Test fun `stale finished clear cannot delete reused queued row`() = runTest {
        reuseBeforeHistoryRemoval(clear = true)
    }

    @Test fun `completed clear retains failures and finished clear removes them`() = runTest {
        val failed = row()
        rows[failed.id] = failed
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        manager.clearCompleted()
        runCurrent()
        assertEquals(listOf(failed.id), manager.state.value.completed.map { it.id })
        assertEquals(setOf(failed.id), rows.keys)
        manager.clearFinished()
        manager.state.first { it.completed.isEmpty() }
        assertTrue(rows.isEmpty())
        coVerify(exactly = 1) { dao.clearCompleted() }
        coVerify(exactly = 0) { dao.clearFinished() }
    }

    @Test fun `reordered selection reuses failed row without changing serialization owner or checkpoint`() = runTest {
        val retained = row()
        rows[retained.id] = retained
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        assertEquals(DownloadEnqueueResult.Accepted(retained.id), manager.enqueue(listOf(3, 9, 3)))
        assertEquals(retained.copy(state = DownloadState.QUEUED.name), rows[retained.id])
        assertEquals(listOf(9L, 3L, 9L), manager.state.value.queue.single().selectedFileIds)
        assertEquals(2048L, File(retained.tempFilePath!!).length())
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify(exactly = 1) { dao.updateState(retained.id, DownloadState.QUEUED.name, any()) }
    }

    @Test fun `changed selection conflicts for failed and paused rows preserving partial and verified files`() = runTest {
        for (state in listOf(DownloadState.FAILED, DownloadState.PAUSED)) {
            val retained = row(state = state)
            rows[retained.id] = retained
            val target = File(File(retained.tempFilePath!!).parentFile, retained.fileName)
            target.writeBytes(ByteArray(8192) { 23 })
            val manager = manager()
            manager.state.first { it.queue.size + it.completed.size == 1 }
            assertEquals(DownloadEnqueueResult.Conflict(retained.id), manager.enqueue(listOf(4, 9)))
            assertEquals(retained, rows[retained.id])
            assertEquals(2048L, File(retained.tempFilePath).length())
            assertTrue(target.readBytes().all { it == 23.toByte() })
        }
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.deleteById(any()) }
    }

    @Test fun `base logical slot conflicts even when filename changes`() = runTest {
        val retained = row()
        rows[retained.id] = retained
        assertEquals(DownloadEnqueueResult.Conflict(retained.id), manager().enqueue(fileName = "other.zip"))
        assertEquals(1, rows.size)
    }

    @Test fun `new selection is sorted and deduplicated`() = runTest {
        manager().enqueue(listOf(9, 3, 9))
        assertEquals("3,9", rows.values.single().selectedFileIds)
    }

    @Test fun `malformed retained selection conflicts with valid subset but permits unrelated request`() = runTest {
        val retained = row().copy(selectedFileIds = "3, invalid, 9")
        rows[retained.id] = retained
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        assertEquals(DownloadEnqueueResult.Conflict(retained.id), manager.enqueue(listOf(3, 9)))
        assertEquals(retained, rows[retained.id])
        assertTrue(manager.enqueue(gameId = 2, fileName = "unrelated.zip") is DownloadEnqueueResult.Accepted)
        assertEquals(retained, rows[retained.id])
    }

    @Test fun `whitespace selection retries same row without rewriting retained selection`() = runTest {
        val retained = row().copy(selectedFileIds = " 9, 3 ,9 ")
        rows[retained.id] = retained
        val manager = manager()
        manager.state.first { it.completed.size == 1 }
        assertEquals(DownloadEnqueueResult.Accepted(retained.id), manager.enqueue(listOf(3, 9)))
        assertEquals(retained.copy(state = DownloadState.QUEUED.name), rows[retained.id])
    }

    @Test fun `completed request is not reused`() = runTest {
        val retained = row(state = DownloadState.COMPLETED)
        rows[retained.id] = retained
        val result = manager().enqueue()
        assertEquals(DownloadEnqueueResult.Accepted(8), result)
    }

    @Test fun `distinct disc and base are independent with different transfer paths`() = runTest {
        val retained = row().copy(discId = 80, discNumber = 1, fileName = "disc.chd")
        rows[retained.id] = retained
        assertEquals(DownloadEnqueueResult.Accepted(8), manager().enqueue())
        assertEquals(retained.discId, rows[7]?.discId)
    }

    @Test fun `retained verified target conflicts across different games`() = runTest {
        val retained = row().copy(tempFilePath = File(storage.root, "nes/game.zip.tmp").absolutePath)
        rows[retained.id] = retained
        assertEquals(DownloadEnqueueResult.Conflict(7), manager().enqueue(gameId = 2))
        assertEquals(1, rows.size)
    }

    @Test fun `concurrent identical and incompatible admissions insert at most one row`() = runTest {
        val manager = manager()
        val results = List(12) { index -> async {
            manager.enqueue(if (index % 2 == 0) listOf(3, 9) else listOf(4, 9))
        } }.awaitAll()
        assertEquals(1, rows.size)
        assertTrue(results.any { it is DownloadEnqueueResult.Accepted })
        assertTrue(results.any { it is DownloadEnqueueResult.Conflict })
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test fun `startup refuses recovered shared transfer path before transfer mutation`() = runTest {
        val first = row(state = DownloadState.QUEUED)
        val second = first.copy(id = 8, gameId = 2)
        rows[first.id] = first
        rows[second.id] = second
        every { repository.isConnected() } returns true
        val manager = manager()
        manager.state.first { it.completed.size == 2 }
        assertTrue(rows.values.all { it.errorReason == DownloadFailureReasonCodec.encode(DownloadFailureReason.RequestConflict) })
        assertEquals(2048L, File(first.tempFilePath!!).length())
        coVerify(exactly = 0) { dao.updateTransferPath(any(), any(), any()) }
    }

    @Test fun `redownload deletes only selected base recovery row and leaves sibling`() = runTest {
        val base = row()
        val sibling = row(8).copy(discId = 80, discNumber = 1, fileName = "disc.chd")
        rows[base.id] = base
        rows[sibling.id] = sibling
        val manager = manager()
        manager.state.first { it.completed.size == 2 }
        manager.deleteFileAndRedownload(1)
        assertEquals(setOf(8L), rows.keys)
        assertTrue(File(sibling.tempFilePath!!).exists())
        coVerify(exactly = 0) { dao.deleteByGameId(any()) }
    }

    @Test fun `ambiguous base recovery refuses extraction and redownload without deletion`() = runTest {
        rows[7] = row()
        rows[8] = row(8)
        val manager = manager()
        manager.state.first { it.completed.size == 2 }
        assertTrue(manager.retryExtraction(1) is DownloadManager.ExtractionResult.Failure)
        manager.deleteFileAndRedownload(1)
        assertEquals(2, rows.size)
        coVerify(exactly = 0) { dao.deleteById(any()) }
        coVerify(exactly = 0) { dao.deleteByGameId(any()) }
    }

    @Test fun `direct extraction recovery deletes only base row and retains disc sibling`() = runTest {
        val base = row().copy(fileName = "game.nes", selectedFileIds = null, isMultiFileRom = false)
        val target = File(File(base.tempFilePath!!).parentFile, base.fileName)
        target.writeBytes(ByteArray(2048) { 31 })
        val sibling = row(8).copy(discId = 80, fileName = "disc.chd")
        rows[7] = base
        rows[8] = sibling
        val manager = manager()
        manager.state.first { it.completed.size == 2 }
        assertTrue(manager.retryExtraction(1) is DownloadManager.ExtractionResult.Success)
        assertEquals(setOf(8L), rows.keys)
        assertTrue(File(sibling.tempFilePath!!).exists())
        coVerify(exactly = 0) { dao.deleteByGameId(any()) }
    }

    @Test fun `direct extraction recovery refuses shared target without deleting either row`() = runTest {
        val base = row()
        val target = File(File(base.tempFilePath!!).parentFile, base.fileName)
        target.writeBytes(ByteArray(2048) { 31 })
        rows[7] = base
        rows[8] = base.copy(id = 8, gameId = 2)
        val manager = manager()
        manager.state.first { it.completed.size == 2 }
        assertEquals(
            DownloadManager.ExtractionResult.Failure(DownloadFailureReason.RequestConflict),
            manager.retryExtraction(1)
        )
        assertTrue(target.exists())
        assertEquals(2, rows.size)
        coVerify(exactly = 0) { dao.deleteById(any()) }
    }
}
