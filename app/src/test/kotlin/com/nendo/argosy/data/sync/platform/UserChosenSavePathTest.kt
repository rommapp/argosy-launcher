package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * A path the user picks in settings is stored as the root its platform scans from, so that
 * every level of a layout resolves to one value. The mapping from emulator to layout runs
 * through the registry, which is where a fork or a newly added emulator can silently miss.
 */
class UserChosenSavePathTest {

    private lateinit var tempDir: File
    private lateinit var registry: PlatformSaveHandlerRegistry

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("chosen_path").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = SaveArchiver(androidDataAccessor, fal),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `every folder-based emulator resolves to its platform's handler`() {
        val expected = mapOf(
            "azahar" to "3ds",
            "citra" to "3ds",
            "ppsspp" to "psp",
            "nethersx2" to "ps2",
            "aethersx2" to "ps2",
            "armsx2" to "ps2",
            "vita3k" to "vita",
            "cemu" to "wiiu"
        )
        expected.forEach { (emulatorId, platformSlug) ->
            val handler = registry.getFolderHandlerForEmulator(emulatorId)
            assertNotNull("$emulatorId resolved to no folder handler", handler)
            assertEquals(emulatorId, platformSlug, handler?.platformSlug)
        }
    }

    @Test
    fun `an emulator spanning many folder platforms resolves to none`() {
        assertNull(registry.getFolderHandlerForEmulator("retroarch"))
        assertNull(registry.getFolderHandlerForEmulator("builtin"))
    }

    @Test
    fun `an unknown emulator id resolves to none`() {
        assertNull(registry.getFolderHandlerForEmulator("not-an-emulator"))
    }

    @Test
    fun `a 3ds path above the sd root is stored as the sd root`() {
        val sdRoot = File(tempDir, "Azahar/sdmc/Nintendo 3DS").apply { mkdirs() }

        assertEquals(
            sdRoot.absolutePath,
            registry.normalizeUserChosenSavePath("azahar", File(tempDir, "Azahar").absolutePath)
        )
        assertEquals(
            sdRoot.absolutePath,
            registry.normalizeUserChosenSavePath("azahar", File(tempDir, "Azahar/sdmc").absolutePath)
        )
    }

    @Test
    fun `a 3ds path below the sd root is stored as the sd root`() {
        val sdRoot = File(tempDir, "Azahar/sdmc/Nintendo 3DS")
        val deep = File(sdRoot, "id0/id1/title/00040000/00033500/data").apply { mkdirs() }

        assertEquals(sdRoot.absolutePath, registry.normalizeUserChosenSavePath("azahar", deep.absolutePath))
    }

    @Test
    fun `a platform with no layout keeps the path as picked`() {
        val chosen = File(tempDir, "RetroArch/saves").apply { mkdirs() }.absolutePath

        assertEquals(chosen, registry.normalizeUserChosenSavePath("retroarch", chosen))
    }

    @Test
    fun `presence reports a missing folder`() {
        val present = File(tempDir, "here").apply { mkdirs() }
        assertTrue(registry.pathIsPresent(present.absolutePath))
        assertEquals(false, registry.pathIsPresent(File(tempDir, "gone").absolutePath))
    }
}
