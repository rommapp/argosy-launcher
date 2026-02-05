package com.nendo.argosy.data.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens

sealed class HardcoreSyncConflictAction {
    data object KeepHardcore : HardcoreSyncConflictAction()
    data object DowngradeToCasual : HardcoreSyncConflictAction()
    data object KeepLocal : HardcoreSyncConflictAction()
}

@Composable
fun HardcoreSyncConflictDialog(
    gameName: String,
    onAction: (HardcoreSyncConflictAction) -> Unit
) {
    val warningColor = Color(0xFFFF9800)

    AlertDialog(
        onDismissRequest = { onAction(HardcoreSyncConflictAction.KeepLocal) },
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(48.dp),
                tint = warningColor
            )
        },
        title = {
            Text(
                text = "Hardcore Save Conflict",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "The server version of this save no longer meets the requirements for hardcore mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = "This can happen if the save was modified outside of Argosy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Button(
                    onClick = { onAction(HardcoreSyncConflictAction.KeepHardcore) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep Hardcore (Upload Local)")
                }
                OutlinedButton(
                    onClick = { onAction(HardcoreSyncConflictAction.DowngradeToCasual) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Downgrade to Casual (Use Server)")
                }
                TextButton(
                    onClick = { onAction(HardcoreSyncConflictAction.KeepLocal) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip Sync")
                }
            }
        },
        dismissButton = null,
        shape = RoundedCornerShape(Dimens.radiusXl)
    )
}
