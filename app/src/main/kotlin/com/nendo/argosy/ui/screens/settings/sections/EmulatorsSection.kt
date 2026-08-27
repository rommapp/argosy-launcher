package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.VariantPickerModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

internal sealed class EmulatorsItem(
    val key: String,
    val section: String,
    open val isFocusable: Boolean = true
) {
    data object CheckForUpdates : EmulatorsItem("check_updates", "platforms")

    class SectionHeader(key: String, section: String, val titleRes: Int) : EmulatorsItem(
        key = key, section = section
    ) { override val isFocusable = false }

    class PlatformItem(val config: PlatformEmulatorConfig, val index: Int) : EmulatorsItem(
        key = "platform_${config.platform.id}",
        section = if (config.platform.syncEnabled) "platforms" else "disabled"
    )

    companion object {
        fun buildItems(platforms: List<PlatformEmulatorConfig>): List<EmulatorsItem> {
            val active = platforms.filter { it.platform.syncEnabled }
            val disabled = platforms.filter { !it.platform.syncEnabled }
            return buildList {
                add(SectionHeader("header_active", "platforms", R.string.settings_emulators_section_active))
                add(CheckForUpdates)
                active.forEach { config ->
                    add(PlatformItem(config, platforms.indexOf(config)))
                }
                if (disabled.isNotEmpty()) {
                    add(SectionHeader("header_disabled", "disabled", R.string.settings_emulators_section_disabled))
                    disabled.forEach { config ->
                        add(PlatformItem(config, platforms.indexOf(config)))
                    }
                }
            }
        }
    }
}

internal fun createEmulatorsLayout(items: List<EmulatorsItem>) = SettingsLayout<EmulatorsItem, Unit>(
    allItems = items,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "platforms" -> R.string.settings_emulators_rail_platforms
            "disabled" -> R.string.settings_emulators_section_disabled
            else -> null
        }
    }
)

internal fun emulatorsMaxFocusIndex(platforms: List<PlatformEmulatorConfig>): Int =
    createEmulatorsLayoutInfo(platforms).layout.maxFocusIndex(Unit)

internal data class EmulatorsLayoutInfo(
    val layout: SettingsLayout<EmulatorsItem, Unit>,
    val state: Unit = Unit
)

internal fun createEmulatorsLayoutInfo(
    platforms: List<PlatformEmulatorConfig>
): EmulatorsLayoutInfo {
    val items = EmulatorsItem.buildItems(platforms)
    val layout = createEmulatorsLayout(items)
    return EmulatorsLayoutInfo(layout)
}

internal fun emulatorsSections(info: EmulatorsLayoutInfo) = info.layout.buildSections(Unit)

internal fun emulatorsItemAtFocusIndex(index: Int, info: EmulatorsLayoutInfo): EmulatorsItem? =
    info.layout.itemAtFocusIndex(index, info.state)

@Composable
fun EmulatorsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onLaunchSavePathPicker: () -> Unit
) {
    val emulators = uiState.emulators

    val allItems = remember(emulators.platforms) {
        EmulatorsItem.buildItems(emulators.platforms)
    }

    val context = LocalContext.current
    val layout = remember(allItems) { createEmulatorsLayout(allItems) }
    val visibleItems = remember(allItems) { layout.visibleItems(Unit) }
    val sections = remember(allItems, context) { layout.buildSections(Unit, context) }

    fun isFocused(item: EmulatorsItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    fun openFrom(item: EmulatorsItem, enter: () -> Unit) {
        viewModel.setFocusIndex(layout.focusIndexOf(item, Unit))
        enter()
    }

    val modalBlur by animateDpAsState(
        targetValue = if (emulators.showEmulatorPicker || emulators.showSavePathModal || emulators.showVariantPicker || emulators.updateModal != null) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "emulatorPickerBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { layout.focusToListIndex(it, Unit) },
            itemKey = { it.key },
            isNavItem = { false },
            isHeader = { it is EmulatorsItem.SectionHeader },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
                when (item) {
                    is EmulatorsItem.SectionHeader ->
                        com.nendo.argosy.ui.screens.settings.components.SectionHeader(
                            stringResource(item.titleRes)
                        )

                    EmulatorsItem.CheckForUpdates -> ActionPreference(
                        title = stringResource(R.string.settings_emulators_check_updates_title),
                        subtitle = if (emulators.assignedUpdatesAvailable > 0) {
                            pluralStringResource(
                                R.plurals.settings_emulators_check_updates_available,
                                emulators.assignedUpdatesAvailable,
                                emulators.assignedUpdatesAvailable
                            )
                        } else {
                            stringResource(R.string.settings_emulators_check_updates_subtitle)
                        },
                        isFocused = isFocused(item),
                        onClick = { viewModel.forceCheckEmulatorUpdates() }
                    )

                    is EmulatorsItem.PlatformItem -> {
                        val config = item.config
                        val isDisabled = !config.platform.syncEnabled
                        val gameCount = config.platform.gameCount
                        val emulatorName = config.effectiveEmulatorName
                            ?: stringResource(R.string.settings_emulators_platform_unconfigured)
                        val subtitle = if (isDisabled) {
                            stringResource(R.string.settings_emulators_platform_disabled)
                        } else if (gameCount > 0) {
                            pluralStringResource(
                                R.plurals.settings_emulators_platform_games,
                                gameCount,
                                gameCount
                            )
                        } else {
                            stringResource(R.string.settings_emulators_platform_no_games)
                        }
                        val hasUpdate = !isDisabled && config.effectiveEmulatorId != null &&
                            config.effectiveEmulatorId in emulators.emulatorUpdateVersions
                        ActionPreference(
                            title = config.platform.name,
                            subtitle = subtitle,
                            isFocused = isFocused(item),
                            trailingText = if (isDisabled) null else emulatorName,
                            badge = if (hasUpdate) {
                                stringResource(R.string.settings_emulators_platform_update_badge)
                            } else {
                                null
                            },
                            isEnabled = !isDisabled,
                            onClick = { openFrom(item) { viewModel.navigateToPlatformDetail(item.index) } }
                        )
                    }
                }
        }

        if (emulators.showEmulatorPicker && emulators.emulatorPickerInfo != null) {
            EmulatorPickerPopup(
                info = emulators.emulatorPickerInfo,
                focusIndex = emulators.emulatorPickerFocusIndex,
                selectedIndex = emulators.emulatorPickerSelectedIndex,
                onItemTap = { index -> viewModel.handleEmulatorPickerItemTap(index) },
                onConfirm = { viewModel.confirmEmulatorPickerSelection() },
                onDismiss = { viewModel.dismissEmulatorPicker() }
            )
        }

        if (emulators.showSavePathModal && emulators.savePathModalInfo != null) {
            SavePathModal(
                info = emulators.savePathModalInfo,
                focusIndex = emulators.savePathModalFocusIndex,
                buttonFocusIndex = emulators.savePathModalButtonIndex,
                onDismiss = { viewModel.dismissSavePathModal() },
                onChangeSavePath = onLaunchSavePathPicker,
                onResetSavePath = {
                    viewModel.resetEmulatorSavePath(emulators.savePathModalInfo.emulatorId)
                },
                onToggleBesideRom = { viewModel.toggleSavesBesideRom() }
            )
        }

        if (emulators.showVariantPicker && emulators.variantPickerInfo != null) {
            VariantPickerModal(
                info = emulators.variantPickerInfo,
                focusIndex = emulators.variantPickerFocusIndex,
                onItemTap = { index -> viewModel.handleVariantPickerItemTap(index) },
                onConfirm = { viewModel.confirmVariantSelection() },
                onDismiss = { viewModel.dismissVariantPicker() }
            )
        }

        if (emulators.updateModal != null) {
            com.nendo.argosy.ui.screens.settings.components.EmulatorUpdateModal(
                modal = emulators.updateModal,
                focusIndex = emulators.updateModalFocusIndex,
                onVariantTap = { index -> viewModel.moveUpdateModalFocus(index - emulators.updateModalFocusIndex) },
                onConfirmVariant = { viewModel.selectUpdateModalVariant() },
                onDismiss = { viewModel.dismissUpdateModal() }
            )
        }
    }
}
