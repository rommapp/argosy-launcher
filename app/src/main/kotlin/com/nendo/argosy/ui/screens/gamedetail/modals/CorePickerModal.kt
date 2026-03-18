package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun CorePickerModal(
    availableCores: List<RetroArchCore>,
    selectedCoreId: String?,
    focusIndex: Int,
    onSelectCore: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "SELECT CORE",
        subtitle = "Cores must be installed in RetroArch first",
        onDismiss = onDismiss
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            item {
                OptionItem(
                    label = "Use Platform Default",
                    isFocused = focusIndex == 0,
                    isSelected = selectedCoreId == null,
                    onClick = { onSelectCore(null) }
                )
            }
            itemsIndexed(availableCores) { index, core ->
                OptionItem(
                    label = core.displayName,
                    value = core.id,
                    isFocused = focusIndex == index + 1,
                    isSelected = core.id == selectedCoreId,
                    onClick = { onSelectCore(core.id) }
                )
            }
        }
    }
}
