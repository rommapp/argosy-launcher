package com.nendo.argosy.ui.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

const val TEXT_ENTRY_CANCEL_BUTTON = 0
const val TEXT_ENTRY_CONFIRM_BUTTON = 1

enum class TextEntryRow { FIELD, BUTTONS }

/**
 * Where the gamepad cursor sits inside a [TextEntryModal]. Owned by whoever routes the
 * modal's input: a ViewModel when the host screen routes it, the wrapper when the modal
 * captures input itself. A null focus on the modal means touch only.
 */
data class TextEntryFocus(
    val row: TextEntryRow = TextEntryRow.FIELD,
    val buttonIndex: Int = TEXT_ENTRY_CONFIRM_BUTTON
)

/**
 * One-field editor with Cancel and confirm. Pure visuals: it captures no input, and the
 * caller owns [text] so a host that routes gamepad input can submit what it holds.
 */
@Composable
fun TextEntryModal(
    title: String,
    label: String,
    confirmLabel: String,
    cancelLabel: String,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    focus: TextEntryFocus?,
    placeholder: String? = null,
    canSubmit: Boolean = text.isNotBlank(),
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val theme = LocalArgosyTheme.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val fieldFocused = focus == null || focus.row == TextEntryRow.FIELD
    val submit = { if (canSubmit) onSubmit() }

    LaunchedEffect(text) {
        if (fieldValue.text != text) fieldValue = TextFieldValue(text, TextRange(text.length))
    }

    LaunchedEffect(fieldFocused) {
        if (fieldFocused) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = title, onDismiss = onDismiss) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                val changed = next.text != fieldValue.text
                fieldValue = next
                if (changed) onTextChange(next.text)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            shape = fieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (focus?.row == TextEntryRow.FIELD) {
                        Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    } else {
                        Modifier
                    }
                ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            ModalActionButton(
                label = cancelLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focus?.row == TextEntryRow.BUTTONS && focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON,
                onClick = onDismiss
            )
            ModalActionButton(
                label = confirmLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focus?.row == TextEntryRow.BUTTONS && focus.buttonIndex == TEXT_ENTRY_CONFIRM_BUTTON,
                onClick = submit,
                enabled = canSubmit
            )
        }
    }
}
