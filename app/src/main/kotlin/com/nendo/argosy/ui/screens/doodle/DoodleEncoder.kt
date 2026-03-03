package com.nendo.argosy.ui.screens.doodle

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

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
        val buffer = ByteBuffer.wrap(data)

        val version = buffer.get().toInt() and 0xFF
        require(version == 1) { "Unsupported doodle version: $version" }

        val sizeEnum = buffer.get().toInt() and 0xFF
        val size = CanvasSize.fromEnum(sizeEnum)

        val paletteCount = buffer.get().toInt() and 0xFF
        val palette = mutableListOf<DoodleColor>()

        repeat(paletteCount) {
            val r = buffer.get().toInt() and 0xFF
            val g = buffer.get().toInt() and 0xFF
            val b = buffer.get().toInt() and 0xFF

            val hex = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            val color = DoodleColor.entries.find { it.hex == hex } ?: DoodleColor.BLACK
            palette.add(color)
        }

        val pixelCountHigh = buffer.get().toInt() and 0xFF
        val pixelCountLow = buffer.get().toInt() and 0xFF
        val pixelCount = (pixelCountHigh shl 8) or pixelCountLow

        val pixels = mutableMapOf<Pair<Int, Int>, DoodleColor>()

        repeat(pixelCount) {
            val x = buffer.get().toInt() and 0xFF
            val y = buffer.get().toInt() and 0xFF
            val colorIndex = buffer.get().toInt() and 0xFF

            if (colorIndex < palette.size) {
                pixels[x to y] = palette[colorIndex]
            }
        }

        return DecodedDoodle(size, pixels)
    }

    fun decodeFromBase64(base64: String): DecodedDoodle {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return decode(bytes)
    }
}
