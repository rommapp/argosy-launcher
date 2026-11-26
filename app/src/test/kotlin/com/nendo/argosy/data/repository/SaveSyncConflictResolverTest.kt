package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
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
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var gameDao: GameDao
    private lateinit var saveArchiver: SaveArchiver
    private lateinit var savePathResolver: SavePathResolver
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var saveCacheManager: dagger.Lazy<SaveCacheManager>
    private lateinit var apiClient: dagger.Lazy<SaveSyncApiClient>
    private lateinit var fal: com.nendo.argosy.data.storage.FileAccessLayer
    private lateinit var saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
    private lateinit var resolver: SaveSyncConflictResolver
    private val gciSaveHandler: com.nendo.argosy.data.sync.platform.GciSaveHandler = mockk(relaxed = true)

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
        emulatorConfigDao = mockk(relaxed = true)
        emulatorResolver = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        saveArchiver = mockk(relaxed = true)
        savePathResolver = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        fal = mockk(relaxed = true)
        saveHandlerRegistry = mockk(relaxed = true)
        every { saveHandlerRegistry.sigil } returns com.nendo.argosy.data.sync.fixtures.notRoutedSigil()
        every { fal.exists(any()) } returns true
        every { fal.commitSaveAccess(*anyVararg()) } returns true
        every { fal.getTransformedFile(any()) } answers { java.io.File(firstArg<String>()) }
        every { saveHandlerRegistry.isValidCachedSavePath(any(), any()) } returns true

        mockCacheManager = mockk(relaxed = true)
        mockApiClient = mockk(relaxed = true)
        mockApi = mockk(relaxed = true)

        saveCacheManager = dagger.Lazy { mockCacheManager }
        apiClient = dagger.Lazy { mockApiClient }

        every { mockApiClient.getApi() } returns mockApi
        every { mockApiClient.getDeviceId() } returns "device-1"

        coEvery { gameDao.getById(1L) } returns testGame

        resolver = SaveSyncConflictResolver(
            saveSyncDao = saveSyncDao,
            emulatorConfigDao = emulatorConfigDao,
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            userPreferencesRepository = userPreferencesRepository,
            syncPreferencesRepository = mockk(relaxed = true),
            saveCacheManager = saveCacheManager,
            apiClient = apiClient,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            gciSaveHandler = gciSaveHandler
        )
    }

    @Test
    fun `checkForConflict local matches client anchor returns null`() = runTest {
        val syncEntity = makeSyncEntity(localContentHash = "matching_hash")
        setupConflictCheckMocks(syncEntity)
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns "matching_hash"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict local changed but server unchanged is not a conflict`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "server_anchor", localContentHash = "anchor_local")
        setupConflictCheckMocks(syncEntity, contentHash = "server_anchor")
        every { mockApiClient.getCapabilities() } returns RomMCapabilities.from("4.9.0")
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns "new_local"

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNull(result)
    }

    @Test
    fun `checkForConflict both sides changed returns ConflictInfo`() = runTest {
        val syncEntity = makeSyncEntity(lastUploadedHash = "server_anchor", localContentHash = "anchor_local")
        setupConflictCheckMocks(syncEntity, contentHash = "server_new")
        every { mockApiClient.getCapabilities() } returns RomMCapabilities.from("4.9.0")
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns "new_local"

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
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns "restored_hash"

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
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns "restored_hash"

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
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns null

        val result = resolver.checkForConflict(1L, "retroarch", null)

        assertNotNull(result)
    }

    @Test
    fun `checkForConflict no local file returns null`() = runTest {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns makeSyncEntity(
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
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns makeSyncEntity(
            localSavePath = localFile.absolutePath
        )
        coEvery { mockCacheManager.calculateLocalSaveHash(any(), any(), any()) } returns null

        val result = resolver.checkForConflict(1L, "retroarch", "Pokemon Violet")

        assertNotNull("Accented server slot should match ASCII channel name", result)
        localFile.delete()
    }

    @Test
    fun `downgrading a GameCube hardcore save places the server save in the card folder`() = runTest {
        coEvery { gameDao.getById(2L) } returns testGame.copy(id = 2L, platformSlug = "ngc", localPath = "/roms/zelda.iso")
        coEvery { mockCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns true
        val placed = "/GC/USA/Card A/01-GZLE-DATA.gci"
        coEvery { gciSaveHandler.extractDownload(any(), any()) } returns
            com.nendo.argosy.data.sync.platform.ExtractResult(success = true, targetPath = placed)
        val held = File.createTempFile("held", ".gci").apply { writeBytes(byteArrayOf(1)) }

        val result = resolver.resolveHardcoreConflict(
            SaveSyncResult.NeedsHardcoreResolution(
                tempFilePath = held.absolutePath, gameId = 2L, gameName = "Zelda", emulatorId = "dolphin",
                targetPath = "/GC/USA/Card A/old.gci", isFolderBased = false, channelName = null
            ),
            HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
        )

        assertTrue("Expected success, got $result", result is SaveSyncResult.Success)
        io.mockk.coVerify { gciSaveHandler.extractDownload(any(), any()) }
        io.mockk.coVerify { mockCacheManager.cacheCurrentSave(2L, "dolphin", placed, null, any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        io.mockk.coVerify(exactly = 0) { saveArchiver.copyFileToPath(any(), any()) }
    }

    @Test
    fun `downgrading a GameCube hardcore save places it for the package the game launches with`() = runTest {
        val launchPackage = "org.dolphinemu.mmjr"
        val cardBase = "/storage/emulated/0/mmjr/GC"
        coEvery { gameDao.getById(2L) } returns testGame.copy(id = 2L, platformSlug = "ngc", localPath = "/roms/zelda.iso")
        coEvery { emulatorResolver.getEmulatorPackageForGame(2L, testGame.platformId, "ngc") } returns launchPackage
        coEvery { savePathResolver.gciBaseOverride(any(), 2L, "ngc", launchPackage) } returns cardBase
        coEvery { mockCacheManager.protectBeforeOverwrite(any(), any(), any()) } returns true
        val context = io.mockk.slot<com.nendo.argosy.data.sync.platform.SaveContext>()
        coEvery { gciSaveHandler.extractDownload(any(), capture(context)) } returns
            com.nendo.argosy.data.sync.platform.ExtractResult(success = true, targetPath = "$cardBase/USA/Card A/01-GZLE-DATA.gci")
        val held = File.createTempFile("held", ".gci").apply { writeBytes(byteArrayOf(1)) }

        val result = resolver.resolveHardcoreConflict(
            SaveSyncResult.NeedsHardcoreResolution(
                tempFilePath = held.absolutePath, gameId = 2L, gameName = "Zelda", emulatorId = "dolphin",
                targetPath = "/GC/USA/Card A/old.gci", isFolderBased = false, channelName = null
            ),
            HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
        )

        assertTrue("Expected success, got $result", result is SaveSyncResult.Success)
        assertEquals(launchPackage, context.captured.emulatorPackage)
        assertEquals(cardBase, context.captured.basePathOverride)
        io.mockk.coVerify(exactly = 0) { savePathResolver.gciBaseOverride(any(), any(), any(), null) }
    }

    @Test
    fun `a cached save path in the other Eden build's folder is read from the launch build's folder`() = runTest {
        val stable = "dev.eden.eden_emulator"
        val nightly = "dev.eden.eden_emulator.nightly"
        val root = java.nio.file.Files.createTempDirectory("eden-builds").toFile().apply { deleteOnExit() }
        fun saves(pkg: String) = File(root, "Android/data/$pkg/files/nand/user/save").apply { mkdirs() }
        val stableSaves = saves(stable)
        val nightlySaves = saves(nightly)
        coEvery { gameDao.getById(1L) } returns testGame.copy(platformSlug = "switch")
        coEvery { emulatorResolver.getEmulatorPackageForGame(1L, testGame.platformId, "switch") } returns nightly
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns
            makeSyncEntity(localSavePath = stableSaves.absolutePath).copy(emulatorId = "eden")
        coEvery { mockApiClient.checkSavesForGame(1L, 100L) } returns emptyList()

        val result = resolver.analyzeChannel(1L, "eden", null)

        assertEquals(SyncAnalysis.LocalNewer(nightlySaves.absolutePath, null), result)
        root.deleteRecursively()
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
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns entityWithRealPath
    }

}
