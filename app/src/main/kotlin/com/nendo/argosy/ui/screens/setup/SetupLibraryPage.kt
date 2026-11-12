package com.nendo.argosy.ui.screens.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.QrCodeWithOverlay
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.KeyboardFieldType
import com.nendo.argosy.ui.primitives.ArgosyIndeterminateProgressBar
import com.nendo.argosy.ui.screens.settings.components.PAIRING_CODE_LENGTH
import com.nendo.argosy.ui.screens.settings.components.PairingCodeInput
import com.nendo.argosy.ui.screens.settings.components.pairingCodeFilter
import com.nendo.argosy.ui.screens.setup.components.SetupChoiceRow
import com.nendo.argosy.ui.screens.setup.components.SetupErrorLine
import com.nendo.argosy.ui.screens.setup.components.SetupFieldRow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.MotionTokens

@Composable
internal fun setupErrorText(error: SetupError?): String? {
    if (error == null) return null
    val textRes = error.textRes ?: return error.serverMessage
    val arg = error.arg
    return if (arg != null) stringResource(textRes, arg) else stringResource(textRes)
}

@Composable
internal fun LibraryPage(state: SetupUiState, viewModel: SetupViewModel) {
    val view = state.signIn.view
    SetupPageBody(
        state = state,
        viewModel = viewModel,
        context = if (view == LibraryView.CHOICE) ({ RommContext() }) else null,
    ) {
        val reduced = LocalMotionTier.current == MotionTier.Reduced
        val ms = if (reduced) MotionTokens.Tween.fastMs else Motion.durationContent
        AnimatedContent(
            targetState = view,
            transitionSpec = { fadeIn(tween(ms)) togetherWith fadeOut(tween(ms)) },
            label = "setup-library-view",
        ) { shown ->
            val viewState = state.copy(signIn = state.signIn.copy(view = shown))
            when (shown) {
                LibraryView.CHOICE -> ChoiceView(viewState, viewModel)
                LibraryView.ADDRESS -> AddressView(viewState, viewModel)
                LibraryView.APPROVE -> ApproveView(viewState)
                LibraryView.PAIRING -> PairingView(viewState, viewModel)
                LibraryView.SIGNED_IN -> SignedInView(viewState)
            }
        }
    }
}

@Composable
private fun RommContext() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(stringResource(R.string.setup_library_context_downloads))
        Text(stringResource(R.string.setup_library_context_saves))
        Text(stringResource(R.string.setup_library_context_play_time))
    }
}

@Composable
private fun ChoiceView(state: SetupUiState, viewModel: SetupViewModel) {
    val focus = focusedTarget(state)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.listGap)) {
        SetupChoiceRow(
            icon = Icons.Default.CloudSync,
            title = stringResource(R.string.setup_library_romm_row_title),
            subtitle = stringResource(R.string.setup_library_romm_row_subtitle),
            selected = true,
            focused = focus == SetupTarget.RommSource,
            onClick = { viewModel.tap(SetupTarget.RommSource) },
        )
        SetupChoiceRow(
            icon = Icons.Default.PhoneAndroid,
            title = stringResource(R.string.setup_library_local_row_title),
            subtitle = stringResource(R.string.setup_library_local_row_unavailable),
            selected = false,
            focused = false,
            enabled = false,
            onClick = {},
        )
    }
}

@Composable
private fun AddressView(state: SetupUiState, viewModel: SetupViewModel) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SetupFieldRow(
            label = stringResource(R.string.setup_library_address_field_label),
            value = state.signIn.url,
            placeholder = stringResource(R.string.setup_library_address_placeholder),
            focused = focusedTarget(state) == SetupTarget.ServerField,
            onOpen = { viewModel.tap(SetupTarget.ServerField) },
        )
        setupErrorText(state.signIn.error)?.let { SetupErrorLine(it, Modifier.padding(horizontal = Dimens.spacingMd)) }
        Text(
            text = stringResource(R.string.setup_library_address_hint),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
            modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        )
    }
}

@Composable
private fun ApproveView(state: SetupUiState) {
    val theme = LocalArgosyTheme.current
    val signIn = state.signIn
    val qrSize = (ComponentDefaults.Setup.qrSizeDp * LocalUiScale.current.scale).dp
    val qrDescription = stringResource(R.string.setup_library_approve_qr_desc)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        signIn.deviceVerificationUrl?.let { url ->
            QrCodeWithOverlay(
                data = url,
                size = qrSize,
                modifier = Modifier.semantics { contentDescription = qrDescription },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            Text(
                text = stringResource(R.string.setup_library_approve_code_label),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
            )
            signIn.deviceUserCode?.let { code ->
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = theme.textPrimary,
                )
            }
            signIn.deviceVerificationUrl?.let { url ->
                Text(text = url, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            }
            ArgosyIndeterminateProgressBar(modifier = Modifier.width(qrSize))
            Text(
                text = stringResource(R.string.setup_library_approve_status),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
            )
        }
    }
}

@Composable
private fun PairingView(state: SetupUiState, viewModel: SetupViewModel) {
    val requester = remember { FocusRequester() }
    Column(modifier = Modifier.fillMaxWidth()) {
        PairingCodeInput(
            code = state.signIn.pairingCode,
            onCodeChange = viewModel::setPairingCode,
            isFocused = focusedTarget(state) == SetupTarget.PairingField,
            focusRequester = requester,
            onTap = { viewModel.tap(SetupTarget.PairingField) },
            onImeAction = { viewModel.submitPairingCode() },
        )
        setupErrorText(state.signIn.error)?.let { SetupErrorLine(it) }
        Text(
            text = state.signIn.serverName,
            style = MaterialTheme.typography.bodySmall,
            color = LocalArgosyTheme.current.textDim,
            modifier = Modifier.padding(top = Dimens.spacingSm),
        )
    }
}

@Composable
private fun SignedInView(state: SetupUiState) {
    val theme = LocalArgosyTheme.current
    val signIn = state.signIn
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Text(
            text = stringResource(R.string.setup_library_signed_in_server, signIn.serverName),
            style = MaterialTheme.typography.bodyLarge,
            color = theme.textPrimary,
        )
        Text(
            text = stringResource(
                R.string.firstrun_romm_success_library,
                pluralStringResource(R.plurals.firstrun_romm_success_game_count, signIn.gameCount, signIn.gameCount),
                pluralStringResource(R.plurals.firstrun_romm_success_platform_count, signIn.platformCount, signIn.platformCount)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
        )
        setupErrorText(signIn.error)?.let { SetupErrorLine(it) }
    }
}

@Composable
internal fun SetupKeyboards(state: SetupUiState, viewModel: SetupViewModel) {
    val signIn = state.signIn
    val error = setupErrorText(signIn.error)
    ControllerKeyboardSession(
        open = state.page == SetupPage.LIBRARY && signIn.view == LibraryView.ADDRESS &&
            state.openField == SetupField.SERVER_ADDRESS,
        label = stringResource(R.string.setup_library_address_field_label),
        text = signIn.url,
        onTextChange = viewModel::setServerAddress,
        onSubmit = viewModel::submitServerAddress,
        onClose = viewModel::closeField,
        submitLabel = stringResource(R.string.firstrun_romm_url_keyboard_submit),
        placeholder = stringResource(R.string.setup_library_address_placeholder),
        fieldType = KeyboardFieldType.URI,
        error = error,
        canSubmit = !signIn.isConnecting && signIn.url.isNotBlank()
    )
    ControllerKeyboardSession(
        open = state.page == SetupPage.LIBRARY && signIn.view == LibraryView.PAIRING &&
            state.openField == SetupField.PAIRING_CODE,
        label = stringResource(R.string.firstrun_pairing_code_title),
        text = signIn.pairingCode,
        onTextChange = { raw -> pairingCodeFilter(raw)?.let(viewModel::setPairingCode) },
        onSubmit = viewModel::submitPairingCode,
        onClose = viewModel::closeField,
        submitLabel = stringResource(R.string.firstrun_pairing_code_keyboard_submit),
        maxLength = PAIRING_CODE_LENGTH,
        error = error,
        canSubmit = !signIn.isConnecting
    )
}
