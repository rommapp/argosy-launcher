package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.emulator.LibretroSavePathResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DOLPHIN_PKG = "org.dolphinemu.dolphinemu"
private const val PPSSPP_PKG = "org.ppsspp.ppsspp"
private const val PPSSPP_ROOT = "/storage/emulated/0/Android/data/org.ppsspp.ppsspp"
private const val PPSSPP_OWN = "$PPSSPP_ROOT/files/PSP/SAVEDATA"
private const val PPSSPP_SHARED = "/storage/emulated/0/PSP/SAVEDATA"

class SavePathAuthorityTest {

    private val saveConfigRepo: EmulatorSaveConfigRepository = mockk(relaxed = true)
    private val fal: FileAccessLayer = mockk(relaxed = true)
    private val libretroSavePathResolver: LibretroSavePathResolver = mockk(relaxed = true)

    private fun authority(candidates: List<String> = emptyList()) =
        object : SavePathAuthority(saveConfigRepo, fal, libretroSavePathResolver) {
            override fun candidatePaths(
                config: SavePathConfig,
                emulatorPackage: String?,
                builtinSavesDir: String?
            ) = candidates
        }

    private val psp = SavePathRequest("psp", "ppsspp", PPSSPP_PKG)
    private val pspCandidates = listOf(PPSSPP_OWN, PPSSPP_SHARED)

    private fun nothingStored() {
        coEvery { saveConfigRepo.resolveUserSavePath(any(), any()) } returns null
        coEvery { saveConfigRepo.resolveEvaluatedSavePath(any()) } returns null
    }

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
        assertEquals("/sd/Wii", resolution.basePath)
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

    /**
     * PPSSPP's own folder is the preferred default. Readability is judged on the package root,
     * which every installed app populates, so an empty save folder inside it still counts.
     */
    @Test
    fun `ppsspp settles on its own folder when it can be read`() = runTest {
        val auth = authority(pspCandidates)
        nothingStored()
        dirs(PPSSPP_ROOT, "files")
        dirs(PPSSPP_OWN, "ULUS10064DATA00")

        assertEquals(PPSSPP_OWN, auth.ensureEvaluatedDefault(psp))
        coVerify { saveConfigRepo.setEvaluatedSavePath("ppsspp", PPSSPP_OWN) }
    }

    @Test
    fun `ppsspp falls back to the shared folder when its own cannot be seen`() = runTest {
        val auth = authority(pspCandidates)
        nothingStored()
        dirs(PPSSPP_SHARED, "ULUS10064DATA00")

        assertEquals(PPSSPP_SHARED, auth.ensureEvaluatedDefault(psp))
        coVerify { saveConfigRepo.setEvaluatedSavePath("ppsspp", PPSSPP_SHARED) }
    }

    @Test
    fun `a folder that already holds saves outranks an empty preferred one`() = runTest {
        val auth = authority(pspCandidates)
        nothingStored()
        dirs(PPSSPP_ROOT, "files")
        dirs(PPSSPP_OWN)
        dirs(PPSSPP_SHARED, "ULUS10064DATA00")

        assertEquals(PPSSPP_SHARED, auth.ensureEvaluatedDefault(psp))
    }

    @Test
    fun `an existing empty preferred folder outranks a readable root with no folder yet`() = runTest {
        val auth = authority(pspCandidates)
        nothingStored()
        dirs(PPSSPP_ROOT, "files")
        dirs(PPSSPP_OWN)

        assertEquals(PPSSPP_OWN, auth.ensureEvaluatedDefault(psp))
    }

    @Test
    fun `resolve reports whether the preferred folder could be read`() = runTest {
        val auth = authority(pspCandidates)
        coEvery { saveConfigRepo.resolveUserSavePath(any(), any()) } returns null
        coEvery { saveConfigRepo.resolveEvaluatedSavePath("ppsspp") } returns PPSSPP_SHARED

        assertFalse(auth.resolve(psp).preferredReadable)

        dirs(PPSSPP_ROOT, "files")
        assertTrue(auth.resolve(psp).preferredReadable)
    }

    @Test
    fun `nothing is written when no folder can be seen`() = runTest {
        val auth = authority(pspCandidates)
        nothingStored()

        assertNull(auth.ensureEvaluatedDefault(psp))
        coVerify(exactly = 0) { saveConfigRepo.setEvaluatedSavePath(any(), any()) }
    }

    /**
     * Not on a whim: once a folder is settled, a session that could now read the preferred one
     * does not move onto it. Only a reset does.
     */
    @Test
    fun `an evaluated folder holds even when the preferred one becomes readable`() = runTest {
        val auth = authority(pspCandidates)
        coEvery { saveConfigRepo.resolveUserSavePath(any(), any()) } returns null
        coEvery { saveConfigRepo.resolveEvaluatedSavePath("ppsspp") } returns PPSSPP_SHARED
        dirs(PPSSPP_ROOT, "files")
        dirs(PPSSPP_OWN, "ULUS10064DATA00")

        assertEquals(PPSSPP_SHARED, auth.ensureEvaluatedDefault(psp))
        coVerify(exactly = 0) { saveConfigRepo.setEvaluatedSavePath(any(), any()) }

        val resolution = auth.resolve(psp)
        assertEquals(SavePathSource.EVALUATED_DEFAULT, resolution.source)
        assertEquals(PPSSPP_SHARED, resolution.basePath)
        assertEquals(PPSSPP_OWN, resolution.preferredPath)
        assertTrue("the shared folder is a fallback from the preferred one", resolution.isFallbackDefault)
    }

    @Test
    fun `an evaluated folder that is the preferred one is not a fallback`() = runTest {
        val auth = authority(pspCandidates)
        coEvery { saveConfigRepo.resolveUserSavePath(any(), any()) } returns null
        coEvery { saveConfigRepo.resolveEvaluatedSavePath("ppsspp") } returns PPSSPP_OWN

        val resolution = auth.resolve(psp)
        assertEquals(SavePathSource.EVALUATED_DEFAULT, resolution.source)
        assertFalse(resolution.isFallbackDefault)
    }

    private fun builtinAuthority() =
        object : SavePathAuthority(saveConfigRepo, fal, libretroSavePathResolver) {
            override fun candidatePaths(
                config: SavePathConfig,
                emulatorPackage: String?,
                builtinSavesDir: String?
            ) = listOf("$builtinSavesDir/PSP/SAVEDATA")
        }

    private val builtinPsp = SavePathRequest("psp", "builtin", "argosy.builtin.libretro", platformId = 7L)

    @Test
    fun `the built-in core resolves through the libretro save directory and names the source`() = runTest {
        val auth = builtinAuthority()
        coEvery { libretroSavePathResolver.liveSaveBaseDir(any(), any()) } returns java.io.File("/sd/argosy-saves")
        every { libretroSavePathResolver.isDefaultBase(any()) } returns false

        val resolution = auth.resolve(builtinPsp)

        assertEquals("/sd/argosy-saves/PSP/SAVEDATA", resolution.basePath)
        assertEquals(SavePathSource.USER_OVERRIDE, resolution.source)
        coVerify(exactly = 0) { saveConfigRepo.resolveUserSavePath(any(), any()) }
    }

    @Test
    fun `the built-in default base reads as the registry default`() = runTest {
        val auth = builtinAuthority()
        coEvery { libretroSavePathResolver.liveSaveBaseDir(any(), any()) } returns java.io.File("/data/app/files/libretro/saves")
        every { libretroSavePathResolver.isDefaultBase(any()) } returns true

        val resolution = auth.resolve(builtinPsp)

        assertEquals("/data/app/files/libretro/saves/PSP/SAVEDATA", resolution.basePath)
        assertEquals(SavePathSource.REGISTRY_DEFAULT, resolution.source)
    }

    @Test
    fun `the built-in core is never evaluated`() = runTest {
        val auth = builtinAuthority()

        assertNull(auth.ensureEvaluatedDefault(builtinPsp))
        coVerify(exactly = 0) { saveConfigRepo.setEvaluatedSavePath(any(), any()) }
    }

    @Test
    fun `a psp save folder is accepted at any level of the memory stick`() {
        val auth = authority()
        val config = auth.configFor(psp)

        assertEquals(SavePathVerdict.Ok, auth.validate("/sd/MemStick/PSP/SAVEDATA", config, "psp"))
        dirs("/sd/MemStick/PSP", "SAVEDATA", "SYSTEM")
        assertEquals(SavePathVerdict.Ok, auth.validate("/sd/MemStick/PSP", config, "psp"))
        dirs("/sd/MemStick", "PSP")
        assertEquals(SavePathVerdict.Ok, auth.validate("/sd/MemStick", config, "psp"))
    }

    @Test
    fun `a folder with no psp markers is flagged and an unlistable one is unreadable`() {
        val auth = authority()
        val config = auth.configFor(psp)

        dirs("/sd/Downloads", "Cache", "Logs")
        assertTrue(auth.validate("/sd/Downloads", config, "psp") is SavePathVerdict.LooksWrong)

        unlistable("/sd/gone")
        assertEquals(SavePathVerdict.Unreadable, auth.validate("/sd/gone", config, "psp"))
    }

    @Test
    fun `a user override outranks evaluation and is never overwritten by it`() = runTest {
        val auth = authority(pspCandidates)
        coEvery { saveConfigRepo.resolveUserSavePath("ppsspp", "psp") } returns "/sd/psp-saves"
        dirs(PPSSPP_ROOT, "files")
        dirs(PPSSPP_OWN, "ULUS10064DATA00")

        assertNull(auth.ensureEvaluatedDefault(psp))
        coVerify(exactly = 0) { saveConfigRepo.setEvaluatedSavePath(any(), any()) }
        assertEquals(SavePathSource.USER_OVERRIDE, auth.resolve(psp).source)
    }
}
