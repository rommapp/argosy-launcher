package com.nendo.argosy.ui.util

import com.nendo.argosy.data.preferences.GridDensity

object GridUtils {

    private const val WIDE_SCREEN_THRESHOLD_DP = 900
    private const val WIDE_SCREEN_MULTIPLIER = 1.5f

    fun getGameGridColumns(density: GridDensity, screenWidthDp: Int): Int {
        val baseColumns = when (density) {
            GridDensity.COMPACT -> 8
            GridDensity.NORMAL -> 6
            GridDensity.SPACIOUS -> 5
        }
        return applyWideScreenMultiplier(baseColumns, screenWidthDp)
    }

    fun getAppGridColumns(density: GridDensity, screenWidthDp: Int): Int {
        val baseColumns = when (density) {
            GridDensity.COMPACT -> 5
            GridDensity.NORMAL -> 4
            GridDensity.SPACIOUS -> 3
        }
        return applyWideScreenMultiplier(baseColumns, screenWidthDp)
    }

    fun getGridSpacingDp(density: GridDensity): Int = when (density) {
        GridDensity.COMPACT -> 4
        GridDensity.NORMAL -> 6
        GridDensity.SPACIOUS -> 8
    }

    private fun applyWideScreenMultiplier(baseColumns: Int, screenWidthDp: Int): Int {
        return if (screenWidthDp > WIDE_SCREEN_THRESHOLD_DP) {
            (baseColumns * WIDE_SCREEN_MULTIPLIER).toInt()
        } else {
            baseColumns
        }
    }
}
