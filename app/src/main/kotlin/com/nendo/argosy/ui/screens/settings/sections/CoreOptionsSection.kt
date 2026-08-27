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
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import com.nendo.argosy.ui.components.ActionPreference
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
    val isFocusable: Boolean get() = true

    data object CoreSelector : CoreOptionItem("core_selector")

    data object DownloadCore : CoreOptionItem("download_core")

    data object DeleteCore : CoreOptionItem("delete_core")

    data class Option(
        val optionKey: String,
        val displayName: String,
        val description: String?,
        val values: List<String>,
        val currentValue: String,
        val isOverridden: Boolean,
        val displayValue: String = currentValue,
        val valueLabels: Map<String, String> = emptyMap()
    ) : CoreOptionItem(optionKey) {
        fun displayValueFor(value: String): String = valueLabels[value] ?: value
    }

    data object ResetAll : CoreOptionItem("reset_all")
}

internal fun buildCoreOptionItems(state: CoreOptionsState): List<CoreOptionItem> = buildList {
    add(CoreOptionItem.CoreSelector)
    val core = state.selectedCore ?: return@buildList
    add(CoreOptionItem.DownloadCore)
    if (core.isInstalled) add(CoreOptionItem.DeleteCore)
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
                    displayValue = option.displayValue,
                    valueLabels = option.valueLabels
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
    isFocusable = { item -> item.isFocusable && !(item is CoreOptionItem.Option && !isInstalled) },
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
            text = stringResource(R.string.settings_shell_coreoptions_no_platforms),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Dimens.spacingMd)
        )
        return
    }

    val installedSelectorStatus = stringResource(R.string.settings_shell_coreoptions_status_installed_selector)
    val notDownloadedSelectorStatus = stringResource(R.string.settings_shell_coreoptions_status_not_downloaded_selector)
    val installedOptionStatus = stringResource(R.string.settings_shell_coreoptions_status_installed_option)
    val notDownloadedOptionStatus = stringResource(R.string.settings_shell_coreoptions_status_not_downloaded_option)
    val selectorValueTemplate = stringResource(R.string.settings_shell_coreoptions_selector_value_template)
    val optionValueTemplate = stringResource(R.string.settings_shell_coreoptions_option_value_template)
    val noCoresAvailable = stringResource(R.string.settings_shell_coreoptions_none_available)

    val coreSelectorValue = if (selectedCore != null) {
        val status = if (selectedCore.isInstalled) installedSelectorStatus else notDownloadedSelectorStatus
        selectorValueTemplate.format(selectedCore.displayName, status)
    } else {
        noCoresAvailable
    }
    val coreSelectorOptions = remember(
        coreState.coresForCurrentPlatform,
        installedOptionStatus,
        notDownloadedOptionStatus,
        optionValueTemplate
    ) {
        coreState.coresForCurrentPlatform.map { core ->
            val status = if (core.isInstalled) installedOptionStatus else notDownloadedOptionStatus
            optionValueTemplate.format(core.displayName, status)
        }
    }

    fun pickerToken(key: String): Int =
        if (uiState.enumPickerKey == key) uiState.enumPickerToken else 0

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
                        title = stringResource(R.string.settings_shell_coreoptions_core_title),
                        value = coreSelectorValue,
                        isFocused = isFocused,
                        onClick = { viewModel.cycleCoreSelector(1) },
                        onPrev = { viewModel.cycleCoreSelector(-1) },
                        options = coreSelectorOptions.takeIf { it.isNotEmpty() },
                        onSelect = { viewModel.cycleCoreSelector(it - coreState.selectedCoreIndex) },
                        pickerRequestToken = pickerToken(item.key)
                    )
                }

                is CoreOptionItem.DownloadCore -> {
                    val isDownloadingThis = coreState.isDownloading &&
                        coreState.downloadingCoreId == selectedCore?.coreId
                    val title = stringResource(
                        if (isInstalled) R.string.settings_shell_coreoptions_redownload
                        else R.string.settings_shell_coreoptions_download
                    )
                    val subtitle = when {
                        isDownloadingThis -> stringResource(R.string.settings_shell_coreoptions_downloading)
                        isInstalled -> stringResource(R.string.settings_shell_coreoptions_reinstall_subtitle)
                        else -> stringResource(R.string.settings_shell_coreoptions_download_subtitle)
                    }
                    ActionPreference(
                        title = title,
                        subtitle = subtitle,
                        isFocused = isFocused,
                        onClick = {
                            if (!isDownloadingThis) {
                                selectedCore?.let { viewModel.downloadCoreWithNotification(it.coreId) }
                            }
                        }
                    )
                }

                is CoreOptionItem.DeleteCore -> {
                    ActionPreference(
                        title = stringResource(R.string.settings_shell_coreoptions_delete_core),
                        subtitle = stringResource(R.string.settings_shell_coreoptions_delete_core_subtitle),
                        isFocused = isFocused,
                        isDangerous = true,
                        onClick = {
                            selectedCore?.let { viewModel.requestDeleteCore(it.coreId) }
                        }
                    )
                }

                is CoreOptionItem.Option -> {
                    val isLocked = !isInstalled
                    val currentValueIndex = item.values.indexOf(item.currentValue).coerceAtLeast(0)
                    CyclePreference(
                        title = item.displayName,
                        value = item.displayValue,
                        isFocused = isFocused && !isLocked,
                        onClick = {
                            if (!isLocked) viewModel.cycleCoreOptionValue(item.optionKey, 1)
                        },
                        onPrev = {
                            if (!isLocked) viewModel.cycleCoreOptionValue(item.optionKey, -1)
                        },
                        subtitle = item.description,
                        isCustom = item.isOverridden,
                        showResetButton = item.isOverridden && isFocused,
                        onReset = { viewModel.resetCoreOption(item.optionKey) },
                        options = if (!isLocked && item.values.size > 1) item.values.map { item.displayValueFor(it) } else null,
                        onSelect = { viewModel.cycleCoreOptionValue(item.optionKey, it - currentValueIndex) },
                        pickerRequestToken = pickerToken(item.optionKey)
                    )
                }

                is CoreOptionItem.ResetAll -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    OptionItem(
                        label = stringResource(R.string.settings_shell_coreoptions_reset_all),
                        isFocused = isFocused,
                        isDangerous = true,
                        onClick = { viewModel.resetAllCoreOptions() }
                    )
                }
            }
        }
    }
}
