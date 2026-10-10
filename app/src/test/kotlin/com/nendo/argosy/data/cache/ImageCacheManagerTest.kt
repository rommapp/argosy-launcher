package com.nendo.argosy.data.cache

import android.content.Context
import coil.Coil
import coil.ImageLoader
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.dao.PendingScreenshot
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageVolumeHealth
import com.nendo.argosy.data.storage.VolumeProbe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ImageCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var gameDao: GameDao
    private lateinit var gameArtDao: GameArtDao
    private lateinit var gameScreenshotDao: GameScreenshotDao
    private lateinit var platformDao: PlatformDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var volumeHealth: StorageVolumeHealth
    private lateinit var fileAccessLayer: FileAccessLayer
    private lateinit var imageCacheManager: ImageCacheManager
    private lateinit var defaultCacheDir: File
    private lateinit var legacyCacheDir: File

    @Before
    fun setup() {
        legacyCacheDir = tempFolder.newFolder("cache")
        defaultCacheDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true) {
            every { cacheDir } returns legacyCacheDir
            every { filesDir } returns defaultCacheDir
        }
        gameDao = mockk(relaxed = true)
        gameArtDao = mockk(relaxed = true)
        gameScreenshotDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        achievementDao = mockk(relaxed = true)
        volumeHealth = mockk(relaxed = true)
        fileAccessLayer = mockk(relaxed = true)
        imageCacheManager = ImageCacheManager(
            context,
            gameDao,
            gameArtDao,
            gameScreenshotDao,
            platformDao,
            achievementDao,
            volumeHealth,
            fileAccessLayer
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun stubDecodedImageCache() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(Coil)
        mockkStatic(Coil::class)
        every { Coil.imageLoader(any()) } returns mockk<ImageLoader>(relaxed = true)
    }

    private fun cacheFile(vararg segments: String): File {
        val file = segments.fold(File(imageCacheManager.getCurrentCachePath())) { dir, name -> File(dir, name) }
        file.parentFile?.mkdirs()
        file.writeText("image")
        return file
    }

    private fun stubArt(slot: ArtSlot, overridePath: String? = null, cachedPath: String? = null, cachedFromUrl: String? = null) {
        coEvery { gameArtDao.get(7L, slot.name) } returns GameArtEntity(
            gameId = 7L,
            slot = slot.name,
            cachedPath = cachedPath,
            cachedFromUrl = cachedFromUrl,
            overridePath = overridePath
        )
    }

    private val oldUrl = "https://romm.example/assets/romm/resources/roms/1/42/cover/big.png?ts=2026-09-01"
    private val newUrl = "https://romm.example/assets/romm/resources/roms/1/42/cover/big.png?ts=2026-09-30"
    private val cachedFromOld = "/data/user/0/app/files/covers/snes/cover_42_${artUrlHash(oldUrl)}.jpg"

    @Test
    fun `cached art matches its source url and stops matching once the server url changes`() {
        assertTrue(isCachedFileFrom(cachedFromOld, oldUrl))
        assertFalse(isCachedFileFrom(cachedFromOld, newUrl))
    }

    @Test
    fun `a stale check on a row already cached from its source writes nothing`() = runTest {
        stubArt(ArtSlot.COVER, cachedPath = cachedFromOld, cachedFromUrl = oldUrl)

        imageCacheManager.queueArtIfStale(7L, ArtSlot.COVER, listOf(oldUrl), 42L, null, "Game")

        coVerify(exactly = 0) { gameArtDao.backfillCachedFromUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `a migrated row whose file name carries the source hash is backfilled without a download`() = runTest {
        stubArt(ArtSlot.COVER, cachedPath = cachedFromOld)

        imageCacheManager.queueArtIfStale(7L, ArtSlot.COVER, listOf(oldUrl), 42L, null, "Game")

        coVerify(exactly = 1) {
            gameArtDao.backfillCachedFromUrl(7L, ArtSlot.COVER.name, cachedFromOld, oldUrl)
        }
    }

    @Test
    fun `a cached file gone from a readable volume is forgotten so it downloads again`() = runTest {
        val probe = mockk<VolumeProbe> { every { isGenuinelyAbsent(cachedFromOld) } returns true }
        every { volumeHealth.newProbe() } returns probe
        stubArt(ArtSlot.BOX_SPINE, cachedPath = cachedFromOld, cachedFromUrl = oldUrl)

        imageCacheManager.queueArtIfStale(7L, ArtSlot.BOX_SPINE, listOf(oldUrl), 42L, null, "Game")

        coVerify(exactly = 1) { gameArtDao.updateCached(7L, ArtSlot.BOX_SPINE.name, null, null) }
    }

    @Test
    fun `a cached file the volume cannot vouch for is kept`() = runTest {
        val probe = mockk<VolumeProbe> { every { isGenuinelyAbsent(cachedFromOld) } returns false }
        every { volumeHealth.newProbe() } returns probe
        stubArt(ArtSlot.BOX_SPINE, cachedPath = cachedFromOld, cachedFromUrl = oldUrl)

        imageCacheManager.queueArtIfStale(7L, ArtSlot.BOX_SPINE, listOf(oldUrl), 42L, null, "Game")

        coVerify(exactly = 0) { gameArtDao.updateCached(any(), any(), any(), any()) }
    }

    @Test
    fun `every box face type routes to the box face cache, never the cover slot`() {
        assertTrue(ImageType.BOX_3D.isBoxFace)
        assertTrue(ImageType.BOX_SPINE.isBoxFace)
        assertTrue(ImageType.BOX_BACK.isBoxFace)
        assertTrue(ImageType.LOGO.isBoxFace)
        assertFalse(ImageType.COVER.isBoxFace)
        assertFalse(ImageType.BACKGROUND.isBoxFace)
    }

    @Test
    fun `a migrated row whose file came from another url is not backfilled`() = runTest {
        stubArt(ArtSlot.COVER, cachedPath = cachedFromOld)

        imageCacheManager.queueArtIfStale(7L, ArtSlot.COVER, listOf(newUrl), 42L, null, "Game")

        coVerify(exactly = 0) { gameArtDao.backfillCachedFromUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `server art older than the cached file keeps the file`() {
        assertEquals(CachedArtDecision.KEEP_AND_RENAME, cachedArtDecision(2_000L, 1_000L))
    }

    @Test
    fun `server art as old as the cached file keeps the file`() {
        assertEquals(CachedArtDecision.KEEP_AND_RENAME, cachedArtDecision(2_000L, 2_000L))
    }

    @Test
    fun `server art newer than the cached file replaces it`() {
        assertEquals(CachedArtDecision.REPLACE, cachedArtDecision(2_000L, 3_000L))
    }

    @Test
    fun `a server without Last-Modified replaces the cached file once`() {
        assertEquals(CachedArtDecision.REPLACE, cachedArtDecision(2_000L, 0L))
    }

    @Test
    fun `no answering candidate leaves the cached file alone`() {
        assertEquals(CachedArtDecision.SKIP, cachedArtDecision(2_000L, null))
    }

    @Test
    fun `clearing the image cache keeps referenced artwork overrides`() = runTest {
        val override = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        val unreferencedOverride = cacheFile("snes", "covers", "cover_override_9_def.jpg")
        val cached = cacheFile("snes", "covers", "cover_7_123456789abc.jpg")
        coEvery { gameArtDao.getAllOverridePaths() } returns listOf(override.absolutePath)

        imageCacheManager.clearCache()

        assertTrue(override.exists())
        assertFalse(unreferencedOverride.exists())
        assertFalse(cached.exists())
    }

    @Test
    fun `art every candidate reports gone is dropped`() {
        assertEquals(CachedArtDecision.DROP, cachedArtDecision(2_000L, null, everyCandidateGone = true))
    }

    @Test
    fun `an unreachable server is not mistaken for removed art`() {
        assertEquals(CachedArtDecision.SKIP, cachedArtDecision(2_000L, null, everyCandidateGone = false))
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
    fun `getDefaultCachePath returns path under context filesDir`() {
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

    @Test
    fun `platform cache clear deletes server art and keeps every override file`() = runTest {
        stubDecodedImageCache()
        val serverCover = cacheFile("snes", "covers", "cover_42_abc.jpg")
        val coverOverride = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        val backgroundOverride = cacheFile("snes", "backgrounds", "bg_override_7_def.jpg")
        val logoOverride = cacheFile("snes", "logos", "logo_override_7_ghi.png")
        coEvery { gameArtDao.getOverridePathsForPlatform("snes") } returns listOf(
            coverOverride.absolutePath,
            backgroundOverride.absolutePath,
            logoOverride.absolutePath
        )

        imageCacheManager.clearPlatformCache("snes")

        assertFalse(serverCover.exists())
        assertTrue(coverOverride.exists())
        assertTrue(backgroundOverride.exists())
        assertTrue(logoOverride.exists())
        coVerify(exactly = 1) { gameArtDao.clearCachedForPlatform("snes") }
        coVerify(exactly = 1) { gameScreenshotDao.clearCachedForPlatform("snes") }
        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.updateSourceUrl(any(), any(), any()) }
    }

    @Test
    fun `clearing an override clears the column and deletes its file`() = runTest {
        val override = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        stubArt(ArtSlot.COVER, overridePath = override.absolutePath)

        imageCacheManager.clearArtOverride(7L, ArtSlot.COVER)

        assertFalse(override.exists())
        coVerify(exactly = 1) { gameArtDao.updateOverride(7L, ArtSlot.COVER.name, null) }
    }

    @Test
    fun `clearing a background override leaves the cover override alone`() = runTest {
        val cover = cacheFile("snes", "covers", "cover_override_7_abc.jpg")
        val background = cacheFile("snes", "backgrounds", "bg_override_7_def.jpg")
        stubArt(ArtSlot.COVER, overridePath = cover.absolutePath)
        stubArt(ArtSlot.BACKGROUND, overridePath = background.absolutePath)

        imageCacheManager.clearArtOverride(7L, ArtSlot.BACKGROUND)

        assertTrue(cover.exists())
        assertFalse(background.exists())
        coVerify(exactly = 1) { gameArtDao.updateOverride(7L, ArtSlot.BACKGROUND.name, null) }
        coVerify(exactly = 0) { gameArtDao.updateOverride(7L, ArtSlot.COVER.name, any()) }
    }

    @Test
    fun `clearing an override never deletes a file the override did not write`() = runTest {
        val foreign = cacheFile("snes", "covers", "cover_42_abc.jpg")
        stubArt(ArtSlot.LOGO, overridePath = foreign.absolutePath)

        imageCacheManager.clearArtOverride(7L, ArtSlot.LOGO)

        assertTrue(foreign.exists())
        coVerify(exactly = 1) { gameArtDao.updateOverride(7L, ArtSlot.LOGO.name, null) }
    }

    @Test
    fun `clearing a slot with no override writes nothing`() = runTest {
        stubArt(ArtSlot.COVER)

        imageCacheManager.clearArtOverride(7L, ArtSlot.COVER)

        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
    }

    @Test
    fun `an unreadable picked file sets no override`() = runTest {
        every { fileAccessLayer.readBytes("/storage/emulated/0/Pictures/art.png") } returns null

        val applied = imageCacheManager.applyArtOverrideFromFile(
            7L,
            ArtSlot.BACKGROUND,
            "/storage/emulated/0/Pictures/art.png"
        )

        assertFalse(applied)
        coVerify(exactly = 0) { gameArtDao.setOverride(any(), any(), any()) }
        coVerify(exactly = 0) { gameArtDao.updateOverride(any(), any(), any()) }
    }

    @Test
    fun `missing-file sweep clears an override whose file is gone`() = runTest {
        stubDecodedImageCache()
        val probe = mockk<VolumeProbe>(relaxed = true)
        every { volumeHealth.newProbe() } returns probe
        every { probe.isGenuinelyAbsent("/gone/cover_override_7_abc.jpg") } returns true
        every { probe.isGenuinelyAbsent("/kept/bg_override_7_def.jpg") } returns false
        coEvery { gameArtDao.getAllCachedPaths() } returns emptyList()
        coEvery { gameArtDao.getAllOverridePaths() } returns listOf(
            "/gone/cover_override_7_abc.jpg",
            "/kept/bg_override_7_def.jpg"
        )
        coEvery { gameScreenshotDao.getCached() } returns emptyList()
        coEvery { platformDao.getAllPlatforms() } returns emptyList()

        imageCacheManager.validateAndCleanCache(force = true)

        coVerify(exactly = 1) { gameArtDao.clearOverridePaths(listOf("/gone/cover_override_7_abc.jpg")) }
    }

    @Test
    fun `missing-file sweep clears a cached path whose file is gone and keeps one on an unhealthy volume`() = runTest {
        stubDecodedImageCache()
        val probe = mockk<VolumeProbe>(relaxed = true)
        every { volumeHealth.newProbe() } returns probe
        every { probe.isGenuinelyAbsent("/gone/cover_42_abc.jpg") } returns true
        every { probe.isGenuinelyAbsent("/unmounted/bg_42_def.jpg") } returns false
        coEvery { gameArtDao.getAllCachedPaths() } returns listOf(
            "/gone/cover_42_abc.jpg",
            "/unmounted/bg_42_def.jpg"
        )
        coEvery { gameArtDao.getAllOverridePaths() } returns emptyList()
        coEvery { gameScreenshotDao.getCached() } returns emptyList()
        coEvery { platformDao.getAllPlatforms() } returns emptyList()

        imageCacheManager.validateAndCleanCache(force = true)

        coVerify(exactly = 1) { gameArtDao.clearCachedPaths(listOf("/gone/cover_42_abc.jpg")) }
    }

    @Test
    fun `missing-file sweep clears a screenshot row whose file is gone`() = runTest {
        stubDecodedImageCache()
        val probe = mockk<VolumeProbe>(relaxed = true)
        every { volumeHealth.newProbe() } returns probe
        every { probe.isGenuinelyAbsent("/gone/ss_42_0_abc.jpg") } returns true
        coEvery { gameArtDao.getAllCachedPaths() } returns emptyList()
        coEvery { gameArtDao.getAllOverridePaths() } returns emptyList()
        coEvery { gameScreenshotDao.getCached() } returns listOf(
            GameScreenshotEntity(7L, 0, oldUrl, "/gone/ss_42_0_abc.jpg", oldUrl)
        )
        coEvery { platformDao.getAllPlatforms() } returns emptyList()

        imageCacheManager.validateAndCleanCache(force = true)

        coVerify(exactly = 1) { gameScreenshotDao.clearCachedPaths(listOf("/gone/ss_42_0_abc.jpg")) }
    }

    @Test
    fun `a pending screenshot whose file name carries the source hash is backfilled and one from another url is queued`() {
        val matching = PendingScreenshot(7L, 0, oldUrl, cachedFromOld, null, 42L, "Game")
        val stale = PendingScreenshot(8L, 0, newUrl, cachedFromOld, null, 43L, "Other")
        coEvery { gameScreenshotDao.getPending() } returns listOf(matching, stale)

        imageCacheManager.resumePendingScreenshotCache()

        coVerify(timeout = 5_000) { gameScreenshotDao.backfillCachedFromUrls(listOf(matching)) }
        coVerify(timeout = 5_000) { gameScreenshotDao.getForGame(8L) }
        coVerify(exactly = 0) { gameScreenshotDao.getForGame(7L) }
    }

    @Test
    fun `only a cached file named for its source url can be backfilled`() {
        assertTrue(canBackfillCachedFromUrl(cachedFromOld, null, oldUrl))
        assertFalse(canBackfillCachedFromUrl(cachedFromOld, null, newUrl))
        assertFalse(canBackfillCachedFromUrl(cachedFromOld, oldUrl, oldUrl))
        assertFalse(canBackfillCachedFromUrl(null, null, oldUrl))
    }
}
