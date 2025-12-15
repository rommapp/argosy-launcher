package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.runtime.Composable
import com.nendo.argosy.data.emulator.RetroArchCore
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
        OptionItem(
            label = "Use Platform Default",
            isFocused = focusIndex == 0,
            isSelected = selectedCoreId == null,
            onClick = { onSelectCore(null) }
        )

        availableCores.forEachIndexed { index, core ->
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
