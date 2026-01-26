package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.LaunchMode
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun GameModeSelectionDialog(
    isOnline: Boolean,
    onSelectMode: (LaunchMode) -> Unit,
    onDismiss: () -> Unit
) {
    val goldColor = Color(0xFFFFD700)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How do you want to play?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                OutlinedButton(
                    onClick = { onSelectMode(LaunchMode.NEW_CASUAL) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusMd)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingMd))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CASUAL",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Save states, rewind, cheats OK",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onSelectMode(LaunchMode.NEW_HARDCORE) },
                    enabled = isOnline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isOnline) 1f else 0.5f),
                    shape = RoundedCornerShape(Dimens.radiusMd),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = goldColor
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isOnline) goldColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacingMd))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HARDCORE",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isOnline) "Original experience, achievements count more"
                                       else "Requires internet connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(Dimens.radiusXl)
    )
}
