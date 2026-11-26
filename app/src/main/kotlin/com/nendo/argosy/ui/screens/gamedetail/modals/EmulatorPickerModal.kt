package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.common.BuiltinCoreSummary
import com.nendo.argosy.ui.common.builtinCoreSubtitle
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun EmulatorPickerModal(
    availableEmulators: List<InstalledEmulator>,
    builtinCore: BuiltinCoreSummary?,
    currentEmulatorName: String?,
    focusIndex: Int,
    onSelectEmulator: (InstalledEmulator?) -> Unit,
    onSelectOtherApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val otherAppSelected = currentEmulatorName != null &&
        availableEmulators.none { it.def.displayName == currentEmulatorName }
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
            itemsIndexed(availableEmulators, key = { _, emulator -> emulator.def.id }) { index, emulator ->
                val coreLine = if (emulator.def.id == EmulatorRegistry.BUILTIN_ID && builtinCore != null) {
                    builtinCoreSubtitle(builtinCore, R.string.gamedetail_emulator_picker_builtin_core_build)
                } else {
                    null
                }
                OptionItem(
                    label = emulator.def.displayName,
                    subtext = coreLine,
                    value = emulator.versionName,
                    isFocused = focusIndex == index + 1,
                    isSelected = emulator.def.displayName == currentEmulatorName,
                    onClick = { onSelectEmulator(emulator) }
                )
            }
            item {
                OptionItem(
                    label = stringResource(R.string.gamedetail_emulator_picker_other_app),
                    subtext = if (otherAppSelected) {
                        currentEmulatorName
                    } else {
                        stringResource(R.string.gamedetail_emulator_picker_other_app_subtitle)
                    },
                    isFocused = focusIndex == availableEmulators.size + 1,
                    isSelected = otherAppSelected,
                    onClick = onSelectOtherApp
                )
            }
        }
    }
}
