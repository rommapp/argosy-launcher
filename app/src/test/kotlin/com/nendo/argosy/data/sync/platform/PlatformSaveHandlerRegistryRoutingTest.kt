package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import io.mockk.mockk
import org.junit.Assert.assertNotNull
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
    private val defaultHandler = mockk<DefaultSaveHandler>(relaxed = true)

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
            defaultSaveHandler = defaultHandler,
        )
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
    fun `retroarch + standard core (snes) still routes to RetroArchSaveHandler`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "snes")
        val handler = registry.getHandler(config, "snes", "retroarch_64")

        assertSame(retroArchHandler, handler)
    }

    @Test
    fun `standalone Switch emulator routes to SwitchSaveHandler`() {
        val config = SavePathRegistry.getConfigForPlatform("eden", "switch")
        val handler = registry.getHandler(config, "switch", "eden")

        assertSame(switchHandler, handler)
    }
}
