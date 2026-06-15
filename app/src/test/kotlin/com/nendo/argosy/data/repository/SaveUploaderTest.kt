package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.PlatformSaveHandler
import com.nendo.argosy.data.sync.platform.PreparedSave
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.titledb.TitleDbRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class SaveUploaderTest {

    private lateinit var tempDir: File
    private lateinit var preparedFile: File

    private val context = mockk<Context>(relaxed = true)
    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val titleDbRepository = mockk<TitleDbRepository>(relaxed = true)
    private val saveArchiver = mockk<SaveArchiver>(relaxed = true)
    private val savePathResolver = mockk<SavePathResolver>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val switchSaveHandler = mockk<SwitchSaveHandler>(relaxed = true)
    private val conflictDetector = mockk<ConflictDetector>(relaxed = true)
    private val saveCacheManager = mockk<SaveCacheManager>(relaxed = true)
    private val romMApi = mockk<RomMApi>(relaxed = true)
    private val platformHandler = mockk<PlatformSaveHandler>(relaxed = true)
    private val apiClient = mockk<SaveSyncApiClient>(relaxed = true)

    private lateinit var uploader: SaveUploader

    private val gameId = 7L
    private val rommId = 99L
    private val emulatorId = "eden"
    private val deviceId = "device-1"

    private val game = GameEntity(
        id = gameId,
        platformId = 1L,
        platformSlug = "snes",
        title = "Super Mario World",
        sortTitle = "super mario world",
        localPath = "/roms/smw.sfc",
        rommId = rommId,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_uploader_test").toFile()
        preparedFile = File(tempDir, "smw.srm").apply {
            writeBytes(ByteArray(256) { 0x42 })
        }
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }

        every { apiClient.getApi() } returns romMApi
        every { apiClient.getDeviceId() } returns deviceId
        coEvery { apiClient.resolveEmulatorForGame(any()) } returns emulatorId
        coEvery { apiClient.resolveCoreForGame(any<GameEntity>()) } returns null
        every { apiClient.getHandler(any(), any(), any()) } returns platformHandler
        coEvery { apiClient.checkSavesForGame(any(), any()) } returns emptyList()

        coEvery { gameDao.getById(gameId) } returns game
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns null

        every { fal.exists(any()) } answers { File(firstArg<String>()).exists() }
        every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        every { fal.lastModified(any()) } answers { File(firstArg<String>()).lastModified() }

        coEvery { platformHandler.prepareForUpload(any(), any()) } returns PreparedSave(
            file = preparedFile,
            isTemporary = false
        )

        every { conflictDetector.detectUploadConflict(any(), any(), any(), any(), any(), any(), any()) } returns null
        every { conflictDetector.pickLatestServerSave(any(), any(), any(), any()) } returns null
        every { conflictDetector.pickExistingServerSave(any(), any(), any(), any()) } returns null

        uploader = SaveUploader(
            context = context,
            saveSyncDao = saveSyncDao,
            saveCacheDao = saveCacheDao,
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            titleDbRepository = titleDbRepository,
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            fal = fal,
            switchSaveHandler = switchSaveHandler,
            apiClient = dagger.Lazy { apiClient },
            conflictDetector = conflictDetector,
            saveCacheManager = dagger.Lazy { saveCacheManager }
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `skip-check returns Success noOp without invoking remote upload when hash matches anchor`() = runTest {
        val syncEntity = SaveSyncEntity(
            id = 11L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = null,
            rommSaveId = 555L,
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            lastUploadedHash = "deadbeef",
            localContentHash = "deadbeef"
        )
        coEvery {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        } returns syncEntity
        every { saveArchiver.calculateContentHash(any()) } returns "deadbeef"
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns null

        val result = uploader.uploadSave(gameId, emulatorId, channelName = null)

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        assertEquals(true, (result as SaveSyncResult.Success).noOp)
        coVerify(exactly = 0) { romMApi.uploadSave(any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any()) }
        coVerify(exactly = 0) { romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any()) }
    }

    @Test
    fun `post-upload reconcile discards just-uploaded cache row when an older row already has the server hash`() = runTest {
        val older = SaveCacheEntity(
            id = 10L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-01T00:00:00Z"),
            saveSize = 256L,
            cachePath = "older/path",
            channelName = "manual",
            contentHash = "abc123",
            rommSaveId = null
        )
        val newlyUploaded = SaveCacheEntity(
            id = 20L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-02T00:00:00Z"),
            saveSize = 256L,
            cachePath = "newer/path",
            channelName = "manual",
            contentHash = "abc123",
            rommSaveId = null
        )

        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "manual")
        } returns SaveSyncEntity(
            id = 1L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = "manual",
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastUploadedHash = null
        )
        every { saveArchiver.calculateContentHash(any()) } returns "abc123"
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns newlyUploaded
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(gameId, "manual", "abc123")
        } returns listOf(older, newlyUploaded)

        val serverSave = serverSaveOf(id = 999L, contentHash = "abc123")
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } returns Response.success(serverSave)

        val result = uploader.uploadSave(gameId, emulatorId, channelName = "manual", uploadedCacheId = 20L)

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        coVerify(exactly = 1) { saveCacheManager.deleteCachedSave(20L) }
        coVerify(exactly = 1) { saveCacheDao.updateRommSaveId(10L, 999L) }
        coVerify(exactly = 0) { saveCacheDao.updateRommSaveId(20L, any()) }
    }

    @Test
    fun `post-upload reconcile keeps and pairs the just-uploaded row when it is the only match`() = runTest {
        val newlyUploaded = SaveCacheEntity(
            id = 20L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-02T00:00:00Z"),
            saveSize = 256L,
            cachePath = "newer/path",
            channelName = "manual",
            contentHash = "xyz789",
            rommSaveId = null
        )

        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "manual")
        } returns SaveSyncEntity(
            id = 1L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = "manual",
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastUploadedHash = null
        )
        every { saveArchiver.calculateContentHash(any()) } returns "xyz789"
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns newlyUploaded
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(gameId, "manual", "xyz789")
        } returns listOf(newlyUploaded)

        val serverSave = serverSaveOf(id = 999L, contentHash = "xyz789")
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } returns Response.success(serverSave)

        val result = uploader.uploadSave(gameId, emulatorId, channelName = "manual", uploadedCacheId = 20L)

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        coVerify(exactly = 0) { saveCacheManager.deleteCachedSave(any()) }
        coVerify(exactly = 1) { saveCacheDao.updateRommSaveId(20L, 999L) }
    }

    @Test
    fun `skip-check self-heal must scope to caller channel (audit Bug A3)`() = runTest {
        val channelACache = SaveCacheEntity(
            id = 100L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-01T00:00:00Z"),
            saveSize = 256L,
            cachePath = "a/path",
            channelName = "channel-A",
            contentHash = "shared-hash",
            rommSaveId = null,
        )
        val channelBCache = SaveCacheEntity(
            id = 200L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-02T00:00:00Z"),
            saveSize = 256L,
            cachePath = "b/path",
            channelName = "channel-B",
            contentHash = "shared-hash",
            rommSaveId = null,
        )

        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "channel-A")
        } returns SaveSyncEntity(
            id = 11L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = "channel-A",
            rommSaveId = 555L,
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_SYNCED,
            lastUploadedHash = "shared-hash",
            localContentHash = "shared-hash"
        )
        every { saveArchiver.calculateContentHash(any()) } returns "shared-hash"
        coEvery { saveCacheDao.getByGameAndHash(gameId, "shared-hash") } returns channelBCache
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(gameId, "channel-A", "shared-hash")
        } returns listOf(channelACache)

        val result = uploader.uploadSave(gameId, emulatorId, channelName = "channel-A")

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        assertEquals(true, (result as SaveSyncResult.Success).noOp)
        coVerify(exactly = 1) {
            saveCacheDao.updateRommSaveId(100L, 555L)
        }
        coVerify(exactly = 0) {
            saveCacheDao.updateRommSaveId(200L, any())
        }
    }

    @Test
    fun `skip-check adopt-by-older-row updates older row rommSaveId with no discard (coverage gap)`() = runTest {
        val older = SaveCacheEntity(
            id = 10L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-01T00:00:00Z"),
            saveSize = 256L,
            cachePath = "older/path",
            channelName = "manual",
            contentHash = "h-older",
            rommSaveId = null,
        )

        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "manual")
        } returns SaveSyncEntity(
            id = 1L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = "manual",
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastUploadedHash = null
        )
        every { saveArchiver.calculateContentHash(any()) } returns "h-older"
        coEvery { saveCacheDao.getByGameAndHash(gameId, "h-older") } returns older
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(gameId, "manual", "h-older")
        } returns listOf(older)

        val serverSave = serverSaveOf(id = 999L, contentHash = "h-older")
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } returns Response.success(serverSave)

        val result = uploader.uploadSave(
            gameId = gameId,
            emulatorId = emulatorId,
            channelName = "manual",
            uploadedCacheId = null
        )

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        coVerify(exactly = 1) { saveCacheDao.updateRommSaveId(10L, 999L) }
        coVerify(exactly = 0) { saveCacheManager.deleteCachedSave(any()) }
    }

    @Test
    fun `serverSave with null contentHash does not leak local hash into SaveSyncEntity write (coverage gap)`() = runTest {
        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "manual")
        } returns SaveSyncEntity(
            id = 1L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = "manual",
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastUploadedHash = null
        )
        every { saveArchiver.calculateContentHash(any()) } returns "local-hash"
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns null
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(any(), any(), any())
        } returns emptyList()

        val serverSave = RomMSave(
            id = 999L,
            romId = rommId,
            userId = 1L,
            emulator = emulatorId,
            fileName = "manual.srm",
            updatedAt = "2026-05-02T00:00:00Z",
            contentHash = null
        )
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } returns Response.success(serverSave)

        val capturedEntities = mutableListOf<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(capturedEntities)) } returns 1L

        val result = uploader.uploadSave(
            gameId = gameId,
            emulatorId = emulatorId,
            channelName = "manual",
            uploadedCacheId = null
        )

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        assertTrue("upsert must have been called", capturedEntities.isNotEmpty())
        val written = capturedEntities.last()
        assertEquals(
            "When server reports null contentHash, SaveSyncEntity.lastUploadedHash must stay null (no local-hash leak)",
            null,
            written.lastUploadedHash
        )
    }

    @Test
    fun `post-upload reconcile with multiple older rows in channel picks the first (pin current behavior)`() = runTest {
        val olderA = SaveCacheEntity(
            id = 10L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-01T00:00:00Z"),
            saveSize = 256L,
            cachePath = "older-a/path",
            channelName = "manual",
            contentHash = "shared",
            rommSaveId = null,
        )
        val olderB = SaveCacheEntity(
            id = 11L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-01T12:00:00Z"),
            saveSize = 256L,
            cachePath = "older-b/path",
            channelName = "manual",
            contentHash = "shared",
            rommSaveId = null,
        )
        val newlyUploaded = SaveCacheEntity(
            id = 20L,
            gameId = gameId,
            emulatorId = emulatorId,
            cachedAt = Instant.parse("2026-05-02T00:00:00Z"),
            saveSize = 256L,
            cachePath = "newer/path",
            channelName = "manual",
            contentHash = "shared",
            rommSaveId = null,
        )

        coEvery {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, "manual")
        } returns SaveSyncEntity(
            id = 1L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = "manual",
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastUploadedHash = null
        )
        every { saveArchiver.calculateContentHash(any()) } returns "shared"
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns olderA
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(gameId, "manual", "shared")
        } returns listOf(olderA, olderB, newlyUploaded)

        val serverSave = serverSaveOf(id = 999L, contentHash = "shared")
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } returns Response.success(serverSave)

        val result = uploader.uploadSave(
            gameId = gameId,
            emulatorId = emulatorId,
            channelName = "manual",
            uploadedCacheId = 20L
        )

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        coVerify(exactly = 1) { saveCacheManager.deleteCachedSave(20L) }
        coVerify(exactly = 1) { saveCacheDao.updateRommSaveId(10L, 999L) }
        coVerify(exactly = 0) { saveCacheDao.updateRommSaveId(11L, any()) }
        coVerify(exactly = 0) { saveCacheDao.updateRommSaveId(20L, any()) }
    }

    @Test
    fun `hardcore upload with null channelName threads through without misbehavior (coverage gap)`() = runTest {
        coEvery {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        } returns SaveSyncEntity(
            id = 1L,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = null,
            localSavePath = preparedFile.absolutePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastUploadedHash = null
        )
        every { saveArchiver.calculateContentHash(any()) } returns "hc-hash"
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns null
        coEvery {
            saveCacheDao.getAllByGameChannelAndHash(gameId, null, "hc-hash")
        } returns emptyList()

        val serverSave = serverSaveOf(id = 7L, contentHash = "hc-hash")
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } returns Response.success(serverSave)

        val result = uploader.uploadSave(
            gameId = gameId,
            emulatorId = emulatorId,
            channelName = null,
            isHardcore = true,
            uploadedCacheId = null
        )

        assertTrue("expected Success but got $result", result is SaveSyncResult.Success)
        coVerify(exactly = 1) {
            saveCacheDao.getAllByGameChannelAndHash(gameId, null, "hc-hash")
        }
    }

    private fun serverSaveOf(id: Long, contentHash: String): RomMSave = RomMSave(
        id = id,
        romId = rommId,
        userId = 1L,
        emulator = emulatorId,
        fileName = "manual.srm",
        updatedAt = "2026-05-02T00:00:00Z",
        contentHash = contentHash
    )
}
