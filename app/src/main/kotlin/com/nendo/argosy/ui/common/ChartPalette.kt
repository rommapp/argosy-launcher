package com.nendo.argosy.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * Chart colour rules. Categorical hues are the eight fixed slots in token order, assigned by
 * entity and never cycled; a magnitude reads on one hue, the theme accent, from the surface
 * track up to full strength.
 */
object ChartPalette {
    fun series(isDark: Boolean): List<Color> = listOf(
        pick(isDark, ColorTokens.Domain.Chart.Series1.dark, ColorTokens.Domain.Chart.Series1.light),
        pick(isDark, ColorTokens.Domain.Chart.Series2.dark, ColorTokens.Domain.Chart.Series2.light),
        pick(isDark, ColorTokens.Domain.Chart.Series3.dark, ColorTokens.Domain.Chart.Series3.light),
        pick(isDark, ColorTokens.Domain.Chart.Series4.dark, ColorTokens.Domain.Chart.Series4.light),
        pick(isDark, ColorTokens.Domain.Chart.Series5.dark, ColorTokens.Domain.Chart.Series5.light),
        pick(isDark, ColorTokens.Domain.Chart.Series6.dark, ColorTokens.Domain.Chart.Series6.light),
        pick(isDark, ColorTokens.Domain.Chart.Series7.dark, ColorTokens.Domain.Chart.Series7.light),
        pick(isDark, ColorTokens.Domain.Chart.Series8.dark, ColorTokens.Domain.Chart.Series8.light)
    )

    fun slotColor(series: List<Color>, slot: Int?, others: Color): Color =
        slot?.let { series.getOrNull(it) } ?: others

    fun sequential(track: Color, accent: Color, level: Int, levels: Int, floor: Float = 0f): Color {
        if (levels <= 0 || level <= 0) return track
        val step = level.coerceAtMost(levels).toFloat() / levels
        return lerp(track, accent, floor + (1f - floor) * step)
    }

    fun levelOf(value: Long, max: Long, levels: Int): Int {
        if (value <= 0L || max <= 0L || levels <= 0) return 0
        val level = ((value.toDouble() / max) * levels).toInt() + 1
        return level.coerceIn(1, levels)
    }

    private fun pick(isDark: Boolean, dark: Color, light: Color): Color = if (isDark) dark else light
}
