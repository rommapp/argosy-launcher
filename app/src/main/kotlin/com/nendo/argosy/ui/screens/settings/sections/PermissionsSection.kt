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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.cardDescriptionRes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.PermissionCard
import com.nendo.argosy.ui.screens.settings.PermissionsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.screens.settings.components.settingsRowItems
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class PermissionsItem(
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
    data object AccessibilityService : PermissionsItem("accessibilityService")
    data object LiveWallpaper : PermissionsItem(
        key = "liveWallpaper",
        visibleWhen = { it.isLiveWallpaperRelevant }
    )
    data object PrivilegedAccess : PermissionsItem(
        key = "privilegedAccess",
        visibleWhen = { it.privilegedRoute != null }
    )
    data object StatusFooter : PermissionsItem("statusFooter")

    companion object {
        val ALL: List<PermissionsItem>
            get() = listOf(
                InfoText, StorageAccess, UsageStats, Notifications, WriteSettings, ScreenCapture, DisplayOverlay,
                AccessibilityService, LiveWallpaper, PrivilegedAccess, StatusFooter
            )
    }
}

internal fun permissionsItemAt(focusIndex: Int, permissions: PermissionsState): PermissionsItem? =
    permissionsLayout.itemAtFocusIndex(focusIndex, permissions)

internal fun PermissionsItem.confirm(viewModel: SettingsViewModel) {
    when (this) {
        PermissionsItem.StorageAccess -> viewModel.requestStoragePermission()
        PermissionsItem.UsageStats -> viewModel.openUsageStatsSettings()
        PermissionsItem.Notifications -> viewModel.openNotificationSettings()
        PermissionsItem.WriteSettings -> viewModel.openWriteSettings()
        PermissionsItem.ScreenCapture -> viewModel.requestScreenCapturePermission()
        PermissionsItem.DisplayOverlay -> viewModel.openDisplayOverlaySettings()
        PermissionsItem.AccessibilityService -> viewModel.openAccessibilitySettings()
        PermissionsItem.LiveWallpaper -> viewModel.openLiveWallpaperPicker()
        PermissionsItem.PrivilegedAccess -> viewModel.enablePrivilegedAccess()
        PermissionsItem.InfoText, PermissionsItem.StatusFooter -> Unit
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

    val visibleItems = remember(permissions) {
        permissionsLayout.visibleItems(permissions)
    }

    fun isFocused(item: PermissionsItem): Boolean =
        uiState.focusedIndex == permissionsLayout.focusIndexOf(item, permissions)

    FocusedScroll(
        listState = listState,
        focusedIndex = permissionsLayout.focusToListIndex(uiState.focusedIndex, permissions),
        isTouchMode = LocalSettingsTouchFocus.current.isTouchMode
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        settingsRowItems(
            visibleItems,
            key = { it.key },
            focusIndexOf = { permissionsLayout.focusIndexOf(it, permissions) }
        ) { item ->
            when (item) {
                PermissionsItem.InfoText -> Text(
                    text = stringResource(R.string.settings_permissions_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )

                PermissionsItem.StorageAccess -> PermissionCard(
                    icon = Icons.Default.Folder,
                    title = stringResource(R.string.settings_permissions_storage_title),
                    description = stringResource(R.string.settings_permissions_storage_description),
                    isGranted = permissions.hasStorageAccess,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.UsageStats -> PermissionCard(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.settings_permissions_usage_stats_title),
                    description = stringResource(R.string.settings_permissions_usage_stats_description),
                    isGranted = permissions.hasUsageStats,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.Notifications -> PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_permissions_notifications_title),
                    description = stringResource(R.string.settings_permissions_notifications_description),
                    isGranted = permissions.hasNotificationPermission,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.WriteSettings -> PermissionCard(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_permissions_write_settings_title),
                    description = stringResource(R.string.settings_permissions_write_settings_description),
                    isGranted = permissions.hasWriteSettings,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.ScreenCapture -> PermissionCard(
                    icon = Icons.Default.ScreenshotMonitor,
                    title = stringResource(R.string.settings_permissions_screen_capture_title),
                    description = stringResource(R.string.settings_permissions_screen_capture_description),
                    isGranted = permissions.hasScreenCapture,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.DisplayOverlay -> PermissionCard(
                    icon = Icons.Default.Layers,
                    title = stringResource(R.string.settings_permissions_display_overlay_title),
                    description = stringResource(R.string.settings_permissions_display_overlay_description),
                    isGranted = permissions.hasDisplayOverlay,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.AccessibilityService -> PermissionCard(
                    icon = Icons.Default.Accessibility,
                    title = stringResource(R.string.settings_permissions_accessibility_title),
                    description = stringResource(R.string.settings_permissions_accessibility_description),
                    isGranted = permissions.hasAccessibilityService,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.LiveWallpaper -> PermissionCard(
                    icon = Icons.Default.Wallpaper,
                    title = stringResource(R.string.settings_permissions_live_wallpaper_title),
                    description = stringResource(R.string.settings_permissions_live_wallpaper_description),
                    isGranted = permissions.hasLiveWallpaper,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.PrivilegedAccess -> PermissionCard(
                    icon = Icons.Default.AdminPanelSettings,
                    title = stringResource(
                        R.string.settings_permissions_privileged_title,
                        permissions.privilegedRoute?.productName.orEmpty()
                    ),
                    description = stringResource(permissions.privilegedReach.cardDescriptionRes),
                    isGranted = permissions.privilegedReach.isReady,
                    isFocused = isFocused(item),
                    onClick = { item.confirm(viewModel) }
                )

                PermissionsItem.StatusFooter -> Column {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    Text(
                        text = stringResource(
                            R.string.settings_permissions_status_footer,
                            permissions.grantedCount,
                            permissions.totalCount
                        ),
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

