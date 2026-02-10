package com.nendo.argosy.data.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

    Modal(
        title = "Save Sync Conflict",
        baseWidth = 400.dp,
        onDismiss = onSkip
    ) {
        Text(
            text = conflictInfo.gameName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        if (conflictInfo.isHashConflict) {
            Text(
                text = "Your local save has changed since the last sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = "A newer save exists on the server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        if (conflictInfo.channelName != null) {
            Text(
                text = "Channel: ${conflictInfo.channelName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Local save: $localTimeStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Server save: $serverTimeStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Button(
                onClick = onKeepLocal,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusIndex == 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (focusIndex == 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                shape = RoundedCornerShape(Dimens.radiusMd)
            ) {
                Text("Upload Local")
            }

            Button(
                onClick = onKeepServer,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusIndex == 1) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (focusIndex == 1) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                shape = RoundedCornerShape(Dimens.radiusMd)
            ) {
                Text("Download Server")
            }

            Button(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusIndex == 2) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (focusIndex == 2) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                shape = RoundedCornerShape(Dimens.radiusMd)
            ) {
                Text("Skip")
            }
        }
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
