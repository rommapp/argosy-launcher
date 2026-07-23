package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleRaster
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class QuayPassDoodleCodecTest {

    @Test
    fun `round trips a 16x16 raster pixel-exact`() {
        val indices = IntArray(256) { it % 16 }
        val bytes = QuayPassDoodleCodec.encode(QuayPassDoodleRaster(16, indices))
        assertEquals(130, bytes.size)
        val decoded = QuayPassDoodleCodec.decode(bytes)!!
        assertEquals(16, decoded.size)
        assertArrayEquals(indices, decoded.paletteIndices)
    }

    @Test
    fun `round trips a 32x32 raster pixel-exact`() {
        val indices = IntArray(1024) { (it * 7) % 16 }
        val bytes = QuayPassDoodleCodec.encode(QuayPassDoodleRaster(32, indices))
        assertEquals(514, bytes.size)
        val decoded = QuayPassDoodleCodec.decode(bytes)!!
        assertEquals(32, decoded.size)
        assertArrayEquals(indices, decoded.paletteIndices)
    }

    @Test
    fun `rejects malformed raster bytes`() {
        assertNull(QuayPassDoodleCodec.decode(ByteArray(0)))
        assertNull(QuayPassDoodleCodec.decode(byteArrayOf(2, 16)))
        assertNull(QuayPassDoodleCodec.decode(byteArrayOf(1, 24)))
        assertNull(QuayPassDoodleCodec.decode(ByteArray(129) { if (it == 0) 1 else if (it == 1) 16 else 0 }))
    }

    @Test
    fun `exact palette colors map to their own index`() {
        assertEquals(0, QuayPassDoodleCodec.nearestPaletteIndex(0xFFFFFF))
        assertEquals(1, QuayPassDoodleCodec.nearestPaletteIndex(0x000000))
        assertEquals(4, QuayPassDoodleCodec.nearestPaletteIndex(0xFF8800))
        assertEquals(15, QuayPassDoodleCodec.nearestPaletteIndex(0xFFCC88))
    }

    @Test
    fun `unknown colors map to the nearest palette color`() {
        assertEquals(0, QuayPassDoodleCodec.nearestPaletteIndex(0xFEFEFE))
        assertEquals(1, QuayPassDoodleCodec.nearestPaletteIndex(0x050505))
        assertEquals(3, QuayPassDoodleCodec.nearestPaletteIndex(0xF01010))
    }

    @Test
    fun `sparse doodle converts to a raster with nearest-color mapping`() {
        val sparse = sparseDoodle(
            sizeEnum = 0,
            paletteRgb = listOf(0xF01010),
            pixels = listOf(Triple(2, 3, 0), Triple(15, 15, 0))
        )
        val raster = QuayPassDoodleCodec.fromSparseDoodle(sparse)!!
        assertEquals(16, raster.size)
        assertEquals(3, raster.paletteIndices[3 * 16 + 2])
        assertEquals(3, raster.paletteIndices[15 * 16 + 15])
        assertEquals(0, raster.paletteIndices[0])
    }

    @Test
    fun `sparse doodle with unsupported canvas is rejected`() {
        assertNull(QuayPassDoodleCodec.fromSparseDoodle(sparseDoodle(sizeEnum = 2, paletteRgb = emptyList(), pixels = emptyList())))
    }

    private fun sparseDoodle(
        sizeEnum: Int,
        paletteRgb: List<Int>,
        pixels: List<Triple<Int, Int, Int>>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(1)
        out.write(sizeEnum)
        out.write(paletteRgb.size)
        paletteRgb.forEach { rgb ->
            out.write((rgb shr 16) and 0xFF)
            out.write((rgb shr 8) and 0xFF)
            out.write(rgb and 0xFF)
        }
        out.write((pixels.size shr 8) and 0xFF)
        out.write(pixels.size and 0xFF)
        pixels.forEach { (x, y, colorIndex) ->
            out.write(x)
            out.write(y)
            out.write(colorIndex)
        }
        return out.toByteArray()
    }
}
