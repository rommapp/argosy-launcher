package com.nendo.argosy.ui.components

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
import com.nendo.argosy.ui.theme.Dimens
import java.time.Duration
import java.time.Instant

data class SaveConflictInfo(
    val gameId: Long,
    val gameName: String,
    val emulatorId: String,
    val channelName: String?,
    val localTimestamp: Instant,
    val serverTimestamp: Instant
)

@Composable
fun SaveConflictModal(
    info: SaveConflictInfo,
    focusedButton: Int,
    onKeepLocal: () -> Unit,
    onOverwrite: () -> Unit
) {
    val localIsNewer = info.localTimestamp.isAfter(info.serverTimestamp)

    Modal(
        title = "Save Conflict",
        baseWidth = 400.dp,
        onDismiss = onKeepLocal,
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                GameTitle(
                    title = info.gameName,
                    titleStyle = MaterialTheme.typography.titleMedium,
                    titleColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = info.channelName ?: "Default Save",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    ) {
        Text(
            text = "The server has a newer save. " +
                "Overwriting will replace it with your local save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            SaveSourceRow(
                icon = Icons.Default.PhoneAndroid,
                label = "Local",
                timestamp = info.localTimestamp.toRelativeString(),
                isNewer = localIsNewer
            )
            SaveSourceRow(
                icon = Icons.Default.Cloud,
                label = "Server",
                timestamp = info.serverTimestamp.toRelativeString(),
                isNewer = !localIsNewer
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Button(
                onClick = onKeepLocal,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusedButton == 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (focusedButton == 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                shape = RoundedCornerShape(Dimens.radiusMd)
            ) {
                Text("Skip Sync")
            }

            Button(
                onClick = onOverwrite,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusedButton == 1) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (focusedButton == 1) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                ),
                shape = RoundedCornerShape(Dimens.radiusMd)
            ) {
                Text("Overwrite")
            }
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
