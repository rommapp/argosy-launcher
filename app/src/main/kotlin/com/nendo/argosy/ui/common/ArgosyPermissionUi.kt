package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.ui.components.PermissionRowState

/**
 * The row title: what the permission does in Argosy. The privileged access title takes the
 * route's product name as its argument.
 */
@get:StringRes
val ArgosyPermission.titleRes: Int
    get() = when (this) {
        ArgosyPermission.ALL_FILES_ACCESS -> R.string.permissions_all_files_title
        ArgosyPermission.USAGE_ACCESS -> R.string.permissions_usage_title
        ArgosyPermission.NOTIFICATIONS -> R.string.permissions_notifications_title
        ArgosyPermission.DISPLAY_OVERLAY -> R.string.permissions_overlay_title
        ArgosyPermission.ACCESSIBILITY -> R.string.permissions_accessibility_title
        ArgosyPermission.BATTERY_UNRESTRICTED -> R.string.permissions_battery_title
        ArgosyPermission.WRITE_SETTINGS -> R.string.permissions_write_settings_title
        ArgosyPermission.SCREEN_CAPTURE -> R.string.permissions_screen_capture_title
        ArgosyPermission.LIVE_WALLPAPER -> R.string.permissions_live_wallpaper_title
        ArgosyPermission.PRIVILEGED_ACCESS -> R.string.settings_permissions_privileged_title
    }

/**
 * Android's own name for the permission, the second line of the row. Null for privileged access,
 * whose second line describes what the route currently reaches.
 */
@get:StringRes
val ArgosyPermission.androidNameRes: Int?
    get() = when (this) {
        ArgosyPermission.ALL_FILES_ACCESS -> R.string.permissions_all_files_android_name
        ArgosyPermission.USAGE_ACCESS -> R.string.permissions_usage_android_name
        ArgosyPermission.NOTIFICATIONS -> R.string.permissions_notifications_android_name
        ArgosyPermission.DISPLAY_OVERLAY -> R.string.permissions_overlay_android_name
        ArgosyPermission.ACCESSIBILITY -> R.string.permissions_accessibility_android_name
        ArgosyPermission.BATTERY_UNRESTRICTED -> R.string.permissions_battery_android_name
        ArgosyPermission.WRITE_SETTINGS -> R.string.permissions_write_settings_android_name
        ArgosyPermission.SCREEN_CAPTURE -> R.string.permissions_screen_capture_android_name
        ArgosyPermission.LIVE_WALLPAPER -> R.string.permissions_live_wallpaper_android_name
        ArgosyPermission.PRIVILEGED_ACCESS -> null
    }

/**
 * What stops working while the permission is off, for the permissions whose changes are tracked.
 */
@get:StringRes
val ArgosyPermission.lossRes: Int?
    get() = when (this) {
        ArgosyPermission.ALL_FILES_ACCESS -> R.string.permissions_all_files_loss
        ArgosyPermission.USAGE_ACCESS -> R.string.permissions_usage_loss
        ArgosyPermission.NOTIFICATIONS -> R.string.permissions_notifications_loss
        ArgosyPermission.DISPLAY_OVERLAY -> R.string.permissions_overlay_loss
        ArgosyPermission.ACCESSIBILITY -> R.string.permissions_accessibility_loss
        ArgosyPermission.BATTERY_UNRESTRICTED -> R.string.permissions_battery_loss
        ArgosyPermission.WRITE_SETTINGS,
        ArgosyPermission.SCREEN_CAPTURE,
        ArgosyPermission.LIVE_WALLPAPER,
        ArgosyPermission.PRIVILEGED_ACCESS -> null
    }

val ArgosyPermission.icon: ImageVector
    get() = when (this) {
        ArgosyPermission.ALL_FILES_ACCESS -> Icons.Default.Folder
        ArgosyPermission.USAGE_ACCESS -> Icons.Default.Timer
        ArgosyPermission.NOTIFICATIONS -> Icons.Default.Notifications
        ArgosyPermission.DISPLAY_OVERLAY -> Icons.Default.Layers
        ArgosyPermission.ACCESSIBILITY -> Icons.Default.Accessibility
        ArgosyPermission.BATTERY_UNRESTRICTED -> Icons.Default.BatteryFull
        ArgosyPermission.WRITE_SETTINGS -> Icons.Default.Tune
        ArgosyPermission.SCREEN_CAPTURE -> Icons.Default.ScreenshotMonitor
        ArgosyPermission.LIVE_WALLPAPER -> Icons.Default.Wallpaper
        ArgosyPermission.PRIVILEGED_ACCESS -> Icons.Default.AdminPanelSettings
    }

val PermissionAvailability.rowState: PermissionRowState
    get() = when (this) {
        PermissionAvailability.GRANTED -> PermissionRowState.GRANTED
        PermissionAvailability.MISSING -> PermissionRowState.MISSING
        PermissionAvailability.CHECKING -> PermissionRowState.CHECKING
        PermissionAvailability.UNAVAILABLE -> PermissionRowState.UNAVAILABLE
    }
