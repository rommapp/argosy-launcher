package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun SteamLauncherPickerModal(
    availableLaunchers: List<SteamLauncher>,
    currentLauncherName: String?,
    focusIndex: Int,
    onSelectLauncher: (SteamLauncher?) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "SELECT LAUNCHER",
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
                    label = "Auto",
                    isFocused = focusIndex == 0,
                    isSelected = currentLauncherName == null || currentLauncherName == "Auto",
                    onClick = { onSelectLauncher(null) }
                )
            }
            itemsIndexed(availableLaunchers) { index, launcher ->
                OptionItem(
                    label = launcher.displayName,
                    isFocused = focusIndex == index + 1,
                    isSelected = launcher.displayName == currentLauncherName,
                    onClick = { onSelectLauncher(launcher) }
                )
            }
        }
    }
}
