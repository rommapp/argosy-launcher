package com.nendo.argosy.data.sync.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SigilSaveRootTest {

    private val data = "/storage/emulated/0/Android/data"

    @Test
    fun `each standalone save path roots where its layout expects`() {
        assertEquals("$data/com.nethersx2/files", SigilSaveHandler.rootFor("$data/com.nethersx2/files/memcards", listOf("memcards")))
        assertEquals("$data/org.dolphinemu.dolphinemu/files", SigilSaveHandler.rootFor("$data/org.dolphinemu.dolphinemu/files/GC", listOf("GC")))
        assertEquals("$data/org.ppsspp.ppsspp/files", SigilSaveHandler.rootFor("$data/org.ppsspp.ppsspp/files/PSP/SAVEDATA", listOf("PSP/SAVEDATA")))
        assertEquals("$data/org.vita3k.emulator/files/VITA", SigilSaveHandler.rootFor("$data/org.vita3k.emulator/files/VITA/ux0/user/00/savedata", listOf("ux0/user")))
        assertEquals("$data/aenu.aps3e/files/aps3e/config", SigilSaveHandler.rootFor("$data/aenu.aps3e/files/aps3e/config/dev_hdd0/home/00000001/savedata", listOf("dev_hdd0/home")))
        assertEquals("$data/dev.eden.eden_emulator/files", SigilSaveHandler.rootFor("$data/dev.eden.eden_emulator/files/nand/user/save", listOf("nand/user/save/0000000000000000")))
        assertEquals("$data/skyline.emu/files", SigilSaveHandler.rootFor("$data/skyline.emu/files/switch/nand/user/save", listOf("switch/nand/user/save/0000000000000000")))
        assertEquals("$data/org.kenjinx.android/files", SigilSaveHandler.rootFor("$data/org.kenjinx.android/files/bis/user/save", listOf("bis/user/save", "system")))
        assertEquals("$data/info.cemu.cemu/files", SigilSaveHandler.rootFor("$data/info.cemu.cemu/files/mlc01/usr/save/00050000", listOf("mlc01/usr/save/00050000")))
        assertEquals("$data/org.azahar_emu.azahar/files", SigilSaveHandler.rootFor("$data/org.azahar_emu.azahar/files/sdmc/Nintendo 3DS", listOf("sdmc/Nintendo 3DS/0/0/title")))
    }

    @Test
    fun `a path with none of the layout's folders has no root`() {
        assertNull(SigilSaveHandler.rootFor("/storage/emulated/0/Saves/PS2", listOf("memcards")))
    }

    @Test
    fun `an override on the Dolphin User folder roots at that folder`() {
        val user = "/storage/emulated/0/RetroArch/saves/dolphin-emu/User"
        val dirs = setOf("$user/Wii")

        assertNull(SigilSaveHandler.rootFor(user, "dolphin_standalone"))
        assertEquals(user, SigilSaveHandler.rootAbove("$user/", "dolphin_standalone", dirs::contains))
    }

    @Test
    fun `an override on the Wii folder roots at the folder above it`() {
        val user = "/storage/emulated/0/RetroArch/saves/dolphin-emu/User"

        assertEquals(user, SigilSaveHandler.rootFor("$user/Wii", "dolphin_standalone"))
    }

    @Test
    fun `an override with no anchor folder inside it has no root`() {
        val path = "/storage/emulated/0/Saves/Dolphin"

        assertNull(SigilSaveHandler.rootAbove(path, "dolphin_standalone") { false })
        assertNull(SigilSaveHandler.rootAbove(path, "unknown_layout") { true })
    }

    @Test
    fun `a folder name that only starts like the anchor does not match`() {
        assertNull(SigilSaveHandler.rootFor("/storage/emulated/0/memcards_old/x", listOf("memcards")))
    }

    @Test
    fun `a root, its anchor folder and a card in it are protected from deletion`() {
        assertTrue(SigilSaveHandler.isProtectedSavePath("$data/com.nethersx2/files/memcards"))
        assertTrue(SigilSaveHandler.isProtectedSavePath("$data/com.nethersx2/files/memcards/Mcd001.ps2"))
        assertTrue(SigilSaveHandler.isProtectedSavePath("$data/org.dolphinemu.dolphinemu/files/GC/USA"))
        assertTrue(SigilSaveHandler.isProtectedSavePath("$data/org.ppsspp.ppsspp/files/PSP/SAVEDATA"))
    }

    @Test
    fun `one game's folder or file deeper down is not protected`() {
        assertFalse(SigilSaveHandler.isProtectedSavePath("$data/com.nethersx2/files/memcards/Mcd001.ps2/BASLUS-20312"))
        assertFalse(SigilSaveHandler.isProtectedSavePath("$data/org.dolphinemu.dolphinemu/files/GC/USA/Card A/01-GZLE-zelda.gci"))
        assertFalse(SigilSaveHandler.isProtectedSavePath("$data/org.ppsspp.ppsspp/files/PSP/SAVEDATA/ULUS10041DATA"))
        assertFalse(SigilSaveHandler.isProtectedSavePath("/storage/emulated/0/RetroArch/saves/Game.srm"))
    }

    @Test
    fun `layouts follow the emulator and platform`() {
        assertEquals("dolphin_standalone", SigilSaveHandler.layoutFor("dolphin", "ngc"))
        assertNull(SigilSaveHandler.layoutFor("dolphin", "wii"))
        assertEquals("pcsx2_standalone", SigilSaveHandler.layoutFor("armsx2", "ps2"))
        assertEquals("azahar", SigilSaveHandler.layoutFor("azahar", "n3ds"))
        assertEquals("flycast_standalone", SigilSaveHandler.layoutFor("flycast", "dc"))
        assertEquals("citra", SigilSaveHandler.layoutFor("citra_mmj", "3ds"))
        assertEquals("citra", SigilSaveHandler.layoutFor("borked3ds", "3ds"))
        assertNull(SigilSaveHandler.layoutFor("retroarch_64", "ps2"))
        assertNull(SigilSaveHandler.layoutFor("argosy", "psx"))
    }

    @Test
    fun `the built-in ARMSX2 core saves through the libretro pcsx2 layout`() {
        assertEquals("pcsx2", SigilSaveHandler.libretroLayoutFor("ps2", "armsx2", retroArch = false))
        assertEquals("pcsx2", SigilSaveHandler.libretroLayoutFor("playstation2", "armsx2", retroArch = false))
        assertNull(SigilSaveHandler.libretroLayoutFor("ps2", "armsx2", retroArch = true))
        assertNull(SigilSaveHandler.genericLibretroLayoutFor("ps2", "armsx2"))
        assertEquals("pcsx2_standalone", SigilSaveHandler.layoutFor("armsx2", "ps2"))
    }

    @Test
    fun `the built-in PS2 cards root in pcsx2 memcards under the libretro system directory`() {
        val system = java.io.File("/data/user/0/com.nendo.argosy/files/libretro/system")

        assertEquals("${system.absolutePath}/pcsx2/memcards", SigilSaveHandler.pcsx2MemcardRoot(system))
        assertTrue(SigilSaveHandler.isProtectedSavePath("${system.absolutePath}/pcsx2/memcards/Mcd001.ps2"))
    }
}
