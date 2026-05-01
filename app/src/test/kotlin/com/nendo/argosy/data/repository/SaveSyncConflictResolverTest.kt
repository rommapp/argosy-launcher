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
import io.mockk.coVerify
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
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var gameDao: GameDao
    private lateinit var saveArchiver: SaveArchiver
    private lateinit var savePathResolver: SavePathResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var saveCacheManager: dagger.Lazy<SaveCacheManager>
    private lateinit var apiClient: dagger.Lazy<SaveSyncApiClient>
    private lateinit var switchSaveHandler: SwitchSaveHandler
    private lateinit var fal: com.nendo.argosy.data.storage.FileAccessLayer
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
        fal = mockk(relaxed = true)
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
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            userPreferencesRepository = userPreferencesRepository,
            saveCacheManager = saveCacheManager,
            apiClient = apiClient,
            switchSaveHandler = switchSaveHandler,
            fal = fal
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

    // --- Unicode preLaunchSync tests ---

    private val accentGame = GameEntity(
        id = 2L,
        title = "Pokemon Violet",
        sortTitle = "pokemon violet",
        platformId = 2L,
        platformSlug = "switch",
        rommId = 200L,
        igdbId = null,
        localPath = "/storage/roms/switch/Pokemon Violet.nsp",
        source = GameSource.ROMM_SYNCED
    )

    @Test
    fun `preLaunchSync fresh device with accented server save finds match`() = runTest {
        coEvery { gameDao.getById(2L) } returns accentGame
        coEvery { gameDao.getActiveSaveChannel(2L) } returns null
        coEvery { gameDao.getActiveSaveApplied(2L) } returns false
        every { mockApiClient.getDeviceId() } returns "device-new"

        val serverSave = makeServerSave(
            id = 10L,
            fileName = "Pok\u00e9mon Violet.srm",
            slot = null,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-other", isCurrent = true))
        ).copy(romId = 200L, emulator = "yuzu", fileNameNoExt = "Pok\u00e9mon Violet")
        coEvery { mockApiClient.checkSavesForGame(2L, 200L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(2L, 200L, "yuzu")

        assertTrue(
            "Expected ServerIsNewer but got $result (accented server filename should match ASCII romBaseName)",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync accented server slot matches active channel`() = runTest {
        coEvery { gameDao.getById(2L) } returns accentGame
        coEvery { gameDao.getActiveSaveChannel(2L) } returns "Pokemon Violet"
        coEvery { gameDao.getActiveSaveApplied(2L) } returns false

        val serverSave = makeServerSave(
            id = 10L,
            fileName = "Pok\u00e9mon Violet.srm",
            slot = "Pok\u00e9mon Violet",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        ).copy(romId = 200L, emulator = "yuzu", fileNameNoExt = "Pok\u00e9mon Violet")
        coEvery { mockApiClient.checkSavesForGame(2L, 200L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(2L, 200L, "yuzu")

        assertTrue(
            "Expected ServerIsNewer but got $result (accented slot should match ASCII activeChannel via equalsNormalized)",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync device not current with accented save returns ServerIsNewer`() = runTest {
        coEvery { gameDao.getById(2L) } returns accentGame
        coEvery { gameDao.getActiveSaveChannel(2L) } returns null
        coEvery { gameDao.getActiveSaveApplied(2L) } returns false

        val serverSave = makeServerSave(
            id = 10L,
            fileName = "Pok\u00e9mon Violet.srm",
            slot = null,
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        ).copy(romId = 200L, emulator = "yuzu", fileNameNoExt = "Pok\u00e9mon Violet")
        coEvery { mockApiClient.checkSavesForGame(2L, 200L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(2L, 200L, "yuzu")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync no deviceId with accented save uses timestamp fallback`() = runTest {
        every { mockApiClient.getDeviceId() } returns null
        coEvery { gameDao.getById(2L) } returns accentGame
        coEvery { gameDao.getActiveSaveChannel(2L) } returns null
        coEvery { gameDao.getActiveSaveApplied(2L) } returns false

        val serverSave = makeServerSave(
            id = 10L,
            fileName = "Pok\u00e9mon Violet.srm",
            updatedAt = "2025-01-15T12:00:00Z",
            slot = null,
            deviceSyncs = null
        ).copy(romId = 200L, emulator = "yuzu", fileNameNoExt = "Pok\u00e9mon Violet")
        coEvery { mockApiClient.checkSavesForGame(2L, 200L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(2L, 200L, "yuzu")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `checkForConflict accented server slot matches local channel`() = runTest {
        val localFile = File.createTempFile("test_save", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        coEvery { gameDao.getById(1L) } returns testGame
        val serverSave = makeServerSave(
            slot = "Pok\u00e9mon Violet",
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

    // --- Cross-device sync state tests ---

    @Test
    fun `preLaunchSync multiple server saves picks correct channel by slot`() = runTest {
        coEvery { gameDao.getActiveSaveChannel(1L) } returns "slot1"
        val slot1Save = makeServerSave(
            id = 1L,
            fileName = "slot1.srm",
            slot = "slot1",
            updatedAt = "2025-01-15T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        val slot2Save = makeServerSave(
            id = 2L,
            fileName = "slot2.srm",
            slot = "slot2",
            updatedAt = "2025-01-16T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        val latestSave = makeServerSave(
            id = 3L,
            fileName = "argosy-latest.srm",
            slot = null,
            updatedAt = "2025-01-17T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(slot1Save, slot2Save, latestSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(1L, "retroarch", "slot1") } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
        val serverResult = result as SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        assertEquals("slot1", serverResult.channelName)
    }

    @Test
    fun `preLaunchSync device sync entry missing for this device falls to timestamp`() = runTest {
        val serverSave = makeServerSave(
            deviceSyncs = listOf(
                RomMDeviceSync(deviceId = "device-other", isCurrent = true)
            )
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer when device has no sync entry (timestamp fallback), got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync server save with null emulator still matches`() = runTest {
        val serverSave = RomMSave(
            id = 1L,
            romId = 100L,
            userId = 1L,
            fileName = "argosy-latest.srm",
            emulator = null,
            updatedAt = "2025-01-15T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false)),
            fileNameNoExt = "argosy-latest"
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Null emulator server save should match, got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync sync entity lookup uses server slot for DB query`() = runTest {
        coEvery { gameDao.getActiveSaveChannel(1L) } returns "slot1"
        val serverSave = makeServerSave(
            slot = "slot1",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(1L, "retroarch", "slot1") } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(1L, "retroarch", "slot1") } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer)
        coVerify { saveSyncDao.upsert(match { it.channelName == "slot1" }) }
    }

    @Test
    fun `preLaunchSync flushPendingDeviceSync called before server check`() = runTest {
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns emptyList()

        resolver.preLaunchSync(1L, 100L, "retroarch")

        coVerify { mockApiClient.flushPendingDeviceSync(1L) }
    }

    // --- Hash divergence & local modification tests ---

    @Test
    fun `preLaunchSync no deviceId local hash differs from all caches returns LocalModified`() = runTest {
        every { mockApiClient.getDeviceId() } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false

        val localFile = File.createTempFile("test_local", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val syncEntity = makeSyncEntity(localSavePath = localFile.absolutePath)
        val serverSave = makeServerSave(deviceSyncs = null)
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns syncEntity
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns syncEntity
        coEvery { gameDao.getActiveSaveTimestamp(1L) } returns null
        coEvery { mockCacheManager.getMostRecentInChannel(any(), any()) } returns null
        coEvery { mockCacheManager.calculateLocalSaveHash(localFile.absolutePath) } returns "unique_local_hash"
        coEvery { mockCacheManager.getByGameAndHash(1L, "unique_local_hash") } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected LocalModified but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.LocalModified
        )
        localFile.delete()
    }

    @Test
    fun `preLaunchSync no deviceId local hash matches a cache returns ServerIsNewer`() = runTest {
        every { mockApiClient.getDeviceId() } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false

        val localFile = File.createTempFile("test_local", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(Instant.parse("2024-01-01T00:00:00Z").toEpochMilli())
            deleteOnExit()
        }
        val syncEntity = makeSyncEntity(localSavePath = localFile.absolutePath)
        val serverSave = makeServerSave(deviceSyncs = null)
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns syncEntity
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns syncEntity
        coEvery { gameDao.getActiveSaveTimestamp(1L) } returns null
        coEvery { mockCacheManager.getMostRecentInChannel(any(), any()) } returns null
        coEvery { mockCacheManager.calculateLocalSaveHash(localFile.absolutePath) } returns "known_hash"
        coEvery { mockCacheManager.getByGameAndHash(1L, "known_hash") } returns mockk(relaxed = true)

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
        localFile.delete()
    }

    @Test
    fun `preLaunchSync cache hash fallback when activeChannel set`() = runTest {
        coEvery { gameDao.getActiveSaveChannel(1L) } returns "slot1"
        val localFile = File.createTempFile("test_local", ".srm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val syncEntity = makeSyncEntity(localSavePath = localFile.absolutePath)
        val serverSave = makeServerSave(
            slot = "slot1",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(serverSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(1L, "retroarch", "slot1") } returns syncEntity
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns syncEntity
        coEvery { mockCacheManager.calculateLocalSaveHash(localFile.absolutePath) } returns "diverged_hash"
        val cacheEntry = mockk<SaveCacheEntity>(relaxed = true)
        every { cacheEntry.contentHash } returns "different_cache_hash"
        coEvery { saveCacheDao.getLatestCasualSaveInChannel(1L, "slot1") } returns cacheEntry

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected LocalModified from cache hash fallback but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.LocalModified
        )
        localFile.delete()
    }

    // --- Dual-screen carousel launch (activeChannel==null) with slotted saves ---

    @Test
    fun `preLaunchSync no activeChannel picks newer slotted save over stale unslotted save`() = runTest {
        // Reproduces Thor dual-screen bug: carousel launch has no activeChannel,
        // server has stale slot=None save (185) with is_current=true for this device,
        // plus newer slotted saves matching romBaseName. The stale save should NOT
        // be preferred -- the newest matching save should win.
        coEvery { gameDao.getById(2L) } returns accentGame
        coEvery { gameDao.getActiveSaveChannel(2L) } returns null
        coEvery { gameDao.getActiveSaveApplied(2L) } returns false

        val staleSave = makeServerSave(
            id = 185L,
            fileName = "Pok\u00e9mon Violet.zip",
            slot = null,
            updatedAt = "2026-02-28T10:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
        ).copy(romId = 200L, emulator = "yuzu", fileNameNoExt = "Pok\u00e9mon Violet")

        val newerSlottedSave = makeServerSave(
            id = 205L,
            fileName = "Pok\u00e9mon Violet [2026-03-02_09-10-49].zip",
            slot = "Pok\u00e9mon Violet",
            updatedAt = "2026-03-02T09:10:49Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        ).copy(romId = 200L, emulator = "yuzu", fileNameNoExt = "Pok\u00e9mon Violet [2026-03-02_09-10-49]")

        coEvery { mockApiClient.checkSavesForGame(2L, 200L) } returns listOf(staleSave, newerSlottedSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(2L, 200L, "yuzu")

        assertTrue(
            "Expected ServerIsNewer (should find newer slotted save) but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync no activeChannel matches save by slot equaling romBaseName`() = runTest {
        // When activeChannel is null, saves with slot matching romBaseName should be candidates
        coEvery { gameDao.getById(1L) } returns testGame
        coEvery { gameDao.getActiveSaveChannel(1L) } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false

        val slottedSave = makeServerSave(
            id = 10L,
            fileName = "test [2025-01-15_12-00-00].srm",
            slot = "test",
            updatedAt = "2025-01-15T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(slottedSave)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer (slot='test' matches romBaseName='test') but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
    }

    @Test
    fun `preLaunchSync no activeChannel picks most recent among multiple candidates`() = runTest {
        // When multiple saves match (both unslotted and slotted), pick the newest one
        coEvery { gameDao.getActiveSaveChannel(1L) } returns null
        coEvery { gameDao.getActiveSaveApplied(1L) } returns false

        val olderLatest = makeServerSave(
            id = 1L,
            fileName = "argosy-latest.srm",
            slot = null,
            updatedAt = "2025-01-10T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = true))
        )
        val newerSlotted = makeServerSave(
            id = 2L,
            fileName = "test [2025-01-15_12-00-00].srm",
            slot = "test",
            updatedAt = "2025-01-15T12:00:00Z",
            deviceSyncs = listOf(RomMDeviceSync(deviceId = "device-1", isCurrent = false))
        )
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns listOf(olderLatest, newerSlotted)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameAndEmulatorWithDefault(any(), any(), any()) } returns null

        val result = resolver.preLaunchSync(1L, 100L, "retroarch")

        assertTrue(
            "Expected ServerIsNewer but got $result",
            result is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        )
        val serverResult = result as SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer
        assertEquals(
            "Should pick the newer slotted save",
            "test",
            serverResult.channelName
        )
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
