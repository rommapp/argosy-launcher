package com.nendo.argosy.ui.screens.settings.sections

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.RA_PROXY_FIELD_INDEX
import com.nendo.argosy.ui.screens.settings.RA_PROXY_TOGGLE_INDEX
import com.nendo.argosy.ui.screens.settings.RASettingsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun RASettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val raState = uiState.retroAchievements
    val listState = rememberLazyListState()

    FocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (raState.showLoginForm) {
            RALoginForm(
                raState = raState,
                focusedIndex = uiState.focusedIndex,
                viewModel = viewModel
            )
        } else if (raState.isLoggedIn) {
            RALoggedInContent(
                raState = raState,
                focusedIndex = uiState.focusedIndex,
                viewModel = viewModel,
                listState = listState,
                secureSaves = uiState.syncSettings.secureSaves
            )
        } else {
            RALoggedOutContent(
                raState = raState,
                focusedIndex = uiState.focusedIndex,
                viewModel = viewModel,
                listState = listState
            )
        }
    }
}

@Composable
private fun RALoggedInContent(
    raState: RASettingsState,
    focusedIndex: Int,
    viewModel: SettingsViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    secureSaves: Boolean
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(Dimens.radiusMd)
                    )
                    .padding(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = ALauncherColors.StarGold,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_ra_logged_in_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = raState.username ?: stringResource(R.string.settings_ra_username_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = ALauncherColors.StarGold
                    )
                }
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.settings_ra_connected_icon_description),
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (raState.pendingAchievementsCount > 0) {
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                SectionHeader(stringResource(R.string.settings_ra_section_pending_sync))
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            LocalLauncherTheme.current.semanticColors.warningContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(Dimens.radiusMd)
                        )
                        .padding(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_ra_pending_sync_count,
                            raState.pendingAchievementsCount,
                            raState.pendingAchievementsCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            SectionHeader(stringResource(R.string.settings_ra_section_account))
        }
        item {
            ActionPreference(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = if (raState.isLoggingOut) {
                    stringResource(R.string.settings_ra_logout_title_busy)
                } else {
                    stringResource(R.string.settings_ra_logout_title)
                },
                subtitle = stringResource(R.string.settings_ra_logout_subtitle),
                isFocused = focusedIndex == 0,
                onClick = { viewModel.logoutFromRA() }
            )
        }

        item {
            if (!secureSaves) {
                ActionPreference(
                    title = stringResource(R.string.settings_ra_play_mode_title),
                    subtitle = stringResource(R.string.settings_ra_play_mode_subtitle_locked),
                    isFocused = focusedIndex == 1,
                    isEnabled = false,
                    onClick = {}
                )
            } else {
                val cycleOptions = listOf(
                    stringResource(R.string.settings_ra_play_mode_ask),
                    stringResource(R.string.settings_ra_play_mode_casual),
                    stringResource(R.string.settings_ra_play_mode_hardcore)
                )
                val tokenOptions = listOf("ask", "casual", "hardcore")
                val currentLabel = when (raState.defaultToHardcore) {
                    "hardcore" -> cycleOptions[2]
                    "casual" -> cycleOptions[1]
                    else -> cycleOptions[0]
                }
                CyclePreference(
                    title = stringResource(R.string.settings_ra_play_mode_title),
                    value = currentLabel,
                    isFocused = focusedIndex == 1,
                    onClick = { viewModel.cycleRADefaultMode(1) },
                    onPrev = { viewModel.cycleRADefaultMode(-1) },
                    options = cycleOptions,
                    onSelect = { index ->
                        val nextToken = tokenOptions.getOrNull(index) ?: "ask"
                        viewModel.setBuiltinDefaultToHardcore(nextToken)
                    },
                    subtitle = when (raState.defaultToHardcore) {
                        "ask" -> stringResource(R.string.settings_ra_play_mode_subtitle_ask)
                        "casual" -> stringResource(R.string.settings_ra_play_mode_subtitle_casual)
                        "hardcore" -> stringResource(R.string.settings_ra_play_mode_subtitle_hardcore)
                        else -> stringResource(R.string.settings_ra_play_mode_subtitle_default)
                    }
                )
            }
        }

        raProxyItems(raState, focusedIndex, viewModel, proxyToggleIndex = 2, proxyFieldIndex = 3)

        if (raState.canPushToRetroArch) {
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                SectionHeader(stringResource(R.string.settings_ra_section_retroarch))
            }
            item {
                val pushIndex = if (raState.proxyEnabled) 4 else 3
                ActionPreference(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_ra_push_retroarch_title),
                    subtitle = stringResource(R.string.settings_ra_push_retroarch_subtitle),
                    isFocused = focusedIndex == pushIndex,
                    onClick = { viewModel.pushRACredentialsToRetroArch() }
                )
            }
        }
    }
}

@Composable
private fun RALoggedOutContent(
    raState: RASettingsState,
    focusedIndex: Int,
    viewModel: SettingsViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(Dimens.radiusMd)
                    )
                    .padding(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = ALauncherColors.StarGold.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Column {
                    Text(
                        text = stringResource(R.string.settings_ra_intro_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_ra_intro_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
        }
        item {
            ActionPreference(
                icon = Icons.AutoMirrored.Filled.Login,
                title = stringResource(R.string.settings_ra_login_row_title),
                subtitle = stringResource(R.string.settings_ra_login_row_subtitle),
                isFocused = focusedIndex == 0,
                onClick = { viewModel.showRALoginForm() }
            )
        }

        raProxyItems(raState, focusedIndex, viewModel, proxyToggleIndex = 1, proxyFieldIndex = 2)
    }
}

private fun LazyListScope.raProxyItems(
    raState: RASettingsState,
    focusedIndex: Int,
    viewModel: SettingsViewModel,
    proxyToggleIndex: Int,
    proxyFieldIndex: Int
) {
    item {
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        SectionHeader(stringResource(R.string.settings_ra_section_offline_proxy))
    }
    item {
        SwitchPreference(
            title = stringResource(R.string.settings_ra_proxy_title),
            subtitle = stringResource(R.string.settings_ra_proxy_subtitle),
            isEnabled = raState.proxyEnabled,
            isFocused = focusedIndex == proxyToggleIndex,
            onToggle = { viewModel.setRAProxyEnabled(it) }
        )
    }
    if (raState.proxyEnabled) {
        item {
            val inputShape = RoundedCornerShape(Dimens.radiusMd)
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(raState.focusField) {
                if (raState.focusField == proxyFieldIndex) {
                    focusRequester.requestFocus()
                    viewModel.clearRAFocusField()
                }
            }
            OutlinedTextField(
                value = raState.proxyAddress,
                onValueChange = { viewModel.setRAProxyAddress(it) },
                label = { Text(stringResource(R.string.settings_ra_proxy_address_label)) },
                placeholder = { Text("127.0.0.1:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = inputShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .then(
                        if (focusedIndex == proxyFieldIndex)
                            Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                        else Modifier
                    )
            )
        }
    }
}

@Composable
private fun RALoginForm(
    raState: RASettingsState,
    focusedIndex: Int,
    viewModel: SettingsViewModel
) {
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(raState.focusField) {
        when (raState.focusField) {
            0 -> usernameFocusRequester.requestFocus()
            1 -> passwordFocusRequester.requestFocus()
        }
        if (raState.focusField != null) {
            viewModel.clearRAFocusField()
        }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    ALauncherColors.StarGold.copy(alpha = 0.1f),
                    RoundedCornerShape(Dimens.radiusMd)
                )
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = ALauncherColors.StarGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.settings_ra_login_form_title),
                style = MaterialTheme.typography.titleMedium,
                color = ALauncherColors.StarGold
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        OutlinedTextField(
            value = raState.loginUsername,
            onValueChange = { viewModel.setRALoginUsername(it) },
            label = { Text(stringResource(R.string.settings_ra_login_username_label)) },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            singleLine = true,
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(usernameFocusRequester)
                .then(
                    if (focusedIndex == 0)
                        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                    else Modifier
                )
        )

        OutlinedTextField(
            value = raState.loginPassword,
            onValueChange = { viewModel.setRALoginPassword(it) },
            label = { Text(stringResource(R.string.settings_ra_login_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .then(
                    if (focusedIndex == 1)
                        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), inputShape)
                    else Modifier
                )
        )

        if (raState.loginError != null) {
            Text(
                text = raState.loginError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = if (raState.isLoggingIn) {
                stringResource(R.string.settings_ra_login_submit_title_busy)
            } else {
                stringResource(R.string.settings_ra_login_submit_title)
            },
            subtitle = stringResource(R.string.settings_ra_login_submit_subtitle),
            isFocused = focusedIndex == 2,
            onClick = { viewModel.loginToRA() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_ra_login_cancel_title),
            subtitle = stringResource(R.string.settings_ra_login_cancel_subtitle),
            isFocused = focusedIndex == 3,
            onClick = { viewModel.hideRALoginForm() }
        )
    }
}
