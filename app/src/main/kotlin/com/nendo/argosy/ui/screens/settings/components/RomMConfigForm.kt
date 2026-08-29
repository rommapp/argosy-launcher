package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.QrCodeWithOverlay
import com.nendo.argosy.ui.components.QrScannerWithPermission
import com.nendo.argosy.ui.screens.settings.ROMM_AUTH_METHOD_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
private fun authMethodLabel(method: RomMAuthMethod): String = when (method) {
    RomMAuthMethod.DEVICE -> stringResource(R.string.settings_romm_config_auth_method_device)
    RomMAuthMethod.PAIRING_CODE -> stringResource(R.string.settings_romm_config_auth_method_pairing_code)
}

private fun cycleAuthMethod(current: RomMAuthMethod, direction: Int): RomMAuthMethod {
    val methods = RomMAuthMethod.entries
    return methods[(methods.indexOf(current) + direction).mod(methods.size)]
}

@Composable
fun RomMConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.server.rommShowScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScannerWithPermission(
                onResult = { result ->
                    viewModel.handleRommScanResult(result.origin, result.code)
                },
                onDismiss = { viewModel.dismissRommScanner() }
            )
        }
        return
    }

    if (uiState.server.rommDevicePairing) {
        DevicePairingScreen(uiState, viewModel)
        return
    }

    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    var wasUrlFocused by remember { mutableStateOf(false) }
    val urlFocusRequester = remember { FocusRequester() }
    val pairingCodeFocusRequester = remember { FocusRequester() }

    val authMethod = uiState.server.rommAuthMethod
    val isDevice = authMethod == RomMAuthMethod.DEVICE
    val isPairingCode = authMethod == RomMAuthMethod.PAIRING_CODE
    val hasCamera = uiState.server.rommHasCamera

    LaunchedEffect(uiState.server.rommFocusField) {
        when (uiState.server.rommFocusField) {
            0 -> urlFocusRequester.requestFocus()
            2 -> if (isPairingCode) pairingCodeFocusRequester.requestFocus()
        }
        if (uiState.server.rommFocusField != null) {
            viewModel.clearRommFocusField()
        }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        OutlinedTextField(
            value = uiState.server.rommConfigUrl,
            onValueChange = { viewModel.setRommConfigUrl(it) },
            label = { Text(stringResource(R.string.settings_romm_config_server_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_romm_config_server_url_placeholder)) },
            singleLine = true,
            shape = inputShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (!uiState.server.rommConnecting && uiState.server.rommConfigUrl.isNotBlank()) {
                        keyboard?.hide()
                        viewModel.commitRommUrl()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(urlFocusRequester)
                .onFocusChanged { fs ->
                    if (wasUrlFocused && !fs.isFocused && uiState.server.rommConfigUrl.isNotBlank()) {
                        viewModel.commitRommUrl()
                    }
                    wasUrlFocused = fs.isFocused
                }
                .then(
                    if (uiState.focusedIndex == 0)
                        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                    else Modifier
                )
        )

        CyclePreference(
            title = stringResource(R.string.settings_romm_config_auth_method_title),
            value = authMethodLabel(authMethod),
            isFocused = uiState.focusedIndex == 1,
            onClick = { viewModel.setRommAuthMethod(cycleAuthMethod(authMethod, 1)) },
            onPrev = { viewModel.setRommAuthMethod(cycleAuthMethod(authMethod, -1)) },
            options = RomMAuthMethod.entries.map { authMethodLabel(it) },
            onSelect = { viewModel.setRommAuthMethod(RomMAuthMethod.entries[it]) },
            pickerRequestToken = if (uiState.enumPickerKey == ROMM_AUTH_METHOD_PICKER_KEY) uiState.enumPickerToken else 0
        )

        when {
            isDevice -> Text(
                text = stringResource(R.string.settings_romm_config_device_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
            isPairingCode -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_romm_config_pairing_code_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                PairingCodeInput(
                    code = uiState.server.rommConfigPairingCode,
                    onCodeChange = { viewModel.setRommConfigPairingCode(it) },
                    isFocused = uiState.focusedIndex == 2,
                    focusRequester = pairingCodeFocusRequester
                )
            }
        }

        if (uiState.server.rommConfigError != null) {
            Text(
                text = uiState.server.rommConfigError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        var buttonIndex = when (authMethod) {
            RomMAuthMethod.DEVICE -> 2
            RomMAuthMethod.PAIRING_CODE -> 3
        }

        ActionPreference(
            title = when {
                uiState.server.rommConnecting && isDevice -> stringResource(R.string.settings_romm_config_connect_generating_title)
                uiState.server.rommConnecting -> stringResource(R.string.settings_romm_config_connect_connecting_title)
                isDevice -> stringResource(R.string.settings_romm_config_connect_pair_title)
                else -> stringResource(R.string.settings_romm_config_connect_title)
            },
            subtitle = if (isDevice) {
                stringResource(R.string.settings_romm_config_connect_device_subtitle)
            } else {
                stringResource(R.string.settings_romm_config_connect_subtitle)
            },
            isFocused = uiState.focusedIndex == buttonIndex,
            onClick = { viewModel.connectToRomm() }
        )
        buttonIndex++

        if (hasCamera && isPairingCode) {
            ActionPreference(
                title = stringResource(R.string.settings_romm_config_scan_title),
                subtitle = stringResource(R.string.settings_romm_config_scan_subtitle),
                isFocused = uiState.focusedIndex == buttonIndex,
                onClick = { viewModel.showRommScanner() }
            )
            buttonIndex++
        }

        ActionPreference(
            title = stringResource(R.string.settings_romm_config_certificate_title),
            subtitle = if (uiState.server.importedCertCount > 0) {
                pluralStringResource(
                    R.plurals.settings_romm_config_certificate_subtitle_count,
                    uiState.server.importedCertCount,
                    uiState.server.importedCertCount
                )
            } else {
                stringResource(R.string.settings_romm_config_certificate_subtitle)
            },
            isFocused = uiState.focusedIndex == buttonIndex,
            onClick = { viewModel.requestCertificatePicker() }
        )
        buttonIndex++

        ActionPreference(
            title = stringResource(R.string.settings_romm_config_cancel_title),
            subtitle = stringResource(R.string.settings_romm_config_cancel_subtitle),
            isFocused = uiState.focusedIndex == buttonIndex,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}

@Composable
private fun DevicePairingScreen(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val server = uiState.server
    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_romm_device_pairing_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.settings_romm_device_pairing_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        server.rommDeviceVerificationUrl?.let { url ->
            QrCodeWithOverlay(data = url, size = 220.dp)
        }

        server.rommDeviceUserCode?.let { code ->
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
            )
        }

        server.rommDeviceVerificationUrl?.let { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (server.rommConfigError != null) {
            Text(
                text = server.rommConfigError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = stringResource(R.string.settings_romm_device_pairing_cancel_title),
            subtitle = stringResource(R.string.settings_romm_device_pairing_cancel_subtitle),
            isFocused = true,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}
