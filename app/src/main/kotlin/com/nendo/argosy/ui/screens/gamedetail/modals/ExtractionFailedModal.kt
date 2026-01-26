package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.ExtractionFailedInfo

@Composable
fun ExtractionFailedModal(
    info: ExtractionFailedInfo,
    focusIndex: Int,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit
) {
    CenteredModal(
        title = "EXTRACTION FAILED",
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Select",
            InputButton.B to "Cancel"
        )
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(Dimens.iconXl)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "Failed to extract ${info.fileName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg, Alignment.CenterHorizontally)
        ) {
            if (focusIndex == 0) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Retry Extraction")
                }
            } else {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry Extraction")
                }
            }

            if (focusIndex == 1) {
                Button(
                    onClick = onRedownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Redownload")
                }
            } else {
                OutlinedButton(onClick = onRedownload) {
                    Text("Redownload")
                }
            }
        }
    }
}
