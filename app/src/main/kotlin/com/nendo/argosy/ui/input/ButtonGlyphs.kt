package com.nendo.argosy.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.ui.components.InputButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ButtonGlyphSwaps(
    val ab: Boolean,
    val xy: Boolean,
    val startSelect: Boolean
)

enum class ControllerPosition { TOP, LEFT, RIGHT, BOTTOM, MENU_LEFT, MENU_RIGHT }

private val POSITIONED_BUTTONS = listOf(
    InputButton.A, InputButton.B, InputButton.X, InputButton.Y, InputButton.START, InputButton.SELECT
)

/**
 * The physical position an intent button sits at under [swaps], or null for a button with no face
 * or menu position. The one home of the swap mapping: glyphs and the controller diagram both read it.
 */
fun InputButton.positionUnder(swaps: ButtonGlyphSwaps): ControllerPosition? = when (this) {
    InputButton.A -> if (swaps.ab) ControllerPosition.RIGHT else ControllerPosition.BOTTOM
    InputButton.B -> if (swaps.ab) ControllerPosition.BOTTOM else ControllerPosition.RIGHT
    InputButton.X -> if (swaps.xy) ControllerPosition.TOP else ControllerPosition.LEFT
    InputButton.Y -> if (swaps.xy) ControllerPosition.LEFT else ControllerPosition.TOP
    InputButton.START -> if (swaps.startSelect) ControllerPosition.MENU_LEFT else ControllerPosition.MENU_RIGHT
    InputButton.SELECT -> if (swaps.startSelect) ControllerPosition.MENU_RIGHT else ControllerPosition.MENU_LEFT
    else -> null
}

fun ControllerPosition.intentButton(swaps: ButtonGlyphSwaps): InputButton =
    POSITIONED_BUTTONS.first { it.positionUnder(swaps) == this }

fun UserPreferences.buttonGlyphSwaps(detected: DetectedLayout?): ButtonGlyphSwaps {
    val nintendo = ControllerDetector.isNintendoLayout(controllerLayout, detected)
    return ButtonGlyphSwaps(
        ab = nintendo xor swapAB,
        xy = nintendo xor swapXY,
        startSelect = swapStartSelect
    )
}

/**
 * Supplies the glyph swap locals to [content] for a surface that is not hosted inside `ArgosyApp`.
 * [detectedLayout] is the active controller's layout.
 */
@Composable
fun ProvideButtonGlyphs(
    preferences: Flow<UserPreferences>,
    detectedLayout: StateFlow<DetectedLayout?>,
    content: @Composable () -> Unit
) {
    val prefs by preferences.collectAsState(initial = null)
    val layout by detectedLayout.collectAsState()
    val swaps = remember(prefs, layout) {
        prefs?.buttonGlyphSwaps(layout)
            ?: ButtonGlyphSwaps(ab = false, xy = false, startSelect = false)
    }
    CompositionLocalProvider(
        LocalABIconsSwapped provides swaps.ab,
        LocalXYIconsSwapped provides swaps.xy,
        LocalSwapStartSelect provides swaps.startSelect,
        content = content
    )
}
