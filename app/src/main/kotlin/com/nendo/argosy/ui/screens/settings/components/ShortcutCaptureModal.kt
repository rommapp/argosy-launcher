package com.nendo.argosy.ui.screens.settings.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.core.input.actsAsGamepad
import com.nendo.argosy.libretro.HotkeyManager
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.UiShortcutKeys
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun ShortcutCaptureModal(
    actionTitle: String,
    onAssign: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val currentOnAssign = rememberUpdatedState(onAssign)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    var rejectedKey by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val device = event.device
                val isBack = event.keyCode == KeyEvent.KEYCODE_BACK ||
                    gamepadInputHandler?.mapKeyToEvent(event.keyCode) == GamepadEvent.Back
                when {
                    isBack -> currentOnDismiss.value()
                    device == null || !device.actsAsGamepad() -> {}
                    UiShortcutKeys.isBindable(event.keyCode) -> currentOnAssign.value(event.keyCode)
                    else -> rejectedKey = event.keyCode
                }
            }
            true
        }

        gamepadInputHandler?.setRawKeyEventListener(listener)

        onDispose {
            gamepadInputHandler?.setRawKeyEventListener(null)
        }
    }

    Modal(
        title = stringResource(R.string.settings_navigation_shortcut_capture_title),
        subtitle = actionTitle,
        baseWidth = Dimens.modalWidth,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.B to stringResource(R.string.settings_navigation_shortcut_capture_hint_cancel)
        ),
        inlineFooterHints = true,
        onFooterHintClick = { button -> if (button == InputButton.B) onDismiss() }
    ) {
        ShortcutListeningRow(rejectedKey)
    }
}

@Composable
private fun ShortcutListeningRow(rejectedKey: Int?) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(Dimens.borderThin, theme.focusAccent, RoundedCornerShape(Dimens.radiusMd))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_navigation_shortcut_capture_prompt),
            style = MaterialTheme.typography.titleMedium,
            color = theme.focusAccent
        )
        Text(
            text = if (rejectedKey != null) {
                stringResource(
                    R.string.settings_navigation_shortcut_capture_reserved,
                    HotkeyManager.getKeyName(rejectedKey)
                )
            } else {
                stringResource(R.string.settings_navigation_shortcut_capture_subtitle)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (rejectedKey != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
    }
}
