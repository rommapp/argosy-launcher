package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.BuiltinSaveBase
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.sync.SaveUnitResolver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SigilBuiltinRouteTest {

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val saveUnitResolver = mockk<SaveUnitResolver>(relaxed = true)
    private val builtinSaveBase = mockk<BuiltinSaveBase>(relaxed = true)
    private val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
    private val retroArchPathResolver = mockk<RetroArchPathResolver>(relaxed = true)

    private val handler = SigilSaveHandler(
        context = mockk<Context>(relaxed = true),
        gameDao = gameDao,
        emulatorResolver = emulatorResolver,
        emulatorSaveConfigRepository = mockk(relaxed = true),
        switchProfileParser = mockk(relaxed = true),
        titleDbRepository = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        fal = mockk(relaxed = true),
        connectionManager = { connectionManager },
        saveUnitResolver = saveUnitResolver,
        builtinSaveBase = builtinSaveBase,
        retroArchPathResolver = retroArchPathResolver,
        sigilSyncStateDao = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        cartFeatureScanner = mockk(relaxed = true),
        saveLocator = mockk(relaxed = true)
    )

    private fun builtinGame(platformSlug: String, core: String): GameEntity {
        val game = GameEntity(
            id = GAME_ID,
            title = "Game",
            sortTitle = "game",
            platformId = 1L,
            platformSlug = platformSlug,
            rommId = 7L,
            igdbId = null,
            localPath = "/roms/game.iso",
            source = GameSource.ROMM_SYNCED
        )
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns null
        coEvery { saveUnitResolver.layoutFor(game, EmulatorRegistry.BUILTIN_ID, null) } returns core
        coEvery { saveUnitResolver.optionsFor(any(), any()) } returns emptyMap()
        coEvery { builtinSaveBase.forGame(game, any()) } returns SAVES
        return game
    }

    @Test
    fun `each built-in core Sigil now covers maps to its own layout`() {
        assertEquals("ppsspp", SigilSaveHandler.libretroLayoutFor("psp", "ppsspp", retroArch = false))
        assertEquals("mednafen_psx_hw", SigilSaveHandler.libretroLayoutFor("psx", "mednafen_psx_hw", retroArch = false))
        assertEquals("pcsx_rearmed", SigilSaveHandler.libretroLayoutFor("psx", "pcsx_rearmed", retroArch = false))
        assertEquals("swanstation", SigilSaveHandler.libretroLayoutFor("psx", "swanstation", retroArch = false))
        assertEquals("azahar", SigilSaveHandler.libretroLayoutFor("3ds", "azahar", retroArch = false))
        assertEquals("azahar", SigilSaveHandler.libretroLayoutFor("n3ds", "azahar", retroArch = false))
        assertEquals("dolphin", SigilSaveHandler.libretroLayoutFor("gc", "dolphin", retroArch = false))
        assertEquals("dolphin", SigilSaveHandler.libretroLayoutFor("ngc", "dolphin", retroArch = false))
    }

    @Test
    fun `a core Sigil knows under another name resolves to that layout name and every other keeps its own`() {
        assertEquals("pcsx2", SigilSaveHandler.libretroLayoutName("armsx2"))
        assertEquals("mupen64plus_next", SigilSaveHandler.libretroLayoutName("mupen64plus_next_gles3"))
        assertEquals("mupen64plus_next", SigilSaveHandler.libretroLayoutName("mupen64plus_next_gles2"))
        assertEquals("pcsx2", SigilSaveHandler.libretroLayoutName("pcsx2"))
        assertEquals("snes9x", SigilSaveHandler.libretroLayoutName("snes9x"))
    }

    @Test
    fun `the built-in-only layouts never route under RetroArch`() {
        assertNull(SigilSaveHandler.libretroLayoutFor("psx", "pcsx_rearmed", retroArch = true))
        assertNull(SigilSaveHandler.libretroLayoutFor("3ds", "azahar", retroArch = true))
        assertNull(SigilSaveHandler.libretroLayoutFor("gc", "dolphin", retroArch = true))
        assertEquals("dolphin", SigilSaveHandler.libretroLayoutFor("wii", "dolphin", retroArch = true))
    }

    @Test
    fun `RetroArch's PPSSPP and Citra cores route through their own layouts`() {
        assertEquals("ppsspp", SigilSaveHandler.libretroLayoutFor("psp", "ppsspp", retroArch = true))
        assertEquals("citra", SigilSaveHandler.libretroLayoutFor("3ds", "citra", retroArch = true))
        assertEquals("citra", SigilSaveHandler.libretroLayoutFor("n3ds", "citra", retroArch = true))
    }

    @Test
    fun `RetroArch PPSSPP roots at the save folder RetroArch hands the core`() = runTest {
        val game = builtinGame("psp", "ppsspp")
        coEvery { saveUnitResolver.layoutFor(game, RETROARCH, null) } returns "ppsspp"
        coEvery {
            retroArchPathResolver.resolveSaveDirectories(match { it.coreName == "ppsspp" })
        } returns listOf(RETROARCH_PPSSPP_SAVES)

        val route = handler.localRoute(GAME_ID, RETROARCH)

        assertEquals("ppsspp", route?.layout)
        assertEquals(RETROARCH_PPSSPP_SAVES, route?.root)
    }

    @Test
    fun `RetroArch Citra roots at the Citra user folder under its save folder`() = runTest {
        val game = builtinGame("3ds", "citra")
        coEvery { saveUnitResolver.layoutFor(game, RETROARCH, null) } returns "citra"
        coEvery {
            retroArchPathResolver.resolveSaveDirectories(match { it.coreName == "citra" })
        } returns listOf(RETROARCH_CITRA_SAVES)

        val route = handler.localRoute(GAME_ID, RETROARCH)

        assertEquals("citra", route?.layout)
        assertEquals(File(RETROARCH_CITRA_SAVES, "Citra").absolutePath, route?.root)
    }

    @Test
    fun `built-in Azahar roots at the Azahar user folder under the save base`() = runTest {
        builtinGame("n3ds", "azahar")
        every { connectionManager.snapshotsEnabled() } returns true

        val route = handler.route(GAME_ID, EmulatorRegistry.BUILTIN_ID)

        assertEquals("azahar", route?.layout)
        assertEquals(File(SAVES, "Azahar").absolutePath, route?.root)
    }

    @Test
    fun `built-in PPSSPP and Dolphin root at the save base itself`() = runTest {
        every { connectionManager.snapshotsEnabled() } returns true

        builtinGame("psp", "ppsspp")
        assertEquals(SAVES, handler.route(GAME_ID, EmulatorRegistry.BUILTIN_ID)?.root)

        builtinGame("ngc", "dolphin")
        assertEquals(SAVES, handler.route(GAME_ID, EmulatorRegistry.BUILTIN_ID)?.root)
    }

    @Test
    fun `the local route stands without a units-capable server while the upload route does not`() = runTest {
        builtinGame("psx", "pcsx_rearmed")
        every { connectionManager.snapshotsEnabled() } returns false

        assertEquals("pcsx_rearmed", handler.localRoute(GAME_ID, EmulatorRegistry.BUILTIN_ID)?.layout)
        assertNull(handler.route(GAME_ID, EmulatorRegistry.BUILTIN_ID))
    }

    @Test
    fun `a core with no layout of its own falls back to the generic libretro layout`() {
        assertEquals("libretro", SigilSaveHandler.genericLibretroLayoutFor("snes", "snes9x"))
        assertEquals("libretro", SigilSaveHandler.genericLibretroLayoutFor("gba", "mgba"))
        assertNull(SigilSaveHandler.genericLibretroLayoutFor("psp", "ppsspp"))
        assertNull(SigilSaveHandler.genericLibretroLayoutFor("gc", "dolphin"))
        assertNull(SigilSaveHandler.genericLibretroLayoutFor("n64", "mupen64plus_next_gles3"))
        assertNull(SigilSaveHandler.genericLibretroLayoutFor("gb", "gambatte"))
    }

    @Test
    fun `an unlisted built-in core routes locally through the generic layout at the built-in save base`() = runTest {
        builtinGame("snes", "snes9x")
        every { connectionManager.snapshotsEnabled() } returns false

        val route = handler.localRoute(GAME_ID, EmulatorRegistry.BUILTIN_ID)

        assertEquals("libretro", route?.layout)
        assertEquals(SAVES, route?.root)
        assertEquals(true, route?.libretro)
    }

    @Test
    fun `an unlisted RetroArch core roots at the save folder RetroArch resolves for the real core`() = runTest {
        val game = builtinGame("snes", "snes9x")
        coEvery { saveUnitResolver.layoutFor(game, RETROARCH, null) } returns "snes9x"
        coEvery {
            retroArchPathResolver.resolveSaveDirectories(match { it.coreName == "snes9x" })
        } returns listOf(RETROARCH_SNES9X_SAVES)

        val route = handler.localRoute(GAME_ID, RETROARCH)

        assertEquals("libretro", route?.layout)
        assertEquals(RETROARCH_SNES9X_SAVES, route?.root)
    }

    @Test
    fun `the generic layout never decides what travels to RomM`() = runTest {
        val game = builtinGame("snes", "snes9x")
        coEvery { saveUnitResolver.layoutFor(game, RETROARCH, null) } returns "snes9x"
        every { connectionManager.snapshotsEnabled() } returns true

        assertNull(handler.route(GAME_ID, EmulatorRegistry.BUILTIN_ID))
        assertNull(handler.route(GAME_ID, RETROARCH))
    }

    @Test
    fun `a built-in-only core gets no generic route under RetroArch`() = runTest {
        val game = builtinGame("psx", "pcsx_rearmed")
        coEvery { saveUnitResolver.layoutFor(game, RETROARCH, null) } returns "pcsx_rearmed"

        assertNull(handler.localRoute(GAME_ID, RETROARCH))
    }

    private companion object {
        const val GAME_ID = 9L
        const val SAVES = "/data/user/0/com.nendo.argosy/files/libretro/saves"
        const val RETROARCH = "retroarch"
        const val RETROARCH_SNES9X_SAVES = "/storage/emulated/0/RetroArch/saves/Snes9x"
        const val RETROARCH_PPSSPP_SAVES = "/storage/emulated/0/RetroArch/saves/PPSSPP"
        const val RETROARCH_CITRA_SAVES = "/storage/emulated/0/RetroArch/saves"
    }
}
