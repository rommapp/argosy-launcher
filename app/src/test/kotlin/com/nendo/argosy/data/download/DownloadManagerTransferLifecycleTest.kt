package com.nendo.argosy.data.download

import android.content.Context
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
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
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Streaming

class DownloadManagerTransferLifecycleTest {
    @get:Rule val storage = TemporaryFolder()
    private val server = MockWebServer()
    private val rows = ConcurrentHashMap<Long, DownloadQueueEntity>()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val jobs = mutableListOf<kotlinx.coroutines.Job>()
    private val dao = mockk<DownloadQueueDao>(relaxed = true)
    private val repository = mockk<RomMRepository>(relaxed = true)
    private lateinit var staging: RomStagingManager
    private lateinit var destination: File
    private lateinit var part: File
    @Volatile private lateinit var api: FixtureApi
    @Volatile private var completionGate: CompletableDeferred<Unit>? = null
    private val completing = CompletableDeferred<Unit>()
    @Volatile private var linkedPath: String? = null
    private val bytes = ByteArray(10240) { ((it * 31 + 7) % 251).toByte() }

    interface FixtureApi {
        @Streaming @GET("fixture") @Headers("Accept-Encoding: identity")
        fun content(@Header("Range") range: String?, @Header("If-Range") ifRange: String?): Call<ResponseBody>
    }

    private fun api(server: MockWebServer): FixtureApi = Retrofit.Builder().baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build())
        .build().create(FixtureApi::class.java)

    @Before fun setup() {
        server.start()
        api = api(server)
        destination = storage.newFolder("destination")
        part = File(destination, "fixture.bin.tmp")
        val context = mockk<Context>()
        every { context.filesDir } returns storage.newFolder("private")
        staging = spyk(RomStagingManager(context))
        every { staging.availableBytes(any()) } returns 100_000_000_000L
        every { staging.internalAvailableBytes() } returns 100_000_000_000L
        every { repository.isConnected() } returns true
        every { repository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)
        every { repository.newDownloadCall(any(), any(), any(), any(), any(), any()) } answers {
            api.content(arg(2), arg(4))
        }
        every { repository.downloadRequestKey(any(), any(), any(), any()) } answers {
            api.content(null, null).request().url.toString()
        }
        coEvery { dao.getRecoverableDownloads() } answers {
            rows.values.filter { it.state != DownloadState.COMPLETED.name }.sortedBy { it.createdAt }
        }
        coEvery { dao.getById(any()) } answers { rows[firstArg()] }
        coEvery { dao.getByGameId(any()) } answers { rows.values.firstOrNull { it.gameId == firstArg<Long>() } }
        coEvery { dao.update(any()) } answers { firstArg<DownloadQueueEntity>().let { rows[it.id] = it }; Unit }
        coEvery { dao.updateState(any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val state = secondArg<String>()
            if (state == DownloadState.COMPLETED.name && completionGate != null) {
                completing.complete(Unit)
                completionGate!!.await()
            }
            rows.computeIfPresent(id) { _, row -> row.copy(state = state, errorReason = arg(2)) }
            Unit
        }
        coEvery { dao.updateProgress(any(), any()) } answers {
            rows.computeIfPresent(firstArg()) { _, row -> row.copy(bytesDownloaded = secondArg()) }; Unit
        }
        coEvery { dao.updateTransferPath(any(), any(), any()) } answers {
            rows.computeIfPresent(firstArg()) { _, row -> row.copy(tempFilePath = arg(1), requestKey = arg(2)) }; Unit
        }
        coEvery { dao.updateTransferCheckpoint(any(), any(), any(), any(), any()) } answers {
            rows.computeIfPresent(firstArg()) { _, row -> row.copy(
                resumeValidator = arg(1), responseTotalBytes = arg(2), validatedBytes = arg(3),
                representationChecksum = arg(4)
            ) }; Unit
        }
        coEvery { dao.updateRetry(any(), any(), any()) } answers {
            rows.computeIfPresent(firstArg()) { _, row -> row.copy(retryAttempt = arg(1), nextRetryAt = arg(2)) }; Unit
        }
        coEvery { dao.deleteById(any()) } answers { rows.remove(firstArg<Long>()); Unit }
        rows[1] = DownloadQueueEntity(
            id = 1, gameId = 10, rommId = 100, gameFileId = 20, fileCategory = "manual",
            fileName = "fixture.bin", gameTitle = "Synthetic fixture", platformSlug = "nes", coverPath = null,
            bytesDownloaded = 0, totalBytes = bytes.size.toLong(), state = DownloadState.QUEUED.name,
            errorReason = null, tempFilePath = part.absolutePath, ownerUserId = 42,
            requestKey = server.url("/fixture").toString(), selectedFileIds = "8,9"
        )
    }

    @After fun teardown() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
        dispatcher.close()
        server.shutdown()
    }

    private fun manager(): DownloadManager {
        val job = SupervisorJob().also { jobs += it }
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userPreferences } returns flowOf(UserPreferences(
            romStoragePath = destination.absolutePath, maxConcurrentDownloads = 1,
            stageDownloadsInternally = false
        ))
        val folders = mockk<PlatformFolderResolver>()
        coEvery { folders.storageBase() } returns destination
        coEvery { folders.downloadDirForGame(any(), any()) } returns destination
        val gameDao = mockk<GameDao>(relaxed = true)
        coEvery { gameDao.getById(any()) } returns null
        coEvery { gameDao.getByPath(any()) } returns null
        coEvery { gameDao.updateLocalPath(any(), any(), any(), any(), any()) } answers {
            linkedPath = arg(1)
            Unit
        }
        val thermal = mockk<DownloadThermalManager>()
        every { thermal.thermalStatus } returns MutableStateFlow(ThermalStatus())
        return DownloadManager(
            gameDao, mockk(relaxed = true), mockk(relaxed = true), dao, mockk(relaxed = true),
            repository, preferences, mockk(relaxed = true), mockk(relaxed = true), dagger.Lazy { thermal },
            dagger.Lazy { mockk<MediaDownloadManager>(relaxed = true) }, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), staging, folders,
            CoroutineScope(job + dispatcher), DownloadConnectivity.AlwaysAvailable
        )
    }

    private fun full() = MockResponse().setBody(Buffer().write(bytes)).setHeader("ETag", "\"v1\"")
    private fun partial(offset: Int) = MockResponse().setResponseCode(206)
        .setBody(Buffer().write(bytes, offset, bytes.size - offset)).setHeader("ETag", "\"v1\"")
        .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
    private suspend fun DownloadManager.awaitState(state: DownloadState) = withTimeout(15000) {
        this@awaitState.state.first { snapshot ->
            (snapshot.activeDownloads + snapshot.queue + snapshot.completed).any { it.id == 1L && it.state == state }
        }
    }
    private fun retained(offset: Int, state: DownloadState = DownloadState.DOWNLOADING) {
        part.writeBytes(bytes.copyOf(offset))
        rows[1] = rows.getValue(1).copy(state = state.name, bytesDownloaded = 17,
            resumeValidator = "\"v1\"", responseTotalBytes = bytes.size.toLong())
    }

    @Test fun `automatic retry resumes exact eighty percent prefix`() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes, 0, 8192))
            .setHeader("Content-Length", bytes.size).setHeader("ETag", "\"v1\"")
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        server.enqueue(partial(8192))
        val manager = manager()
        manager.awaitState(DownloadState.COMPLETED)
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals("bytes=8192-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(bytes, File(destination, "fixture.bin").readBytes())
        assertEquals(2, server.requestCount)
    }

    @Test fun `retry waits for server again without losing received progress`() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes, 0, 8192))
            .setHeader("Content-Length", bytes.size).setHeader("ETag", "\"v1\"")
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        server.enqueue(partial(8192))
        completionGate = CompletableDeferred()
        val awaitingResponse = CompletableDeferred<DownloadProgress>()
        lateinit var manager: DownloadManager
        every { repository.newDownloadCall(any(), any(), any(), any(), any(), any()) } answers {
            if (arg<String?>(2) != null) {
                awaitingResponse.complete(manager.state.value.activeDownloads.single { it.id == 1L })
            }
            api.content(arg(2), arg(4))
        }
        manager = manager()
        val retrying = manager.awaitState(DownloadState.RETRYING).activeDownloads.single { it.id == 1L }
        assertFalse(retrying.isAwaitingServer)
        val waiting = withTimeout(15000) { awaitingResponse.await() }
        assertEquals(DownloadState.DOWNLOADING, waiting.state)
        assertTrue(waiting.isAwaitingServer)
        assertEquals(retrying.bytesDownloaded, waiting.bytesDownloaded)
        assertTrue(waiting.bytesDownloaded > 0)
        assertEquals(retrying.totalBytes, waiting.totalBytes)
        assertEquals(0L, waiting.bytesPerSecond)
        withTimeout(15000) { completing.await() }
        assertFalse(manager.state.value.activeDownloads.single { it.id == 1L }.isAwaitingServer)
        completionGate!!.complete(Unit)
        manager.awaitState(DownloadState.COMPLETED)
        assertArrayEquals(bytes, File(destination, "fixture.bin").readBytes())
    }

    @Test fun `recreated manager resumes durable partial without stale byte count`() = runBlocking {
        retained(8192)
        server.enqueue(MockResponse().setResponseCode(503))
        val first = manager()
        first.awaitState(DownloadState.RETRYING)
        jobs.last().cancelAndJoin()
        server.enqueue(partial(8192))
        manager().awaitState(DownloadState.COMPLETED)
        assertEquals("bytes=8192-", server.takeRequest().getHeader("Range"))
        assertEquals("bytes=8192-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(bytes, File(destination, "fixture.bin").readBytes())
    }

    @Test fun `manual retry preserves owned selected row and legacy staging path`() = runBlocking {
        val original = rows.getValue(1)
        val area = staging.open(StagingManifest(1, 10, original.gameTitle, original.fileName,
            destination.absolutePath, StagingPhase.DOWNLOADING))
        part = File(area.archiveDir, "fixture.bin.tmp")
        retained(8192, DownloadState.FAILED)
        server.enqueue(partial(8192))
        val manager = manager()
        manager.awaitState(DownloadState.FAILED)
        assertEquals(part.absolutePath, rows.getValue(1).tempFilePath)
        manager.retryDownload(1)
        manager.awaitState(DownloadState.COMPLETED)
        val row = rows.getValue(1)
        assertEquals(original.id, row.id)
        assertEquals(original.createdAt, row.createdAt)
        assertEquals(original.ownerUserId, row.ownerUserId)
        assertEquals(original.selectedFileIds, row.selectedFileIds)
        assertEquals("bytes=8192-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(bytes, File(destination, "fixture.bin").readBytes())
    }

    @Test fun `clearing completed preserves restored failed row partial and manual retry`() = runBlocking {
        retained(8192, DownloadState.FAILED)
        rows[2] = rows.getValue(1).copy(
            id = 2, gameId = 11, gameFileId = 21, fileName = "other.bin",
            state = DownloadState.QUEUED.name, bytesDownloaded = 0,
            tempFilePath = File(destination, "other.bin.tmp").absolutePath,
            resumeValidator = null, responseTotalBytes = null
        )
        val cleared = CompletableDeferred<Unit>()
        coEvery { dao.clearCompleted() } answers {
            rows.entries.removeIf { it.value.state == DownloadState.COMPLETED.name }
            cleared.complete(Unit)
            Unit
        }
        server.enqueue(full())
        val manager = manager()
        withTimeout(15000) { manager.state.first { it.completed.size == 2 } }
        val failed = rows.getValue(1)

        manager.clearCompleted()
        withTimeout(5000) { cleared.await() }

        assertEquals(listOf(1L), manager.state.value.completed.map { it.id })
        assertEquals(DownloadState.FAILED, manager.state.value.completed.single().state)
        assertEquals(failed, rows.getValue(1))
        assertFalse(rows.containsKey(2))
        assertArrayEquals(bytes.copyOf(8192), part.readBytes())
        server.takeRequest()
        server.enqueue(partial(8192))
        manager.retryDownload(1)
        manager.awaitState(DownloadState.COMPLETED)
        assertEquals("bytes=8192-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(bytes, File(destination, "fixture.bin").readBytes())
    }

    @Test fun `explicit clear finished still removes restored failed entries and rows`() = runBlocking {
        retained(8192, DownloadState.FAILED)
        val cleared = CompletableDeferred<Unit>()
        coEvery { dao.clearFinished() } answers {
            rows.entries.removeIf { it.value.state in listOf("COMPLETED", "FAILED") }
            cleared.complete(Unit)
            Unit
        }
        val manager = manager()
        manager.awaitState(DownloadState.FAILED)

        manager.clearFinished()
        withTimeout(5000) { cleared.await() }

        assertTrue(manager.state.value.completed.isEmpty())
        assertTrue(rows.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun `pause and cancel during backoff retain or delete only after owner exits`() = runBlocking {
        retained(8192)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30"))
        val manager = manager()
        manager.awaitState(DownloadState.RETRYING)
        manager.pauseDownload(100)
        manager.awaitState(DownloadState.PAUSED)
        assertEquals(8192L, part.length())
        assertEquals(1, server.requestCount)
        manager.cancelDownload(100)
        withTimeout(5000) { manager.state.first { it.queue.isEmpty() && it.activeDownloads.isEmpty() } }
        assertFalse(part.exists())
        assertFalse(rows.containsKey(1))
        assertEquals(1, server.requestCount)
    }

    @Test fun `server switch during retry rejects new resource without touching prefix`() = runBlocking {
        retained(8192)
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            val manager = manager()
            manager.awaitState(DownloadState.RETRYING)
            api = api(other)
            manager.awaitState(DownloadState.FAILED)
            assertEquals(0, other.requestCount)
            assertArrayEquals(bytes.copyOf(8192), part.readBytes())
            assertEquals(server.url("/fixture").toString(), rows.getValue(1).requestKey)
        } finally { other.shutdown() }
    }

    @Test fun `settling owner retains slot until completion persistence returns`() = runBlocking {
        completionGate = CompletableDeferred()
        server.enqueue(full())
        val manager = manager()
        withTimeout(10000) { completing.await() }
        repeat(8) { manager.onExternalSlotFreed() }
        assertEquals(1, manager.totalActiveDownloads())
        completionGate!!.complete(Unit)
        manager.awaitState(DownloadState.COMPLETED)
        assertEquals(1, server.requestCount)
        assertArrayEquals(bytes, File(destination, "fixture.bin").readBytes())
    }

    @Test fun `cancel during backoff joins transfer before deleting actual part`() = runBlocking {
        retained(8192)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30"))
        val manager = manager()
        manager.awaitState(DownloadState.RETRYING)
        repeat(8) { manager.onExternalSlotFreed() }
        manager.cancelDownload(100)
        withTimeout(5000) { manager.state.first { it.queue.isEmpty() && it.activeDownloads.isEmpty() } }
        assertFalse(part.exists())
        assertFalse(rows.containsKey(1))
        assertEquals(1, server.requestCount)
    }

    @Test fun `initial changed server leaves pinned work untouched`() = runBlocking {
        retained(8192)
        val other = MockWebServer().apply { start() }
        try {
            api = api(other)
            manager().awaitState(DownloadState.FAILED)
            assertEquals(0, other.requestCount)
            assertArrayEquals(bytes.copyOf(8192), part.readBytes())
            assertEquals(server.url("/fixture").toString(), rows.getValue(1).requestKey)
        } finally { other.shutdown() }
    }

    @Test fun `busy retained staging does not starve a direct download`() = runBlocking {
        val original = rows.getValue(1)
        val area = staging.open(StagingManifest(1, 10, original.gameTitle, original.fileName,
            destination.absolutePath, StagingPhase.DOWNLOADING))
        val stagedPart = File(area.archiveDir, "fixture.bin.tmp").apply { writeBytes(bytes.copyOf(8192)) }
        rows[1] = original.copy(resumeValidator = "\"v1\"", responseTotalBytes = bytes.size.toLong())
        rows[2] = original.copy(id = 2, gameId = 11, gameFileId = 21, fileName = "other.bin",
            createdAt = original.createdAt.plusSeconds(1), tempFilePath = File(destination, "other.bin.tmp").absolutePath)
        assertTrue(staging.claim(999))
        try {
            server.enqueue(full())
            val manager = manager()
            withTimeout(10000) { manager.state.first { it.completed.any { item -> item.id == 2L } } }
            assertEquals(DownloadState.QUEUED.name, rows.getValue(1).state)
            assertEquals(stagedPart.absolutePath, rows.getValue(1).tempFilePath)
            assertArrayEquals(bytes.copyOf(8192), stagedPart.readBytes())
            assertArrayEquals(bytes, File(destination, "other.bin").readBytes())
            assertEquals(1, server.requestCount)
        } finally { staging.release(999) }
    }

    @Test fun `missing part resets stale retained byte count before admission`() = runBlocking {
        rows[1] = rows.getValue(1).copy(bytesDownloaded = 9000)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30"))
        val manager = manager()
        manager.awaitState(DownloadState.RETRYING)
        assertEquals(0L, rows.getValue(1).bytesDownloaded)
        assertNull(server.takeRequest().getHeader("Range"))
        manager.cancelDownload(100)
        withTimeout(5000) { manager.state.first { it.activeDownloads.isEmpty() && it.queue.isEmpty() } }
        Unit
    }

    private fun prepareStagedExtractionRetry(): ByteArray {
        val archive = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("fixture.bin"))
                val filler = ByteArray(4096).also { java.util.Random(42).nextBytes(it) }
                zip.write(filler)
                zip.closeEntry()
            }
        }.toByteArray()
        val original = rows.getValue(1).copy(
            gameFileId = null, fileCategory = null, selectedFileIds = null,
            fileName = "fixture.zip", platformSlug = "arcade", state = DownloadState.FAILED.name,
            totalBytes = archive.size.toLong(), bytesDownloaded = archive.size.toLong(),
            responseTotalBytes = archive.size.toLong(), validatedBytes = archive.size.toLong()
        )
        val area = staging.open(StagingManifest(1, 10, original.gameTitle, original.fileName,
            destination.absolutePath, StagingPhase.DOWNLOADING))
        File(area.archiveDir, original.fileName).writeBytes(archive)
        rows[1] = original.copy(tempFilePath = File(area.archiveDir, "fixture.zip.tmp").absolutePath)
        return archive
    }

    @Test fun `staged extraction retry deploys passthrough archive before cleanup`() = runBlocking {
        val archive = prepareStagedExtractionRetry()
        val manager = manager()
        manager.awaitState(DownloadState.FAILED)

        val result = manager.retryExtraction(10)

        assertTrue(result is DownloadManager.ExtractionResult.Success)
        val path = (result as DownloadManager.ExtractionResult.Success).localPath
        assertEquals(linkedPath, path)
        assertFalse(path.startsWith(staging.stagingRoot.absolutePath + File.separator))
        manager.cleanAbandonedStaging()
        assertArrayEquals(archive, File(path).readBytes())
        assertEquals(0, server.requestCount)
        assertTrue(staging.claim(999))
        staging.release(999)
        val entries = manager.state.value.let { it.activeDownloads + it.queue + it.completed }
        assertEquals(1, entries.count { it.id == 1L })
        assertEquals(DownloadState.COMPLETED, entries.single { it.id == 1L }.state)
    }

    @Test fun `staged extraction retry preserves failed row when sole slot is occupied`() = runBlocking {
        val other = rows.getValue(1).copy(id = 2, gameId = 11, gameFileId = 21)
        val archive = prepareStagedExtractionRetry()
        val retained = rows.getValue(1)
        rows[2] = other
        completionGate = CompletableDeferred()
        server.enqueue(full())
        val manager = manager()
        manager.awaitState(DownloadState.FAILED)
        withTimeout(10000) { completing.await() }

        val result = manager.retryExtraction(10)

        assertTrue(result is DownloadManager.ExtractionResult.Failure)
        assertEquals(retained, rows.getValue(1))
        assertEquals(1, manager.totalActiveDownloads())
        assertArrayEquals(archive, File(File(retained.tempFilePath!!).parentFile, retained.fileName).readBytes())
        val entries = manager.state.value.let { it.activeDownloads + it.queue + it.completed }
        assertEquals(1, entries.count { it.id == 1L })
        assertEquals(DownloadState.FAILED, entries.single { it.id == 1L }.state)
        completionGate!!.complete(Unit)
        Unit
    }

    @Test fun `concurrent staged extraction retries register only one owner and final entry`() = runBlocking {
        prepareStagedExtractionRetry()
        completionGate = CompletableDeferred()
        val manager = manager()
        manager.awaitState(DownloadState.FAILED)
        val first = async { manager.retryExtraction(10) }
        withTimeout(10000) { completing.await() }

        val second = manager.retryExtraction(10)

        assertTrue(second is DownloadManager.ExtractionResult.Failure)
        assertFalse(first.isCompleted)
        assertEquals(1, manager.totalActiveDownloads())
        completionGate!!.complete(Unit)
        assertTrue(withTimeout(10000) { first.await() } is DownloadManager.ExtractionResult.Success)
        val entries = manager.state.value.let { it.activeDownloads + it.queue + it.completed }
        assertEquals(1, entries.count { it.id == 1L })
        assertEquals(DownloadState.COMPLETED, entries.single { it.id == 1L }.state)
        assertEquals(0, server.requestCount)
    }

    @Test fun `folder ZIP response containing recognized raw CHD retains rename fallback`() = runBlocking {
        val chd = bytes.copyOf().apply { "MComprHD".toByteArray(Charsets.US_ASCII).copyInto(this) }
        rows[1] = rows.getValue(1).copy(
            gameFileId = null, fileCategory = null, selectedFileIds = null,
            fileName = "fixture.zip", platformSlug = "psx", isMultiFileRom = true,
            tempFilePath = File(destination, "fixture.zip.tmp").absolutePath
        )
        server.enqueue(MockResponse().setBody(Buffer().write(chd)))

        manager().awaitState(DownloadState.COMPLETED)

        val installed = File(destination, "fixture.chd")
        assertEquals(installed.absolutePath, linkedPath)
        assertArrayEquals(chd, installed.readBytes())
        assertFalse(File(destination, "fixture.zip").exists())
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("bytes=0-", request.getHeader("Range"))
        assertNull(request.getHeader("If-Range"))
    }

    @Test fun `single file ZIP does not opt into initial range`() = runBlocking {
        rows[1] = rows.getValue(1).copy(fileName = "fixture.zip", isMultiFileRom = false)
        server.enqueue(full())
        manager().awaitState(DownloadState.FAILED)
        val request = server.takeRequest()
        assertNull(request.getHeader("Range"))
        assertNull(request.getHeader("If-Range"))
    }

    @Test fun `expected ZIP with unrecognized raw content is not linked`() = runBlocking {
        rows[1] = rows.getValue(1).copy(
            gameFileId = null, fileCategory = null, selectedFileIds = null,
            fileName = "fixture.zip", platformSlug = "psx", isMultiFileRom = true,
            tempFilePath = File(destination, "fixture.zip.tmp").absolutePath
        )
        server.enqueue(full())

        manager().awaitState(DownloadState.FAILED)

        assertNull(linkedPath)
        assertNull(rows.getValue(1).validatedBytes)
        assertFalse(File(destination, "fixture.zip").exists())
        assertEquals(1, server.requestCount)
    }
}
