package com.nendo.argosy.data.download

import android.content.Context
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
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

class DownloadManagerFailureReasonTest {
    @get:Rule val storage = TemporaryFolder()
    private val server = MockWebServer()
    private val rows = ConcurrentHashMap<Long, DownloadQueueEntity>()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val jobs = mutableListOf<kotlinx.coroutines.Job>()
    private val dao = mockk<DownloadQueueDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val gameDiscDao = mockk<GameDiscDao>(relaxed = true)
    private val gameFileDao = mockk<GameFileDao>(relaxed = true)
    private val repository = mockk<RomMRepository>(relaxed = true)
    private lateinit var staging: RomStagingManager
    private lateinit var destination: File
    private lateinit var part: File
    @Volatile private lateinit var api: FixtureApi
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
        coEvery { gameDao.getById(any()) } returns null
        val thermal = mockk<DownloadThermalManager>()
        every { thermal.thermalStatus } returns MutableStateFlow(ThermalStatus())
        return DownloadManager(
            gameDao, gameDiscDao, gameFileDao, dao, mockk(relaxed = true),
            repository, preferences, mockk(relaxed = true), mockk(relaxed = true), dagger.Lazy { thermal },
            dagger.Lazy { mockk<MediaDownloadManager>(relaxed = true) }, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), staging, folders,
            CoroutineScope(job + dispatcher), DownloadConnectivity.AlwaysAvailable
        )
    }

    @Test fun httpFailurePersistsStatusAndExposesDecodedReason() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("private server diagnostic"))
        assertFailure(DownloadFailureReason.HttpError(401))
    }

    @Test fun checksumFailurePersistsIntegrityReason() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes))
            .setHeader("Content-MD5", "AAAAAAAAAAAAAAAAAAAAAA=="))
        assertFailure(DownloadFailureReason.IntegrityCheckFailed)
    }

    @Test fun storageFailurePersistsStorageReason() = runBlocking {
        coEvery { dao.updateTransferCheckpoint(any(), any(), any(), any(), any()) } throws java.io.IOException("private disk diagnostic")
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        assertFailure(DownloadFailureReason.StorageAccessFailed)
    }

    private suspend fun assertFailure(reason: DownloadFailureReason) {
        val manager = manager()
        val snapshot = withTimeout(15000) {
            manager.state.first { state ->
                (state.activeDownloads + state.queue + state.completed).any { it.id == 1L && it.state == DownloadState.FAILED }
            }
        }
        val failed = (snapshot.activeDownloads + snapshot.queue + snapshot.completed).first { it.id == 1L }
        assertEquals(DownloadFailureReasonCodec.encode(reason), rows.getValue(1).errorReason)
        assertEquals(reason, failed.errorReason)
        assertEquals(reason, DownloadFailureReasonCodec.decode(rows.getValue(1).errorReason))
    }
}
