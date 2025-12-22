package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.runtime.Composable
import com.nendo.argosy.data.launcher.SteamLauncher
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
        OptionItem(
            label = "Auto",
            isFocused = focusIndex == 0,
            isSelected = currentLauncherName == null || currentLauncherName == "Auto",
            onClick = { onSelectLauncher(null) }
        )

        availableLaunchers.forEachIndexed { index, launcher ->
            OptionItem(
                label = launcher.displayName,
                isFocused = focusIndex == index + 1,
                isSelected = launcher.displayName == currentLauncherName,
                onClick = { onSelectLauncher(launcher) }
            )
        }
    }
}
