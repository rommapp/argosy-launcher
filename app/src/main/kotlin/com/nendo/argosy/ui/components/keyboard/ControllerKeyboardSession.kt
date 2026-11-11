package com.nendo.argosy.ui.components.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Keeps the Argosy keyboard open for one field while [open] is true. The keyboard starts from
 * [text] and reports every edit through [onTextChange]; [onClose] runs when the keyboard closes
 * itself (back, a newer field, or a hand-off to the system keyboard), never when [open] turns false.
 */
@Composable
fun ControllerKeyboardSession(
    open: Boolean,
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    submitLabel: String,
    placeholder: String? = null,
    fieldType: KeyboardFieldType = KeyboardFieldType.TEXT,
    maxLength: Int? = null,
    error: String? = null,
    canSubmit: Boolean = true,
    enabled: Boolean = true,
    onSystemKeyboard: (() -> Unit)? = null
) {
    val owner = remember { Any() }
    val currentText by rememberUpdatedState(text)
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnSystemKeyboard by rememberUpdatedState(onSystemKeyboard)
    val handsOff = onSystemKeyboard != null
    val props = ControllerKeyboardLiveProps(text = text, error = error, canSubmit = canSubmit, enabled = enabled)
    val currentProps by rememberUpdatedState(props)

    LaunchedEffect(open, owner) {
        if (open) {
            ControllerKeyboard.open(
                ControllerKeyboardRequest(
                    owner = owner,
                    label = label,
                    placeholder = placeholder,
                    initialText = currentText,
                    fieldType = fieldType,
                    submitLabel = submitLabel,
                    maxLength = maxLength,
                    onTextChange = { currentOnTextChange(it) },
                    onSubmit = { currentOnSubmit() },
                    onClose = { currentOnClose() },
                    onSystemKeyboard = if (handsOff) ({ currentOnSystemKeyboard?.invoke() }) else null
                ),
                currentProps
            )
        } else {
            ControllerKeyboard.close(owner)
        }
    }

    LaunchedEffect(props, owner) {
        ControllerKeyboard.update(owner, props)
    }

    DisposableEffect(owner) {
        onDispose { ControllerKeyboard.close(owner) }
    }
}
