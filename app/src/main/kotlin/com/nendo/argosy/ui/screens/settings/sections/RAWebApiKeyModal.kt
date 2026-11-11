package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.TextEntryAction
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyAction
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyEditor
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyStatus

/**
 * Entry modal for the RetroAchievements web API key. Pure visuals: gamepad input reaches it
 * through the settings modal router, touch through its own buttons.
 */
@Composable
fun RAWebApiKeyModal(
    editor: RAWebApiKeyEditor,
    onDraftChange: (String) -> Unit,
    onAction: (RAWebApiKeyAction) -> Unit,
    onDismiss: () -> Unit
) {
    val validating = editor.status == RAWebApiKeyStatus.VALIDATING
    val status = statusMessageRes(editor.status)?.let { stringResource(it) }
    val leadingActions = editor.actions
        .filter { it != RAWebApiKeyAction.CANCEL && it != RAWebApiKeyAction.SAVE }
        .map { action -> TextEntryAction(stringResource(labelRes(action, validating))) { onAction(action) } }

    TextEntryModal(
        title = stringResource(R.string.settings_ra_web_api_key_modal_title),
        label = stringResource(R.string.settings_ra_web_api_key_field_label),
        confirmLabel = stringResource(labelRes(RAWebApiKeyAction.SAVE, validating)),
        cancelLabel = stringResource(labelRes(RAWebApiKeyAction.CANCEL, validating)),
        text = editor.draft,
        onTextChange = onDraftChange,
        onDismiss = onDismiss,
        onSubmit = { onAction(RAWebApiKeyAction.SAVE) },
        focus = TextEntryFocus(
            row = editor.row,
            buttonIndex = editor.buttonIndex,
            keyboardRequest = editor.keyboardRequest
        ),
        message = stringResource(R.string.settings_ra_web_api_key_modal_message),
        errorMessage = status.takeUnless { validating },
        statusMessage = status.takeIf { validating },
        canSubmit = editor.draft.isNotBlank(),
        enabled = !validating,
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
        leadingActions = leadingActions
    )
}

@StringRes
private fun labelRes(action: RAWebApiKeyAction, validating: Boolean): Int = when (action) {
    RAWebApiKeyAction.PASTE -> R.string.settings_ra_web_api_key_paste
    RAWebApiKeyAction.CLEAR -> R.string.settings_ra_web_api_key_clear
    RAWebApiKeyAction.CANCEL -> R.string.settings_ra_web_api_key_cancel
    RAWebApiKeyAction.SAVE -> if (validating) R.string.settings_ra_web_api_key_save_busy else R.string.settings_ra_web_api_key_save
}

@StringRes
private fun statusMessageRes(status: RAWebApiKeyStatus): Int? = when (status) {
    RAWebApiKeyStatus.IDLE -> null
    RAWebApiKeyStatus.VALIDATING -> R.string.settings_ra_web_api_key_status_validating
    RAWebApiKeyStatus.REJECTED -> R.string.settings_ra_web_api_key_status_rejected
    RAWebApiKeyStatus.UNREACHABLE -> R.string.settings_ra_web_api_key_status_unreachable
    RAWebApiKeyStatus.CLIPBOARD_EMPTY -> R.string.settings_ra_web_api_key_status_clipboard_empty
}
