package com.nendo.argosy.ui.screens.settings.components

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.hardware.SystemHotkeyCombos
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SAVED_DWELL_MS = 500L

/**
 * Records a Home or Back combo in press order. A lone tap of the Back button, or the system Back
 * key, cancels; the Back button held with other buttons is recorded as part of the combo.
 */
@Composable
fun SystemComboCaptureModal(
    actionTitle: String,
    onAssign: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val scope = rememberCoroutineScope()
    val currentOnAssign = rememberUpdatedState(onAssign)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    var heldKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var chordPeak by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }

    DisposableEffect(gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val key = event.keyCode
            if (!isComplete && event.repeatCount == 0) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> when {
                        key == KeyEvent.KEYCODE_BACK -> currentOnDismiss.value()
                        SystemHotkeyCombos.isRecordable(key) && heldKeys.size < SystemHotkeyCombos.MAX_KEYS -> {
                            heldKeys = heldKeys + key
                            chordPeak = maxOf(chordPeak, heldKeys.size)
                        }
                    }
                    KeyEvent.ACTION_UP -> if (key in heldKeys) {
                        heldKeys = heldKeys - key
                        val loneBack = chordPeak == 1 &&
                            gamepadInputHandler?.mapKeyToEvent(key) == GamepadEvent.Back
                        if (heldKeys.isEmpty()) chordPeak = 0
                        if (loneBack) currentOnDismiss.value()
                    }
                }
            }
            true
        }

        gamepadInputHandler?.setRawKeyEventListener(listener)

        onDispose {
            gamepadInputHandler?.setRawKeyEventListener(null)
        }
    }

    RecordingContent(
        title = actionTitle,
        heldKeys = heldKeys.takeIf { it.size >= SystemHotkeyCombos.MIN_KEYS } ?: emptySet(),
        isComplete = isComplete,
        onComplete = { keys ->
            scope.launch {
                delay(SAVED_DWELL_MS)
                currentOnAssign.value(keys.toList())
            }
        },
        onCancel = onDismiss,
        onUpdateComplete = { isComplete = it },
        prompt = stringResource(R.string.settings_navigation_system_combo_capture_prompt)
    )
}
