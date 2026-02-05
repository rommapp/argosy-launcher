package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import java.time.Duration
import java.time.Instant

data class SaveConflictInfo(
    val gameId: Long,
    val gameName: String,
    val emulatorId: String,
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
    Modal(
        title = "Save Conflict",
        baseWidth = 380.dp,
        onDismiss = onKeepLocal
    ) {
        Text(
            text = "The server has a newer save for:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = info.gameName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = "Server save: ${info.serverTimestamp.toRelativeString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Local save: ${info.localTimestamp.toRelativeString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Text(
            text = "Overwriting will replace the server save with your local save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
