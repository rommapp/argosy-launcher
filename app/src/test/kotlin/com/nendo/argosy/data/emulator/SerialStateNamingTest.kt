package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerialStateNamingTest {

    private val duck = StateSlotPattern.DuckStationSerial
    private val ppsspp = StateSlotPattern.PpssppSerial
    private val one = StateNameKey("Game (USA)", setOf("SLUS-00001"))

    @Test
    fun `duckstation and ppsspp resolve to their serial patterns`() {
        assertEquals(duck, StatePathRegistry.getConfig("duckstation")?.slotPattern)
        assertEquals(ppsspp, StatePathRegistry.getConfig("ppsspp")?.slotPattern)
        assertEquals(ppsspp, StatePathRegistry.getConfig("ppsspp_gold")?.slotPattern)
        assertEquals(ppsspp, StatePathRegistry.getConfig("ppsspp_legacy")?.slotPattern)
    }

    @Test
    fun `duckstation parses numbered and resume slots for the game's serial only`() {
        assertEquals(4, duck.parseSlotNumber("SLUS-00001_4.sav", one))
        assertEquals(-1, duck.parseSlotNumber("SLUS-00001_resume.sav", one))
        assertNull(duck.parseSlotNumber("SCUS-94163_4.sav", one))
        assertNull(duck.parseSlotNumber("savestate_1.sav", one))
        assertNull(duck.parseSlotNumber("resume.sav", one))
        assertNull(duck.parseSlotNumber("Game (USA)_1.sav", one))
    }

    @Test
    fun `duckstation writes a cached name of its own serial under the requested slot`() {
        assertEquals("SLUS-00001_2.sav", duck.targetFileName(one, 2, "SLUS-00001_7.sav"))
        assertEquals("SLUS-00001_resume.sav", duck.targetFileName(one, -1, "SLUS-00001_resume.sav"))
    }

    @Test
    fun `duckstation refuses a cached name carrying another game's serial`() {
        assertNull(duck.targetFileName(one, 1, "SCUS-94163_1.sav"))
        assertNull(duck.restoreFileName(one, 1, "SCUS-94163_1.sav"))
        assertNull(duck.targetFileName(one, 1, "savestate_1.sav"))
    }

    @Test
    fun `duckstation builds a server copy's name from the one serial`() {
        assertEquals("SLUS-00001_3.sav", duck.targetFileName(one, 3, "Game (USA) [2026-01-01_10-00-00].state3"))
        assertEquals("SLUS-00001_resume.sav", duck.targetFileName(one, -1, null))
    }

    @Test
    fun `duckstation will not guess which disc a serial-less copy belongs to`() {
        val twoDiscs = StateNameKey("Game", setOf("SLUS-00892", "SLUS-00908"))

        assertNull(duck.targetFileName(twoDiscs, 1, "Game [2026-01-01_10-00-00].state1"))
        assertEquals("SLUS-00908_1.sav", duck.targetFileName(twoDiscs, 1, "SLUS-00908_1.sav"))
    }

    @Test
    fun `no serial means no name at all`() {
        val none = StateNameKey("Game", emptySet())

        assertNull(duck.parseSlotNumber("SLUS-00001_1.sav", none))
        assertNull(duck.targetFileName(none, 1, null))
        assertNull(ppsspp.parseSlotNumber("ULUS10064_1.00_1.ppst", none))
        assertEquals(emptyList<String>(), duck.resumeFileNames(none))
    }

    @Test
    fun `duckstation resume names come from every serial of the game`() {
        val twoDiscs = StateNameKey("Game", setOf("SLUS-00892", "SLUS-00908"))

        assertEquals(
            listOf("SLUS-00892_resume.sav", "SLUS-00908_resume.sav"),
            duck.resumeFileNames(twoDiscs).sorted()
        )
    }

    @Test
    fun `ppsspp parses its disc id and keeps two discs on slot 3 apart`() {
        val ulus = StateNameKey("Game", setOf("ULUS10064"))

        assertEquals(3, ppsspp.parseSlotNumber("ULUS10064_1.00_3.ppst", ulus))
        assertNull(ppsspp.parseSlotNumber("NPJH50804_1.01_3.ppst", ulus))
        assertNull(ppsspp.parseSlotNumber("ULUS10064_1.00_3.undo.ppst", ulus))
        assertNull(ppsspp.parseSlotNumber("ULUS10064_1.00_3.jpg", ulus))
        assertNull(ppsspp.parseSlotNumber("ULUS10064_1.00_3.name.txt", ulus))
        assertNull(ppsspp.parseSlotNumber("load_undo.ppst", ulus))
    }

    @Test
    fun `ppsspp keeps the disc version of the cached name and never invents one`() {
        val ulus = StateNameKey("Game", setOf("ULUS10064"))

        assertEquals("ULUS10064_1.00_4.ppst", ppsspp.targetFileName(ulus, 4, "ULUS10064_1.00_0.ppst"))
        assertNull(ppsspp.targetFileName(ulus, 4, "Game [2026-01-01_10-00-00].state4"))
        assertNull(ppsspp.targetFileName(ulus, 4, "NPJH50804_1.01_4.ppst"))
        assertNull(ppsspp.targetFileName(ulus, -1, "ULUS10064_1.00_0.ppst"))
        assertEquals(emptyList<String>(), ppsspp.resumeFileNames(ulus))
    }

    @Test
    fun `ppsspp pairs a state with the jpg of the same stem`() {
        val state = java.io.File("/states/ULUS10064_1.00_2.ppst")

        assertEquals("/states/ULUS10064_1.00_2.jpg", ppsspp.screenshotFile(state).path)
        assertEquals("/states/ULUS10064_1.00_2.jpg", StatePathRegistry.screenshotFileFor("ppsspp", state).path)
    }

    @Test
    fun `rom-named emulators keep the png sidecar and their cached names`() {
        val state = java.io.File("/states/Game.state1")
        val key = StateNameKey("Game", emptySet())
        val retroarch = StatePathRegistry.getConfig("retroarch")!!.slotPattern

        assertEquals("/states/Game.state1.png", StatePathRegistry.screenshotFileFor("retroarch", state).path)
        assertEquals("Game.state1", retroarch.restoreFileName(key, 1, "Game.state1"))
        assertEquals("Game.state2", retroarch.targetFileName(key, 2, "Game.state1"))
        assertEquals(1, retroarch.parseSlotNumber("Game.state1", key))
    }
}
