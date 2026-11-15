package com.nendo.argosy.ui.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.common.ClientCertImportStatus
import com.nendo.argosy.ui.common.ClientCertPasswordEntry
import com.nendo.argosy.ui.common.errorRes
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import kotlinx.coroutines.CoroutineScope

/**
 * Password entry for a PKCS#12 file. Pure visuals: the host routes gamepad input and owns
 * [entry].
 */
@Composable
fun ClientCertPasswordModal(
    entry: ClientCertPasswordEntry,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val importing = entry.status == ClientCertImportStatus.IMPORTING
    TextEntryModal(
        title = stringResource(R.string.ui_client_cert_password_title),
        label = stringResource(R.string.ui_client_cert_password_label),
        confirmLabel = stringResource(
            if (importing) R.string.ui_client_cert_password_import_busy else R.string.ui_client_cert_password_import
        ),
        cancelLabel = stringResource(R.string.ui_client_cert_password_cancel),
        text = entry.draft,
        onTextChange = onDraftChange,
        onDismiss = onDismiss,
        onSubmit = onSubmit,
        focus = TextEntryFocus(row = entry.row, buttonIndex = entry.buttonIndex, keyboardRequest = entry.keyboardRequest),
        message = stringResource(R.string.ui_client_cert_password_message, entry.fileName),
        errorMessage = entry.status.errorRes?.let { stringResource(it) },
        statusMessage = if (importing) stringResource(R.string.ui_client_cert_password_status_importing) else null,
        canSubmit = true,
        enabled = !importing,
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation()
    )
}

class ClientCertPasswordInputHandler(
    private val delegate: ClientCertDelegate,
    private val scope: CoroutineScope
) : InputHandler {
    override fun onUp(): InputResult {
        delegate.movePasswordRow(TextEntryRow.FIELD)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        delegate.movePasswordRow(TextEntryRow.BUTTONS)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        delegate.movePasswordButton(-1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        delegate.movePasswordButton(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        delegate.confirmPasswordEntry(scope)
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        delegate.closePasswordEntry()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onMenu() = InputResult.HANDLED
    override fun onSelect() = InputResult.HANDLED
    override fun onSecondaryAction() = InputResult.HANDLED
    override fun onContextMenu() = InputResult.HANDLED
    override fun onPrevSection() = InputResult.HANDLED
    override fun onNextSection() = InputResult.HANDLED
    override fun onPrevTrigger() = InputResult.HANDLED
    override fun onNextTrigger() = InputResult.HANDLED
    override fun onLeftStickClick() = InputResult.HANDLED
    override fun onRightStickClick() = InputResult.HANDLED
    override fun onLongConfirm() = InputResult.HANDLED
}
