package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.runtime.Composable
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun EmulatorPickerModal(
    availableEmulators: List<InstalledEmulator>,
    currentEmulatorName: String?,
    focusIndex: Int,
    onSelectEmulator: (InstalledEmulator?) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "SELECT EMULATOR",
        onDismiss = onDismiss
    ) {
        OptionItem(
            label = "Use Platform Default",
            isFocused = focusIndex == 0,
            isSelected = currentEmulatorName == null,
            onClick = { onSelectEmulator(null) }
        )

        availableEmulators.forEachIndexed { index, emulator ->
            OptionItem(
                label = emulator.def.displayName,
                value = emulator.versionName,
                isFocused = focusIndex == index + 1,
                isSelected = emulator.def.displayName == currentEmulatorName,
                onClick = { onSelectEmulator(emulator) }
            )
        }
    }
}
