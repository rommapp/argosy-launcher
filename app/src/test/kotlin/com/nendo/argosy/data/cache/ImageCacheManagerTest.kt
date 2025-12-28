package com.nendo.argosy.data.cache

import android.content.Context
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var imageCacheManager: ImageCacheManager
    private lateinit var defaultCacheDir: File

    @Before
    fun setup() {
        defaultCacheDir = tempFolder.newFolder("cache")
        context = mockk {
            every { cacheDir } returns defaultCacheDir
        }
        gameDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        achievementDao = mockk(relaxed = true)
        imageCacheManager = ImageCacheManager(context, gameDao, platformDao, achievementDao)
    }

    @Test
    fun `getCustomCachePath returns null by default`() {
        assertNull(imageCacheManager.getCustomCachePath())
    }

    @Test
    fun `setCustomCachePath updates customCachePath`() {
        val customPath = tempFolder.newFolder("custom").absolutePath

        imageCacheManager.setCustomCachePath(customPath)

        assertEquals(customPath, imageCacheManager.getCustomCachePath())
    }

    @Test
    fun `setCustomCachePath with null clears customCachePath`() {
        val customPath = tempFolder.newFolder("custom").absolutePath
        imageCacheManager.setCustomCachePath(customPath)

        imageCacheManager.setCustomCachePath(null)

        assertNull(imageCacheManager.getCustomCachePath())
    }

    @Test
    fun `getDefaultCachePath returns path under context cacheDir`() {
        val path = imageCacheManager.getDefaultCachePath()

        assertTrue(path.startsWith(defaultCacheDir.absolutePath))
        assertTrue(path.endsWith("images"))
    }

    @Test
    fun `getCurrentCachePath returns default path when customCachePath is null`() {
        val path = imageCacheManager.getCurrentCachePath()

        assertEquals(imageCacheManager.getDefaultCachePath(), path)
    }

    @Test
    fun `getCurrentCachePath returns custom path when customCachePath is set`() {
        val customPath = tempFolder.newFolder("custom").absolutePath
        imageCacheManager.setCustomCachePath(customPath)

        val path = imageCacheManager.getCurrentCachePath()

        assertTrue(path.startsWith(customPath))
        assertTrue(path.endsWith("argosy_images"))
    }

    @Test
    fun `setCustomCachePath creates argosy_images subfolder`() {
        val customPath = tempFolder.newFolder("custom").absolutePath

        imageCacheManager.setCustomCachePath(customPath)

        val expectedSubfolder = File(customPath, "argosy_images")
        assertTrue(expectedSubfolder.exists())
        assertTrue(expectedSubfolder.isDirectory)
    }

    @Test
    fun `getCurrentCachePath switches correctly between default and custom`() {
        val customPath = tempFolder.newFolder("custom").absolutePath
        val defaultPath = imageCacheManager.getDefaultCachePath()

        assertEquals(defaultPath, imageCacheManager.getCurrentCachePath())

        imageCacheManager.setCustomCachePath(customPath)
        assertTrue(imageCacheManager.getCurrentCachePath().startsWith(customPath))

        imageCacheManager.setCustomCachePath(null)
        assertEquals(defaultPath, imageCacheManager.getCurrentCachePath())
    }
}
