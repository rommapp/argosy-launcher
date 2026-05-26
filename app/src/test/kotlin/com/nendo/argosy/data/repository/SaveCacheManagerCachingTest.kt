package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class SaveCacheManagerCachingTest {

    private lateinit var tempDir: File
    private lateinit var manager: SaveCacheManager

    private val context = mockk<Context>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_caching").toFile()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences())
        every { saveHandlerRegistry.getFolderHandler(any()) } returns null
        coEvery { saveCacheDao.findUnchangedSinceMtime(any(), any(), any()) } returns null
        coEvery { saveCacheDao.getByGameAndHash(any(), any()) } returns null
        coEvery { saveCacheDao.insert(any()) } returns 1L

        val fal = realFsFal()
        val archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        manager = SaveCacheManager(context, saveCacheDao, gameDao, preferencesRepository, archiver, fal, saveHandlerRegistry)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `caching a file save records the inserted entity with size and hash`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
        )

        assertTrue("Should report Created result", result is SaveCacheManager.CacheResult.Created)
        assertEquals(2048L, captured.captured.saveSize)
        assertTrue("Content hash must be populated", !captured.captured.contentHash.isNullOrBlank())
        assertEquals("retroarch", captured.captured.emulatorId)
        assertEquals(1L, captured.captured.gameId)
    }

    @Test
    fun `caching a file save returns Duplicate when DAO already has the same content hash`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024)) }
        val existing = SaveCacheEntity(
            id = 99L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 1024L,
            cachePath = "1/old/game.srm", contentHash = "deadbeef",
        )
        coEvery { saveCacheDao.getByGameAndHash(1L, any()) } returns existing

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            skipDuplicateCheck = false,
        )

        assertTrue("Duplicate must be reported when hash matches", result is SaveCacheManager.CacheResult.Duplicate)
        val dup = result as SaveCacheManager.CacheResult.Duplicate
        assertEquals(99L, dup.cacheId)
        coVerify(exactly = 0) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `mtime shortcut returns Duplicate without rehashing for unchanged file save`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024)) }
        val unchanged = SaveCacheEntity(
            id = 7L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 1024L,
            cachePath = "1/old/game.srm", contentHash = "cafebabe",
        )
        coEvery { saveCacheDao.findUnchangedSinceMtime(1L, 1024L, any()) } returns unchanged

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Duplicate)
        assertEquals("cafebabe", (result as SaveCacheManager.CacheResult.Duplicate).contentHash)
        coVerify(exactly = 0) { saveCacheDao.getByGameAndHash(any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `skipDuplicateCheck bypasses both mtime shortcut and hash lookup`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(512)) }
        coEvery { saveCacheDao.findUnchangedSinceMtime(any(), any(), any()) } returns SaveCacheEntity(
            id = 7L, gameId = 1L, emulatorId = "retroarch",
            cachedAt = Instant.now(), saveSize = 512L,
            cachePath = "1/old/game.srm", contentHash = "should-not-short-circuit",
        )

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            skipDuplicateCheck = true,
        )

        assertTrue("Caller asked to skip dedup, must Create", result is SaveCacheManager.CacheResult.Created)
        coVerify(exactly = 1) { saveCacheDao.insert(any()) }
    }

    @Test
    fun `caching a folder save zips contents and stores a zip cache path`() = runTest {
        val folder = File(tempDir, "savedir").apply { mkdirs() }
        File(folder, "a.bin").writeBytes(ByteArray(64))
        File(folder, "b.bin").writeBytes(ByteArray(64))
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "eden", savePath = folder.absolutePath,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Created)
        assertTrue("Folder caches must end with .zip", captured.captured.cachePath.endsWith(".zip"))
        val cachedZip = File(tempDir, "save_cache/${captured.captured.cachePath}")
        assertTrue("Cached zip must exist on disk", cachedZip.isFile)
        assertTrue("Cached zip must be non-empty", cachedZip.length() > 0L)
    }

    @Test
    fun `hardcore flag appends trailer to the cached file growing its size`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(256)) }
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            isHardcore = true,
        )

        assertTrue(result is SaveCacheManager.CacheResult.Created)
        assertTrue("Hardcore cache must be flagged on the entity", captured.captured.isHardcore)
        assertTrue("Hardcore cache must be larger than the source (trailer appended)", captured.captured.saveSize > 256L)
    }

    @Test
    fun `precomputedContentHash bypasses the archiver hash call`() = runTest {
        val save = File(tempDir, "game.srm").apply { writeBytes(ByteArray(1024)) }
        val captured = slot<SaveCacheEntity>()
        coEvery { saveCacheDao.insert(capture(captured)) } returns 1L

        manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = save.absolutePath,
            precomputedContentHash = "precomputed-hash-value",
        )

        assertEquals("precomputed-hash-value", captured.captured.contentHash)
    }

    @Test
    fun `failing save path returns Failed without DAO writes`() = runTest {
        val result = manager.cacheCurrentSave(
            gameId = 1L, emulatorId = "retroarch", savePath = "/nonexistent/path.srm",
        )

        assertTrue(result is SaveCacheManager.CacheResult.Failed)
        coVerify(exactly = 0) { saveCacheDao.insert(any()) }
    }
}
