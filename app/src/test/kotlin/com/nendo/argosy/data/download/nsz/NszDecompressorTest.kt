package com.nendo.argosy.data.download.nsz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NszDecompressorTest {

    @Test
    fun `isCompressedNsw detects nsz extension`() {
        assertTrue(NszDecompressor.isCompressedNsw(File("game.nsz")))
        assertTrue(NszDecompressor.isCompressedNsw(File("game.NSZ")))
        assertTrue(NszDecompressor.isCompressedNsw(File("path/to/game.nsz")))
    }

    @Test
    fun `isCompressedNsw detects xcz extension`() {
        assertTrue(NszDecompressor.isCompressedNsw(File("game.xcz")))
        assertTrue(NszDecompressor.isCompressedNsw(File("game.XCZ")))
    }

    @Test
    fun `isCompressedNsw rejects non-compressed extensions`() {
        assertFalse(NszDecompressor.isCompressedNsw(File("game.nsp")))
        assertFalse(NszDecompressor.isCompressedNsw(File("game.xci")))
        assertFalse(NszDecompressor.isCompressedNsw(File("game.nca")))
        assertFalse(NszDecompressor.isCompressedNsw(File("game.zip")))
        assertFalse(NszDecompressor.isCompressedNsw(File("game.7z")))
    }
}
