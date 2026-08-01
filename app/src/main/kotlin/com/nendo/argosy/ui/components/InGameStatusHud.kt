package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens

private const val HUD_BACKGROUND_ALPHA = 0.45f
private const val HUD_TEXT_ALPHA = 0.85f

enum class HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * What the in-game bar renders. Every field is a formatted string except the battery, which needs
 * its level to pick a colour; a null field is a bit the user switched off or one with nothing to
 * report yet.
 */
data class InGameStatusHudState(
    val batteryLevel: Int? = null,
    val batteryCharging: Boolean = false,
    val clock: String? = null,
    val sessionElapsed: String? = null,
    val fps: String? = null,
    val lastSave: String? = null
) {
    val hasAnything: Boolean
        get() = batteryLevel != null || clock != null || sessionElapsed != null ||
            fps != null || lastSave != null
}

/**
 * A small always-on readout pinned to a window corner while a game runs. It aligns to the window
 * rather than the game image: the emulator's Compose layer has no access to the letterboxed game
 * rect, and in portrait the band below the game is a more readable backdrop than the game itself.
 */
@Composable
fun InGameStatusHud(
    state: InGameStatusHudState,
    corner: HudCorner,
    modifier: Modifier = Modifier
) {
    if (!state.hasAnything) return

    val alignment = when (corner) {
        HudCorner.TOP_LEFT -> Alignment.TopStart
        HudCorner.TOP_RIGHT -> Alignment.TopEnd
        HudCorner.BOTTOM_LEFT -> Alignment.BottomStart
        HudCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    Box(modifier = modifier, contentAlignment = alignment) {
        Row(
            modifier = Modifier
                .padding(Dimens.spacingSm)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = HUD_BACKGROUND_ALPHA)
                )
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            state.batteryLevel?.let { level ->
                BatteryReadout(level = level, isCharging = state.batteryCharging)
            }
            state.clock?.let { HudText(it) }
            state.sessionElapsed?.let { HudText(it, monospace = true) }
            state.fps?.let { HudText(it, monospace = true) }
            state.lastSave?.let { HudText(it) }
        }
    }
}

@Composable
private fun BatteryReadout(level: Int, isCharging: Boolean) {
    val color = when {
        isCharging -> ColorTokens.Domain.Battery.charging
        level <= BATTERY_LOW_PERCENT -> ColorTokens.Domain.Battery.low
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = HUD_TEXT_ALPHA)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        BatteryIcon(
            level = level,
            isCharging = isCharging,
            color = color,
            modifier = Modifier.size(width = Dimens.iconSm, height = Dimens.radiusMd)
        )
        Text(
            text = "$level%",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun HudText(text: String, monospace: Boolean = false) {
    Text(
        text = text,
        style = if (monospace) {
            MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.labelSmall
        },
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = HUD_TEXT_ALPHA)
    )
}
