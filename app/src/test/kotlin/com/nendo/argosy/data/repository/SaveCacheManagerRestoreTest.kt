package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
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
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val saveArchiver = mockk<SaveArchiver>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_cache_restore_test").toFile()
        cacheBaseDir = File(tempDir, "save_cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        manager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            gameDao = gameDao,
            preferencesRepository = preferencesRepository,
            saveArchiver = saveArchiver,
            fal = fal,
            saveHandlerRegistry = saveHandlerRegistry,
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
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

        val ok = manager.restoreSave(cacheId = 1L, targetPath = "/restricted/path/target")

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

        val ok = manager.restoreSave(cacheId = 2L, targetPath = "/restricted/path/target.sav")

        assertFalse("Restore must report failure when copyFile returns false", ok)
        cacheFile.delete()
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
