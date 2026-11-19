package com.nendo.argosy.data.model

import kotlin.math.roundToInt

/**
 * Where wide hero art is framed inside a portrait column. Every bias is in -1..1 with
 * BiasAlignment semantics for the visible window inside the image: [narrowX] for a side column,
 * [wideX] for the focused column, and [y] for the focused column's window, which is only shorter
 * than the image when [zoom] is above 1. [zoom] is the focused column's scale; a side column
 * draws at 1.
 */
data class HeroFocus(
    val narrowX: Float,
    val wideX: Float,
    val y: Float,
    val zoom: Float
) {
    companion object {
        val CENTERED = HeroFocus(narrowX = 0f, wideX = 0f, y = 0f, zoom = 1f)
    }
}

/**
 * The column shapes a [HeroFocus] is computed for: width over height of a side column and of the
 * focused column, the largest zoom allowed, and the long side in pixels of the analysed image.
 * [key] rounds the aspects to hundredths, so one layout always yields one key.
 */
data class HeroFocusSpec(
    val narrowAspect: Float,
    val wideAspect: Float,
    val maxZoom: Float,
    val analysisSize: Int
) {
    val key: String =
        "r$ANALYZER_REVISION:${hundredths(narrowAspect)}:${hundredths(wideAspect)}:${hundredths(maxZoom)}:$analysisSize"

    private fun hundredths(value: Float): Int = (value * KEY_PRECISION).roundToInt()

    private companion object {
        const val KEY_PRECISION = 100f
        const val ANALYZER_REVISION = 2
    }
}
