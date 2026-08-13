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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.remember
import com.nendo.argosy.ui.components.ActionPreference
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
    val urlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(jellyfin.configFocusField) {
        if (jellyfin.configFocusField == JELLYFIN_CONFIG_URL_INDEX) {
            urlFocusRequester.requestFocus()
            viewModel.clearJellyfinConfigFocusField()
        }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        OutlinedTextField(
            value = jellyfin.configUrl,
            onValueChange = { viewModel.setJellyfinConfigUrl(it) },
            label = { Text("Server Address") },
            placeholder = { Text("https://jellyfin.example.com") },
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
                .focusRequester(urlFocusRequester)
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
            title = "Save",
            subtitle = "Use this address for your media library",
            isFocused = uiState.focusedIndex == JELLYFIN_CONFIG_SAVE_INDEX,
            onClick = { viewModel.commitJellyfinConfig() }
        )

        ActionPreference(
            title = "Cancel",
            subtitle = "Return to Jellyfin settings",
            isFocused = uiState.focusedIndex == JELLYFIN_CONFIG_CANCEL_INDEX,
            onClick = { viewModel.cancelJellyfinConfig() }
        )
    }
}
