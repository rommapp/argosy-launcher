package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroStateFormatTest {

    private val fceumm = bytes("FCSX")
    private val nestopia = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 0x1A)
    private val rzip = bytes("#RZIPv")

    @Test
    fun `a core reads its own state`() {
        assertTrue(LibretroStateFormat.readableBy("fceumm", fceumm))
        assertTrue(LibretroStateFormat.readableBy("nestopia", nestopia))
    }

    @Test
    fun `a known core refuses the other known core's state`() {
        assertFalse(LibretroStateFormat.readableBy("fceumm", nestopia))
        assertFalse(LibretroStateFormat.readableBy("nestopia", fceumm))
    }

    @Test
    fun `an empty state is never refused`() {
        assertTrue(LibretroStateFormat.readableBy("fceumm", ByteArray(0)))
    }

    @Test
    fun `a core with no known header reads anything`() {
        assertTrue(LibretroStateFormat.readableBy("snes9x", fceumm))
        assertTrue(LibretroStateFormat.readableBy(null, nestopia))
    }

    @Test
    fun `a RetroArch compressed state is not judged as another core's`() {
        assertTrue(LibretroStateFormat.readableBy("fceumm", rzip))
        assertTrue(LibretroStateFormat.readableBy("nestopia", rzip))
        assertNull(LibretroStateFormat.writerOf(rzip))
    }

    @Test
    fun `the writer is the core whose header the state carries`() {
        assertEquals("fceumm", LibretroStateFormat.writerOf(fceumm))
        assertEquals("nestopia", LibretroStateFormat.writerOf(nestopia))
        assertNull(LibretroStateFormat.writerOf(ByteArray(0)))
    }

    private fun bytes(text: String) = text.toByteArray(Charsets.US_ASCII)
}
