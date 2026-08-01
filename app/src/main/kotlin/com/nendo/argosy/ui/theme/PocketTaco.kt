package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Resolves the fraction of screen height Pocket Taco Mode reserves at the
 * bottom of the primary display.
 *
 * Returns 0 unless all three hold: the mode is enabled, the display is
 * portrait, and this is not the companion display. The companion never
 * reserves space - it is a second screen, not a screen held in a grip.
 */
fun resolvePocketTacoFraction(
    enabled: Boolean,
    percent: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    isSecondaryDisplay: Boolean = false
): Float {
    if (!enabled || isSecondaryDisplay) return 0f
    if (screenWidthDp >= screenHeightDp) return 0f
    return percent.coerceIn(0, 100) / 100f
}

/**
 * Bottom inset any full-screen overlay should apply so it does not render into
 * the band Pocket Taco Mode reserves. Resolves to 0.dp whenever the mode is
 * inactive, so applying it unconditionally is safe.
 */
@Composable
fun pocketTacoBottomInset(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val fraction = LocalUiScale.current.bottomReservedFraction
    return remember(screenHeightDp, fraction) { (screenHeightDp * fraction).dp }
}
