package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect

private const val MAX_GREETING_RUNES = 140

private fun String.runeCount(): Int = codePointCount(0, length)

@Composable
fun GreetingEditModal(
    initial: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    var greeting by remember { mutableStateOf(initial) }
    var focus by remember { mutableStateOf(TextEntryFocus()) }
    val submit = { onSubmit(greeting.trim()) }
    val currentSubmit by rememberUpdatedState(submit)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focus = focus.copy(row = TextEntryRow.FIELD)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                focus = focus.copy(row = TextEntryRow.BUTTONS)
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (focus.row == TextEntryRow.BUTTONS) focus = focus.copy(buttonIndex = TEXT_ENTRY_CANCEL_BUTTON)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (focus.row == TextEntryRow.BUTTONS) focus = focus.copy(buttonIndex = TEXT_ENTRY_CONFIRM_BUTTON)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when {
                    focus.row == TextEntryRow.FIELD -> focus = focus.requestKeyboard()
                    focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> currentOnDismiss()
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

    ModalInputEffect(active = true, handler = inputHandler, inputDispatcher = inputDispatcher)

    TextEntryModal(
        title = stringResource(R.string.quaypass_greeting_modal_title),
        label = stringResource(R.string.quaypass_greeting_modal_title),
        confirmLabel = stringResource(R.string.quaypass_greeting_modal_footer_save),
        cancelLabel = stringResource(R.string.quaypass_greeting_modal_footer_cancel),
        text = greeting,
        onTextChange = { if (it.runeCount() <= MAX_GREETING_RUNES) greeting = it },
        onDismiss = onDismiss,
        onSubmit = submit,
        focus = focus,
        placeholder = stringResource(R.string.quaypass_greeting_modal_placeholder),
        message = stringResource(R.string.quaypass_greeting_modal_description),
        canSubmit = true,
        maxLength = MAX_GREETING_RUNES
    )
}
