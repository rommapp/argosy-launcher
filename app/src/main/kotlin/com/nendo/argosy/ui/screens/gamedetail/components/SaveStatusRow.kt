package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatAbsoluteTimestamp
import com.nendo.argosy.util.formatRelativeTimeVerbose
import java.time.Instant

enum class SaveSyncStatus {
    SYNCED,
    LOCAL_NEWER,
    LOCAL_ONLY,
    PENDING_UPLOAD,
    NO_SAVE,
    NOT_CONFIGURED
}

data class SaveStatusInfo(
    val status: SaveSyncStatus,
    val channelName: String?,
    val activeSaveTimestamp: Long?,
    val lastSyncTime: Instant?
) {
    val displayLabel: String
        get() = when {
            channelName != null -> channelName
            activeSaveTimestamp != null -> formatAbsoluteTimestamp(activeSaveTimestamp)
            else -> "Latest"
        }

    val effectiveStatus: SaveSyncStatus
        get() = status

    val displayTime: String?
        get() = when {
            activeSaveTimestamp != null -> formatRelativeTimeVerbose(Instant.ofEpochMilli(activeSaveTimestamp))
            lastSyncTime != null -> formatRelativeTimeVerbose(lastSyncTime)
            else -> null
        }
}

data class SaveStatusEvent(
    val channelName: String?,
    val timestamp: Long?
) {
    val isLocalOnly: Boolean get() = timestamp != null
}

@Composable
fun SaveStatusRow(
    status: SaveStatusInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = status.effectiveStatus.icon,
            contentDescription = null,
            tint = status.effectiveStatus.color(),
            modifier = Modifier.size(Dimens.spacingMd)
        )

        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.displayLabel,
                style = MaterialTheme.typography.bodySmall,
                color = LocalLauncherTheme.current.semanticColors.warning,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Text(
                    text = status.effectiveStatus.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = status.effectiveStatus.textColor()
                )

                status.displayTime?.let { time ->
                    Text(
                        text = "· $time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private val SaveSyncStatus.icon: ImageVector
    get() = when (this) {
        SaveSyncStatus.SYNCED -> Icons.Default.Check
        SaveSyncStatus.LOCAL_NEWER -> Icons.Default.CloudUpload
        SaveSyncStatus.LOCAL_ONLY -> Icons.Default.Save
        SaveSyncStatus.PENDING_UPLOAD -> Icons.Default.Sync
        SaveSyncStatus.NO_SAVE -> Icons.Default.CloudOff
        SaveSyncStatus.NOT_CONFIGURED -> Icons.Default.Error
    }

@Composable
private fun SaveSyncStatus.color() = when (this) {
    SaveSyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
    SaveSyncStatus.LOCAL_NEWER -> LocalLauncherTheme.current.semanticColors.warning
    SaveSyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
    SaveSyncStatus.PENDING_UPLOAD -> MaterialTheme.colorScheme.secondary
    SaveSyncStatus.NO_SAVE -> MaterialTheme.colorScheme.onSurfaceVariant
    SaveSyncStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.error
}

@Composable
private fun SaveSyncStatus.textColor() = when (this) {
    SaveSyncStatus.SYNCED -> MaterialTheme.colorScheme.onSurfaceVariant
    SaveSyncStatus.LOCAL_NEWER -> LocalLauncherTheme.current.semanticColors.info
    SaveSyncStatus.LOCAL_ONLY -> LocalLauncherTheme.current.semanticColors.info
    SaveSyncStatus.PENDING_UPLOAD -> LocalLauncherTheme.current.semanticColors.info
    SaveSyncStatus.NO_SAVE -> MaterialTheme.colorScheme.onSurfaceVariant
    SaveSyncStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.error
}

private val SaveSyncStatus.displayName: String
    get() = when (this) {
        SaveSyncStatus.SYNCED -> "Synced"
        SaveSyncStatus.LOCAL_NEWER -> "Local newer"
        SaveSyncStatus.LOCAL_ONLY -> "Local"
        SaveSyncStatus.PENDING_UPLOAD -> "Pending upload"
        SaveSyncStatus.NO_SAVE -> "No save"
        SaveSyncStatus.NOT_CONFIGURED -> "Not configured"
    }

