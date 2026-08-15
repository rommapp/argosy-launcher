package com.nendo.argosy.ui.theme

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.PlatformIndicatorStyle
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.FooterStyleConfig
import com.nendo.argosy.ui.components.LocalFooterStyle
import com.nendo.argosy.ui.theme.backdrop.LocalBackdropStamps
import com.nendo.argosy.ui.theme.backdrop.LocalSurfaceBackdrop
import com.nendo.argosy.ui.theme.backdrop.backdropStampProvider
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

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

/** Tints a surface-ramp color toward [hue]: capped saturation plus a dark-weighted value lift so near-black chrome reads; bleed 0 returns the color unchanged. */
internal fun tintSurface(color: Color, hue: Float, bleed: Int): Color {
    if (bleed <= 0) return color
    val fraction = (bleed / 100f).coerceIn(0f, 1f)
    val hsv = colorToHsv(color)
    hsv[0] = hue.mod(360f)
    hsv[1] = (fraction * ComponentDefaults.SurfaceTint.maxSaturationRatio)
        .coerceIn(0f, ComponentDefaults.SurfaceTint.maxSaturationRatio)
    hsv[2] = hsv[2] + fraction * ComponentDefaults.SurfaceTint.valueLiftRatio * (1f - hsv[2])
    return Color(AndroidColor.HSVToColor((color.alpha * 255).toInt(), hsv))
}

internal fun hueOf(color: Color): Float = colorToHsv(color)[0]

internal fun createDarkColorScheme(
    primary: Color = ColorTokens.Scheme.Dark.primary,
    secondary: Color = ColorTokens.Scheme.Dark.secondary,
    tintHue: Float = 0f,
    tintBleed: Int = 0
) = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = toContainerDark(primary),
    onPrimaryContainer = primary,

    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = toContainerDark(secondary),
    onSecondaryContainer = secondary,

    tertiary = secondary,
    onTertiary = Color.White,
    tertiaryContainer = toContainerDark(secondary),
    onTertiaryContainer = secondary,

    background = tintSurface(ColorTokens.Scheme.Dark.background, tintHue, tintBleed),
    onBackground = ColorTokens.Scheme.Dark.onSurface,

    surface = tintSurface(ColorTokens.Scheme.Dark.surface, tintHue, tintBleed),
    onSurface = ColorTokens.Scheme.Dark.onSurface,
    surfaceVariant = tintSurface(ColorTokens.Scheme.Dark.surfaceVariant, tintHue, tintBleed),
    onSurfaceVariant = ColorTokens.Scheme.Dark.onSurface.copy(alpha = 0.8f),

    outline = tintSurface(ColorTokens.Scheme.Dark.outline, tintHue, tintBleed),
    outlineVariant = tintSurface(ColorTokens.Scheme.Dark.outlineVariant, tintHue, tintBleed)
)

internal fun createLightColorScheme(
    primary: Color = ColorTokens.Scheme.Light.primary,
    secondary: Color = ColorTokens.Scheme.Light.secondary,
    tintHue: Float = 0f,
    tintBleed: Int = 0
) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = toContainerLight(primary),
    onPrimaryContainer = darken(primary),

    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = toContainerLight(secondary),
    onSecondaryContainer = darken(secondary),

    tertiary = secondary,
    onTertiary = Color.White,
    tertiaryContainer = toContainerLight(secondary),
    onTertiaryContainer = darken(secondary),

    background = tintSurface(ColorTokens.Scheme.Light.background, tintHue, tintBleed),
    onBackground = ColorTokens.Scheme.Light.onSurface,

    surface = tintSurface(ColorTokens.Scheme.Light.surface, tintHue, tintBleed),
    onSurface = ColorTokens.Scheme.Light.onSurface,
    surfaceVariant = tintSurface(ColorTokens.Scheme.Light.surfaceVariant, tintHue, tintBleed),
    onSurfaceVariant = ColorTokens.Scheme.Light.onSurface.copy(alpha = 0.8f),

    outline = tintSurface(ColorTokens.Scheme.Light.outline, tintHue, tintBleed),
    outlineVariant = tintSurface(ColorTokens.Scheme.Light.outlineVariant, tintHue, tintBleed)
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
    val onInfoContainer: Color,
    val progress: Color,
    val progressContainer: Color,
    val onProgressContainer: Color
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
    onInfoContainer = ALauncherColors.Indigo,
    progress = ALauncherColors.Mint,
    progressContainer = toContainerDark(ALauncherColors.Mint),
    onProgressContainer = ALauncherColors.Mint
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
    onInfoContainer = darken(ALauncherColors.IndigoDark),
    progress = ALauncherColors.MintDark,
    progressContainer = toContainerLight(ALauncherColors.MintDark),
    onProgressContainer = darken(ALauncherColors.MintDark)
)

val LocalLauncherTheme = staticCompositionLocalOf {
    LauncherThemeConfig(
        isDarkTheme = true,
        focusGlowColor = ALauncherColors.Cyan.copy(alpha = 0.4f),
        overlayLight = Color.Black.copy(alpha = 0.3f),
        overlayDark = Color.Black.copy(alpha = 0.7f),
        semanticColors = DarkSemanticColors
    )
}

data class BoxArtStyleConfig(
    val aspectRatio: Float = 3f / 4f,
    val nativeAspectRatio: Boolean = false,
    val cornerRadiusDp: Dp = 8.dp,
    val borderThicknessDp: Dp = 2.dp,
    val borderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID,
    val glassBorderTintAlpha: Float = 0f,
    val glowAlpha: Float = BoxArtGlowStrength.MEDIUM.alpha,
    val isShadow: Boolean = BoxArtGlowStrength.MEDIUM.isShadow,
    val outerEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val outerEffectThicknessPx: Float = 16f,
    val glowColorMode: GlowColorMode = GlowColorMode.AUTO,
    val accentColor: Color? = null,
    val secondaryColor: Color? = null,
    val innerEffect: BoxArtInnerEffect = BoxArtInnerEffect.SHADOW,
    val innerEffectThicknessPx: Float = 4f,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPaddingDp: Dp = 8.dp,
    val platformIndicatorStyle: PlatformIndicatorStyle = PlatformIndicatorStyle.TAB,
    val platformIndicatorContent: PlatformIndicatorContent = PlatformIndicatorContent.NAME
)

val LocalBoxArtStyle = staticCompositionLocalOf { BoxArtStyleConfig() }

/**
 * Resolved palette derived from a [ThemeState] (plus an optional host-broadcast
 * primary override used by the secondary display). Single source of truth so
 * the primary and secondary themes can never disagree on dark-mode resolution,
 * default-accent fallbacks, or what counts as "effective primary".
 */
data class ArgosyPalette(
    val isDarkTheme: Boolean,
    val rawPrimary: Color?,
    val effectivePrimary: Color,
    val rawSecondary: Color?,
    val effectiveSecondary: Color,
    val surfaceTintBleed: Int = 0
) {
    val surfaceTintHue: Float get() = hueOf(effectivePrimary)
}

@Composable
internal fun rememberArgosyPalette(
    themeState: ThemeState,
    primaryOverride: Color? = null
): ArgosyPalette {
    val isDarkTheme = when (themeState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val defaultPrimary = if (BuildConfig.DEBUG) ColorTokens.Scheme.DebugOverrides.Dark.primary else ColorTokens.Scheme.Dark.primary
    val defaultPrimaryDark = if (BuildConfig.DEBUG) ColorTokens.Scheme.DebugOverrides.Light.primary else ColorTokens.Scheme.Light.primary
    val rawPrimary = primaryOverride ?: themeState.primaryColor?.let { Color(it) }
    val rawSecondary = themeState.secondaryColor?.let { Color(it) }
    val effectivePrimary = rawPrimary ?: if (isDarkTheme) defaultPrimary else defaultPrimaryDark
    val effectiveSecondary = rawSecondary ?: effectivePrimary
    return ArgosyPalette(
        isDarkTheme, rawPrimary, effectivePrimary, rawSecondary, effectiveSecondary,
        themeState.surfaceTintBleed
    )
}

internal fun argosyColorScheme(palette: ArgosyPalette) = if (palette.isDarkTheme) {
    createDarkColorScheme(
        primary = palette.effectivePrimary,
        secondary = palette.effectiveSecondary,
        tintHue = palette.surfaceTintHue,
        tintBleed = palette.surfaceTintBleed
    )
} else {
    createLightColorScheme(
        primary = palette.effectivePrimary,
        secondary = palette.effectiveSecondary,
        tintHue = palette.surfaceTintHue,
        tintBleed = palette.surfaceTintBleed
    )
}

@Composable
fun ALauncherTheme(
    viewModel: ThemeViewModel = hiltViewModel(),
    isSecondaryDisplay: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeState by viewModel.themeState.collectAsState()
    val customFonts by viewModel.customFonts.collectAsState()
    val palette = rememberArgosyPalette(themeState)
    val typography = remember(customFonts, themeState.displayFontScale, themeState.bodyFontScale) {
        argosyTypography(customFonts, themeState.displayFontScale, themeState.bodyFontScale)
    }

    ProvideArgosyThemeLocals(
        themeState = themeState,
        palette = palette,
        isSecondaryDisplay = isSecondaryDisplay
    ) {
        MaterialTheme(
            colorScheme = argosyColorScheme(palette),
            typography = typography,
            content = content
        )
    }
}

/**
 * Provides the launcher's CompositionLocals (LocalUiScale, LocalLauncherTheme,
 * LocalBoxArtStyle, LocalFooterStyle). Both [ALauncherTheme] and the
 * secondary-display theme call into this -- without it, cover-art style
 * settings (corners, borders, glow, etc.) silently fall back to defaults on
 * the dual-screen home and library grid because [LocalBoxArtStyle] would
 * never be overridden there.
 */
@Composable
fun ProvideArgosyThemeLocals(
    themeState: ThemeState,
    palette: ArgosyPalette,
    isSecondaryDisplay: Boolean = false,
    content: @Composable () -> Unit
) {
    val focusGlow = palette.effectivePrimary.copy(alpha = 0.4f)
    val semanticColors = if (palette.isDarkTheme) DarkSemanticColors else LightSemanticColors

    val launcherConfig = LauncherThemeConfig(
        isDarkTheme = palette.isDarkTheme,
        focusGlowColor = focusGlow,
        overlayLight = Color.Black.copy(alpha = 0.3f),
        overlayDark = Color.Black.copy(alpha = 0.7f),
        semanticColors = semanticColors
    )

    val boxArtStyle = BoxArtStyleConfig(
        aspectRatio = themeState.boxArtShape.aspectRatio,
        nativeAspectRatio = themeState.boxArtShape.isNative,
        cornerRadiusDp = themeState.boxArtCornerRadius.dp.dp,
        borderThicknessDp = themeState.boxArtBorderThickness.dp.dp,
        borderStyle = themeState.boxArtBorderStyle,
        glassBorderTintAlpha = themeState.glassBorderTintAlpha,
        glowAlpha = themeState.boxArtGlowStrength.alpha,
        isShadow = themeState.boxArtGlowStrength.isShadow,
        outerEffect = themeState.boxArtOuterEffect,
        outerEffectThicknessPx = themeState.boxArtOuterEffectThickness.px,
        glowColorMode = themeState.glowColorMode,
        accentColor = palette.effectivePrimary,
        secondaryColor = palette.effectiveSecondary,
        innerEffect = themeState.boxArtInnerEffect,
        innerEffectThicknessPx = themeState.boxArtInnerEffectThickness.px,
        systemIconPosition = themeState.systemIconPosition,
        systemIconPaddingDp = themeState.systemIconPadding.dp.dp,
        platformIndicatorStyle = themeState.platformIndicatorStyle,
        platformIndicatorContent = themeState.platformIndicatorContent
    )

    val footerStyle = FooterStyleConfig(
        useAccentColor = themeState.useAccentColorFooter
    )

    val configuration = LocalConfiguration.current
    val aspectRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
    val aspectRatioClass = when {
        aspectRatio >= 2.0f -> AspectRatioClass.ULTRA_WIDE
        aspectRatio >= 1.6f -> AspectRatioClass.WIDE
        aspectRatio >= 0.5f -> AspectRatioClass.STANDARD
        aspectRatio >= 0.35f -> AspectRatioClass.TALL
        else -> AspectRatioClass.ULTRA_TALL
    }

    val uiScaleConfig = UiScaleConfig(
        scale = themeState.uiScale / 100f,
        aspectRatioClass = aspectRatioClass,
        bottomReservedFraction = resolveGripReserveFraction(
            enabled = isGripReserveActive(
                mode = themeState.gripReserveMode,
                autoControllerConnected = themeState.gripAutoControllerConnected
            ),
            percent = themeState.gripReservePercent,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            isSecondaryDisplay = isSecondaryDisplay
        ),
        compactFooter = themeState.compactFooter
    )

    val context = LocalContext.current
    val stampProvider = remember(context) {
        runCatching { backdropStampProvider(context) }.getOrNull()
    }

    CompositionLocalProvider(
        LocalUiScale provides uiScaleConfig,
        LocalLauncherTheme provides launcherConfig,
        LocalArgosyTheme provides argosyThemeTokens(palette),
        LocalSurfaceBackdrop provides themeState.surfaceBackdrop,
        LocalBackdropStamps provides stampProvider,
        LocalBoxArtStyle provides boxArtStyle,
        LocalFooterStyle provides footerStyle,
        content = content
    )
}
