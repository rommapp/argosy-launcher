package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun RomMConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val urlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.server.rommFocusField) {
        when (uiState.server.rommFocusField) {
            0 -> urlFocusRequester.requestFocus()
            1 -> usernameFocusRequester.requestFocus()
            2 -> passwordFocusRequester.requestFocus()
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
                        if (uiState.focusedIndex == 1)
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
                        if (uiState.focusedIndex == 2)
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                        else Modifier
                    )
            )
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = Modifier.fillMaxWidth()
        ) {
            ActionPreference(
                title = if (uiState.server.rommConnecting) "Connecting..." else "Connect",
                subtitle = "Connect to RomM server",
                isFocused = uiState.focusedIndex == 3,
                onClick = { viewModel.connectToRomm() }
            )
        }

        ActionPreference(
            title = "Cancel",
            subtitle = "Return to Server settings",
            isFocused = uiState.focusedIndex == 4,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}
