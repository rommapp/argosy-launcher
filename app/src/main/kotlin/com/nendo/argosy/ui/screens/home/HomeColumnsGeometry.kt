package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeColumnCount
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

/**
 * The columns the strip lays across a [screenWidth] by [screenHeight] screen. [HomeColumnCount.AUTO]
 * picks by aspect ratio; any other [count] fixes it.
 */
fun homeColumnsSlotCount(
    screenWidth: Float,
    screenHeight: Float,
    count: HomeColumnCount = HomeColumnCount.AUTO
): Int = count.fixedSlots ?: autoSlotCount(screenWidth / screenHeight.coerceAtLeast(1f))

/**
 * The column count this choice fixes, or null for [HomeColumnCount.AUTO].
 */
val HomeColumnCount.fixedSlots: Int?
    get() = when (this) {
        HomeColumnCount.AUTO -> null
        HomeColumnCount.COMPACT -> ComponentDefaults.HomeColumns.slotsCompact
        HomeColumnCount.WIDE -> ComponentDefaults.HomeColumns.slotsWide
        HomeColumnCount.ULTRA_WIDE -> ComponentDefaults.HomeColumns.slotsUltraWide
    }

private fun autoSlotCount(aspect: Float): Int = when {
    aspect <= 1f -> ComponentDefaults.HomeColumns.slotsPhone
    aspect < ComponentDefaults.HomeColumns.wideAspectMin -> ComponentDefaults.HomeColumns.slotsCompact
    aspect < ComponentDefaults.HomeColumns.ultraWideAspectMin -> ComponentDefaults.HomeColumns.slotsWide
    else -> ComponentDefaults.HomeColumns.slotsUltraWide
}

fun homeColumnsGeometry(width: Float, slots: Int, gap: Float): HomeColumnsGeometry {
    val sliver = width * ComponentDefaults.HomeColumns.sliverWidthRatio
    val focusScale = ComponentDefaults.HomeColumns.focusedWidthScale
    val units = (slots.coerceAtLeast(1) - 1) + focusScale
    val columnWidth = ((width - sliver * 2 - gap * (slots + 1)) / units).coerceAtLeast(0f)
    return HomeColumnsGeometry(columnWidth = columnWidth, gap = gap, centerX = width / 2f, focusScale = focusScale)
}
