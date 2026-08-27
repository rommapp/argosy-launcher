package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.components.FocusedScroll
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
        title = stringResource(R.string.gamedetail_emulator_picker_title),
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
                    label = stringResource(R.string.gamedetail_emulator_picker_platform_default),
                    isFocused = focusIndex == 0,
                    isSelected = currentEmulatorName == null,
                    onClick = { onSelectEmulator(null) }
                )
            }
            itemsIndexed(availableEmulators) { index, emulator ->
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
}
