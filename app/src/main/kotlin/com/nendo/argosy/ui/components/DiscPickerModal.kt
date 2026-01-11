package com.nendo.argosy.ui.components

import androidx.compose.runtime.Composable
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
        discs.forEachIndexed { index, disc ->
            OptionItem(
                label = "Disc ${disc.discNumber}",
                value = disc.fileName,
                isFocused = focusIndex == index,
                onClick = { onSelectDisc(disc.filePath) }
            )
        }
    }
}
