package com.nendo.argosy.data.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import java.time.Duration
import java.time.Instant

@Composable
fun BackgroundSyncConflictDialog(
    conflictInfo: ConflictInfo,
    focusIndex: Int,
    onKeepLocal: () -> Unit,
    onKeepServer: () -> Unit,
    onSkip: () -> Unit
) {
    val localTimeStr = conflictInfo.localTimestamp.toRelativeString()
    val serverTimeStr = conflictInfo.serverTimestamp.toRelativeString()
    val localIsNewer = conflictInfo.localTimestamp.isAfter(conflictInfo.serverTimestamp)

    Modal(
        title = "Save Sync Conflict",
        baseWidth = 400.dp,
        onDismiss = onSkip,
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                GameTitle(
                    title = conflictInfo.gameName,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    titleColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = conflictInfo.channelName ?: "Default Save",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    ) {
        Text(
            text = if (conflictInfo.isHashConflict)
                "Your local save has changed since the last sync."
            else
                "A newer save exists on the server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            SaveSourceRow(
                icon = Icons.Default.PhoneAndroid,
                label = "Local",
                timestamp = localTimeStr,
                isNewer = localIsNewer
            )
            SaveSourceRow(
                icon = Icons.Default.Cloud,
                label = "Server",
                timestamp = serverTimeStr,
                isNewer = !localIsNewer
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            ConflictActionButton(
                icon = Icons.Default.CloudUpload,
                label = "Upload Local",
                isFocused = focusIndex == 0,
                onClick = onKeepLocal
            )
            ConflictActionButton(
                icon = Icons.Default.CloudDownload,
                label = "Download Server",
                isFocused = focusIndex == 1,
                onClick = onKeepServer
            )
            ConflictActionButton(
                label = "Skip",
                isFocused = focusIndex == 2,
                isDimmed = true,
                onClick = onSkip
            )
        }
    }
}

@Composable
private fun SaveSourceRow(
    icon: ImageVector,
    label: String,
    timestamp: String,
    isNewer: Boolean
) {
    val tint = if (isNewer) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isNewer) FontWeight.Bold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = if (isNewer) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConflictActionButton(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    isDimmed: Boolean = false
) {
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isDimmed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(Dimens.radiusMd)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSm)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        Text(label)
    }
}

private fun Instant.toRelativeString(): String {
    val now = Instant.now()
    val duration = Duration.between(this, now)
    return when {
        duration.isNegative -> "in the future"
        duration.toMinutes() < 1 -> "just now"
        duration.toHours() < 1 -> "${duration.toMinutes()} minutes ago"
        duration.toDays() < 1 -> "${duration.toHours()} hours ago"
        duration.toDays() < 30 -> "${duration.toDays()} days ago"
        else -> "${duration.toDays() / 30} months ago"
    }
}
