package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun QuayPassAnnouncementModal(
    focusedButton: Int,
    onLearnMore: () -> Unit,
    onDismiss: () -> Unit
) {
    CenteredModal(
        title = "Meet QuayPass",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.B to "Maybe later",
            InputButton.DPAD_HORIZONTAL to "Choose",
            InputButton.A to "Confirm"
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
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (focusedButton == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (focusedButton == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(Dimens.radiusMd)
                ) { Text("Maybe later") }

                Button(
                    onClick = onLearnMore,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (focusedButton == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (focusedButton == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(Dimens.radiusMd)
                ) { Text("Learn more") }
            }
        }
    }
}
