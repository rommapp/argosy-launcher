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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import com.nendo.argosy.ui.components.keyboard.KeyboardFieldType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal const val NAVIDROME_CONFIG_URL_INDEX = 0
internal const val NAVIDROME_CONFIG_SAVE_INDEX = 1
internal const val NAVIDROME_CONFIG_CANCEL_INDEX = 2

internal const val NAVIDROME_LOGIN_USERNAME_INDEX = 0
internal const val NAVIDROME_LOGIN_PASSWORD_INDEX = 1
internal const val NAVIDROME_LOGIN_SUBMIT_INDEX = 2
internal const val NAVIDROME_LOGIN_CANCEL_INDEX = 3

@Composable
private fun fieldFocusModifier(focused: Boolean, shape: RoundedCornerShape): Modifier =
    if (focused) {
        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), shape)
    } else {
        Modifier
    }

@Composable
fun NavidromeConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val navidrome = uiState.navidrome
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    var keyboardOpen by remember { mutableStateOf(false) }

    LaunchedEffect(navidrome.configFocusField) {
        if (navidrome.configFocusField == NAVIDROME_CONFIG_URL_INDEX) {
            keyboardOpen = true
            viewModel.clearNavidromeConfigFocusField()
        }
    }

    ControllerKeyboardSession(
        open = keyboardOpen,
        label = stringResource(R.string.settings_navidrome_config_server_address_label),
        text = navidrome.configUrl,
        onTextChange = { viewModel.setNavidromeConfigUrl(it) },
        onSubmit = {
            keyboardOpen = false
            viewModel.commitNavidromeConfig()
        },
        onClose = { keyboardOpen = false },
        submitLabel = stringResource(R.string.settings_navidrome_config_keyboard_submit),
        placeholder = stringResource(R.string.settings_navidrome_config_server_address_placeholder),
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
                viewModel.setFocusIndex(NAVIDROME_CONFIG_URL_INDEX)
                viewModel.setNavidromeConfigFocusField(NAVIDROME_CONFIG_URL_INDEX)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = navidrome.configUrl,
                onValueChange = { viewModel.setNavidromeConfigUrl(it) },
                label = { Text(stringResource(R.string.settings_navidrome_config_server_address_label)) },
                placeholder = { Text(stringResource(R.string.settings_navidrome_config_server_address_placeholder)) },
                singleLine = true,
                shape = inputShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboard?.hide()
                        viewModel.commitNavidromeConfig()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardOpen = false }
                    .then(fieldFocusModifier(uiState.focusedIndex == NAVIDROME_CONFIG_URL_INDEX, inputShape))
            )
        }

        if (navidrome.configError != null) {
            Text(
                text = navidrome.configError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = stringResource(R.string.settings_navidrome_config_save_title),
            subtitle = stringResource(R.string.settings_navidrome_config_save_subtitle),
            isFocused = uiState.focusedIndex == NAVIDROME_CONFIG_SAVE_INDEX,
            onClick = { viewModel.commitNavidromeConfig() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_navidrome_config_cancel_title),
            subtitle = stringResource(R.string.settings_navidrome_config_cancel_subtitle),
            isFocused = uiState.focusedIndex == NAVIDROME_CONFIG_CANCEL_INDEX,
            onClick = { viewModel.cancelNavidromeConfig() }
        )
    }
}

@Composable
fun NavidromeSignInForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val navidrome = uiState.navidrome
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    var keyboardField by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(navidrome.loginFocusField) {
        when (navidrome.loginFocusField) {
            NAVIDROME_LOGIN_USERNAME_INDEX, NAVIDROME_LOGIN_PASSWORD_INDEX -> keyboardField = navidrome.loginFocusField
        }
        if (navidrome.loginFocusField != null) {
            viewModel.clearNavidromeLoginFocusField()
        }
    }

    ControllerKeyboardSession(
        open = keyboardField == NAVIDROME_LOGIN_USERNAME_INDEX,
        label = stringResource(R.string.settings_navidrome_signin_username_label),
        text = navidrome.loginUsername,
        onTextChange = { viewModel.setNavidromeLoginUsername(it) },
        onSubmit = { keyboardField = null },
        onClose = { keyboardField = null },
        submitLabel = stringResource(R.string.settings_navidrome_signin_username_keyboard_submit)
    )
    ControllerKeyboardSession(
        open = keyboardField == NAVIDROME_LOGIN_PASSWORD_INDEX,
        label = stringResource(R.string.settings_navidrome_signin_password_label),
        text = navidrome.loginPassword,
        onTextChange = { viewModel.setNavidromeLoginPassword(it) },
        onSubmit = {
            keyboardField = null
            viewModel.submitNavidromeSignIn()
        },
        onClose = { keyboardField = null },
        submitLabel = stringResource(R.string.settings_navidrome_signin_password_keyboard_submit),
        fieldType = KeyboardFieldType.PASSWORD,
        canSubmit = !navidrome.isSigningIn
    )

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = navidrome.serverUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.spacingSm)
        )

        ControllerKeyboardTapTarget(
            onTap = {
                viewModel.setFocusIndex(NAVIDROME_LOGIN_USERNAME_INDEX)
                viewModel.setNavidromeLoginFocusField(NAVIDROME_LOGIN_USERNAME_INDEX)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = navidrome.loginUsername,
                onValueChange = { viewModel.setNavidromeLoginUsername(it) },
                label = { Text(stringResource(R.string.settings_navidrome_signin_username_label)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                shape = inputShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardField = null }
                    .then(fieldFocusModifier(uiState.focusedIndex == NAVIDROME_LOGIN_USERNAME_INDEX, inputShape))
            )
        }

        ControllerKeyboardTapTarget(
            onTap = {
                viewModel.setFocusIndex(NAVIDROME_LOGIN_PASSWORD_INDEX)
                viewModel.setNavidromeLoginFocusField(NAVIDROME_LOGIN_PASSWORD_INDEX)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = navidrome.loginPassword,
                onValueChange = { viewModel.setNavidromeLoginPassword(it) },
                label = { Text(stringResource(R.string.settings_navidrome_signin_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = inputShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboard?.hide()
                        viewModel.submitNavidromeSignIn()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardField = null }
                    .then(fieldFocusModifier(uiState.focusedIndex == NAVIDROME_LOGIN_PASSWORD_INDEX, inputShape))
            )
        }

        if (navidrome.signInError != null) {
            Text(
                text = navidrome.signInError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = if (navidrome.isSigningIn) {
                stringResource(R.string.settings_navidrome_signin_submitting_title)
            } else {
                stringResource(R.string.settings_navidrome_signin_submit_title)
            },
            subtitle = stringResource(R.string.settings_navidrome_signin_submit_subtitle),
            isFocused = uiState.focusedIndex == NAVIDROME_LOGIN_SUBMIT_INDEX,
            isEnabled = !navidrome.isSigningIn,
            onClick = { viewModel.submitNavidromeSignIn() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_navidrome_signin_cancel_title),
            subtitle = stringResource(R.string.settings_navidrome_signin_cancel_subtitle),
            isFocused = uiState.focusedIndex == NAVIDROME_LOGIN_CANCEL_INDEX,
            onClick = { viewModel.hideNavidromeLoginForm() }
        )
    }
}
