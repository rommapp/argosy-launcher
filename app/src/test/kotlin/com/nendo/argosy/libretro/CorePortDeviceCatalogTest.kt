package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePortDeviceCatalogTest {

    private val catalogued = LibretroCoreRegistry.getAllCores().map { it.coreId to it.platforms.first() }

    @Test
    fun `every port leads with a real device, the one libretrodroid sets when nothing is stored`() {
        catalogued.forEach { (coreId, platform) ->
            CorePortDeviceCatalog.portDevices(coreId, platform).forEachIndexed { port, devices ->
                assertNotEquals("$coreId port $port", 0, devices.first().id)
                assertEquals("$coreId port $port", 0, CorePortDeviceCatalog.autoDeviceIndex(devices.map { it.id }))
            }
        }
    }

    @Test
    fun `auto is the first device that is not none`() {
        assertEquals(0, CorePortDeviceCatalog.autoDeviceIndex(listOf(1, 0, 2)))
        assertEquals(1, CorePortDeviceCatalog.autoDeviceIndex(listOf(0, 1, 257)))
        assertEquals(2, CorePortDeviceCatalog.autoDeviceIndex(listOf(0, 0, 3)))
        assertNull(CorePortDeviceCatalog.autoDeviceIndex(listOf(0)))
        assertNull(CorePortDeviceCatalog.autoDeviceIndex(emptyList()))
    }

    @Test
    fun `the selected device is the stored one, else auto, else the first`() {
        val ids = listOf(0, 1, 257)
        assertEquals(2, CorePortDeviceCatalog.selectedDeviceIndex(ids, 257))
        assertEquals(0, CorePortDeviceCatalog.selectedDeviceIndex(ids, 0))
        assertEquals(1, CorePortDeviceCatalog.selectedDeviceIndex(ids, null))
        assertEquals(1, CorePortDeviceCatalog.selectedDeviceIndex(ids, 513))
        assertEquals(0, CorePortDeviceCatalog.selectedDeviceIndex(listOf(0), null))
    }

    @Test
    fun `cores whose upstream offers one device per port, or none, have no entry`() {
        listOf(
            "mupen64plus_next_gles3" to "n64", "mupen64plus_next_gles2" to "n64",
            "mgba" to "gba", "gambatte" to "gb", "vbam" to "gba", "vba_next" to "gba", "gpsp" to "gba",
            "picodrive" to "genesis", "armsx2" to "ps2", "ppsspp" to "psp", "melonds" to "nds",
            "melondsds" to "nds", "azahar" to "3ds", "gw" to "gw"
        ).forEach { (coreId, platform) ->
            assertEquals(coreId, emptyList<List<CorePortDevice>>(), CorePortDeviceCatalog.portDevices(coreId, platform))
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

    @Test
    fun `swanstation uses its own device ids and drops the guncon past port two`() {
        val ports = CorePortDeviceCatalog.portDevices("swanstation", "psx")
        assertEquals(4, ports.size)
        assertEquals("Analog Controller (DualShock)", ports[0].first { it.id == 261 }.name)
        assertEquals("Analog Joystick", ports[0].first { it.id == 517 }.name)
        assertEquals("Namco GunCon", ports[1].first { it.id == 260 }.name)
        assertEquals(null, ports[2].firstOrNull { it.id == 260 })
    }
}
