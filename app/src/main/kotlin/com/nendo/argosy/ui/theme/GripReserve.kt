package com.nendo.argosy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val GRIP_RESERVE_MIN_PERCENT = 5
const val GRIP_RESERVE_MAX_PERCENT = 40

/**
 * Resolves the fraction of screen height the grip reserve takes at the bottom
 * of the primary display.
 *
 * Returns 0 unless all three hold: the reserve is enabled, the display is
 * portrait, and this is not the companion display. Every caller resolves it
 * from live values rather than caching one, because any of the three can change
 * without the caller being rebuilt.
 */
fun resolveGripReserveFraction(
    enabled: Boolean,
    percent: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    isSecondaryDisplay: Boolean = false
): Float {
    if (!enabled || isSecondaryDisplay) return 0f
    if (screenWidthDp >= screenHeightDp) return 0f
    return percent.coerceIn(GRIP_RESERVE_MIN_PERCENT, GRIP_RESERVE_MAX_PERCENT) / 100f
}

/**
 * Bottom inset any full-screen overlay should apply so it does not render into
 * the band the grip reserve takes. Resolves to 0.dp whenever the reserve is
 * inactive, so applying it unconditionally is safe.
 */
@Composable
fun gripReserveBottomInset(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val fraction = LocalUiScale.current.bottomReservedFraction
    return remember(screenHeightDp, fraction) { (screenHeightDp * fraction).dp }
}
