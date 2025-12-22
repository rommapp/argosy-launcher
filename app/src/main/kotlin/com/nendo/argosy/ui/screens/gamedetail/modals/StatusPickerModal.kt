package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.components.InputButton

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StatusPickerModal(
    selectedValue: String?,
    currentValue: String?,
    onDismiss: () -> Unit
) {
    val effectiveSelection = selectedValue ?: CompletionStatus.entries.first().apiValue

    CenteredModal(
        title = "SET STATUS",
        width = 320.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.SOUTH to "Confirm",
            InputButton.EAST to "Cancel"
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (status in CompletionStatus.entries) {
                val isSelected = status.apiValue == effectiveSelection
                val isCurrent = status.apiValue == currentValue

                val borderModifier = if (isCurrent) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }

                ListItem(
                    selected = isSelected,
                    onClick = { },
                    headlineContent = {
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = status.icon,
                            contentDescription = null,
                            tint = status.color,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = borderModifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                )
            }
        }
    }
}
