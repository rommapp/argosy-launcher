package com.nendo.argosy.libretro.ui.cheats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScannerCheatCodeTest {

    @Test
    fun `a memory search cheat is editable and keeps its address`() {
        val code = ScannerCheatCode.parse("00C3A0:63")
        assertEquals(ScannerCheatCode("00C3A0", 0x63), code)
        assertEquals("00C3A0:FF", code!!.withValue(255))
        assertEquals("00C3A0:05", code.withValue(5))
    }

    @Test
    fun `codes from a database or a server file are rename only`() {
        assertNull(ScannerCheatCode.parse("04000000 60000000\n04000004 60000000"))
        assertNull(ScannerCheatCode.parse("patch=1,EE,00100000,word,1"))
        assertNull(ScannerCheatCode.parse("*note\n04000000 60000000"))
        assertNull(ScannerCheatCode.parse("8009C6E4 0063"))
        assertNull(ScannerCheatCode.parse("00C3A0:6300"))
    }
}
