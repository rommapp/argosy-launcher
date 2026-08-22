package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DOLPHIN_PKG = "org.dolphinemu.dolphinemu"

class SavePathAuthorityTest {

    private val saveConfigRepo: EmulatorSaveConfigRepository = mockk(relaxed = true)
    private val fal: FileAccessLayer = mockk(relaxed = true)

    private fun authority() = SavePathAuthority(saveConfigRepo, fal)

    private fun dirs(path: String, vararg names: String) {
        every { fal.exists(path) } returns true
        every { fal.isDirectory(path) } returns true
        every { fal.listFiles(path) } returns names.map {
            FileInfo(
                path = "$path/$it",
                name = it,
                isDirectory = true,
                isFile = false,
                size = 0,
                lastModified = 0
            )
        }
    }

    private fun unlistable(path: String) {
        every { fal.exists(path) } returns true
        every { fal.isDirectory(path) } returns true
        every { fal.listFiles(path) } returns null
    }

    /**
     * Issue #380. One emulator serving two platforms must not collapse onto one save layout, and
     * the config id is the override key, so a shared id is a shared row.
     */
    @Test
    fun `dolphin resolves a different config per platform`() {
        val auth = authority()
        val gc = auth.configIdFor(SavePathRequest("gc", "dolphin", DOLPHIN_PKG))
        val wii = auth.configIdFor(SavePathRequest("wii", "dolphin", DOLPHIN_PKG))

        assertEquals("dolphin", gc)
        assertEquals("dolphin_wii", wii)
        assertNotEquals("GameCube and Wii must not share an override key", gc, wii)
    }

    @Test
    fun `the platform decides the layout even when the package is given`() {
        val auth = authority()
        val wii = auth.configFor(SavePathRequest("wii", "dolphin", DOLPHIN_PKG))

        assertEquals("dolphin_wii", wii?.emulatorId)
        assertTrue("Wii saves are folder based, not GCI", wii?.usesFolderBasedSaves == true)
    }

    /**
     * The REGISTRY_DEFAULT branch expands `{extStorage}` through `Environment`, so it needs an
     * Android runtime and cannot be exercised here. Its inputs are covered by the config-id tests
     * above; the branch itself belongs to on-device verification.
     */
    @Test
    fun `an emulator with no save layout resolves to nothing rather than guessing`() = runTest {
        val auth = authority()
        val resolution = auth.resolve(SavePathRequest("gc", "no_such_emulator", null))

        assertEquals(SavePathSource.NONE, resolution.source)
        assertEquals(null, resolution.configId)
        assertEquals(null, resolution.basePath)
    }

    @Test
    fun `a per-game override outranks everything and is named`() = runTest {
        val auth = authority()
        val resolution = auth.resolve(
            SavePathRequest("wii", "dolphin", DOLPHIN_PKG, perGameOverride = "/roms/wii/saves")
        )

        assertEquals(SavePathSource.PER_GAME, resolution.source)
        assertEquals("/roms/wii/saves", resolution.basePath)
    }

    /**
     * The per-game leaf cannot be named without a game, so the shape is carried separately rather
     * than a base being shown as if saves sat directly in it.
     */
    @Test
    fun `wii carries the per-game remainder for display`() = runTest {
        val auth = authority()
        val resolution = auth.resolve(
            SavePathRequest("wii", "dolphin", DOLPHIN_PKG, perGameOverride = "/sd/Wii")
        )

        assertEquals("title/<type>/<id>/data", resolution.unresolvedShape)
        assertEquals("/sd/Wii", resolution.displayPath)
    }

    @Test
    fun `gamecube carries the region and card remainder`() = runTest {
        val auth = authority()
        val resolution = auth.resolve(
            SavePathRequest("gc", "dolphin", DOLPHIN_PKG, perGameOverride = "/sd/GC")
        )

        assertEquals("<region>/Card A", resolution.unresolvedShape)
    }

    @Test
    fun `a wii nand folder is accepted`() {
        val auth = authority()
        val config = auth.configFor(SavePathRequest("wii", "dolphin", DOLPHIN_PKG))
        dirs("/sd/Wii", "title", "ticket", "shared2")

        assertEquals(SavePathVerdict.Ok, auth.validate("/sd/Wii", config, "wii"))
    }

    @Test
    fun `a folder with no nand markers is flagged`() {
        val auth = authority()
        val config = auth.configFor(SavePathRequest("wii", "dolphin", DOLPHIN_PKG))
        dirs("/sd/files", "Cache", "Config", "Logs")

        val verdict = auth.validate("/sd/files", config, "wii")
        assertTrue("expected a warning, got $verdict", verdict is SavePathVerdict.LooksWrong)
    }

    /**
     * Unreadable is not absent. A card that is out must never be reported as the wrong folder, or
     * the user is told to fix something that is already correct.
     */
    @Test
    fun `an unlistable folder is unreadable, never wrong`() {
        val auth = authority()
        val config = auth.configFor(SavePathRequest("wii", "dolphin", DOLPHIN_PKG))
        unlistable("/sd/Wii")

        assertEquals(SavePathVerdict.Unreadable, auth.validate("/sd/Wii", config, "wii"))
    }

    @Test
    fun `a gamecube card folder is accepted and an unrelated one is flagged`() {
        val auth = authority()
        val config = auth.configFor(SavePathRequest("gc", "dolphin", DOLPHIN_PKG))

        dirs("/sd/GC", "USA", "EUR")
        assertEquals(SavePathVerdict.Ok, auth.validate("/sd/GC", config, "gc"))

        dirs("/sd/wrong", "Cache", "Logs")
        assertTrue(auth.validate("/sd/wrong", config, "gc") is SavePathVerdict.LooksWrong)
    }
}
