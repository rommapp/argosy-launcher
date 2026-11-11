package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.remember
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import com.nendo.argosy.ui.components.keyboard.KeyboardFieldType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal const val JELLYFIN_CONFIG_URL_INDEX = 0
internal const val JELLYFIN_CONFIG_SAVE_INDEX = 1
internal const val JELLYFIN_CONFIG_CANCEL_INDEX = 2

@Composable
fun JellyfinConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val jellyfin = uiState.jellyfin
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    var keyboardOpen by remember { mutableStateOf(false) }

    LaunchedEffect(jellyfin.configFocusField) {
        if (jellyfin.configFocusField == JELLYFIN_CONFIG_URL_INDEX) {
            keyboardOpen = true
            viewModel.clearJellyfinConfigFocusField()
        }
    }

    ControllerKeyboardSession(
        open = keyboardOpen,
        label = stringResource(R.string.settings_jellyfin_config_server_address_label),
        text = jellyfin.configUrl,
        onTextChange = { viewModel.setJellyfinConfigUrl(it) },
        onSubmit = {
            keyboardOpen = false
            viewModel.commitJellyfinConfig()
        },
        onClose = { keyboardOpen = false },
        submitLabel = stringResource(R.string.settings_jellyfin_config_keyboard_submit),
        placeholder = stringResource(R.string.settings_jellyfin_config_server_address_placeholder),
        fieldType = KeyboardFieldType.URI
    )

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        ControllerKeyboardTapTarget(
            onTap = {
                viewModel.setFocusIndex(JELLYFIN_CONFIG_URL_INDEX)
                viewModel.setJellyfinConfigFocusField(JELLYFIN_CONFIG_URL_INDEX)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = jellyfin.configUrl,
                onValueChange = { viewModel.setJellyfinConfigUrl(it) },
                label = { Text(stringResource(R.string.settings_jellyfin_config_server_address_label)) },
                placeholder = { Text(stringResource(R.string.settings_jellyfin_config_server_address_placeholder)) },
                singleLine = true,
                shape = inputShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboard?.hide()
                        viewModel.commitJellyfinConfig()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardOpen = false }
                    .then(
                        if (uiState.focusedIndex == JELLYFIN_CONFIG_URL_INDEX) {
                            Modifier.background(
                                LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f),
                                inputShape
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }

        if (jellyfin.configError != null) {
            Text(
                text = jellyfin.configError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = stringResource(R.string.settings_jellyfin_config_save_title),
            subtitle = stringResource(R.string.settings_jellyfin_config_save_subtitle),
            isFocused = uiState.focusedIndex == JELLYFIN_CONFIG_SAVE_INDEX,
            onClick = { viewModel.commitJellyfinConfig() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_jellyfin_config_cancel_title),
            subtitle = stringResource(R.string.settings_jellyfin_config_cancel_subtitle),
            isFocused = uiState.focusedIndex == JELLYFIN_CONFIG_CANCEL_INDEX,
            onClick = { viewModel.cancelJellyfinConfig() }
        )
    }
}
