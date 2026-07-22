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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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

private fun authMethodLabel(method: RomMAuthMethod): String = when (method) {
    RomMAuthMethod.DEVICE -> "Device Pairing"
    RomMAuthMethod.PAIRING_CODE -> "Pairing Code"
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
            label = { Text("Server URL") },
            placeholder = { Text("https://romm.example.com") },
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
            title = "Auth Method",
            value = authMethodLabel(authMethod),
            isFocused = uiState.focusedIndex == 1,
            onClick = { viewModel.setRommAuthMethod(cycleAuthMethod(authMethod, 1)) },
            onPrev = { viewModel.setRommAuthMethod(cycleAuthMethod(authMethod, -1)) },
            options = remember { RomMAuthMethod.entries.map { authMethodLabel(it) } },
            onSelect = { viewModel.setRommAuthMethod(RomMAuthMethod.entries[it]) },
            pickerRequestToken = if (uiState.enumPickerKey == ROMM_AUTH_METHOD_PICKER_KEY) uiState.enumPickerToken else 0
        )

        when {
            isDevice -> Text(
                text = "Pair this device by scanning a QR code with your phone, then approve it in RomM. Requires RomM 5.0 or newer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
            isPairingCode -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create an API token in the RomM web UI, then click Pair Device to get a code.",
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
                uiState.server.rommConnecting && isDevice -> "Generating code..."
                uiState.server.rommConnecting -> "Connecting..."
                isDevice -> "Pair Device"
                else -> "Connect"
            },
            subtitle = if (isDevice) "Generate a pairing QR code" else "Connect to RomM server",
            isFocused = uiState.focusedIndex == buttonIndex,
            onClick = { viewModel.connectToRomm() }
        )
        buttonIndex++

        if (hasCamera && isPairingCode) {
            ActionPreference(
                title = "Scan QR Code",
                subtitle = "Scan pairing QR from the RomM web UI",
                isFocused = uiState.focusedIndex == buttonIndex,
                onClick = { viewModel.showRommScanner() }
            )
            buttonIndex++
        }

        ActionPreference(
            title = "Cancel",
            subtitle = "Return to Server settings",
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
            text = "Scan to pair",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Scan this code with your phone, then approve this device in RomM.",
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
            title = "Cancel",
            subtitle = "Stop pairing",
            isFocused = true,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}
