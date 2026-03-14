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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
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

    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val urlFocusRequester = remember { FocusRequester() }
    val pairingCodeFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val authMethod = uiState.server.rommAuthMethod
    val isPairingCode = authMethod == RomMAuthMethod.PAIRING_CODE
    val hasCamera = uiState.server.rommHasCamera

    LaunchedEffect(uiState.server.rommFocusField) {
        when (uiState.server.rommFocusField) {
            0 -> urlFocusRequester.requestFocus()
            2 -> if (isPairingCode) pairingCodeFocusRequester.requestFocus()
                 else usernameFocusRequester.requestFocus()
            3 -> if (!isPairingCode) passwordFocusRequester.requestFocus()
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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(urlFocusRequester)
                .then(
                    if (uiState.focusedIndex == 0)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        CyclePreference(
            title = "Auth Method",
            value = if (isPairingCode) "Pairing Code" else "Password",
            isFocused = uiState.focusedIndex == 1,
            onClick = {
                val next = if (isPairingCode) RomMAuthMethod.PASSWORD else RomMAuthMethod.PAIRING_CODE
                viewModel.setRommAuthMethod(next)
            }
        )

        if (isPairingCode) {
            Column(
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
        } else {
            Row(
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

        var buttonIndex = if (isPairingCode) 3 else 4

        ActionPreference(
            title = if (uiState.server.rommConnecting) "Connecting..." else "Connect",
            subtitle = "Connect to RomM server",
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
