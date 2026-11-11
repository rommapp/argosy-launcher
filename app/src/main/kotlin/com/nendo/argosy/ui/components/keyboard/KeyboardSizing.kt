package com.nendo.argosy.ui.components.keyboard

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import kotlin.math.floor
import kotlin.math.min

enum class KeyboardPlacement { DOCKED, FULL_SCREEN }

data class KeyboardSizing(
    val placement: KeyboardPlacement,
    val compact: Boolean,
    val keyHeightDp: Float,
    val echoHeightDp: Float,
    val sheetHeightDp: Float,
    val sheetWidthDp: Float,
    val paddingDp: Float,
    val gapDp: Float
)

/**
 * Sheet geometry for a window, in unscaled dp. ControllerKeyboardSheet passes the window's dp
 * divided by LocalUiScale and multiplies every result by the same scale.
 */
fun keyboardSizing(windowWidthDp: Float, windowHeightDp: Float, forceFull: Boolean): KeyboardSizing {
    val tokens = ComponentDefaults.ControllerKeyboard
    val rows = tokens.sizingRowCount
    val gap = DimensionTokens.Spacing.Xs.base.toFloat()
    val minKey = tokens.keyHeightMinDp.toFloat()
    val rowHeight = DimensionTokens.Layout.menuRowHeight.toFloat()
    val rowHeightLg = DimensionTokens.Layout.menuRowHeightLg.toFloat()
    val compact = windowHeightDp <= tokens.compactMaxWindowHeightDp
    if (!forceFull) {
        val echo = if (compact) rowHeight else rowHeightLg
        val padding = (if (compact) DimensionTokens.Spacing.Xs.base else DimensionTokens.Spacing.Sm.base).toFloat()
        val chrome = echo + padding * 3 + gap * (rows - 1)
        val target = windowHeightDp * tokens.sheetTargetHeightPercent / 100f
        val key = floor((target - chrome) / rows).coerceIn(minKey, rowHeight)
        val sheet = chrome + rows * key
        if (sheet <= windowHeightDp * tokens.sheetMaxHeightPercent / 100f) {
            return KeyboardSizing(
                placement = KeyboardPlacement.DOCKED,
                compact = compact,
                keyHeightDp = key,
                echoHeightDp = echo,
                sheetHeightDp = sheet,
                sheetWidthDp = windowWidthDp,
                paddingDp = padding,
                gapDp = gap
            )
        }
    }
    val padding = DimensionTokens.Spacing.Sm.base.toFloat()
    val fixed = padding * 3 + gap * (rows - 1)
    val key = floor((windowHeightDp - rowHeightLg - fixed) / rows).coerceIn(minKey, rowHeightLg)
    val echoMax = rowHeightLg + (tokens.echoFullMaxLines - 1) * tokens.echoFullExtraLineDp
    val echo = min(windowHeightDp - fixed - rows * key, echoMax).coerceAtLeast(0f)
    return KeyboardSizing(
        placement = KeyboardPlacement.FULL_SCREEN,
        compact = false,
        keyHeightDp = key,
        echoHeightDp = echo,
        sheetHeightDp = windowHeightDp,
        sheetWidthDp = windowWidthDp,
        paddingDp = padding,
        gapDp = gap
    )
}
