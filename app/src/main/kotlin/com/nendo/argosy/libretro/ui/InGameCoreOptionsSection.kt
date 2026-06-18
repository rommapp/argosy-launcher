package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.CoreOptionViewItem
import com.nendo.argosy.ui.theme.Dimens

@Composable
internal fun InGameCoreOptionsSection(
    options: List<CoreOptionViewItem>,
    focusedIndex: Int,
    onCycle: (String) -> Unit,
    onReset: (String) -> Unit,
    listState: LazyListState,
    perGameToggleVisible: Boolean = false,
    perGameEnabled: Boolean = false,
    onTogglePerGame: (Boolean) -> Unit = {}
) {
    FocusedScroll(listState = listState, focusedIndex = focusedIndex)

    val offset = if (perGameToggleVisible) 1 else 0

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (perGameToggleVisible) {
            item(key = "__per_game_settings_toggle__") {
                SwitchPreference(
                    title = "Game-specific settings",
                    subtitle = if (perGameEnabled) {
                        "Changes apply to this game only"
                    } else {
                        "Using global defaults for this core"
                    },
                    isEnabled = perGameEnabled,
                    isFocused = focusedIndex == 0,
                    onToggle = onTogglePerGame
                )
            }
        }
        itemsIndexed(options, key = { _, option -> option.key }) { index, option ->
            val isFocused = index + offset == focusedIndex
            CyclePreference(
                title = option.displayName,
                value = option.displayValue,
                isFocused = isFocused,
                onClick = { onCycle(option.key) },
                subtitle = option.description,
                isCustom = option.isOverridden,
                showResetButton = option.isOverridden && isFocused,
                onReset = { onReset(option.key) }
            )
        }
    }
}
