package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun QuayPassAnnouncementModal(
    onLearnMore: () -> Unit,
    onDismiss: () -> Unit
) {
    CenteredModal(
        title = "Meet QuayPass",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.B to "Close",
            InputButton.A to "Learn more"
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = "Pass other Argosy travelers when you're nearby. " +
                    "Share a Mii, a greeting, and the last game you played.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Build a Mii once and turn it on. We'll handle the rest.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Dimens.spacingMd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                ) { Text("Maybe later") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onLearnMore
                ) { Text("Learn more") }
            }
        }
    }
}
