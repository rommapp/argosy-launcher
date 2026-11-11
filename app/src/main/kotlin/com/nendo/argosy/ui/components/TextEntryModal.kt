package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import com.nendo.argosy.ui.components.keyboard.KeyboardFieldType
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

const val TEXT_ENTRY_CANCEL_BUTTON = 0
const val TEXT_ENTRY_CONFIRM_BUTTON = 1

enum class TextEntryRow { FIELD, BUTTONS }

/**
 * Where the gamepad cursor sits inside a [TextEntryModal]. Owned by whoever routes the
 * modal's input: a ViewModel when the host screen routes it, the wrapper when the modal
 * captures input itself. A null focus on the modal means touch only. A change in
 * [keyboardRequest] reopens the Argosy keyboard after the user closed it.
 */
data class TextEntryFocus(
    val row: TextEntryRow = TextEntryRow.FIELD,
    val buttonIndex: Int = TEXT_ENTRY_CONFIRM_BUTTON,
    val keyboardRequest: Int = 0
) {
    fun requestKeyboard(): TextEntryFocus = copy(row = TextEntryRow.FIELD, keyboardRequest = keyboardRequest + 1)
}

data class TextEntryAction(val label: String, val onClick: () -> Unit)

internal fun keyboardFieldTypeOf(
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None
): KeyboardFieldType = when {
    visualTransformation is PasswordVisualTransformation -> KeyboardFieldType.PASSWORD
    keyboardType == KeyboardType.Password || keyboardType == KeyboardType.NumberPassword -> KeyboardFieldType.PASSWORD
    keyboardType == KeyboardType.Uri -> KeyboardFieldType.URI
    keyboardType == KeyboardType.Email -> KeyboardFieldType.EMAIL
    keyboardType == KeyboardType.Number || keyboardType == KeyboardType.Decimal ||
        keyboardType == KeyboardType.Phone -> KeyboardFieldType.NUMBER
    else -> KeyboardFieldType.TEXT
}

/**
 * One-field editor with [leadingActions], Cancel and confirm, in that order; [TextEntryFocus.buttonIndex]
 * counts across all of them. Pure visuals: it captures no input, and the caller owns [text].
 */
@OptIn(ExperimentalLayoutApi::class)
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
    message: String? = null,
    errorMessage: String? = null,
    statusMessage: String? = null,
    canSubmit: Boolean = text.isNotBlank(),
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingActions: List<TextEntryAction> = emptyList(),
    maxLength: Int? = null
) {
    val theme = LocalArgosyTheme.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldFocused = focus == null || focus.row == TextEntryRow.FIELD
    val confirmEnabled = enabled && canSubmit
    val submit = { if (confirmEnabled) onSubmit() }
    val cancelIndex = leadingActions.size + TEXT_ENTRY_CANCEL_BUTTON
    val confirmIndex = leadingActions.size + TEXT_ENTRY_CONFIRM_BUTTON
    fun buttonFocused(index: Int): Boolean = focus?.row == TextEntryRow.BUTTONS && focus.buttonIndex == index

    LaunchedEffect(text) {
        if (fieldValue.text != text) fieldValue = TextFieldValue(text, TextRange(text.length))
    }

    var keyboardDismissed by remember { mutableStateOf(false) }
    var keyboardTapped by remember { mutableStateOf(false) }
    var systemKeyboardActive by remember { mutableStateOf(false) }
    val keyboardRequest = focus?.keyboardRequest ?: 0
    var seenKeyboardRequest by remember { mutableIntStateOf(keyboardRequest) }
    val closeKeyboard = {
        keyboardDismissed = true
        keyboardTapped = false
    }
    val reopenKeyboard = {
        if (systemKeyboardActive) focusManager.clearFocus()
        keyboardDismissed = false
    }

    LaunchedEffect(fieldFocused) {
        if (!fieldFocused) {
            keyboardDismissed = false
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(keyboardRequest) {
        if (keyboardRequest != seenKeyboardRequest) {
            seenKeyboardRequest = keyboardRequest
            reopenKeyboard()
        }
    }

    ControllerKeyboardSession(
        open = (fieldFocused || keyboardTapped) && !keyboardDismissed,
        label = label,
        text = text,
        onTextChange = onTextChange,
        onSubmit = {
            closeKeyboard()
            submit()
        },
        onClose = closeKeyboard,
        submitLabel = confirmLabel,
        placeholder = placeholder,
        fieldType = keyboardFieldTypeOf(keyboardType, visualTransformation),
        maxLength = maxLength,
        error = errorMessage,
        canSubmit = confirmEnabled,
        enabled = enabled,
        onSystemKeyboard = {
            closeKeyboard()
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    )

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = title, onDismiss = onDismiss) {
        if (message != null) {
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
        }
        ControllerKeyboardTapTarget(
            onTap = {
                keyboardTapped = true
                reopenKeyboard()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
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
                enabled = enabled,
                visualTransformation = visualTransformation,
                shape = fieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        systemKeyboardActive = it.isFocused
                        if (it.isFocused) closeKeyboard()
                    }
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
        }
        val shownStatus = errorMessage ?: statusMessage
        if (shownStatus != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = shownStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (errorMessage != null) theme.destructive else theme.textDim
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            leadingActions.forEachIndexed { index, action ->
                ModalActionButton(
                    label = action.label,
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = buttonFocused(index),
                    onClick = action.onClick,
                    enabled = enabled
                )
            }
            ModalActionButton(
                label = cancelLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = buttonFocused(cancelIndex),
                onClick = onDismiss,
                enabled = enabled
            )
            ModalActionButton(
                label = confirmLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = buttonFocused(confirmIndex),
                onClick = submit,
                enabled = confirmEnabled
            )
        }
    }
}
