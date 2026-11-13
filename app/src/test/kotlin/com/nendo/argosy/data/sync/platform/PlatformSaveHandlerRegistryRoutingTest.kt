package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlatformSaveHandlerRegistryRoutingTest {

    private val context = mockk<Context>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val archiver = mockk<SaveArchiver>(relaxed = true)
    private val switchHandler = mockk<SwitchSaveHandler>(relaxed = true)
    private val gciHandler = mockk<GciSaveHandler>(relaxed = true)
    private val retroArchHandler = mockk<RetroArchSaveHandler>(relaxed = true)
    private val dreamcastHandler = mockk<DreamcastSaveHandler>(relaxed = true)
    private val defaultHandler = mockk<DefaultSaveHandler>(relaxed = true)
    private val unitHandler = mockk<UnitSaveHandler>(relaxed = true)
    private val sigilHandler = mockk<SigilSaveHandler>(relaxed = true)

    private lateinit var registry: PlatformSaveHandlerRegistry

    @Before
    fun setUp() {
        registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = archiver,
            switchSaveHandler = switchHandler,
            gciSaveHandler = gciHandler,
            retroArchSaveHandler = retroArchHandler,
            dreamcastSaveHandler = dreamcastHandler,
            defaultSaveHandler = defaultHandler,
            unitSaveHandler = unitHandler,
            sigilSaveHandler = sigilHandler,
        )
    }

    @Test
    fun `built-in Dreamcast routes to the VMU handler rather than the file default`() {
        val config = SavePathRegistry.getConfigForPlatform("argosy", "dreamcast")
        assertNotNull("builtin_dreamcast config must exist", config)
        assertEquals(listOf("bin"), config!!.saveExtensions)

        val handler = registry.getHandler(config, "dreamcast", "argosy")

        assertSame(dreamcastHandler, handler)
    }

    @Test
    fun `standalone Flycast routes to the standalone VMU names`() {
        val standalone = mockk<PlatformSaveHandler>(relaxed = true)
        every { dreamcastHandler.standalone } returns standalone
        val config = SavePathRegistry.getConfigForPlatform("flycast", "dc")
        assertNotNull("standalone flycast config must be usable", config)

        assertSame(standalone, registry.getHandler(config, "dc", "flycast"))
    }

    @Test
    fun `retroarch + ppsspp folder config routes to PSP folder handler not RetroArch`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "psp")
        assertNotNull("retroarch_64_psp config must exist", config)
        assertTrue("config must declare folder layout", config!!.usesFolderBasedSaves)

        val handler = registry.getHandler(config, "psp", "retroarch_64")

        val pspHandler = registry.getFolderHandler("psp")
        assertSame("Must route to PSP folder handler", pspHandler, handler)
    }

    @Test
    fun `retroarch + citra config routes to 3DS folder handler`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "3ds")
        assertNotNull("retroarch_64_3ds config must exist", config)
        assertTrue(config!!.usesFolderBasedSaves)

        val handler = registry.getHandler(config, "3ds", "retroarch_64")

        val n3dsHandler = registry.getFolderHandler("3ds")
        assertSame("Must route to 3DS folder handler", n3dsHandler, handler)
    }

    @Test
    fun `retroarch + dolphin libretro config routes to GCI handler`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "ngc")
        assertNotNull("retroarch_64_ngc config must exist", config)
        assertTrue("config must declare GCI format", config!!.usesGciFormat)

        val handler = registry.getHandler(config, "ngc", "retroarch_64")

        assertSame(gciHandler, handler)
    }

    @Test
    fun `retroarch + dolphin libretro on Wii routes to the Wii folder handler`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "wii")
        assertNotNull("retroarch_64_wii config must exist", config)
        assertTrue(config!!.usesFolderBasedSaves)

        val handler = registry.getHandler(config, "wii", "retroarch_64")

        assertSame(registry.getFolderHandler("wii"), handler)
    }

    @Test
    fun `retroarch + standard core (snes) routes to the unit handler`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "snes")
        val handler = registry.getHandler(config, "snes", "retroarch_64")

        assertSame(unitHandler, handler)
    }

    @Test
    fun `builtin file save routes to the unit handler`() {
        val config = SavePathRegistry.getConfigForPlatform("argosy", "gbc")
        val handler = registry.getHandler(config, "gbc", "argosy")

        assertSame(unitHandler, handler)
    }

    @Test
    fun `built-in ps2 routes to the unit handler and never to the PS2 folder handler`() {
        every { sigilHandler.routes(any(), any()) } answers {
            SigilSaveHandler.layoutFor(firstArg(), secondArg()) != null
        }
        val config = SavePathRegistry.getConfigForPlatform("argosy", "ps2")
        assertNotNull(config)

        val handler = registry.getHandler(config, "ps2", "argosy")

        assertSame(unitHandler, handler)
        assertNotSame(registry.getFolderHandler("ps2"), handler)
    }

    @Test
    fun `standalone file save keeps the default handler`() {
        val config = SavePathRegistry.getConfigForPlatform("pizza_boy_gb", "gb")
        val handler = registry.getHandler(config, "gb", "pizza_boy_gb")

        assertSame(defaultHandler, handler)
    }

    @Test
    fun `standalone card and profile emulators keep their legacy handlers before RomM 5_5`() {
        every { sigilHandler.routes(any(), any()) } returns false
        mapOf(
            ("eden" to "switch") to switchHandler,
            ("dolphin" to "ngc") to gciHandler,
            ("nethersx2" to "ps2") to registry.getFolderHandler("ps2"),
            ("ppsspp" to "psp") to registry.getFolderHandler("psp"),
            ("vita3k" to "vita") to registry.getFolderHandler("vita"),
            ("aps3e" to "ps3") to registry.getFolderHandler("ps3"),
            ("cemu" to "wiiu") to registry.getFolderHandler("wiiu"),
            ("azahar" to "3ds") to registry.getFolderHandler("3ds")
        ).forEach { (key, expected) ->
            val (emulator, platform) = key
            val config = SavePathRegistry.getConfigForPlatform(emulator, platform)
            assertSame("$emulator on $platform", expected, registry.getHandler(config, platform, emulator))
        }
    }

    @Test
    fun `standalone card and profile emulators route to Sigil`() {
        every { sigilHandler.routes(any(), any()) } answers {
            SigilSaveHandler.layoutFor(firstArg(), secondArg()) != null
        }
        listOf(
            "eden" to "switch",
            "ryujinx" to "switch",
            "dolphin" to "ngc",
            "nethersx2" to "ps2",
            "duckstation" to "psx",
            "ppsspp" to "psp",
            "vita3k" to "vita",
            "aps3e" to "ps3",
            "cemu" to "wiiu",
            "azahar" to "3ds"
        ).forEach { (emulator, platform) ->
            val config = SavePathRegistry.getConfigForPlatform(emulator, platform)
            assertSame("$emulator on $platform", sigilHandler, registry.getHandler(config, platform, emulator))
        }
    }

    @Test
    fun `a game Sigil routes through a standalone route takes the Sigil handler before RomM 5_5`() = runTest {
        every { sigilHandler.routes(any(), any()) } returns false
        coEvery { sigilHandler.route(7L, any()) } returns
            SigilRoute("pcsx2", "/sdcard/NetherSX2/memcards", null, null, libretro = false)
        val config = SavePathRegistry.getConfigForPlatform("nethersx2", "ps2")

        assertSame(sigilHandler, registry.getHandlerFor(7L, config, "ps2", "nethersx2"))
        assertSame(registry.getFolderHandler("ps2"), registry.getHandler(config, "ps2", "nethersx2"))
    }

    @Test
    fun `a libretro Sigil route keeps the game-agnostic handler`() = runTest {
        every { sigilHandler.routes(any(), any()) } returns false
        coEvery { sigilHandler.route(7L, any()) } returns
            SigilRoute("pcsx2", "/data/system/pcsx2/memcards", null, null, libretro = true)
        val config = SavePathRegistry.getConfigForPlatform("argosy", "ps2")

        assertSame(registry.getHandler(config, "ps2", "argosy"), registry.getHandlerFor(7L, config, "ps2", "argosy"))
    }

    @Test
    fun `a libretro Sigil route takes the Sigil handler where the built-in config keeps folders or GCI files`() = runTest {
        every { sigilHandler.routes(any(), any()) } returns false
        coEvery { sigilHandler.route(7L, any()) } returns
            SigilRoute("ppsspp", "/data/saves/psp", null, null, libretro = true)

        listOf("psp", "3ds", "wii", "gc").forEach { platform ->
            val config = SavePathRegistry.getConfigForPlatform("argosy", platform)
            assertTrue("built-in $platform keeps folders or GCI files", config!!.usesFolderBasedSaves || config.usesGciFormat)
            assertTrue("built-in $platform", registry.isSigilRoutedFor(7L, config, platform, "argosy"))
            assertSame("built-in $platform", sigilHandler, registry.getHandlerFor(7L, config, platform, "argosy"))
        }
    }

    @Test
    fun `no Sigil route keeps the game-agnostic handler for a folder-shaped built-in config`() = runTest {
        every { sigilHandler.routes(any(), any()) } returns false
        coEvery { sigilHandler.route(7L, any()) } returns null
        val config = SavePathRegistry.getConfigForPlatform("argosy", "psp")

        assertEquals(false, registry.isSigilRoutedFor(7L, config, "psp", "argosy"))
        assertSame(registry.getHandler(config, "psp", "argosy"), registry.getHandlerFor(7L, config, "psp", "argosy"))
    }

    @Test
    fun `dolphin on wii goes through sigil`() {
        every { sigilHandler.routes(any(), any()) } answers {
            SigilSaveHandler.layoutFor(firstArg(), secondArg()) != null
        }
        val config = SavePathRegistry.getConfigForPlatform("dolphin", "wii")
        assertSame(sigilHandler, registry.getHandler(config, "wii", "dolphin"))
    }

    @Test
    fun `a dolphin wii save path anchors at the folder holding Wii`() {
        assertEquals(
            "/sdcard/Android/data/org.dolphinemu.dolphinemu/files",
            SigilSaveHandler.rootFor("/sdcard/Android/data/org.dolphinemu.dolphinemu/files/Wii/title/00010000", "dolphin_standalone")
        )
        assertEquals(
            "/sdcard/Android/data/org.dolphinemu.dolphinemu/files",
            SigilSaveHandler.rootFor("/sdcard/Android/data/org.dolphinemu.dolphinemu/files/GC/USA/Card A", "dolphin_standalone")
        )
    }
}
