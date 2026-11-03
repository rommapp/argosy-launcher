package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveUnitResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SaveDownloaderArchiveEntryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gameId = 7L
    private val serverSaveId = 42L
    private val cachedId = 900L
    private val serverHash = "server-hash"
    private val builtin = EmulatorRegistry.BUILTIN_ID

    private val api: RomMApi = mockk(relaxed = true)
    private val apiClient: SaveSyncApiClient = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val saveCacheManager: SaveCacheManager = mockk(relaxed = true)
    private val fal: FileAccessLayer = mockk(relaxed = true)
    private val saveUnitResolver: SaveUnitResolver = mockk(relaxed = true)

    private lateinit var stale: String
    private lateinit var live: String

    private val downloader = SaveDownloader(
        context = mockk(relaxed = true),
        saveSyncDao = saveSyncDao,
        saveCacheDao = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        gameDao = gameDao,
        activeSaveRepository = mockk(relaxed = true),
        titleDbRepository = mockk(relaxed = true),
        titleIdExtractor = mockk(relaxed = true),
        saveArchiver = mockk(relaxed = true),
        savePathResolver = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        saveCacheManager = dagger.Lazy { saveCacheManager },
        fal = fal,
        switchSaveHandler = mockk(relaxed = true),
        gciSaveHandler = mockk(relaxed = true),
        apiClient = dagger.Lazy { apiClient },
        saveUploader = dagger.Lazy { mockk(relaxed = true) },
        emulatorSaveConfigRepository = mockk(relaxed = true),
        unitSaveHandler = mockk(relaxed = true),
        saveUnitResolver = saveUnitResolver,
        sigilSaveHandler = com.nendo.argosy.data.sync.fixtures.notRoutedSigil()
    )

    @Before
    fun setup() {
        val archive = temp.newFile("Crystal.zip")
        ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("Crystal (USA).gbc"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        val saves = temp.newFolder("saves")
        stale = File(saves, "Crystal.srm").absolutePath
        live = File(saves, "Crystal (USA).srm").absolutePath

        every { apiClient.getApi() } returns api
        every { apiClient.getDeviceId() } returns "device-abc"
        coEvery { gameDao.getById(gameId) } returns GameEntity(
            id = gameId,
            platformId = 1L,
            title = "Crystal",
            sortTitle = "crystal",
            localPath = archive.absolutePath,
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED,
            platformSlug = "gbc"
        )
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(gameId, builtin, any(), any()) } returns SaveSyncEntity(
            gameId = gameId,
            rommId = 100L,
            emulatorId = builtin,
            channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
            rommSaveId = serverSaveId,
            localSavePath = stale,
            syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
        )
        every { fal.exists(any()) } returns true
        coEvery { api.getSaveWithDevice(serverSaveId, "device-abc") } returns Response.success(
            RomMSave(
                id = serverSaveId, romId = 100L, userId = 1L, emulator = "gambatte",
                fileName = "Crystal.srm", updatedAt = "2026-10-01T00:00:00Z", contentHash = serverHash
            )
        )
        coEvery { saveUnitResolver.layoutFor(any(), builtin, any()) } returns "gambatte"
        coEvery { saveUnitResolver.expectedPrimaryPath(any(), "gambatte", "gbc", "Crystal (USA).gbc", any()) } returns live
        coEvery { saveCacheManager.findCachedByHash(gameId, serverHash) } returns SaveCacheEntity(
            id = cachedId,
            gameId = gameId,
            emulatorId = builtin,
            cachedAt = Instant.EPOCH,
            saveSize = 1,
            cachePath = "x/Crystal.srm",
            contentHash = serverHash
        )
        coEvery { saveCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns true
        coEvery { saveCacheManager.restoreSave(any(), any(), any()) } returns true
    }

    @Test
    fun `a tracked archive-named save is backed up and restored under the extracted entry name`() = runTest {
        val result = downloader.downloadSave(gameId, builtin)

        assertTrue(result is SaveSyncResult.Success)
        coVerify { saveCacheManager.protectBeforeOverwrite(gameId, builtin, live) }
        coVerify { saveCacheManager.restoreSave(cachedId, live, builtin) }
        coVerify(exactly = 0) { saveCacheManager.protectBeforeOverwrite(any(), any(), stale) }
        coVerify(exactly = 0) { saveCacheManager.restoreSave(any(), stale, any()) }
    }
}
