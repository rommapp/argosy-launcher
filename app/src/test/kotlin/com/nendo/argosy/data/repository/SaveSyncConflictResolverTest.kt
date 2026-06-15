package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

class SaveSyncConflictResolverTest {

    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var saveCacheDao: SaveCacheDao
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var emulatorSaveConfigDao: EmulatorSaveConfigDao
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var gameDao: GameDao
    private lateinit var saveArchiver: SaveArchiver
    private lateinit var savePathResolver: SavePathResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var saveCacheManager: dagger.Lazy<SaveCacheManager>
    private lateinit var apiClient: dagger.Lazy<SaveSyncApiClient>
    private lateinit var switchSaveHandler: SwitchSaveHandler
    private lateinit var fal: com.nendo.argosy.data.storage.FileAccessLayer
    private lateinit var saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
    private lateinit var appContext: android.content.Context
    private lateinit var resolver: SaveSyncConflictResolver

    private lateinit var mockCacheManager: SaveCacheManager
    private lateinit var mockApiClient: SaveSyncApiClient
    private lateinit var mockApi: RomMApi

    private val testGame = GameEntity(
        id = 1L,
        title = "Test Game",
        sortTitle = "test game",
        platformId = 1L,
        platformSlug = "gba",
        rommId = 100L,
        igdbId = null,
        localPath = "/storage/roms/test.gba",
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        saveSyncDao = mockk(relaxed = true)
        saveCacheDao = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        emulatorSaveConfigDao = mockk(relaxed = true)
        emulatorResolver = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        saveArchiver = mockk(relaxed = true)
        savePathResolver = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        switchSaveHandler = mockk(relaxed = true)
        fal = mockk(relaxed = true)
        saveHandlerRegistry = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        every { fal.exists(any()) } returns true

        mockCacheManager = mockk(relaxed = true)
        mockApiClient = mockk(relaxed = true)
        mockApi = mockk(relaxed = true)

        saveCacheManager = dagger.Lazy { mockCacheManager }
        apiClient = dagger.Lazy { mockApiClient }

        every { mockApiClient.getApi() } returns mockApi
        every { mockApiClient.getDeviceId() } returns "device-1"

        coEvery { gameDao.getById(1L) } returns testGame
        coEvery { gameDao.getActiveSaveChannel(1L) } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false

        resolver = SaveSyncConflictResolver(
            saveSyncDao = saveSyncDao,
            saveCacheDao = saveCacheDao,
            emulatorConfigDao = emulatorConfigDao,
            emulatorSaveConfigDao = emulatorSaveConfigDao,
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            userPreferencesRepository = userPreferencesRepository,
            saveCacheManager = saveCacheManager,
            apiClient = apiClient,
            switchSaveHandler = switchSaveHandler,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            appContext = appContext
        )
    }

    @Test
    fun `checkForConflict local matches client anchor returns null`() = runTest {
        val syncEntity = makeSyncEntity(localContentHash = "matching_hash")
        setupConflictCheckMocks(syncEntity)
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "matching_hash"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict local changed but server unchanged is not a conflict`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "server_anchor", localContentHash = "anchor_local")
        setupConflictCheckMocks(syncEntity, contentHash = "server_anchor")
        every { mockApiClient.getCapabilities() } returns RomMCapabilities.from("4.9.0")
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "new_local"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict both sides changed returns ConflictInfo`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "server_anchor", localContentHash = "anchor_local")
        setupConflictCheckMocks(syncEntity, contentHash = "server_new")
        every { mockApiClient.getCapabilities() } returns RomMCapabilities.from("4.9.0")
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "new_local"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNotNull(result)
        assertTrue(result!!.isHashConflict)
        assertEquals(1L, result.gameId)
    }

    @Test
    fun `checkForConflict local content matching server hash returns null despite stale row`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "old_hash")
        setupConflictCheckMocks(
            syncEntity,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false)),
            contentHash = "restored_hash"
        )
        every { mockApiClient.getCapabilities() } returns RomMCapabilities.from("4.9.0")
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "restored_hash"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict ignores server hash when server below trust floor`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "old_hash", localContentHash = "anchor_local")
        setupConflictCheckMocks(
            syncEntity,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false)),
            contentHash = "restored_hash"
        )
        every { mockApiClient.getCapabilities() } returns RomMCapabilities.NONE
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "restored_hash"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNotNull(result)
    }

    @Test
    fun `checkForConflict device not current returns ConflictInfo`() = runTest {
        val syncEntity = makeSyncEntity()
        setupConflictCheckMocks(
            syncEntity,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns null

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNotNull(result)
    }

    @Test
    fun `checkForConflict no local file returns null`() = runTest {
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns makeSyncEntity(
            localSavePath = "/nonexistent.srm"
        )
        coEvery { savePathResolver.discoverSavePath(
            any(), any(), any(), any(), any(), any(), any(), any()
        ) } returns null

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict accented server slot matches local channel`() = runTest {
        val localFile = File.createTempFile("test_save", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        coEvery { gameDao.getById(1L) } returns testGame
        val serverSave = makeServerSave(
            slot = "Pokémon Violet",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns makeSyncEntity(
            localSavePath = localFile.absolutePath
        )
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns null

        val result = resolver.checkForConflict(1L, "retroarch", "Pokemon Violet")

        assertNotNull("Accented server slot should match ASCII channel name", result)
        localFile.delete()
    }

    private fun makeServerSave(
        id: Long = 1L,
        fileName: String = "argosy-latest.srm",
        updatedAt: String = "2025-01-15T12:00:00Z",
        slot: String? = null,
        deviceSyncs: List<RomMDeviceSync>? = emptyList(),
        contentHash: String? = null
    ) = RomMSave(
        id = id,
        romId = 100L,
        userId = 1L,
        fileName = fileName,
        downloadPath = "/saves/1/argosy-latest.srm",
        emulator = "retroarch",
        updatedAt = updatedAt,
        slot = slot,
        fileNameNoExt = File(fileName).nameWithoutExtension,
        deviceSyncs = deviceSyncs,
        contentHash = contentHash
    )

    private fun makeSyncEntity(
        localSavePath: String? = null,
        lastUploadedHash: String? = null,
        localContentHash: String? = null
    ) = SaveSyncEntity(
        id = 1L,
        gameId = 1L,
        rommId = 100L,
        emulatorId = "retroarch",
        rommSaveId = 1L,
        localSavePath = localSavePath,
        localUpdatedAt = Instant.parse("2025-01-14T12:00:00Z"),
        serverUpdatedAt = Instant.parse("2025-01-15T12:00:00Z"),
        syncStatus = SaveSyncEntity.STATUS_SYNCED,
        lastUploadedHash = lastUploadedHash,
        localContentHash = localContentHash
    )

    private fun setupConflictCheckMocks(
        syncEntity: SaveSyncEntity,
        deviceSyncs: List<RomMDeviceSync>? = emptyList(),
        contentHash: String? = null
    ) {
        val localFile = File.createTempFile("test_save", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val entityWithRealPath = syncEntity.copy(
            localSavePath = localFile.absolutePath
        )
        val serverSave = makeServerSave(deviceSyncs = deviceSyncs, contentHash = contentHash)
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns entityWithRealPath
    }

    private fun makeCache(
        id: Long,
        emulatorId: String,
        contentHash: String? = "cache-hash",
        cachedAtMs: Long = 1_000_000L
    ) = com.nendo.argosy.data.local.entity.SaveCacheEntity(
        id = id,
        gameId = 1L,
        emulatorId = emulatorId,
        cachedAt = Instant.ofEpochMilli(cachedAtMs),
        saveSize = 1024L,
        cachePath = "cache/$id",
        contentHash = contentHash
    )

    @Test
    fun `crossEmulatorMigrateIfNeeded copies cache when latest cache emulator differs`() = runTest {
        val cache = makeCache(id = 7L, emulatorId = "ppsspp")
        coEvery { saveCacheDao.getByGame(1L) } returns listOf(cache)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns "argosy.builtin.libretro"
        coEvery { mockApiClient.resolveCoreForGame(testGame) } returns "ppsspp_libretro"
        coEvery { savePathResolver.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns "/builtin/target.srm"
        coEvery { mockCacheManager.calculateLocalSaveHash("/builtin/target.srm") } returns null
        coEvery { mockCacheManager.restoreSave(7L, "/builtin/target.srm") } returns true

        resolver.crossEmulatorMigrateIfNeeded(1L, currentEmulatorId = "builtin")

        io.mockk.coVerify(exactly = 1) { mockCacheManager.restoreSave(7L, "/builtin/target.srm") }
    }

    @Test
    fun `crossEmulatorMigrateIfNeeded no-ops when latest cache emulator matches current`() = runTest {
        val cache = makeCache(id = 7L, emulatorId = "builtin")
        coEvery { saveCacheDao.getByGame(1L) } returns listOf(cache)

        resolver.crossEmulatorMigrateIfNeeded(1L, currentEmulatorId = "builtin")

        io.mockk.coVerify(exactly = 0) { mockCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `crossEmulatorMigrateIfNeeded no-ops when no cache has a content hash`() = runTest {
        coEvery { saveCacheDao.getByGame(1L) } returns listOf(makeCache(id = 7L, emulatorId = "ppsspp", contentHash = null))

        resolver.crossEmulatorMigrateIfNeeded(1L, currentEmulatorId = "builtin")

        io.mockk.coVerify(exactly = 0) { mockCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `crossEmulatorMigrateIfNeeded no-ops when disk hash already matches cache hash`() = runTest {
        val cache = makeCache(id = 7L, emulatorId = "ppsspp", contentHash = "same-hash")
        coEvery { saveCacheDao.getByGame(1L) } returns listOf(cache)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns "argosy.builtin.libretro"
        coEvery { mockApiClient.resolveCoreForGame(testGame) } returns "ppsspp_libretro"
        coEvery { savePathResolver.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns "/builtin/target.srm"
        coEvery { mockCacheManager.calculateLocalSaveHash("/builtin/target.srm") } returns "same-hash"

        resolver.crossEmulatorMigrateIfNeeded(1L, currentEmulatorId = "builtin")

        io.mockk.coVerify(exactly = 0) { mockCacheManager.restoreSave(any(), any()) }
    }

    @Test
    fun `crossEmulatorMigrateIfNeeded no-ops when target path cannot be resolved`() = runTest {
        val cache = makeCache(id = 7L, emulatorId = "ppsspp")
        coEvery { saveCacheDao.getByGame(1L) } returns listOf(cache)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns null
        coEvery { mockApiClient.resolveCoreForGame(testGame) } returns null
        coEvery { savePathResolver.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        coEvery { emulatorSaveConfigDao.getByEmulator(any()) } returns null

        resolver.crossEmulatorMigrateIfNeeded(1L, currentEmulatorId = "completely-unknown-emulator-id")

        io.mockk.coVerify(exactly = 0) { mockCacheManager.restoreSave(any(), any()) }
    }
}
