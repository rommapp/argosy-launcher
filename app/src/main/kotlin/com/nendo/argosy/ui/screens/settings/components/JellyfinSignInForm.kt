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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal const val JELLYFIN_LOGIN_USERNAME_INDEX = 0
internal const val JELLYFIN_LOGIN_PASSWORD_INDEX = 1
internal const val JELLYFIN_LOGIN_SUBMIT_INDEX = 2
internal const val JELLYFIN_LOGIN_CANCEL_INDEX = 3

/**
 * The fallback sign-in for a server that does not offer Quick Connect. It is reached only when the
 * server has said so, because typing a password on a controller is the slow path.
 */
@Composable
fun JellyfinSignInForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val jellyfin = uiState.jellyfin
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(jellyfin.loginFocusField) {
        when (jellyfin.loginFocusField) {
            JELLYFIN_LOGIN_USERNAME_INDEX -> usernameFocusRequester.requestFocus()
            JELLYFIN_LOGIN_PASSWORD_INDEX -> passwordFocusRequester.requestFocus()
        }
        if (jellyfin.loginFocusField != null) {
            viewModel.clearJellyfinLoginFocusField()
        }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = jellyfin.serverUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.spacingSm)
        )

        OutlinedTextField(
            value = jellyfin.loginUsername,
            onValueChange = { viewModel.setJellyfinLoginUsername(it) },
            label = { Text(stringResource(R.string.settings_jellyfin_signin_username_label)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            shape = inputShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(usernameFocusRequester)
                .then(
                    if (uiState.focusedIndex == JELLYFIN_LOGIN_USERNAME_INDEX) {
                        Modifier.background(
                            LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f),
                            inputShape
                        )
                    } else {
                        Modifier
                    }
                )
        )

        OutlinedTextField(
            value = jellyfin.loginPassword,
            onValueChange = { viewModel.setJellyfinLoginPassword(it) },
            label = { Text(stringResource(R.string.settings_jellyfin_signin_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = inputShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    keyboard?.hide()
                    viewModel.submitJellyfinPasswordSignIn()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .then(
                    if (uiState.focusedIndex == JELLYFIN_LOGIN_PASSWORD_INDEX) {
                        Modifier.background(
                            LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f),
                            inputShape
                        )
                    } else {
                        Modifier
                    }
                )
        )

        if (jellyfin.signInError != null) {
            Text(
                text = jellyfin.signInError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = if (jellyfin.isSigningIn) {
                stringResource(R.string.settings_jellyfin_signin_submitting_title)
            } else {
                stringResource(R.string.settings_jellyfin_signin_submit_title)
            },
            subtitle = stringResource(R.string.settings_jellyfin_signin_submit_subtitle),
            isFocused = uiState.focusedIndex == JELLYFIN_LOGIN_SUBMIT_INDEX,
            isEnabled = !jellyfin.isSigningIn,
            onClick = { viewModel.submitJellyfinPasswordSignIn() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_jellyfin_signin_cancel_title),
            subtitle = stringResource(R.string.settings_jellyfin_signin_cancel_subtitle),
            isFocused = uiState.focusedIndex == JELLYFIN_LOGIN_CANCEL_INDEX,
            onClick = { viewModel.hideJellyfinLoginForm() }
        )
    }
}
