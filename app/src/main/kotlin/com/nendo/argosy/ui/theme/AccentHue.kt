package com.nendo.argosy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The user's custom accent as a position on the colour wheel. Every custom accent shares one
 * saturation and lightness, so a hue alone names it.
 */
object AccentHue {
    const val STEP = 10f
    private const val DEFAULT_HUE = 180f
    private const val SATURATION = 0.7f
    private const val LIGHTNESS = 0.5f

    fun hueOf(color: Int?): Float {
        if (color == null) return DEFAULT_HUE
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        if (delta == 0f) return 0f
        val sector = when (max) {
            r -> ((g - b) / delta).mod(6f)
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        }
        return (sector * 60f).mod(360f)
    }

    fun colorAt(hue: Float): Int = Color.hsl(hue.mod(360f), SATURATION, LIGHTNESS).toArgb()

    fun shifted(color: Int?, delta: Float): Int = colorAt(hueOf(color) + delta)

    val SWATCHES: List<Int?> = listOf(
        null,
        0xFF9575CD.toInt(),
        0xFF4DB6AC.toInt(),
        0xFFFFB74D.toInt(),
        0xFF81C784.toInt(),
        0xFFF06292.toInt(),
        0xFF64B5F6.toInt()
    )
}
