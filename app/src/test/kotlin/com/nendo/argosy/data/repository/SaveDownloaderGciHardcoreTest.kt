package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import kotlin.io.path.createTempDirectory

class SaveDownloaderGciHardcoreTest {
    private val gameId = 3L
    private val serverSaveId = 51L

    private lateinit var tempDir: File
    private val context: Context = mockk(relaxed = true)
    private val api: RomMApi = mockk(relaxed = true)
    private val apiClient: SaveSyncApiClient = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val saveArchiver: SaveArchiver = mockk(relaxed = true)
    private val gciSaveHandler: GciSaveHandler = mockk(relaxed = true)
    private val savePathResolver: com.nendo.argosy.data.sync.SavePathResolver = mockk(relaxed = true)
    private val emulatorResolver: com.nendo.argosy.data.emulator.EmulatorResolver = mockk(relaxed = true)

    private val downloader by lazy {
        SaveDownloader(
            context = context,
            saveSyncDao = saveSyncDao,
            saveCacheDao = mockk(relaxed = true),
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            activeSaveRepository = mockk(relaxed = true),
            titleDbRepository = mockk(relaxed = true),
            titleIdExtractor = mockk(relaxed = true),
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            syncPreferencesRepository = mockk(relaxed = true),
            saveCacheManager = dagger.Lazy { saveCacheManager },
            fal = mockk(relaxed = true),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = gciSaveHandler,
            apiClient = dagger.Lazy { apiClient },
            saveUploader = dagger.Lazy { mockk(relaxed = true) },
            emulatorSaveConfigRepository = mockk(relaxed = true),
            unitSaveHandler = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true),
            sigilSaveHandler = com.nendo.argosy.data.sync.fixtures.notRoutedSigil()
        )
    }

    @Before
    fun setup() {
        tempDir = createTempDirectory("gci_hardcore").toFile()
        every { context.cacheDir } returns tempDir
        every { apiClient.getApi() } returns api
        every { apiClient.getDeviceId() } returns null
        every { apiClient.hasEnoughDiskSpace(any(), any()) } returns true
        coEvery { apiClient.withRetry<Any?>(any(), any(), any(), any()) } coAnswers {
            arg<suspend () -> Any?>(3).invoke()
        }
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId, platformId = 1L, title = "Zelda", sortTitle = "zelda", localPath = "/roms/zelda.iso",
            rommId = 200L, igdbId = null, source = GameSource.ROMM_SYNCED, platformSlug = "ngc"
        )
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(gameId, "dolphin", any(), any()) } returns SaveSyncEntity(
            gameId = gameId, rommId = 200L, emulatorId = "dolphin", channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
            rommSaveId = serverSaveId, syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
        )
        coEvery { saveSyncDao.getCorruptZipTimestamp(any(), any(), any(), any()) } returns null
        coEvery { api.getSave(serverSaveId) } returns Response.success(
            RomMSave(
                id = serverSaveId, romId = 200L, userId = 1L, emulator = "dolphin", fileName = "01-GZLE-DATA.gci",
                downloadPath = "/saves/01-GZLE-DATA.gci", updatedAt = "2026-10-01T00:00:00Z", contentHash = "casual"
            )
        )
        coEvery { api.downloadRaw(any()) } returns Response.success(ByteArray(64) { 1 }.toResponseBody())
        coEvery { saveCacheManager.findCachedByHash(any(), any()) } returns null
        coEvery { saveCacheManager.hasHardcoreSave(gameId) } returns true
        every { saveArchiver.hasHardcoreTrailer(any()) } returns false
        coEvery { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns true
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `a casual GameCube server save over a local hardcore save asks the player instead of overwriting`() = runTest {
        coEvery { savePathResolver.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns "/GC/USA/Card A/01-GZLE-DATA.gci"

        val result = downloader.downloadSave(gameId, "dolphin")

        assertTrue("Expected the hardcore choice, got $result", result is SaveSyncResult.NeedsHardcoreResolution)
        result as SaveSyncResult.NeedsHardcoreResolution
        assertTrue("The held download is gone", File(result.tempFilePath).exists())
        coVerify(exactly = 0) { gciSaveHandler.extractDownload(any(), any()) }
    }

    @Test
    fun `a server autosave fetched into the cache is not locked`() = runTest {
        fetchIntoCacheAs("autosave")

        coVerify {
            saveCacheManager.cacheServerDownload(gameId, any(), any(), "01-GZLE-DATA.gci", "autosave", any(), any(), false, false, serverSaveId)
        }
    }

    @Test
    fun `a named server slot fetched into the cache is locked`() = runTest {
        fetchIntoCacheAs("Before boss")

        coVerify { saveCacheManager.cacheServerDownload(gameId, any(), any(), any(), "Before boss", any(), any(), true, false, serverSaveId) }
    }

    private suspend fun fetchIntoCacheAs(channel: String) {
        coEvery { saveCacheManager.cacheServerDownload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            SaveCacheManager.CacheResult.Created(0L, 77L)
        downloader.downloadToCache(serverSaveId, gameId, channel, activate = false)
    }

    @Test
    fun `a core memory dump on the server never becomes a GameCube cache row`() = runTest {
        coEvery { emulatorResolver.getEmulatorIdForGame(gameId, any(), any()) } returns "dolphin"
        coEvery { api.getSave(serverSaveId) } returns Response.success(
            RomMSave(
                id = serverSaveId, romId = 200L, userId = 1L, emulator = "retroarch", fileName = "Melee.srm",
                downloadPath = "/saves/Melee.srm", updatedAt = "2026-10-20T05:21:15Z", contentHash = "dump"
            )
        )

        val cacheId = downloader.downloadToCache(serverSaveId, gameId, "autosave", activate = false)

        assertNull(cacheId)
        coVerify(exactly = 0) { saveCacheManager.cacheServerDownload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a GameCube server save carrying the hardcore trailer still downloads`() = runTest {
        every { saveArchiver.hasHardcoreTrailer(any()) } returns true

        downloader.downloadSave(gameId, "dolphin")

        coVerify(exactly = 1) { gciSaveHandler.extractDownload(any(), any()) }
    }
}
