package com.nendo.argosy.libretro

/**
 * Vertical LinearLayout weights for the portrait game column, as
 * (top spacer, game, bottom spacer).
 *
 * [reserved] is the fraction of the column a controller grip covers along the
 * bottom; it is taken out first and [position] then places the game within what
 * is left. A [reserved] of 0 reproduces the pre-reserve weights exactly.
 */
fun portraitSplitWeights(position: String, reserved: Float): Triple<Float, Float, Float> {
    val band = reserved.coerceIn(0f, MAX_RESERVED_FRACTION)
    val free = 1f - band
    return when (position) {
        "Top" -> Triple(0f, free / 2f, free / 2f + band)
        "Bottom" -> Triple(free / 2f, free / 2f, band)
        else -> Triple(0f, free, band)
    }
}

private const val MAX_RESERVED_FRACTION = 0.9f
