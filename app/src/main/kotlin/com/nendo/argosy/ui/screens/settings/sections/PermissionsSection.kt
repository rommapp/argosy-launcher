package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun PermissionsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()

    val maxIndex = if (uiState.permissions.isWriteSettingsRelevant) 3 else 2

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            val scrollIndex = uiState.focusedIndex + 1
            listState.animateScrollToItem(scrollIndex, -centerOffset + paddingBuffer)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            Text(
                text = "Argosy requires certain permissions to provide full functionality. Tap each permission to grant access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.spacingMd)
            )
        }

        item {
            PermissionCard(
                icon = Icons.Default.Folder,
                title = "Storage Access",
                description = "Required for downloading games, syncing save files, and accessing ROM files on your device.",
                isGranted = uiState.permissions.hasStorageAccess,
                isFocused = uiState.focusedIndex == 0,
                onClick = { viewModel.openStorageSettings() }
            )
        }

        item {
            PermissionCard(
                icon = Icons.Default.Timer,
                title = "Usage Stats Access",
                description = "Enables accurate play time tracking and seamless game session resume when switching between apps.",
                isGranted = uiState.permissions.hasUsageStats,
                isFocused = uiState.focusedIndex == 1,
                onClick = { viewModel.openUsageStatsSettings() }
            )
        }

        item {
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Shows download progress when downloading games in the background.",
                isGranted = uiState.permissions.hasNotificationPermission,
                isFocused = uiState.focusedIndex == 2,
                onClick = { viewModel.openNotificationSettings() }
            )
        }

        if (uiState.permissions.isWriteSettingsRelevant) {
            item {
                PermissionCard(
                    icon = Icons.Default.Tune,
                    title = "System Settings Access",
                    description = "Allows control of device performance mode and fan settings from the Quick Settings panel.",
                    isGranted = uiState.permissions.hasWriteSettings,
                    isFocused = uiState.focusedIndex == 3,
                    onClick = { viewModel.openWriteSettings() }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "Status: ${uiState.permissions.grantedCount}/${uiState.permissions.totalCount} permissions granted",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.permissions.allGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(backgroundColor, shape)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = "Granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = "Tap to grant",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
