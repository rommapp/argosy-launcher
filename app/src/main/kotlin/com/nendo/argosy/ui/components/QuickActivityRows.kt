package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.ActivityEntry
import com.nendo.argosy.core.notification.ActivityEntryKind
import com.nendo.argosy.core.notification.NotificationCategory
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.ui.components.activity.QuickActivityState
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.core.notification.resolve
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.util.formatClockTime

@Composable
internal fun QuickActivityRow(
    item: QuickSettingsItem,
    state: QuickActivityState,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onOpenRoute: ((String) -> Unit)?
) {
    when (item) {
        QuickSettingsItem.ActivityDownloads -> ActivityLinkRow(
            icon = Icons.Default.Download,
            isFocused = isFocused,
            onClick = onOpenRoute?.let { open -> openAction(onFocus, open, Screen.Downloads.route) }
        ) {
            state.downloads.forEach { line ->
                key(line.key) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = line.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = LocalArgosyTheme.current.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        QuickValueText(quickPercentLabel(line.fraction), isFocused)
                    }
                }
            }
            if (state.queuedDownloads > 0) {
                SecondaryText(
                    pluralStringResource(
                        R.plurals.ui_quick_settings_activity_downloads_queued,
                        state.queuedDownloads,
                        state.queuedDownloads
                    )
                )
            }
        }

        QuickSettingsItem.ActivitySync -> state.sync?.let { sync ->
            ActivityLinkRow(
                icon = Icons.Default.Sync,
                isFocused = isFocused,
                onClick = onOpenRoute?.let { open -> openAction(onFocus, open, Screen.SyncMonitor.route) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ui_quick_settings_activity_syncing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalArgosyTheme.current.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    QuickValueText(
                        pluralStringResource(R.plurals.ui_quick_settings_activity_sync_remaining, sync.remaining, sync.remaining),
                        isFocused
                    )
                }
                sync.gameName?.let { SecondaryText(it) }
            }
        }

        QuickSettingsItem.ActivityIdle -> QuickDetailRow(
            icon = Icons.Default.Done,
            label = stringResource(R.string.ui_quick_settings_activity_idle),
            isFocused = false,
            onClick = null
        )

        QuickSettingsItem.ActivityRecent -> RecentBlock(state.recent)

        else -> Unit
    }
}

private fun openAction(onFocus: () -> Unit, open: (String) -> Unit, route: String): () -> Unit = {
    onFocus()
    open(route)
}

@Composable
private fun ActivityLinkRow(
    icon: ImageVector,
    isFocused: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val enabled = onClick != null
    QuickRowFrame(isFocused = isFocused, onClick = onClick, enabled = enabled, twoLine = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon = icon, tint = if (isFocused && enabled) LocalArgosyTheme.current.focusAccent else LocalArgosyTheme.current.textDim)
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Column(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun RecentBlock(entries: List<ActivityEntry>) {
    if (entries.isEmpty()) {
        QuickDetailRow(
            icon = Icons.Default.History,
            label = stringResource(R.string.ui_quick_settings_activity_recent_empty),
            isFocused = false,
            onClick = null
        )
        return
    }
    Column {
        entries.forEach { entry ->
            key(entry.id) { RecentLine(entry) }
        }
    }
}

@Composable
private fun RecentLine(entry: ActivityEntry) {
    val theme = LocalArgosyTheme.current
    val latestTitle = entry.latest.subtitle?.resolve()
    val primary = when (entry.kind) {
        ActivityEntryKind.NOTICE -> entry.latest.title.resolve()
        ActivityEntryKind.SAVES_SYNCED -> if (latestTitle != null) {
            pluralStringResource(R.plurals.ui_quick_settings_activity_saves_synced_latest, entry.count, entry.count, latestTitle)
        } else {
            pluralStringResource(R.plurals.ui_quick_settings_activity_saves_synced, entry.count, entry.count)
        }
        ActivityEntryKind.DOWNLOADS_FINISHED ->
            pluralStringResource(R.plurals.ui_quick_settings_activity_downloads_finished, entry.count, entry.count)
    }
    val secondary = when (entry.kind) {
        ActivityEntryKind.NOTICE -> latestTitle
        ActivityEntryKind.SAVES_SYNCED -> null
        ActivityEntryKind.DOWNLOADS_FINISHED -> latestTitle ?: entry.latest.title.resolve()
    }
    QuickRowFrame(isFocused = false, onClick = null, twoLine = secondary != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon = entry.icon(), tint = entry.tint())
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                secondary?.let { SecondaryText(it) }
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = formatClockTime(LocalContext.current, entry.atMillis),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconMd))
}

private fun ActivityEntry.icon(): ImageVector = when {
    kind == ActivityEntryKind.SAVES_SYNCED -> Icons.Default.CloudDone
    kind == ActivityEntryKind.DOWNLOADS_FINISHED -> Icons.Default.DownloadDone
    latest.category == NotificationCategory.BATTERY -> Icons.Default.BatteryAlert
    latest.category == NotificationCategory.CONTROLLER -> Icons.Default.SportsEsports
    latest.type == NotificationType.ERROR -> Icons.Default.ErrorOutline
    else -> Icons.Default.WarningAmber
}

@Composable
private fun ActivityEntry.tint(): Color = when (latest.type) {
    NotificationType.ERROR -> LocalArgosyTheme.current.destructive
    NotificationType.WARNING -> LocalLauncherTheme.current.semanticColors.warning
    else -> LocalArgosyTheme.current.textDim
}
