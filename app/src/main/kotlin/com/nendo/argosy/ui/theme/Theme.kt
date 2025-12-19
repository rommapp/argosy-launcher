package com.nendo.argosy.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.FooterStyleConfig
import com.nendo.argosy.ui.components.LocalFooterStyle

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(
        AndroidColor.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ),
        hsv
    )
    return hsv
}

private fun hsvToColor(hsv: FloatArray): Color {
    return Color(AndroidColor.HSVToColor(hsv))
}


private fun toContainerDark(color: Color): Color {
    val hsv = colorToHsv(color)
    hsv[1] = (hsv[1] * 0.3f).coerceIn(0f, 1f)
    hsv[2] = 0.22f
    return hsvToColor(hsv)
}

private fun toContainerLight(color: Color): Color {
    val hsv = colorToHsv(color)
    hsv[1] = (hsv[1] * 0.2f).coerceIn(0f, 1f)
    hsv[2] = 0.95f
    return hsvToColor(hsv)
}

private fun darken(color: Color, factor: Float = 0.6f): Color {
    val hsv = colorToHsv(color)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return hsvToColor(hsv)
}

private fun createDarkColorScheme(
    primary: Color = ALauncherColors.Indigo,
    secondary: Color = ALauncherColors.Teal,
    tertiary: Color = ALauncherColors.Green
) = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = toContainerDark(primary),
    onPrimaryContainer = primary,

    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = toContainerDark(secondary),
    onSecondaryContainer = secondary,

    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = toContainerDark(tertiary),
    onTertiaryContainer = tertiary,

    background = ALauncherColors.SurfaceDark,
    onBackground = ALauncherColors.OnSurfaceDark,

    surface = ALauncherColors.SurfaceDark,
    onSurface = ALauncherColors.OnSurfaceDark,
    surfaceVariant = ALauncherColors.SurfaceDarkVariant,
    onSurfaceVariant = ALauncherColors.OnSurfaceDark.copy(alpha = 0.8f),

    outline = Color.White.copy(alpha = 0.12f),
    outlineVariant = Color.White.copy(alpha = 0.06f)
)

private fun createLightColorScheme(
    primary: Color = ALauncherColors.IndigoDark,
    secondary: Color = ALauncherColors.TealDark,
    tertiary: Color = ALauncherColors.GreenDark
) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = toContainerLight(primary),
    onPrimaryContainer = darken(primary),

    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = toContainerLight(secondary),
    onSecondaryContainer = darken(secondary),

    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = toContainerLight(tertiary),
    onTertiaryContainer = darken(tertiary),

    background = ALauncherColors.SurfaceLight,
    onBackground = ALauncherColors.OnSurfaceLight,

    surface = ALauncherColors.SurfaceLight,
    onSurface = ALauncherColors.OnSurfaceLight,
    surfaceVariant = ALauncherColors.SurfaceLightVariant,
    onSurfaceVariant = ALauncherColors.OnSurfaceLight.copy(alpha = 0.8f),

    outline = Color.Black.copy(alpha = 0.12f),
    outlineVariant = Color.Black.copy(alpha = 0.06f)
)

data class SemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

data class LauncherThemeConfig(
    val isDarkTheme: Boolean,
    val focusGlowColor: Color,
    val overlayLight: Color,
    val overlayDark: Color,
    val semanticColors: SemanticColors
)

private val DarkSemanticColors = SemanticColors(
    success = ALauncherColors.Green,
    successContainer = toContainerDark(ALauncherColors.Green),
    onSuccessContainer = ALauncherColors.Green,
    warning = ALauncherColors.Orange,
    warningContainer = toContainerDark(ALauncherColors.Orange),
    onWarningContainer = ALauncherColors.Orange,
    info = ALauncherColors.Indigo,
    infoContainer = toContainerDark(ALauncherColors.Indigo),
    onInfoContainer = ALauncherColors.Indigo
)

private val LightSemanticColors = SemanticColors(
    success = ALauncherColors.GreenDark,
    successContainer = toContainerLight(ALauncherColors.GreenDark),
    onSuccessContainer = darken(ALauncherColors.GreenDark),
    warning = ALauncherColors.OrangeDark,
    warningContainer = toContainerLight(ALauncherColors.OrangeDark),
    onWarningContainer = darken(ALauncherColors.OrangeDark),
    info = ALauncherColors.IndigoDark,
    infoContainer = toContainerLight(ALauncherColors.IndigoDark),
    onInfoContainer = darken(ALauncherColors.IndigoDark)
)

val LocalLauncherTheme = staticCompositionLocalOf {
    LauncherThemeConfig(
        isDarkTheme = true,
        focusGlowColor = ALauncherColors.FocusGlow,
        overlayLight = Color.Black.copy(alpha = 0.3f),
        overlayDark = Color.Black.copy(alpha = 0.7f),
        semanticColors = DarkSemanticColors
    )
}

data class BoxArtStyleConfig(
    val cornerRadiusDp: Dp = 8.dp,
    val borderThicknessDp: Dp = 2.dp,
    val glowAlpha: Float = 0.4f,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPaddingDp: Dp = 8.dp
)

val LocalBoxArtStyle = staticCompositionLocalOf { BoxArtStyleConfig() }

@Composable
fun ALauncherTheme(
    viewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val themeState by viewModel.themeState.collectAsState()

    val isDarkTheme = when (themeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val defaultPrimary = if (BuildConfig.DEBUG) ALauncherColors.Orange else ALauncherColors.Indigo
    val defaultPrimaryDark = if (BuildConfig.DEBUG) ALauncherColors.OrangeDark else ALauncherColors.IndigoDark
    val primaryColor = themeState.primaryColor?.let { Color(it) }

    val colorScheme = if (isDarkTheme) {
        createDarkColorScheme(
            primary = primaryColor ?: defaultPrimary
        )
    } else {
        createLightColorScheme(
            primary = primaryColor ?: defaultPrimaryDark
        )
    }

    val focusGlow = (primaryColor ?: defaultPrimary).copy(alpha = 0.4f)
    val semanticColors = if (isDarkTheme) DarkSemanticColors else LightSemanticColors

    val launcherConfig = LauncherThemeConfig(
        isDarkTheme = isDarkTheme,
        focusGlowColor = focusGlow,
        overlayLight = Color.Black.copy(alpha = 0.3f),
        overlayDark = Color.Black.copy(alpha = 0.7f),
        semanticColors = semanticColors
    )

    val boxArtStyle = BoxArtStyleConfig(
        cornerRadiusDp = themeState.boxArtCornerRadius.dp.dp,
        borderThicknessDp = themeState.boxArtBorderThickness.dp.dp,
        glowAlpha = themeState.boxArtGlowStrength.alpha,
        systemIconPosition = themeState.systemIconPosition,
        systemIconPaddingDp = themeState.systemIconPadding.dp.dp
    )

    val footerStyle = FooterStyleConfig(
        useAccentColor = themeState.useAccentColorFooter
    )

    CompositionLocalProvider(
        LocalLauncherTheme provides launcherConfig,
        LocalBoxArtStyle provides boxArtStyle,
        LocalFooterStyle provides footerStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
