package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.settings.PermissionsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

private sealed class PermissionsItem(
    val key: String,
    val visibleWhen: (PermissionsState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is InfoText && this !is StatusFooter

    data object InfoText : PermissionsItem("infoText")
    data object StorageAccess : PermissionsItem("storage")
    data object UsageStats : PermissionsItem("usageStats")
    data object Notifications : PermissionsItem("notifications")
    data object WriteSettings : PermissionsItem(
        key = "writeSettings",
        visibleWhen = { it.isWriteSettingsRelevant }
    )
    data object ScreenCapture : PermissionsItem(
        key = "screenCapture",
        visibleWhen = { it.isScreenCaptureRelevant }
    )
    data object DisplayOverlay : PermissionsItem("displayOverlay")
    data object StatusFooter : PermissionsItem("statusFooter")

    companion object {
        val ALL: List<PermissionsItem> = listOf(
            InfoText, StorageAccess, UsageStats, Notifications, WriteSettings, ScreenCapture, DisplayOverlay, StatusFooter
        )
    }
}

private val permissionsLayout = SettingsLayout<PermissionsItem, PermissionsState>(
    allItems = PermissionsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) }
)

internal fun permissionsMaxFocusIndex(permissions: PermissionsState): Int = permissionsLayout.maxFocusIndex(permissions)

@Composable
fun PermissionsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val permissions = uiState.permissions

    val visibleItems = remember(permissions.isWriteSettingsRelevant) {
        permissionsLayout.visibleItems(permissions)
    }

    fun isFocused(item: PermissionsItem): Boolean =
        uiState.focusedIndex == permissionsLayout.focusIndexOf(item, permissions)

    FocusedScroll(
        listState = listState,
        focusedIndex = permissionsLayout.focusToListIndex(uiState.focusedIndex, permissions)
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                PermissionsItem.InfoText -> Text(
                    text = "Argosy requires certain permissions to provide full functionality. Tap each permission to grant access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                PermissionsItem.StorageAccess -> PermissionCard(
                    icon = Icons.Default.Folder,
                    title = "Storage Access",
                    description = "Required for downloading games, syncing save files, and accessing ROM files on your device.",
                    isGranted = permissions.hasStorageAccess,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openStorageSettings() }
                )

                PermissionsItem.UsageStats -> PermissionCard(
                    icon = Icons.Default.Timer,
                    title = "Usage Stats Access",
                    description = "Enables accurate play time tracking and seamless game session resume when switching between apps.",
                    isGranted = permissions.hasUsageStats,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openUsageStatsSettings() }
                )

                PermissionsItem.Notifications -> PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Shows download progress when downloading games in the background.",
                    isGranted = permissions.hasNotificationPermission,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openNotificationSettings() }
                )

                PermissionsItem.WriteSettings -> PermissionCard(
                    icon = Icons.Default.Tune,
                    title = "System Settings Access",
                    description = "Allows control of device performance mode and fan settings from the Quick Settings panel.",
                    isGranted = permissions.hasWriteSettings,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openWriteSettings() }
                )

                PermissionsItem.ScreenCapture -> PermissionCard(
                    icon = Icons.Default.ScreenshotMonitor,
                    title = "Screen Capture",
                    description = "Enables ambient LED lighting to sample screen colors while playing games.",
                    isGranted = permissions.hasScreenCapture,
                    isFocused = isFocused(item),
                    onClick = { viewModel.requestScreenCapturePermission() }
                )

                PermissionsItem.DisplayOverlay -> PermissionCard(
                    icon = Icons.Default.Layers,
                    title = "Display Over Other Apps",
                    description = "Shows save detection notifications while playing games in external emulators.",
                    isGranted = permissions.hasDisplayOverlay,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openDisplayOverlaySettings() }
                )

                PermissionsItem.StatusFooter -> Column {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    Text(
                        text = "Status: ${permissions.grantedCount}/${permissions.totalCount} permissions granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (permissions.allGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
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
            .clickableNoFocus(onClick = onClick)
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
