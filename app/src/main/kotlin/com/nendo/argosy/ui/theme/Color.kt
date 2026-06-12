package com.nendo.argosy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils

object ALauncherColors {
    val Indigo = Color(0xFF5C6BC0)
    val IndigoDark = Color(0xFF26418F)

    val Cyan = Color(0xFF00ACC1)
    val CyanDark = Color(0xFF007C91)

    val Teal = Color(0xFF26A69A)
    val TealDark = Color(0xFF00766C)

    val Orange = Color(0xFFFF7043)
    val OrangeDark = Color(0xFFC63F17)

    val Green = Color(0xFF66BB6A)
    val GreenDark = Color(0xFF388E3C)

    val SurfaceDark = Color(0xFF121212)
    val SurfaceDarkVariant = Color(0xFF1E1E1E)
    val SurfaceLight = Color(0xFFFFFBFE)
    val SurfaceLightVariant = Color(0xFFF5F5F5)

    val OnSurfaceDark = Color(0xFFE1E1E1)
    val OnSurfaceLight = Color(0xFF1C1B1F)

    val StarGold = Color(0xFFFFD700)
    val DifficultyRed = Color(0xFFE53935)
    val TrophyAmber = Color(0xFFFFB300)

    val CompletionPlaying = Color(0xFF5C6BC0)
    val CompletionBeaten = Color(0xFF66BB6A)
    val CompletionCompleted = Color(0xFFFFB300)
}

fun hueToColorInt(hue: Float, saturation: Float = 0.7f, lightness: Float = 0.5f): Int {
    return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
}

fun colorIntToHue(colorInt: Int): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(colorInt, hsl)
    return hsl[0]
}
