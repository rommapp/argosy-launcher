package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DuckStationStateHeaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun state(name: String, serial: String, magic: Int = 0x43435544): File {
        val buffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(magic)
        buffer.putInt(97)
        buffer.put(ByteArray(128))
        buffer.put(serial.toByteArray(Charsets.US_ASCII))
        return temp.newFile(name).apply { writeBytes(buffer.array()) }
    }

    private val key = StateNameKey("Crash Team Racing (USA)", setOf("SCUS-94426"))

    @Test
    fun `the header serial is read`() {
        assertEquals("SCUS-94426", DuckStationStateHeader.serialOf(state("a.state1", "SCUS-94426")))
    }

    @Test
    fun `a file without the magic has no serial`() {
        assertNull(DuckStationStateHeader.serialOf(state("b.state1", "SCUS-94426", magic = 0)))
    }

    @Test
    fun `a truncated file has no serial`() {
        assertNull(DuckStationStateHeader.serialOf(temp.newFile("c.state1").apply { writeBytes(ByteArray(20)) }))
    }

    @Test
    fun `a server copy is placed under its header serial when it is the game's`() {
        val proven = provenStateKey(StateSlotPattern.DuckStationSerial, key, state("CTR [2026].state1", "scus-94426"))
        assertEquals(setOf("SCUS-94426"), proven?.serials)
    }

    @Test
    fun `a server copy whose header names another game is refused`() {
        assertNull(provenStateKey(StateSlotPattern.DuckStationSerial, key, state("CTR [2026].state1", "SLUS-00067")))
    }

    @Test
    fun `a server copy with an unreadable header is refused`() {
        val copy = temp.newFile("CTR [2026].state2").apply { writeBytes(ByteArray(16)) }
        assertNull(provenStateKey(StateSlotPattern.DuckStationSerial, key, copy))
    }

    @Test
    fun `a multi-disc game places a server copy under the disc its header names`() {
        val multi = StateNameKey("FF7", setOf("SCUS-94163", "SCUS-94164"))
        val proven = provenStateKey(StateSlotPattern.DuckStationSerial, multi, state("FF7 [2026].state1", "SCUS-94164"))
        assertEquals(setOf("SCUS-94164"), proven?.serials)
    }

    @Test
    fun `a cached name that carries a serial is left to the name rules`() {
        val named = temp.newFile("SLUS-00067_1.sav").apply { writeBytes(ByteArray(16)) }
        assertEquals(key, provenStateKey(StateSlotPattern.DuckStationSerial, key, named))
    }
}
