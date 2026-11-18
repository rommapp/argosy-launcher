package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.ManagedInstallerRow
import com.nendo.argosy.ui.screens.settings.ManagedInstallersState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

internal sealed class InstallerItem(val key: String) {
    data object AddRepo : InstallerItem("addRepo")
    data class Entry(val row: ManagedInstallerRow) : InstallerItem("installer-${row.id}")
    data class Prereleases(val row: ManagedInstallerRow) : InstallerItem("prereleases-${row.id}")
    data class ImeEnable(val row: ManagedInstallerRow) : InstallerItem("ime-enable-${row.id}")
    data class ImeSelect(val row: ManagedInstallerRow) : InstallerItem("ime-select-${row.id}")
    data class Remove(val row: ManagedInstallerRow) : InstallerItem("remove-${row.id}")
}

internal fun installerItems(state: ManagedInstallersState): List<InstallerItem> = buildList {
    add(InstallerItem.AddRepo)
    state.rows.forEach { row ->
        add(InstallerItem.Entry(row))
        add(InstallerItem.Prereleases(row))
        if (row.installed && row.packageName != null && isKeyboardRow(row)) {
            if (!row.imeEnabled) add(InstallerItem.ImeEnable(row))
            if (row.imeEnabled && !row.imeActive) add(InstallerItem.ImeSelect(row))
        }
        if (!row.locked) add(InstallerItem.Remove(row))
    }
}

private fun isKeyboardRow(row: ManagedInstallerRow): Boolean =
    row.packageName == com.nendo.argosy.data.installer.CONTROLLER_KEYBOARD_PACKAGE

internal fun installersMaxFocusIndex(state: ManagedInstallersState): Int =
    (installerItems(state).size - 1).coerceAtLeast(0)

internal fun installerItemAtFocusIndex(index: Int, state: ManagedInstallersState): InstallerItem? =
    installerItems(state).getOrNull(index)

@Composable
fun ManagedInstallersSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val state = uiState.managedInstallers
    val items = remember(state.rows) { installerItems(state) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.reconcileManagedInstall()
    }

    fun isFocused(index: Int): Boolean = uiState.focusedIndex == index

    SectionPaneLayout(
        items = items,
        sections = emptyList(),
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { it },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { false },
        onSectionTap = {},
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        val index = items.indexOf(item)
        when (item) {
            InstallerItem.AddRepo -> ActionPreference(
                title = stringResource(R.string.settings_installers_add_title),
                subtitle = stringResource(R.string.settings_installers_add_subtitle),
                icon = Icons.Default.Add,
                isFocused = isFocused(index),
                onClick = { viewModel.openInstallerAddModal() }
            )

            is InstallerItem.Entry -> {
                val row = item.row
                val busy = state.busyId == row.id
                val statusRes = state.statusRes
                val failedRes = state.failedRes.takeIf { state.failedId == row.id }
                val subtitle = when {
                    busy && statusRes == R.string.settings_installers_status_downloading ->
                        stringResource(statusRes, (state.busyProgress * 100).toInt())
                    busy && statusRes != null -> stringResource(statusRes)
                    failedRes != null -> stringResource(failedRes)
                    row.updateAvailable -> stringResource(
                        R.string.settings_installers_subtitle_update,
                        row.latestSeenTag ?: ""
                    )
                    row.installed && row.tagAtInstall == null ->
                        stringResource(R.string.settings_installers_subtitle_version_unknown)
                    row.installed -> stringResource(
                        R.string.settings_installers_subtitle_installed,
                        row.tagAtInstall ?: ""
                    )
                    else -> row.repoLabel
                }
                ActionPreference(
                    title = row.displayName,
                    subtitle = subtitle,
                    icon = when {
                        busy -> Icons.Default.Autorenew
                        row.locked -> Icons.Default.Lock
                        else -> Icons.Default.Download
                    },
                    isFocused = isFocused(index),
                    isEnabled = !busy,
                    spinIcon = busy,
                    badge = if (row.updateAvailable) {
                        stringResource(R.string.settings_installers_badge_update)
                    } else {
                        null
                    },
                    onClick = { viewModel.installManagedInstaller(row.id) }
                )
            }

            is InstallerItem.Prereleases -> SwitchPreference(
                title = stringResource(R.string.settings_installers_prereleases_title),
                subtitle = stringResource(R.string.settings_installers_prereleases_subtitle),
                icon = Icons.Default.Science,
                isEnabled = item.row.includePrereleases,
                isFocused = isFocused(index),
                onToggle = { viewModel.toggleInstallerPrereleases(item.row.id) }
            )

            is InstallerItem.ImeEnable -> ActionPreference(
                title = stringResource(R.string.settings_installers_ime_enable_title),
                subtitle = stringResource(R.string.settings_installers_ime_enable_subtitle),
                icon = Icons.Default.Keyboard,
                isFocused = isFocused(index),
                onClick = { viewModel.openInstallerImeSettings() }
            )

            is InstallerItem.ImeSelect -> ActionPreference(
                title = stringResource(R.string.settings_installers_ime_select_title),
                subtitle = stringResource(R.string.settings_installers_ime_select_subtitle),
                icon = Icons.Default.Keyboard,
                isFocused = isFocused(index),
                onClick = { viewModel.showInstallerImePicker() }
            )

            is InstallerItem.Remove -> ActionPreference(
                title = stringResource(R.string.settings_installers_remove_title),
                subtitle = stringResource(R.string.settings_installers_remove_subtitle),
                icon = Icons.Default.Delete,
                isFocused = isFocused(index),
                isDangerous = true,
                onClick = { viewModel.requestInstallerRemove(item.row.id) }
            )
        }
    }

    ManagedInstallerModals(state, viewModel)
}

@Composable
private fun ManagedInstallerModals(state: ManagedInstallersState, viewModel: SettingsViewModel) {
    if (state.showAddModal) {
        TextEntryModal(
            title = stringResource(R.string.settings_installers_add_modal_title),
            label = stringResource(R.string.settings_installers_add_modal_label),
            confirmLabel = stringResource(R.string.settings_installers_add_modal_confirm),
            cancelLabel = stringResource(R.string.settings_installers_add_modal_cancel),
            text = state.addText,
            onTextChange = { viewModel.updateInstallerAddText(it) },
            onDismiss = { viewModel.dismissInstallerAddModal() },
            onSubmit = { viewModel.submitInstallerAdd() },
            focus = state.addFocus,
            placeholder = stringResource(R.string.settings_installers_add_modal_placeholder),
            errorMessage = state.addErrorRes?.let { stringResource(it) },
            canSubmit = state.addText.isNotBlank(),
            keyboardType = KeyboardType.Uri
        )
    }

    if (state.showVariantPicker) {
        InstallerVariantModal(
            assetNames = state.variantAssetNames,
            focusIndex = state.variantFocusIndex,
            onItemTap = { viewModel.selectInstallerVariantAt(it) },
            onDismiss = { viewModel.dismissInstallerVariantPicker() }
        )
    }

    ArgosyConfirmModalHost(
        visible = state.confirmRemoveId != null,
        title = stringResource(R.string.settings_installers_remove_confirm_title),
        message = stringResource(
            R.string.settings_installers_remove_confirm_message,
            state.confirmRemoveName
        ),
        confirmLabel = stringResource(R.string.settings_installers_remove_confirm_action),
        destructive = true,
        onConfirm = { viewModel.confirmInstallerRemove() },
        onDismiss = { viewModel.dismissInstallerRemove() }
    )
}

@Composable
private fun InstallerVariantModal(
    assetNames: List<String>,
    focusIndex: Int,
    onItemTap: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusIndex)

    Modal(
        title = stringResource(R.string.settings_installers_variant_title),
        subtitle = stringResource(R.string.settings_installers_variant_subtitle),
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
        ) {
            itemsIndexed(assetNames, key = { _, name -> name }) { index, name ->
                InstallerVariantRow(
                    label = com.nendo.argosy.ui.common.installerVariantLabel(name),
                    isFocused = index == focusIndex,
                    onClick = { onItemTap(index) }
                )
            }
        }
    }
}

@Composable
private fun InstallerVariantRow(label: String, isFocused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm)
    )
}
