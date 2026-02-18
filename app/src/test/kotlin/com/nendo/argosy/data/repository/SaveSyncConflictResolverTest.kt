package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMDeviceSync
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.ConflictInfo
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
import retrofit2.Response
import java.io.File
import java.time.Instant

class SaveSyncConflictResolverTest {

    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var saveCacheDao: SaveCacheDao
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var gameDao: GameDao
    private lateinit var saveArchiver: SaveArchiver
    private lateinit var savePathResolver: SavePathResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var saveCacheManager: dagger.Lazy<SaveCacheManager>
    private lateinit var apiClient: dagger.Lazy<SaveSyncApiClient>
    private lateinit var switchSaveHandler: SwitchSaveHandler
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
        emulatorResolver = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        saveArchiver = mockk(relaxed = true)
        savePathResolver = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        switchSaveHandler = mockk(relaxed = true)

        mockCacheManager = mockk(relaxed = true)
        mockApiClient = mockk(relaxed = true)
        mockApi = mockk(relaxed = true)

        saveCacheManager = dagger.Lazy { mockCacheManager }
        apiClient = dagger.Lazy { mockApiClient }

        every { mockApiClient.getApi() } returns mockApi
        every { mockApiClient.getDeviceId() } returns "device-1"
        every { mockApiClient.isLatestSaveFileName(any(), any()) } returns true
        every { mockApiClient.parseTimestamp(any()) } answers {
            Instant.parse(firstArg())
        }

        coEvery { gameDao.getById(1L) } returns testGame
        coEvery { gameDao.getActiveSaveChannel(1L) } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false

        resolver = SaveSyncConflictResolver(
            saveSyncDao = saveSyncDao,
            saveCacheDao = saveCacheDao,
            emulatorConfigDao = emulatorConfigDao,
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            userPreferencesRepository = userPreferencesRepository,
            saveCacheManager = saveCacheManager,
            apiClient = apiClient,
            switchSaveHandler = switchSaveHandler
        )
    }

    // --- preLaunchSync tests ---

    @Test
    fun `preLaunchSync device is current returns LocalIsNewer`() = runTest {
        val serverSave = makeServerSave(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulator(any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(result is SaveSyncConflictResolver.PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `preLaunchSync device not current and local hash matches last upload returns ServerIsNewer`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "abc123")
        val serverSave = makeServerSave(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns syncEntity
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "abc123"

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync device not current and local hash differs from last upload returns LocalModified`() = runTest {
        val syncEntity = makeSyncEntity(
            localSavePath = "/saves/test.srm",
            lastUploadedHash = "abc123"
        )
        val serverSave = makeServerSave(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns syncEntity
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { mockCacheManager.calculateLocalSaveHash("/saves/test.srm") } returns "different_hash"

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected LocalModified but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.LocalModified
        )
    }

    @Test
    fun `preLaunchSync device not current and no local file returns ServerIsNewer`() = runTest {
        val syncEntity = makeSyncEntity(localSavePath = "/nonexistent/path.srm")
        val serverSave = makeServerSave(
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns syncEntity
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer)
    }

    @Test
    fun `preLaunchSync no server save returns NoServerSave`() = runTest {
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns emptyList()

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(result is SaveSyncConflictResolver.PreLaunchSyncResult.NoServerSave)
    }

    @Test
    fun `preLaunchSync no API connection returns NoConnection`() = runTest {
        every { mockApiClient.getApi() } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(result is SaveSyncConflictResolver.PreLaunchSyncResult.NoConnection)
    }

    @Test
    fun `preLaunchSync activeSaveApplied true skips sync`() = runTest {
        every { mockApiClient.getDeviceId() } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns true

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(result is SaveSyncConflictResolver.PreLaunchSyncResult.LocalIsNewer)
    }

    @Test
    fun `preLaunchSync channel save matched by slot`() = runTest {
        coEvery { gameDao.getActiveSaveChannel(1L) } returns "slot1"
        val serverSave = makeServerSave(slot = "slot1")
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(1L, "retroarch", "slot1") } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer for channel save but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync no device sync info and server newer returns ServerIsNewer`() = runTest {
        every { mockApiClient.getDeviceId() } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false
        val serverSave = makeServerSave(deviceSyncs = null)
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    // --- checkForConflict tests ---

    @Test
    fun `checkForConflict local hash matches last upload returns null`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "matching_hash")
        setupConflictCheckMocks(syncEntity)
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "matching_hash"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict local hash differs returns ConflictInfo`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "old_hash")
        setupConflictCheckMocks(syncEntity)
        coEvery { mockCacheManager.calculateLocalSaveHash(any()) } returns "new_local_hash"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNotNull(result)
        assertTrue(result!!.isHashConflict)
        assertEquals(1L, result.gameId)
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
            any(), any(), any(), any(), any(), any(), any(), any(), any()
        ) } returns null

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    // --- helpers ---

    private fun makeServerSave(
        id: Long = 1L,
        fileName: String = "argosy-latest.srm",
        updatedAt: String = "2025-01-15T12:00:00Z",
        slot: String? = null,
        deviceSyncs: List<RomMDeviceSync>? = emptyList()
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
        deviceSyncs = deviceSyncs
    )

    private fun makeSyncEntity(
        localSavePath: String? = null,
        lastUploadedHash: String? = null
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
        lastUploadedHash = lastUploadedHash
    )

    private fun setupConflictCheckMocks(
        syncEntity: SaveSyncEntity,
        deviceSyncs: List<RomMDeviceSync>? = emptyList()
    ) {
        val localFile = File.createTempFile("test_save", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val entityWithRealPath = syncEntity.copy(
            localSavePath = localFile.absolutePath
        )
        val serverSave = makeServerSave(deviceSyncs = deviceSyncs)
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns entityWithRealPath
    }
}
