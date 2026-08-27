package com.nendo.argosy.libretro.ui.cheats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.primitives.ArgosyConfirmModal
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus

private const val VALUE_MIN = 0
private const val VALUE_MAX = 255

@Composable
fun SearchDialog(
    currentQuery: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
): InputHandler {
    val theme = LocalArgosyTheme.current
    var textValue by remember {
        mutableStateOf(TextFieldValue(currentQuery, TextRange(0, currentQuery.length)))
    }
    val hasClear = currentQuery.isNotEmpty()
    val lastButtonIndex = if (hasClear) 2 else 1
    var onButtons by remember { mutableStateOf(false) }
    var buttonIndex by remember { mutableIntStateOf(lastButtonIndex) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val currentText by rememberUpdatedState(textValue)
    val currentHasClear by rememberUpdatedState(hasClear)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSearch by rememberUpdatedState(onSearch)

    LaunchedEffect(onButtons) {
        if (onButtons) focusManager.clearFocus() else focusRequester.requestFocus()
    }

    val inputHandler = remember {
        object : InputHandler {
            private fun last(): Int = if (currentHasClear) 2 else 1

            private fun fireButton() {
                val cancelIndex = last() - 1
                when {
                    buttonIndex == last() -> currentOnSearch(currentText.text)
                    buttonIndex == cancelIndex -> currentOnDismiss()
                    else -> currentOnSearch("")
                }
            }

            override fun onUp(): InputResult {
                onButtons = false
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                onButtons = true
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (onButtons) buttonIndex = (buttonIndex - 1).coerceAtLeast(0)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (onButtons) buttonIndex = (buttonIndex + 1).coerceAtMost(last())
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                if (onButtons) fireButton() else onButtons = true
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
        }
    }

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = stringResource(R.string.ingame_cheats_dialog_search_title), onDismiss = onDismiss) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = {
                Text(stringResource(R.string.ingame_cheats_dialog_search_placeholder))
            },
            singleLine = true,
            shape = fieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (!onButtons) Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    else Modifier
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(textValue.text) })
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            if (hasClear) {
                ModalActionButton(
                    label = stringResource(R.string.ingame_cheats_dialog_search_clear),
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = onButtons && buttonIndex == 0,
                    onClick = { onSearch("") }
                )
            }
            ModalActionButton(
                label = stringResource(R.string.ingame_cheats_dialog_search_cancel),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = onButtons && buttonIndex == lastButtonIndex - 1,
                onClick = onDismiss
            )
            ModalActionButton(
                label = stringResource(R.string.ingame_cheats_dialog_search_confirm),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = onButtons && buttonIndex == lastButtonIndex,
                onClick = { onSearch(textValue.text) }
            )
        }
    }

    return inputHandler
}

private const val CHEAT_ROW_NAME = 0
private const val CHEAT_ROW_VALUE = 1

@Composable
fun CheatCreateDialog(
    address: Int,
    currentValue: Int,
    onDismiss: () -> Unit,
    onCreate: (name: String, value: Int) -> Unit
): InputHandler {
    val defaultName = "Custom Cheat @ 0x${address.toString(16).uppercase().padStart(6, '0')}"
    var nameValue by remember {
        mutableStateOf(TextFieldValue(defaultName, TextRange(0, defaultName.length)))
    }
    var valueText by remember { mutableStateOf(currentValue.toString()) }

    return CheatFormDialog(
        title = stringResource(R.string.ingame_cheats_dialog_create_title),
        addressLabel = stringResource(
            R.string.ingame_cheats_dialog_address,
            address.toString(16).uppercase().padStart(6, '0')
        ),
        nameValue = nameValue,
        onNameChange = { nameValue = it },
        valueText = valueText,
        onValueTextChange = { valueText = it },
        confirmLabel = stringResource(R.string.ingame_cheats_dialog_create_confirm),
        onConfirm = { name, value -> onCreate(name, value) },
        onDismiss = onDismiss,
        deleteHandling = null
    )
}

@Composable
fun CheatEditDialog(
    cheatId: Long,
    currentName: String,
    currentCode: String,
    onDismiss: () -> Unit,
    onSave: (name: String, code: String) -> Unit,
    onDelete: () -> Unit
): InputHandler {
    var nameValue by remember {
        mutableStateOf(TextFieldValue(currentName, TextRange(currentName.length)))
    }
    val addressPart = currentCode.substringBefore(':')
    val valuePart = currentCode.substringAfter(':', "")
    val currentValueInt = valuePart.toIntOrNull(16) ?: 0
    var valueText by remember { mutableStateOf(currentValueInt.toString()) }

    return CheatFormDialog(
        title = stringResource(R.string.ingame_cheats_dialog_edit_title),
        addressLabel = stringResource(R.string.ingame_cheats_dialog_address, addressPart),
        nameValue = nameValue,
        onNameChange = { nameValue = it },
        valueText = valueText,
        onValueTextChange = { valueText = it },
        confirmLabel = stringResource(R.string.ingame_cheats_dialog_edit_confirm),
        onConfirm = { name, value ->
            val newCode = "$addressPart:${value.toString(16).uppercase().padStart(2, '0')}"
            onSave(name, newCode)
        },
        onDismiss = onDismiss,
        deleteHandling = DeleteHandling(cheatName = currentName, onDelete = onDelete)
    )
}

private data class DeleteHandling(val cheatName: String, val onDelete: () -> Unit)

@Composable
private fun CheatFormDialog(
    title: String,
    addressLabel: String,
    nameValue: TextFieldValue,
    onNameChange: (TextFieldValue) -> Unit,
    valueText: String,
    onValueTextChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: (name: String, value: Int) -> Unit,
    onDismiss: () -> Unit,
    deleteHandling: DeleteHandling?
): InputHandler {
    val theme = LocalArgosyTheme.current
    val hasDelete = deleteHandling != null
    val deleteRow = if (hasDelete) 2 else -1
    val buttonRow = if (hasDelete) 3 else 2

    var focusRow by remember { mutableIntStateOf(buttonRow) }
    var buttonIndex by remember { mutableIntStateOf(1) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteConfirmFocus by remember { mutableIntStateOf(0) }
    val nameFocusRequester = remember { FocusRequester() }
    val valueFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val parsedValue = valueText.toIntOrNull()
    val isValueValid = parsedValue != null && parsedValue in VALUE_MIN..VALUE_MAX
    val canConfirm = nameValue.text.isNotBlank() && isValueValid

    val currentName by rememberUpdatedState(nameValue)
    val currentValueText by rememberUpdatedState(valueText)
    val currentCanConfirm by rememberUpdatedState(canConfirm)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnValueTextChange by rememberUpdatedState(onValueTextChange)
    val currentDelete by rememberUpdatedState(deleteHandling)

    LaunchedEffect(focusRow) {
        when (focusRow) {
            CHEAT_ROW_NAME -> nameFocusRequester.requestFocus()
            CHEAT_ROW_VALUE -> valueFocusRequester.requestFocus()
            else -> focusManager.clearFocus()
        }
    }

    val inputHandler = remember {
        object : InputHandler {
            private fun submit() {
                val value = currentValueText.toIntOrNull() ?: return
                if (currentCanConfirm) currentOnConfirm(currentName.text.trim(), value)
            }

            private fun stepValue(delta: Int) {
                val value = (currentValueText.toIntOrNull() ?: 0) + delta
                currentOnValueTextChange(value.coerceIn(VALUE_MIN, VALUE_MAX).toString())
            }

            override fun onUp(): InputResult {
                if (showDeleteConfirm) return InputResult.HANDLED
                focusRow = (focusRow - 1).coerceAtLeast(CHEAT_ROW_NAME)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (showDeleteConfirm) return InputResult.HANDLED
                focusRow = (focusRow + 1).coerceAtMost(buttonRow)
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                when {
                    showDeleteConfirm -> deleteConfirmFocus = 0
                    focusRow == CHEAT_ROW_VALUE -> stepValue(-1)
                    focusRow == buttonRow -> buttonIndex = 0
                }
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                when {
                    showDeleteConfirm -> deleteConfirmFocus = 1
                    focusRow == CHEAT_ROW_VALUE -> stepValue(1)
                    focusRow == buttonRow -> buttonIndex = 1
                }
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when {
                    showDeleteConfirm -> {
                        if (deleteConfirmFocus == 1) currentDelete?.onDelete?.invoke()
                        showDeleteConfirm = false
                    }
                    focusRow == CHEAT_ROW_NAME -> focusRow = CHEAT_ROW_VALUE
                    focusRow == CHEAT_ROW_VALUE -> focusRow = buttonRow
                    focusRow == deleteRow -> {
                        deleteConfirmFocus = 0
                        showDeleteConfirm = true
                    }
                    buttonIndex == 0 -> currentOnDismiss()
                    else -> submit()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (showDeleteConfirm) showDeleteConfirm = false else currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
        }
    }

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = title, onDismiss = onDismiss) {
        Text(
            text = addressLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        OutlinedTextField(
            value = nameValue,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.ingame_cheats_dialog_name_label)) },
            singleLine = true,
            shape = fieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocusRequester)
                .then(
                    if (focusRow == CHEAT_ROW_NAME) Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    else Modifier
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        OutlinedTextField(
            value = valueText,
            onValueChange = onValueTextChange,
            label = {
                Text(
                    stringResource(
                        R.string.ingame_cheats_dialog_value_label,
                        VALUE_MIN,
                        VALUE_MAX
                    )
                )
            },
            singleLine = true,
            shape = fieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(valueFocusRequester)
                .then(
                    if (focusRow == CHEAT_ROW_VALUE) Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    else Modifier
                ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (canConfirm && parsedValue != null) onConfirm(nameValue.text.trim(), parsedValue)
                }
            ),
            isError = valueText.isNotEmpty() && !isValueValid,
            supportingText = if (valueText.isNotEmpty() && !isValueValid) {
                {
                    Text(
                        stringResource(
                            R.string.ingame_cheats_dialog_value_error,
                            VALUE_MIN,
                            VALUE_MAX
                        )
                    )
                }
            } else null
        )
        if (deleteHandling != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            DestructiveRow(
                label = stringResource(R.string.ingame_cheats_dialog_delete_row),
                focused = focusRow == deleteRow,
                onClick = {
                    deleteConfirmFocus = 0
                    showDeleteConfirm = true
                }
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            ModalActionButton(
                label = stringResource(R.string.ingame_cheats_dialog_cancel),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == buttonRow && buttonIndex == 0,
                onClick = onDismiss
            )
            ModalActionButton(
                label = confirmLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == buttonRow && buttonIndex == 1,
                onClick = { if (canConfirm && parsedValue != null) onConfirm(nameValue.text.trim(), parsedValue) },
                enabled = canConfirm
            )
        }
    }

    if (deleteHandling != null) {
        ArgosyConfirmModal(
            visible = showDeleteConfirm,
            title = stringResource(R.string.ingame_cheats_dialog_delete_title),
            message = stringResource(
                R.string.ingame_cheats_dialog_delete_message,
                deleteHandling.cheatName
            ),
            confirmLabel = stringResource(R.string.ingame_cheats_dialog_delete_confirm),
            destructive = true,
            onConfirm = {
                deleteHandling.onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
            focusedIndex = deleteConfirmFocus
        )
    }

    return inputHandler
}

@Composable
private fun DestructiveRow(
    label: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    val danger = ColorTokens.Domain.difficulty
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) danger.copy(alpha = 0.18f) else Color.Transparent)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = danger
        )
    }
}
