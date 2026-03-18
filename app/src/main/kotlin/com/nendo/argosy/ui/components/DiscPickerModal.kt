package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun DiscPickerModal(
    discs: List<DiscOption>,
    focusIndex: Int,
    onSelectDisc: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "SELECT DISC",
        subtitle = "Choose which disc to launch",
        onDismiss = onDismiss
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(discs) { index, disc ->
                OptionItem(
                    label = "Disc ${disc.discNumber}",
                    value = disc.fileName,
                    isFocused = focusIndex == index,
                    onClick = { onSelectDisc(disc.filePath) }
                )
            }
        }
    }
}
