package com.nendo.argosy.data.quaypass.ble

import java.util.Base64

class QuayPassDoodleRaster(val size: Int, val paletteIndices: IntArray)

/**
 * Fixed-palette 4-bpp raster codec for doodle avatars on the QuayPass wire:
 * [version u8 = 1][size u8 = 16 or 32][size*size/2 bytes, one nibble per
 * pixel, row-major, high nibble first]. Nibbles index [PALETTE_RGB], which
 * mirrors the fixed doodle palette (ui DoodleColor order); index 0 is blank.
 */
object QuayPassDoodleCodec {

    const val VERSION: Int = 1
    const val SIZE_SMALL: Int = 16
    const val SIZE_MEDIUM: Int = 32

    val PALETTE_RGB: IntArray = intArrayOf(
        0xFFFFFF, 0x000000, 0x808080, 0xFF0000,
        0xFF8800, 0xFFFF00, 0x00FF00, 0x00FFFF,
        0x0088FF, 0x0000FF, 0x8800FF, 0xFF00FF,
        0xFF88AA, 0x884400, 0x88FF88, 0xFFCC88
    )

    fun encode(raster: QuayPassDoodleRaster): ByteArray {
        require(raster.size == SIZE_SMALL || raster.size == SIZE_MEDIUM) {
            "Unsupported raster size: ${raster.size}"
        }
        require(raster.paletteIndices.size == raster.size * raster.size) {
            "Expected ${raster.size * raster.size} pixels, got ${raster.paletteIndices.size}"
        }
        val out = ByteArray(2 + raster.paletteIndices.size / 2)
        out[0] = VERSION.toByte()
        out[1] = raster.size.toByte()
        for (i in raster.paletteIndices.indices step 2) {
            val high = raster.paletteIndices[i] and 0x0F
            val low = raster.paletteIndices[i + 1] and 0x0F
            out[2 + i / 2] = ((high shl 4) or low).toByte()
        }
        return out
    }

    fun decode(bytes: ByteArray): QuayPassDoodleRaster? {
        if (bytes.size < 2) return null
        if (bytes[0].toInt() and 0xFF != VERSION) return null
        val size = bytes[1].toInt() and 0xFF
        if (size != SIZE_SMALL && size != SIZE_MEDIUM) return null
        if (bytes.size != 2 + size * size / 2) return null
        val indices = IntArray(size * size)
        for (i in indices.indices step 2) {
            val packed = bytes[2 + i / 2].toInt() and 0xFF
            indices[i] = packed ushr 4
            indices[i + 1] = packed and 0x0F
        }
        return QuayPassDoodleRaster(size, indices)
    }

    fun nearestPaletteIndex(rgb: Int): Int {
        val masked = rgb and 0xFFFFFF
        val exact = PALETTE_RGB.indexOf(masked)
        if (exact >= 0) return exact
        val r = (masked shr 16) and 0xFF
        val g = (masked shr 8) and 0xFF
        val b = masked and 0xFF
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in PALETTE_RGB.indices) {
            val pr = (PALETTE_RGB[i] shr 16) and 0xFF
            val pg = (PALETTE_RGB[i] shr 8) and 0xFF
            val pb = PALETTE_RGB[i] and 0xFF
            val distance = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /**
     * Converts the sparse doodle format (DoodleEncoder v1: version u8, sizeEnum
     * u8, paletteCount u8, RGB triples, pixelCount u16, x/y/localIndex triples)
     * into a full palette-index grid. Returns null for malformed input or the
     * unsupported 64px canvas.
     */
    fun fromSparseDoodle(bytes: ByteArray): QuayPassDoodleRaster? {
        if (bytes.size < 5) return null
        if (bytes[0].toInt() and 0xFF != 1) return null
        val size = when (bytes[1].toInt() and 0xFF) {
            0 -> SIZE_SMALL
            1 -> SIZE_MEDIUM
            else -> return null
        }
        val paletteCount = bytes[2].toInt() and 0xFF
        var pos = 3
        if (bytes.size < pos + paletteCount * 3 + 2) return null
        val localIndices = IntArray(paletteCount) {
            val r = bytes[pos + it * 3].toInt() and 0xFF
            val g = bytes[pos + it * 3 + 1].toInt() and 0xFF
            val b = bytes[pos + it * 3 + 2].toInt() and 0xFF
            nearestPaletteIndex((r shl 16) or (g shl 8) or b)
        }
        pos += paletteCount * 3
        val pixelCount = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
        pos += 2
        if (bytes.size < pos + pixelCount * 3) return null
        val grid = IntArray(size * size)
        repeat(pixelCount) {
            val x = bytes[pos].toInt() and 0xFF
            val y = bytes[pos + 1].toInt() and 0xFF
            val localIndex = bytes[pos + 2].toInt() and 0xFF
            pos += 3
            if (x < size && y < size && localIndex < paletteCount) {
                grid[y * size + x] = localIndices[localIndex]
            }
        }
        return QuayPassDoodleRaster(size, grid)
    }

    fun encodeFromSparseBase64(doodleBase64: String): ByteArray? = runCatching {
        fromSparseDoodle(Base64.getDecoder().decode(doodleBase64))?.let { encode(it) }
    }.getOrNull()
}
