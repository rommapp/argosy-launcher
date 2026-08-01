package com.nendo.argosy.hardware

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.theme.CustomFontFamilies
import com.nendo.argosy.ui.theme.ProvideArgosyThemeLocals
import com.nendo.argosy.ui.theme.ThemeState
import com.nendo.argosy.ui.theme.argosyColorScheme
import com.nendo.argosy.ui.theme.argosyTypography
import com.nendo.argosy.ui.theme.rememberArgosyPalette

/**
 * Theme for the secondary display. Delegates palette resolution, color-scheme
 * construction, and CompositionLocals provision to the same helpers used by
 * [com.nendo.argosy.ui.theme.ALauncherTheme] so the two displays cannot
 * disagree on dark-mode resolution, accent fallbacks, or cover-art style.
 *
 * [SecondaryHomeActivity] is NOT a Hilt entry point (it builds its
 * ViewModels manually from DualScreenManager), so this composable can't use
 * hiltViewModel() the way ALauncherTheme can. The host is responsible for
 * collecting a ThemeState flow (see UserPreferences.toThemeState) and passing
 * it in.
 *
 * The host activity also broadcasts its primary color (the user-set or
 * default accent) so live accent changes on the primary screen propagate
 * immediately; the override wins over the user-pref color stored in
 * [themeState].
 */
@Composable
fun SecondaryHomeTheme(
    themeState: ThemeState,
    fonts: CustomFontFamilies = CustomFontFamilies(),
    primaryColor: Int? = null,
    content: @Composable () -> Unit
) {
    val palette = rememberArgosyPalette(
        themeState = themeState,
        primaryOverride = primaryColor?.let { Color(it) }
    )
    val typography = remember(fonts, themeState.displayFontScale, themeState.bodyFontScale) {
        argosyTypography(fonts, themeState.displayFontScale, themeState.bodyFontScale)
    }

    ProvideArgosyThemeLocals(
        themeState = themeState,
        palette = palette,
        isSecondaryDisplay = true
    ) {
        MaterialTheme(
            colorScheme = argosyColorScheme(palette),
            typography = typography,
            content = content
        )
    }
}
