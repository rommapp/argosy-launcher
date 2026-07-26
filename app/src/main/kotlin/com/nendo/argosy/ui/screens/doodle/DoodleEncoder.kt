package com.nendo.argosy.ui.screens.doodle

import android.util.Base64
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import java.io.ByteArrayOutputStream

object DoodleEncoder {
    private const val VERSION: Byte = 1

    fun encode(pixels: Map<Pair<Int, Int>, DoodleColor>, size: CanvasSize): ByteArray {
        val output = ByteArrayOutputStream()

        val usedColors = pixels.values
            .filter { it != DoodleColor.WHITE }
            .distinct()
            .sortedBy { it.index }

        val colorToLocalIndex = usedColors.withIndex().associate { (idx, color) -> color to idx }

        output.write(VERSION.toInt())
        output.write(size.sizeEnum)
        output.write(usedColors.size)

        usedColors.forEach { color ->
            val rgb = (color.hex and 0xFFFFFF).toInt()
            output.write((rgb shr 16) and 0xFF)
            output.write((rgb shr 8) and 0xFF)
            output.write(rgb and 0xFF)
        }

        val nonWhitePixels = pixels.filter { it.value != DoodleColor.WHITE }
        val pixelCount = nonWhitePixels.size
        output.write((pixelCount shr 8) and 0xFF)
        output.write(pixelCount and 0xFF)

        nonWhitePixels.forEach { (coords, color) ->
            val (x, y) = coords
            val localIndex = colorToLocalIndex[color] ?: 0
            output.write(x)
            output.write(y)
            output.write(localIndex)
        }

        return output.toByteArray()
    }

    fun encodeToBase64(pixels: Map<Pair<Int, Int>, DoodleColor>, size: CanvasSize): String {
        val bytes = encode(pixels, size)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decode(data: ByteArray): DecodedDoodle {
        val raster = QuayPassDoodleCodec.decodeSparse(data)
            ?: throw IllegalArgumentException("Unsupported or malformed doodle")
        val size = CanvasSize.entries.find { it.pixels == raster.size }
            ?: throw IllegalArgumentException("Unsupported doodle size: ${raster.size}")
        val pixels = mutableMapOf<Pair<Int, Int>, DoodleColor>()
        for (y in 0 until raster.size) {
            for (x in 0 until raster.size) {
                val index = raster.paletteIndices[y * raster.size + x]
                if (index != 0) {
                    pixels[x to y] = DoodleColor.fromIndex(index)
                }
            }
        }
        return DecodedDoodle(size, pixels)
    }

    fun decodeFromBase64(base64: String): DecodedDoodle {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return decode(bytes)
    }
}
