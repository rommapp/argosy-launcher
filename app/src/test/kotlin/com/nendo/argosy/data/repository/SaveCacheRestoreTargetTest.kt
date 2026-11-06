package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilRoute
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class SaveCacheRestoreTargetTest {

    private lateinit var tempDir: File
    private lateinit var archiver: SaveArchiver
    private lateinit var manager: SaveCacheManager

    private val context = mockk<Context>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncPreferencesRepository = mockk<SyncPreferencesRepository>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)
    private val saveUnitResolver = mockk<SaveUnitResolver>(relaxed = true)
    private val ownership = mockk<SaveOwnershipTracker>(relaxed = true)
    private val sigil = mockk<SigilSaveHandler>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_restore_target").toFile()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences())
        every { saveHandlerRegistry.getFolderHandler(any()) } returns null
        coEvery { syncPreferencesRepository.isSecureSaves() } returns true
        coEvery { syncPreferencesRepository.getRommUserId() } returns OWNER
        coEvery { gameDao.getById(GAME_ID) } returns GAME
        coEvery { saveUnitResolver.resolveForSavePath(any(), any(), any(), any(), any()) } returns null
        coEvery { saveUnitResolver.layoutFor(any(), any(), any()) } returns null
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { sigil.localRoute(any(), any()) } returns null
        coEvery { sigil.collect(any(), any(), any()) } returns SigilCollect.NotRouted
        coEvery { sigil.restore(any(), any(), any()) } returns SigilRestore.NotRouted

        val fal = realFsFal()
        archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        manager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            pendingSyncQueueDao = mockk(relaxed = true),
            gameDao = gameDao,
            preferencesRepository = preferencesRepository,
            syncPreferencesRepository = syncPreferencesRepository,
            savePathResolver = mockk(relaxed = true),
            saveArchiver = archiver,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
            saveOwnershipTracker = ownership,
            saveOwnershipDao = mockk(relaxed = true),
            saveUnitResolver = saveUnitResolver,
            gciSaveHandler = GciSaveHandler(context, fal, archiver),
            sigilSaveHandler = sigil,
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun cachedRow(format: String): SaveCacheEntity {
        val cached = File(tempDir, "save_cache/$CACHE_PATH").apply { parentFile?.mkdirs(); writeBytes(ByteArray(64) { 7 }) }
        val row = SaveCacheEntity(
            id = ROW_ID,
            gameId = GAME_ID,
            emulatorId = SOURCE,
            cachedAt = Instant.EPOCH,
            saveSize = cached.length(),
            cachePath = CACHE_PATH,
            contentHash = archiver.calculateContentHash(cached),
            channelName = "autosave",
            ownerUserId = OWNER,
            saveFormat = format
        )
        coEvery { saveCacheDao.getById(ROW_ID) } returns row
        return row
    }

    @Test
    fun `a save restored through Sigil is placed and owned under the emulator it was restored for`() = runTest {
        cachedRow(SaveCacheEntity.FORMAT_NEUTRAL)
        val route = SigilRoute("pcsx_rearmed", File(tempDir, "builtin/saves").absolutePath, null, null, libretro = true)
        coEvery { sigil.localRoute(GAME_ID, TARGET) } returns route
        coEvery { sigil.restoreInto(route, GAME_ID, any()) } returns SigilRestore.Restored(hardcoreMarker = false)
        val target = route.root

        assertTrue(manager.restoreSave(ROW_ID, target, TARGET))

        coVerify(exactly = 1) { sigil.restoreInto(route, GAME_ID, any()) }
        coVerify(exactly = 1) { ownership.record(target, TARGET, any(), GAME_ID, any()) }
        coVerify(exactly = 0) { ownership.record(target, SOURCE, any(), any(), any()) }
    }

    @Test
    fun `the hash check after a restore reads the save as the target emulator`() = runTest {
        cachedRow(SaveCacheEntity.FORMAT_NATIVE)
        coEvery { sigil.collect(GAME_ID, TARGET, any()) } returns
            SigilCollect.Found(byteArrayOf(1), "Lunar.srm", "target-unit-hash", "target-unit-identity")
        val target = File(tempDir, "builtin/saves/Lunar.srm").absolutePath

        assertFalse(manager.restoreSave(ROW_ID, target, TARGET))
    }

    @Test
    fun `a restore for an emulator with no sync row yet creates the row and anchors it`() = runTest {
        coEvery { syncPreferencesRepository.isSecureSaves() } returns false
        cachedRow(SaveCacheEntity.FORMAT_NATIVE)
        val upserted = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(upserted)) } returns 1L
        val target = File(tempDir, "builtin/saves/Lunar.srm")

        assertTrue(manager.restoreSave(ROW_ID, target.absolutePath, TARGET))

        assertEquals(TARGET, upserted.captured.emulatorId)
        assertEquals("autosave", upserted.captured.channelName)
        assertEquals(OWNER, upserted.captured.ownerUserId)
        assertEquals(GAME.rommId, upserted.captured.rommId)
        assertEquals(target.absolutePath, upserted.captured.localSavePath)
        assertEquals(Instant.ofEpochMilli(target.lastModified()), upserted.captured.localUpdatedAt)
    }

    @Test
    fun `a native cache is never written over a Sigil save root`() = runTest {
        cachedRow(SaveCacheEntity.FORMAT_NATIVE)
        val root = File(tempDir, "builtin/saves")
        coEvery { sigil.localRoute(GAME_ID, TARGET) } returns
            SigilRoute("ppsspp", root.absolutePath, null, null, libretro = true)

        assertFalse(manager.restoreSave(ROW_ID, root.absolutePath, TARGET))
        assertFalse(root.exists())
    }

    @Test
    fun `a native cache bound for a Sigil save root is placed through Sigil, as a download is`() = runTest {
        cachedRow(SaveCacheEntity.FORMAT_NATIVE)
        val root = File(tempDir, "builtin/memcards")
        coEvery { sigil.localRoute(GAME_ID, TARGET) } returns
            SigilRoute("pcsx2", root.absolutePath, null, null, libretro = true)
        coEvery { sigil.restore(GAME_ID, any(), TARGET) } returns SigilRestore.Restored(hardcoreMarker = false)

        assertTrue(manager.restoreSave(ROW_ID, root.absolutePath, TARGET))

        coVerify(exactly = 1) { sigil.restore(GAME_ID, File(tempDir, "save_cache/$CACHE_PATH"), TARGET) }
        assertFalse(root.exists())
    }

    private companion object {
        const val GAME_ID = 4L
        const val ROW_ID = 31L
        const val OWNER = 3L
        const val SOURCE = "retroarch"
        const val TARGET = "argosy"
        const val CACHE_PATH = "u3/4/20261012_000000/Lunar.srm"
        val GAME = GameEntity(
            id = GAME_ID,
            title = "Lunar",
            sortTitle = "lunar",
            platformId = 1L,
            platformSlug = "psx",
            rommId = 77L,
            igdbId = null,
            localPath = "/roms/Lunar.cue",
            source = GameSource.ROMM_SYNCED
        )
    }
}
