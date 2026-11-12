package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.util.AppPaths
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class StateCacheManagerScreenshotTest {

    private val filesDir = createTempDirectory("state_screenshot").toFile()
    private val context = mockk<Context>(relaxed = true).also { every { it.filesDir } returns filesDir }

    private val manager = StateCacheManager(
        context = context,
        gameDao = mockk(relaxed = true),
        stateCacheDao = mockk(relaxed = true),
        stateTombstoneDao = mockk(relaxed = true),
        saveCacheDao = mockk(relaxed = true),
        saveSyncDao = mockk(relaxed = true),
        pendingSyncQueueDao = mockk(relaxed = true),
        emulatorSaveConfigDao = mockk(relaxed = true),
        preferencesRepository = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        coreVersionExtractor = mockk(relaxed = true),
        retroArchConfigParser = mockk(relaxed = true),
        retroArchPathResolver = mockk(relaxed = true),
        libretroStatePathResolver = mockk(relaxed = true),
        saveSyncApiClient = mockk(relaxed = true),
        payloadCodec = mockk(relaxed = true),
        attributionRepository = mockk(relaxed = true),
        stateOwnershipTracker = mockk(relaxed = true)
    )

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `a screenshot that cannot be written does not fail the state restore`() {
        val cached = File(AppPaths.stateCacheDir(filesDir), "1/shot.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val liveState = File(filesDir, "states/game.state").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9))
        }
        File("${liveState.absolutePath}.png", "occupied").apply { parentFile?.mkdirs(); writeText("x") }

        manager.restoreScreenshot(entity(screenshotPath = "1/${cached.name}"), liveState)

        assertTrue(liveState.exists())
    }

    @Test
    fun `a ppsspp state gets its screenshot back as the same-stem jpg`() {
        val cached = File(AppPaths.stateCacheDir(filesDir), "1/ULUS10064_1.00_2.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val liveState = File(filesDir, "PPSSPP_STATE/ULUS10064_1.00_2.ppst").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9))
        }

        manager.restoreScreenshot(
            entity(screenshotPath = "1/${cached.name}", emulatorId = "ppsspp"),
            liveState
        )

        val sidecar = File(liveState.parentFile, "ULUS10064_1.00_2.jpg")
        assertTrue(sidecar.readBytes().contentEquals(byteArrayOf(4, 5, 6)))
        assertFalse(File("${liveState.absolutePath}.png").exists())
    }

    @Test
    fun `displacing a ppsspp state moves its jpg with it`() {
        val liveState = File(filesDir, "PPSSPP_STATE/ULUS10064_1.00_0.ppst").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9))
        }
        val sidecar = File(liveState.parentFile, "ULUS10064_1.00_0.jpg").apply { writeBytes(byteArrayOf(1)) }

        assertTrue(manager.displaceLiveState(liveState, "ppsspp"))

        assertFalse(liveState.exists())
        assertFalse(sidecar.exists())
        val displaced = File(AppPaths.stateCacheDir(filesDir), "displaced").walkTopDown().map { it.name }.toSet()
        assertTrue("ULUS10064_1.00_0.jpg" in displaced)
    }

    private fun entity(screenshotPath: String, emulatorId: String = "argosy") = StateCacheEntity(
        gameId = 1L,
        platformSlug = "snes",
        emulatorId = emulatorId,
        slotNumber = 1,
        cachedAt = Instant.now(),
        stateSize = 1,
        cachePath = "1/game.state",
        screenshotPath = screenshotPath
    )
}
