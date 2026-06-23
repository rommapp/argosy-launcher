package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.QrCodeWithOverlay
import com.nendo.argosy.ui.components.QrScannerWithPermission
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

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
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val authMethod = uiState.server.rommAuthMethod
    val isDevice = authMethod == RomMAuthMethod.DEVICE
    val isPairingCode = authMethod == RomMAuthMethod.PAIRING_CODE
    val isPassword = authMethod == RomMAuthMethod.PASSWORD
    val hasCamera = uiState.server.rommHasCamera

    LaunchedEffect(uiState.server.rommFocusField) {
        when (uiState.server.rommFocusField) {
            0 -> urlFocusRequester.requestFocus()
            2 -> if (isPairingCode) pairingCodeFocusRequester.requestFocus()
                 else if (isPassword) usernameFocusRequester.requestFocus()
            3 -> if (isPassword) passwordFocusRequester.requestFocus()
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
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        CyclePreference(
            title = "Auth Method",
            value = when (authMethod) {
                RomMAuthMethod.DEVICE -> "Device Pairing"
                RomMAuthMethod.PAIRING_CODE -> "Pairing Code"
                RomMAuthMethod.PASSWORD -> "Password"
            },
            isFocused = uiState.focusedIndex == 1,
            onClick = {
                val next = when (authMethod) {
                    RomMAuthMethod.DEVICE -> RomMAuthMethod.PAIRING_CODE
                    RomMAuthMethod.PAIRING_CODE -> RomMAuthMethod.PASSWORD
                    RomMAuthMethod.PASSWORD -> RomMAuthMethod.DEVICE
                }
                viewModel.setRommAuthMethod(next)
            }
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
            isPassword -> Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.server.rommConfigUsername,
                    onValueChange = { viewModel.setRommConfigUsername(it) },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = inputShape,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(usernameFocusRequester)
                        .then(
                            if (uiState.focusedIndex == 2)
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                            else Modifier
                        )
                )

                OutlinedTextField(
                    value = uiState.server.rommConfigPassword,
                    onValueChange = { viewModel.setRommConfigPassword(it) },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = inputShape,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(passwordFocusRequester)
                        .then(
                            if (uiState.focusedIndex == 3)
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                            else Modifier
                        )
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
            RomMAuthMethod.PASSWORD -> 4
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
