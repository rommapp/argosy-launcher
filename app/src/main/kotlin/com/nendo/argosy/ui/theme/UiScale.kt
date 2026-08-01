package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

data class UiScaleConfig(
    val scale: Float = 1.0f,
    val aspectRatioClass: AspectRatioClass = AspectRatioClass.STANDARD,
    val bottomReservedFraction: Float = 0f
)

enum class AspectRatioClass {
    ULTRA_WIDE,   // >= 2.0 (21:9)
    WIDE,         // 1.6-2.0 (16:9, 16:10)
    STANDARD,     // 0.5-1.6
    TALL,         // 0.35-0.5 (9:16)
    ULTRA_TALL    // < 0.35 (9:21)
}

val LocalUiScale = staticCompositionLocalOf { UiScaleConfig() }

@Composable
fun Dp.scaled(): Dp = this * LocalUiScale.current.scale
