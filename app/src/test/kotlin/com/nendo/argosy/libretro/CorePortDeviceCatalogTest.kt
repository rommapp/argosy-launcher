package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePortDeviceCatalogTest {

    private val catalogued = LibretroCoreRegistry.getAllCores().map { it.coreId to it.platforms.first() }

    @Test
    fun `every port leads with a real device, the one libretrodroid sets when nothing is stored`() {
        catalogued.forEach { (coreId, platform) ->
            CorePortDeviceCatalog.portDevices(coreId, platform).forEachIndexed { port, devices ->
                assertNotEquals("$coreId port $port", 0, devices.first().id)
            }
        }
    }

    @Test
    fun `no port lists one device id twice`() {
        catalogued.forEach { (coreId, platform) ->
            CorePortDeviceCatalog.portDevices(coreId, platform).forEachIndexed { port, devices ->
                assertEquals("$coreId port $port", devices.size, devices.map { it.id }.distinct().size)
            }
        }
    }

    @Test
    fun `no core configures more ports than libretrodroid sets`() {
        catalogued.forEach { (coreId, platform) ->
            assertTrue(coreId, CorePortDeviceCatalog.portDevices(coreId, platform).size <= CorePortDeviceCatalog.MAX_PORTS)
        }
    }

    @Test
    fun `dolphin offers wiimotes on wii and nothing on gamecube`() {
        val wii = CorePortDeviceCatalog.portDevices("dolphin", "wii")
        assertEquals("WiiMote + Nunchuk", wii[0].first { it.id == 769 }.name)
        assertEquals("GameCube Controller", wii[0].first { it.id == 1537 }.name)
        assertEquals(emptyList<List<CorePortDevice>>(), CorePortDeviceCatalog.portDevices("dolphin", "ngc"))
    }

    @Test
    fun `the two psx cores keep separate lists`() {
        val pcsx = CorePortDeviceCatalog.portDevices("pcsx_rearmed", "psx")[0]
        val beetle = CorePortDeviceCatalog.portDevices("mednafen_psx_hw", "psx")[0]
        assertEquals("negcon", pcsx.first { it.id == 773 }.name)
        assertEquals("Analog Joystick", beetle.first { it.id == 773 }.name)
    }
}
