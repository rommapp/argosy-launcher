package com.nendo.argosy.libretro.ui

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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens

sealed class TaintedSaveAction {
    data object StartFresh : TaintedSaveAction()
    data object ContinueAnyway : TaintedSaveAction()
    data object Cancel : TaintedSaveAction()
}

@Composable
fun TaintedSaveWarningDialog(
    onAction: (TaintedSaveAction) -> Unit
) {
    val warningColor = Color(0xFFFF9800)

    AlertDialog(
        onDismissRequest = { onAction(TaintedSaveAction.Cancel) },
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = warningColor
            )
        },
        title = {
            Text(
                text = "Tainted Save Detected",
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
                    text = "This save was created with cheats enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = "Hardcore achievements may be invalidated by RetroAchievements if detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                TextButton(onClick = { onAction(TaintedSaveAction.Cancel) }) {
                    Text("Cancel")
                }
                OutlinedButton(
                    onClick = { onAction(TaintedSaveAction.ContinueAnyway) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = warningColor
                    )
                ) {
                    Text("Continue Anyway")
                }
                Button(onClick = { onAction(TaintedSaveAction.StartFresh) }) {
                    Text("Start Fresh")
                }
            }
        },
        dismissButton = null,
        shape = RoundedCornerShape(Dimens.radiusXl)
    )
}
