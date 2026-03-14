package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.settings.CoreOptionsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.DisabledBehavior
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class CoreOptionItem(val key: String) {
    val isFocusable: Boolean get() = this !is NotInstalledNotice

    data object CoreSelector : CoreOptionItem("core_selector")

    data object NotInstalledNotice : CoreOptionItem("not_installed")

    data class Option(
        val optionKey: String,
        val displayName: String,
        val description: String?,
        val values: List<String>,
        val currentValue: String,
        val isOverridden: Boolean,
        val displayValue: String = currentValue
    ) : CoreOptionItem(optionKey)

    data object ResetAll : CoreOptionItem("reset_all")
}

internal fun buildCoreOptionItems(state: CoreOptionsState): List<CoreOptionItem> = buildList {
    add(CoreOptionItem.CoreSelector)
    val core = state.selectedCore ?: return@buildList
    if (!core.isInstalled) {
        add(CoreOptionItem.NotInstalledNotice)
    }
    val hasManifest = CoreOptionManifestRegistry.hasManifest(core.coreId)
    if (hasManifest) {
        for (option in state.options) {
            add(
                CoreOptionItem.Option(
                    optionKey = option.key,
                    displayName = option.displayName,
                    description = option.description,
                    values = option.values,
                    currentValue = option.currentValue,
                    isOverridden = option.isOverridden,
                    displayValue = option.displayValue
                )
            )
        }
        if (state.overrides.isNotEmpty() && core.isInstalled) {
            add(CoreOptionItem.ResetAll)
        }
    }
}

internal fun createCoreOptionsLayout(
    items: List<CoreOptionItem>,
    isInstalled: Boolean
) = SettingsLayout<CoreOptionItem, Unit>(
    allItems = items,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    disabledBehavior = { item ->
        if (item is CoreOptionItem.Option && !isInstalled) DisabledBehavior.LOCKED
        else DisabledBehavior.HIDDEN
    }
)

internal fun coreOptionsMaxFocusIndex(state: CoreOptionsState): Int {
    val items = buildCoreOptionItems(state)
    val isInstalled = state.selectedCore?.isInstalled == true
    return createCoreOptionsLayout(items, isInstalled).maxFocusIndex(Unit)
}

internal fun coreOptionsItemAtFocusIndex(index: Int, state: CoreOptionsState): CoreOptionItem? {
    val items = buildCoreOptionItems(state)
    val isInstalled = state.selectedCore?.isInstalled == true
    return createCoreOptionsLayout(items, isInstalled).itemAtFocusIndex(index, Unit)
}

@Composable
fun CoreOptionsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val coreState = uiState.coreOptions
    val selectedCore = coreState.selectedCore
    val isInstalled = selectedCore?.isInstalled == true

    val items = remember(coreState) { buildCoreOptionItems(coreState) }
    val layout = remember(items, isInstalled) { createCoreOptionsLayout(items, isInstalled) }
    val visibleItems = remember(layout) { layout.visibleItems(Unit) }

    FocusedScroll(
        listState = listState,
        focusedIndex = layout.focusToListIndex(uiState.focusedIndex, Unit)
    )

    if (coreState.availablePlatforms.isEmpty()) {
        Text(
            text = "No platforms with built-in emulator support",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Dimens.spacingMd)
        )
        return
    }

    val coreSelectorValue = if (selectedCore != null) {
        val status = if (selectedCore.isInstalled) "Installed" else "Not Downloaded"
        "${selectedCore.displayName} ($status)"
    } else {
        "No cores available"
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        items(visibleItems.size, key = { visibleItems[it].key }) { index ->
            val item = visibleItems[index]
            val focusIndex = layout.focusIndexOf(item, Unit)
            val isFocused = focusIndex == uiState.focusedIndex

            when (item) {
                is CoreOptionItem.CoreSelector -> {
                    CyclePreference(
                        title = "Core",
                        value = coreSelectorValue,
                        isFocused = isFocused,
                        onClick = { viewModel.cycleCoreSelector(1) }
                    )
                }

                is CoreOptionItem.NotInstalledNotice -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    Text(
                        text = "Core not downloaded -- options are read-only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                }

                is CoreOptionItem.Option -> {
                    val isLocked = !isInstalled
                    CyclePreference(
                        title = item.displayName,
                        value = item.displayValue,
                        isFocused = isFocused && !isLocked,
                        onClick = {
                            if (!isLocked) viewModel.cycleCoreOptionValue(item.optionKey, 1)
                        },
                        subtitle = item.description,
                        isCustom = item.isOverridden,
                        showResetButton = item.isOverridden && isFocused,
                        onReset = { viewModel.resetCoreOption(item.optionKey) }
                    )
                }

                is CoreOptionItem.ResetAll -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    OptionItem(
                        label = "Reset All to Defaults",
                        isFocused = isFocused,
                        isDangerous = true,
                        onClick = { viewModel.resetAllCoreOptions() }
                    )
                }
            }
        }
    }
}
