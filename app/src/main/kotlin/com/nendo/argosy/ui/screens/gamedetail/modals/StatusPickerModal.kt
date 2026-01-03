package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun StatusPickerModal(
    selectedValue: String?,
    currentValue: String?,
    onSelect: (String) -> Unit,
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

                OptionItem(
                    icon = status.icon,
                    iconTint = status.color,
                    label = status.label,
                    isFocused = isSelected,
                    isSelected = isCurrent,
                    onClick = { onSelect(status.apiValue) }
                )
            }
        }
    }
}
