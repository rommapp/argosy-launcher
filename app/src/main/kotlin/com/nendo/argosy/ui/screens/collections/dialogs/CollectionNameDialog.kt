package com.nendo.argosy.ui.screens.collections.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val ROW_FIELD = 0
private const val ROW_BUTTONS = 1

/** Single-text-field modal form with self-owned gamepad focus; [gamepadInput] off for hosts that route input themselves. */
@Composable
internal fun CollectionNameDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initialName: String,
    gamepadInput: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val theme = LocalArgosyTheme.current
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    var focusRow by remember { mutableIntStateOf(ROW_FIELD) }
    var buttonIndex by remember { mutableIntStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canSubmit = textFieldValue.text.isNotBlank()
    val submit = {
        if (textFieldValue.text.isNotBlank()) onSubmit(textFieldValue.text.trim())
    }

    LaunchedEffect(focusRow) {
        if (focusRow == ROW_FIELD) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    if (gamepadInput) {
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val currentSubmit by rememberUpdatedState(submit)
        val inputHandler = remember {
            object : InputHandler {
                override fun onUp(): InputResult {
                    focusRow = ROW_FIELD
                    return InputResult.HANDLED
                }

                override fun onDown(): InputResult {
                    focusRow = ROW_BUTTONS
                    return InputResult.HANDLED
                }

                override fun onLeft(): InputResult {
                    if (focusRow == ROW_BUTTONS) buttonIndex = 0
                    return InputResult.HANDLED
                }

                override fun onRight(): InputResult {
                    if (focusRow == ROW_BUTTONS) buttonIndex = 1
                    return InputResult.HANDLED
                }

                override fun onConfirm(): InputResult {
                    when {
                        focusRow == ROW_FIELD -> focusRow = ROW_BUTTONS
                        buttonIndex == 0 -> currentOnDismiss()
                        else -> currentSubmit()
                    }
                    return InputResult.HANDLED
                }

                override fun onBack(): InputResult {
                    currentOnDismiss()
                    return InputResult.handled(SoundType.CLOSE_MODAL)
                }

                override fun onMenu(): InputResult = InputResult.HANDLED
                override fun onSecondaryAction(): InputResult = InputResult.HANDLED
                override fun onContextMenu(): InputResult = InputResult.HANDLED
                override fun onPrevSection(): InputResult = InputResult.HANDLED
                override fun onNextSection(): InputResult = InputResult.HANDLED
                override fun onPrevTrigger(): InputResult = InputResult.HANDLED
                override fun onNextTrigger(): InputResult = InputResult.HANDLED
                override fun onSelect(): InputResult = InputResult.HANDLED
                override fun onLeftStickClick(): InputResult = InputResult.HANDLED
                override fun onRightStickClick(): InputResult = InputResult.HANDLED
                override fun onLongConfirm(): InputResult = InputResult.HANDLED
            }
        }
        ModalInputEffect(active = true, handler = inputHandler)
    }

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = title, onDismiss = onDismiss) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            label = { Text(label) },
            singleLine = true,
            shape = fieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (gamepadInput && focusRow == ROW_FIELD) {
                        Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    } else Modifier
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            ModalActionButton(
                label = stringResource(R.string.collections_namedialog_cancel),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = gamepadInput && focusRow == ROW_BUTTONS && buttonIndex == 0,
                onClick = onDismiss
            )
            ModalActionButton(
                label = confirmLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = gamepadInput && focusRow == ROW_BUTTONS && buttonIndex == 1,
                onClick = submit,
                enabled = canSubmit
            )
        }
    }
}
