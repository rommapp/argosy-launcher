package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.common.color
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.components.FocusedScroll
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
    val focusedIndex = CompletionStatus.entries.indexOfFirst {
        it.apiValue == effectiveSelection
    }.coerceAtLeast(0)

    CenteredModal(
        title = "SET STATUS",
        baseWidth = 320.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_VERTICAL to "Navigate",
            InputButton.A to "Confirm",
            InputButton.B to "Cancel"
        )
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusedIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(CompletionStatus.entries.size) { index ->
                val status = CompletionStatus.entries[index]
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
