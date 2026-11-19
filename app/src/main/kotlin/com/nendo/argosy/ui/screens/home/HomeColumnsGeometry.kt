package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Horizontal geometry of the Columns strip in one unit, dp or px. A column [offset] slots from the
 * centre is [widthAt] wide and starts at [startOf]; the centre slot is [focusScale] times as wide as
 * the others, and a column grows smoothly as its offset approaches zero. [widthProgressAt] is that
 * growth from 0 at side width to 1 at focused width.
 */
data class HomeColumnsGeometry(
    val columnWidth: Float,
    val gap: Float,
    val centerX: Float,
    val focusScale: Float
) {
    fun widthAt(offset: Float): Float =
        columnWidth * (1f + (focusScale - 1f) * widthProgressAt(offset))

    fun widthProgressAt(offset: Float): Float = 1f - min(abs(offset), 1f)

    fun startOf(offset: Float): Float = centerOf(offset) - widthAt(offset) / 2f

    private fun centerOf(offset: Float): Float {
        val distance = abs(offset)
        val firstStep = columnWidth * focusScale / 2f + gap + columnWidth / 2f
        val travel = if (distance <= 1f) distance * firstStep else firstStep + (distance - 1f) * (columnWidth + gap)
        return centerX + sign(offset) * travel
    }
}

fun homeColumnsSlotCount(screenWidth: Float, screenHeight: Float): Int {
    val aspect = screenWidth / screenHeight.coerceAtLeast(1f)
    return when {
        aspect <= 1f || aspect >= ComponentDefaults.HomeColumns.phoneAspectMin ->
            ComponentDefaults.HomeColumns.slotsPhone
        aspect < ComponentDefaults.HomeColumns.wideAspectMin -> ComponentDefaults.HomeColumns.slotsCompact
        else -> ComponentDefaults.HomeColumns.slotsWide
    }
}

fun homeColumnsGeometry(width: Float, slots: Int, gap: Float): HomeColumnsGeometry {
    val sliver = width * ComponentDefaults.HomeColumns.sliverWidthRatio
    val focusScale = ComponentDefaults.HomeColumns.focusedWidthScale
    val units = (slots.coerceAtLeast(1) - 1) + focusScale
    val columnWidth = ((width - sliver * 2 - gap * (slots + 1)) / units).coerceAtLeast(0f)
    return HomeColumnsGeometry(columnWidth = columnWidth, gap = gap, centerX = width / 2f, focusScale = focusScale)
}
