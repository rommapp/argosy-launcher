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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.settings.RASettingsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.theme.Dimens

private val goldColor = Color(0xFFFFD700)

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
                listState = listState
            )
        } else {
            RALoggedOutContent(
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
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(Dimens.radiusMd)
                    )
                    .padding(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = goldColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Logged in as",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = raState.username ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        color = goldColor
                    )
                }
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Connected",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (raState.pendingAchievementsCount > 0) {
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                SectionHeader("PENDING SYNC")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(Dimens.radiusMd)
                        )
                        .padding(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${raState.pendingAchievementsCount} achievement(s) waiting to sync",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            SectionHeader("ACCOUNT")
        }
        item {
            ActionPreference(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = if (raState.isLoggingOut) "Logging out..." else "Logout",
                subtitle = "Sign out of RetroAchievements",
                isFocused = focusedIndex == 0,
                onClick = { viewModel.logoutFromRA() }
            )
        }
    }
}

@Composable
private fun RALoggedOutContent(
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
                    tint = goldColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Column {
                    Text(
                        text = "RetroAchievements",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Earn achievements while playing classic games",
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
                title = "Login",
                subtitle = "Sign in with your RetroAchievements account",
                isFocused = focusedIndex == 0,
                onClick = { viewModel.showRALoginForm() }
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
                    goldColor.copy(alpha = 0.1f),
                    RoundedCornerShape(Dimens.radiusMd)
                )
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = goldColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = "RetroAchievements Login",
                style = MaterialTheme.typography.titleMedium,
                color = goldColor
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        OutlinedTextField(
            value = raState.loginUsername,
            onValueChange = { viewModel.setRALoginUsername(it) },
            label = { Text("Username") },
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
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        OutlinedTextField(
            value = raState.loginPassword,
            onValueChange = { viewModel.setRALoginPassword(it) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .then(
                    if (focusedIndex == 1)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
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
            title = if (raState.isLoggingIn) "Logging in..." else "Login",
            subtitle = "Sign in to RetroAchievements",
            isFocused = focusedIndex == 2,
            onClick = { viewModel.loginToRA() }
        )

        ActionPreference(
            title = "Cancel",
            subtitle = "Return to RetroAchievements settings",
            isFocused = focusedIndex == 3,
            onClick = { viewModel.hideRALoginForm() }
        )
    }
}
