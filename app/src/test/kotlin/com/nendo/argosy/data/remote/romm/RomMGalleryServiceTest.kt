package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.local.entity.ScreenshotSource
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files

class RomMGalleryServiceTest {

    private val romAdapter = Moshi.Builder().build().adapter(RomMRom::class.java)

    private val api = mockk<RomMApi>(relaxed = true)
    private val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
    private val dao = mockk<GameScreenshotDao>(relaxed = true)
    private val imageCacheManager = mockk<ImageCacheManager>(relaxed = true)
    private lateinit var cacheDir: File
    private lateinit var service: RomMGalleryService

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("gallery").toFile()
        every { connectionManager.getApi() } returns api
        every { connectionManager.getBaseUrl() } returns "https://romm"
        service = RomMGalleryService(
            connectionManager,
            RomMApiClient(connectionManager, mockk(relaxed = true)),
            dao,
            imageCacheManager
        )
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    private fun shotJson(id: Long, createdAt: String, isGallery: Boolean = true, username: String = "me") =
        """{"id":$id,"rom_id":42,"user_id":1,"file_extension":"png","is_gallery":$isGallery,
            |"created_at":"$createdAt","updated_at":"$createdAt","username":"$username"}""".trimMargin()

    private fun rom(own: List<String>, all: List<String>?): RomMRom {
        val allField = all?.let { ""","all_user_screenshots":[${it.joinToString(",")}]""" }.orEmpty()
        return requireNotNull(
            romAdapter.fromJson(
                """{"id":42,"platform_id":1,"platform_slug":"snes","name":"Game",
                    |"user_screenshots":[${own.joinToString(",")}]$allField}""".trimMargin()
            )
        )
    }

    private fun galleryRow(id: Long, cachedPath: String?, source: String = ScreenshotSource.OWN) =
        GameScreenshotEntity(
            gameId = 7L,
            position = 0,
            sourceUrl = "https://romm/api/screenshots/$id/content",
            cachedPath = cachedPath,
            cachedFromUrl = cachedPath?.let { "https://romm/api/screenshots/$id/content" },
            source = source,
            rommScreenshotId = id
        )

    @Test
    fun `all_user_screenshots is parsed and only gallery shots are kept`() {
        val parsed = rom(
            own = listOf(shotJson(1, "2026-01-01T00:00:00Z"), shotJson(2, "2026-01-02T00:00:00Z", isGallery = false)),
            all = listOf(
                shotJson(1, "2026-01-01T00:00:00Z"),
                shotJson(3, "2026-01-03T00:00:00Z", username = "ana"),
                shotJson(4, "2026-01-04T00:00:00Z", isGallery = false, username = "ana")
            )
        )

        assertEquals(listOf(1L, 3L, 4L), parsed.allUserScreenshots.orEmpty().map { it.id })
        assertEquals("ana", parsed.allUserScreenshots?.get(1)?.username)
        assertEquals("png", parsed.allUserScreenshots?.get(1)?.fileExtension)
        assertEquals(listOf(1L, 3L), parsed.galleryShots().map { it.screenshot.id })
    }

    @Test
    fun `own shots come before other users' and each group is newest first`() {
        val parsed = rom(
            own = listOf(shotJson(1, "2026-01-01T00:00:00Z"), shotJson(2, "2026-03-01T00:00:00Z")),
            all = listOf(
                shotJson(1, "2026-01-01T00:00:00Z"),
                shotJson(2, "2026-03-01T00:00:00Z"),
                shotJson(10, "2026-02-01T00:00:00Z", username = "ana"),
                shotJson(11, "2026-04-01T00:00:00Z", username = "bo")
            )
        )

        val shots = parsed.galleryShots()

        assertEquals(listOf(2L, 1L, 11L, 10L), shots.map { it.screenshot.id })
        assertEquals(
            listOf(ScreenshotSource.OWN, ScreenshotSource.OWN, ScreenshotSource.COMMUNITY, ScreenshotSource.COMMUNITY),
            shots.map { it.source }
        )
    }

    @Test
    fun `a server without all_user_screenshots yields own gallery shots only`() {
        val parsed = rom(
            own = listOf(shotJson(1, "2026-01-01T00:00:00Z"), shotJson(2, "2026-01-02T00:00:00Z", isGallery = false)),
            all = null
        )

        assertEquals(listOf(1L), parsed.galleryShots().map { it.screenshot.id })
    }

    @Test
    fun `planned rows are numbered per source and keep a stored shot's cache`() {
        val parsed = rom(
            own = listOf(shotJson(1, "2026-01-01T00:00:00Z")),
            all = listOf(shotJson(1, "2026-01-01T00:00:00Z"), shotJson(10, "2026-02-01T00:00:00Z", username = "ana"))
        )
        val stored = galleryRow(1L, "/cache/uss_42_1_abc.png")

        val plan = planGalleryRows(7L, listOf(stored), parsed.galleryShots()) { "https://romm/api/screenshots/$it/content" }

        assertEquals(listOf(0, 0), plan.rows.map { it.position })
        assertEquals("/cache/uss_42_1_abc.png", plan.rows[0].cachedPath)
        assertNull(plan.rows[1].cachedPath)
        assertEquals("ana", plan.rows[1].ownerUsername)
        assertEquals(ScreenshotSource.COMMUNITY, plan.rows[1].source)
        assertTrue(plan.removedFiles.isEmpty())
    }

    @Test
    fun `a failed RomM call keeps the stored rows and their files`() = runTest {
        val file = File(cacheDir, "uss_42_1_abc.png").apply { writeText("bytes") }
        coEvery { dao.getGalleryForGame(7L) } returns listOf(galleryRow(1L, file.absolutePath))
        coEvery { api.getRom(42L) } throws IOException("offline")

        val result = service.refresh(7L, 42L, downloadEnabled = true)

        assertNull(result)
        assertTrue(file.exists())
        coVerify(exactly = 0) { dao.replaceGallery(any(), any()) }
        coVerify(exactly = 0) { dao.deleteGalleryForGame(any()) }
    }

    @Test
    fun `an error response keeps the stored rows`() = runTest {
        coEvery { api.getRom(42L) } returns Response.error(503, "".toResponseBody())

        assertNull(service.refresh(7L, 42L, downloadEnabled = true))
        coVerify(exactly = 0) { dao.replaceGallery(any(), any()) }
    }

    @Test
    fun `a shot deleted on the server loses its row and its cached file`() = runTest {
        val kept = File(cacheDir, "uss_42_1_abc.png").apply { writeText("bytes") }
        val deleted = File(cacheDir, "uss_42_5_def.png").apply { writeText("bytes") }
        coEvery { dao.getGalleryForGame(7L) } returns listOf(
            galleryRow(1L, kept.absolutePath),
            galleryRow(5L, deleted.absolutePath)
        )
        coEvery { api.getRom(42L) } returns Response.success(
            rom(own = listOf(shotJson(1, "2026-01-01T00:00:00Z")), all = listOf(shotJson(1, "2026-01-01T00:00:00Z")))
        )

        service.refresh(7L, 42L, downloadEnabled = false)

        assertFalse(deleted.exists())
        assertTrue(kept.exists())
        coVerify(exactly = 1) {
            dao.replaceGallery(7L, match { rows -> rows.map { it.rommScreenshotId } == listOf(1L) })
        }
    }

    @Test
    fun `nothing is downloaded while screenshot sync is off`() = runTest {
        coEvery { dao.getGalleryForGame(7L) } returns emptyList()
        coEvery { imageCacheManager.userScreenshotTargetFile(any(), any(), any(), any()) } returns
            File(cacheDir, "uss_42_1_abc.png")
        coEvery { api.getRom(42L) } returns Response.success(
            rom(own = listOf(shotJson(1, "2026-01-01T00:00:00Z")), all = null)
        )

        service.refresh(7L, 42L, downloadEnabled = false)

        coVerify(exactly = 0) { api.downloadScreenshotContent(any()) }
        coVerify(exactly = 0) { dao.setGalleryCached(any(), any(), any(), any()) }
    }
}
