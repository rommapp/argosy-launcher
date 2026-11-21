package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class SaveCacheManagerRestoreTest {

    private lateinit var tempDir: File
    private lateinit var cacheBaseDir: File
    private lateinit var manager: SaveCacheManager

    private val context = mockk<Context>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<com.nendo.argosy.data.local.dao.SaveSyncDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncPreferencesRepository = mockk<com.nendo.argosy.data.preferences.SyncPreferencesRepository>(relaxed = true)
    private val savePathResolver = mockk<com.nendo.argosy.data.sync.SavePathResolver>(relaxed = true)
    private val saveArchiver = mockk<SaveArchiver>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_restore_test").toFile()
        cacheBaseDir = File(tempDir, "save_cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        coEvery { syncPreferencesRepository.isSecureSaves() } returns true
        manager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            pendingSyncQueueDao = mockk(relaxed = true),
            gameDao = gameDao,
            preferencesRepository = preferencesRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            savePathResolver = savePathResolver,
            saveArchiver = saveArchiver,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            saveOwnershipTracker = mockk(relaxed = true),
            saveOwnershipDao = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true),
            gciSaveHandler = GciSaveHandler(context, fal, saveArchiver),
            sigilSaveHandler = com.nendo.argosy.data.sync.fixtures.notRoutedSigil(),
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun syncRecord(rommSaveId: Long?, localContentHash: String?) =
        com.nendo.argosy.data.local.entity.SaveSyncEntity(
            gameId = 100L,
            rommId = 3169L,
            emulatorId = "argosy",
            channelName = "autosave",
            rommSaveId = rommSaveId,
            localSavePath = "/saves/Crystal.srm",
            localUpdatedAt = null,
            serverUpdatedAt = null,
            lastSyncedAt = null,
            syncStatus = com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SYNCED,
            localContentHash = localContentHash
        )

    @Test
    fun `a live save still hashing as the sync record placed it is unchanged, one played on is not`() = runTest {
        val spied = io.mockk.spyk(manager)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(100L, "argosy", "autosave", any()) } returns
            syncRecord(rommSaveId = 2192L, localContentHash = "placed")

        coEvery { spied.calculateLocalSaveHash("/saves/Crystal.srm", 100L, "argosy") } returns "placed"
        assertTrue(spied.isUnchangedServerPlacement(100L, "argosy", null, "/saves/Crystal.srm"))

        coEvery { spied.calculateLocalSaveHash("/saves/Crystal.srm", 100L, "argosy") } returns "played"
        assertFalse(spied.isUnchangedServerPlacement(100L, "argosy", null, "/saves/Crystal.srm"))
    }

    @Test
    fun `a placed save whose clock ticked but whose data did not still counts as unchanged`() = runTest {
        val spied = io.mockk.spyk(manager)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(100L, "argosy", "autosave", any()) } returns
            syncRecord(rommSaveId = 2194L, localContentHash = "ram-and-old-clock").copy(localIdentityHash = "ram")
        coEvery { spied.calculateLocalSaveHash("/saves/Silver.srm", 100L, "argosy") } returns "ram-and-new-clock"

        coEvery { spied.calculateLocalSaveIdentity("/saves/Silver.srm", 100L, "argosy") } returns "ram"
        assertTrue(spied.isUnchangedServerPlacement(100L, "argosy", null, "/saves/Silver.srm"))

        coEvery { spied.calculateLocalSaveIdentity("/saves/Silver.srm", 100L, "argosy") } returns "played"
        assertFalse(spied.isUnchangedServerPlacement(100L, "argosy", null, "/saves/Silver.srm"))
    }

    @Test
    fun `a sync record that never matched a server save is not a placement`() = runTest {
        val spied = io.mockk.spyk(manager)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(100L, "argosy", "autosave", any()) } returns
            syncRecord(rommSaveId = null, localContentHash = "placed")
        coEvery { spied.calculateLocalSaveHash(any(), any(), any()) } returns "placed"

        assertFalse(spied.isUnchangedServerPlacement(100L, "argosy", null, "/saves/Crystal.srm"))
    }

    @Test
    fun `restoreSave returns false when unzipSingleFolder fails`() = runTest {
        val cacheFile = File(cacheBaseDir, "save.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        val entity = entity(cachePath = "save.zip")
        coEvery { saveCacheDao.getById(1L) } returns entity
        coEvery { gameDao.getById(any()) } returns null
        every { fal.mkdirs(any()) } returns true
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { saveArchiver.unzipSingleFolder(any(), any()) } returns false

        val ok = manager.restoreSave(cacheId = 1L, targetPath = "/restricted/path/target", targetEmulatorId = "eden")

        assertFalse("Restore must report failure when unzip returns false", ok)
        cacheFile.delete()
    }

    @Test
    fun `restoreSave returns false when fal copyFile fails for plain save`() = runTest {
        val cacheFile = File(cacheBaseDir, "save.sav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entity = entity(cachePath = "save.sav")
        coEvery { saveCacheDao.getById(2L) } returns entity
        every { fal.mkdirs(any()) } returns true
        every { saveArchiver.readBytesWithoutTrailer(any()) } returns null
        every { fal.copyFile(any(), any()) } returns false

        val ok = manager.restoreSave(cacheId = 2L, targetPath = "/restricted/path/target.sav", targetEmulatorId = "eden")

        assertFalse("Restore must report failure when copyFile returns false", ok)
        cacheFile.delete()
    }

    @Test
    fun `a ps2 cache restore prepares the folder card after unpacking`() = runTest {
        val cacheFile = File(cacheBaseDir, "card.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        coEvery { saveCacheDao.getById(3L) } returns entity(cachePath = "card.zip").copy(emulatorId = "nethersx2")
        coEvery { gameDao.getById(any()) } returns com.nendo.argosy.data.local.entity.GameEntity(
            id = 100L,
            title = "Game",
            sortTitle = "game",
            platformId = 1L,
            platformSlug = "ps2",
            rommId = 7L,
            igdbId = null,
            localPath = null,
            source = com.nendo.argosy.data.model.GameSource.ROMM_SYNCED
        )
        val folderHandler = mockk<com.nendo.argosy.data.sync.platform.FolderSaveHandler>(relaxed = true)
        every { saveHandlerRegistry.getFolderHandler("ps2") } returns folderHandler
        every { fal.mkdirs(any()) } returns true
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { saveArchiver.unzipToFolder(any(), any()) } returns true
        val target = File(tempDir, "memcards/Shared.ps2")

        manager.restoreSave(cacheId = 3L, targetPath = target.absolutePath, targetEmulatorId = "nethersx2")

        io.mockk.verify(exactly = 1) { folderHandler.ensureContainerPrepared(target) }
        cacheFile.delete()
    }

    private fun switchGame(titleId: String) = com.nendo.argosy.data.local.entity.GameEntity(
        id = 100L,
        title = "Advance Wars",
        sortTitle = "advance wars",
        platformId = 1L,
        platformSlug = "switch",
        rommId = 7L,
        igdbId = null,
        localPath = null,
        source = com.nendo.argosy.data.model.GameSource.ROMM_SYNCED,
        titleId = titleId
    )

    private fun stubSwitchRestore(cacheName: String, archiveRoot: String) {
        File(cacheBaseDir, cacheName).writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        coEvery { saveCacheDao.getById(4L) } returns entity(cachePath = cacheName)
        coEvery { gameDao.getById(100L) } returns switchGame("0100300012F2A000")
        every { saveArchiver.peekRootFolderName(any()) } returns archiveRoot
        every { saveArchiver.isJksvFormat(any()) } returns false
        every { saveHandlerRegistry.getFolderHandler("switch") } returns null
        every { fal.mkdirs(any()) } returns true
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { saveArchiver.unzipSingleFolder(any(), any()) } returns true
    }

    @Test
    fun `a Switch cache rooted at another game's title id is never unpacked`() = runTest {
        stubSwitchRestore("other.zip", archiveRoot = "0100000000010000")
        val target = File(tempDir, "save/0000000000000000/PROFILE/0100300012F2A000")

        val ok = manager.restoreSave(cacheId = 4L, targetPath = target.absolutePath, targetEmulatorId = "eden")

        assertFalse(ok)
        io.mockk.verify(exactly = 0) { saveArchiver.unzipSingleFolder(any(), any()) }
    }

    @Test
    fun `a Switch cache rooted at the game's own title id or its update id is unpacked`() = runTest {
        for (root in listOf("0100300012F2A000", "0100300012F2A800")) {
            stubSwitchRestore("own-$root.zip", archiveRoot = root)
            val target = File(tempDir, "save/0000000000000000/PROFILE/0100300012F2A000")

            manager.restoreSave(cacheId = 4L, targetPath = target.absolutePath, targetEmulatorId = "eden")
        }

        io.mockk.verify(exactly = 2) { saveArchiver.unzipSingleFolder(any(), any()) }
    }

    @Test
    fun `placing a save for a game with no RomM id creates no sync row`() = runTest {
        val target = File(tempDir, "placed.srm").apply { writeBytes(byteArrayOf(1)) }
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { gameDao.getById(100L) } returns com.nendo.argosy.data.local.entity.GameEntity(
            id = 100L,
            title = "Local Game",
            sortTitle = "local game",
            platformId = 1L,
            platformSlug = "snes",
            rommId = null,
            igdbId = null,
            localPath = null,
            source = com.nendo.argosy.data.model.GameSource.LOCAL_ONLY
        )
        every { fal.isDirectory(target.absolutePath) } returns false
        every { fal.lastModified(target.absolutePath) } returns 1_700_000_000_000L

        manager.recordPlacement(
            entity(cachePath = "placed.srm").copy(channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME),
            target.absolutePath,
            "retroarch",
            secureSaves = false
        )

        io.mockk.coVerify(exactly = 0) { saveSyncDao.upsert(any()) }
    }

    private fun entity(cachePath: String): SaveCacheEntity = SaveCacheEntity(
        id = 0,
        gameId = 100L,
        emulatorId = "eden",
        cachedAt = Instant.now(),
        saveSize = 0,
        cachePath = cachePath,
        contentHash = "deadbeef"
    )
}
