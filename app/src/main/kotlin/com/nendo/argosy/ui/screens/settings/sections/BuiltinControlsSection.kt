package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.data.repository.MappingPlatforms
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.coreoptions.CoreControlManifestRegistry
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.settings.BuiltinControlsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.ControllerOrderModal
import com.nendo.argosy.ui.screens.settings.components.HotkeysModal
import com.nendo.argosy.ui.screens.settings.components.InputMappingModal
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class BuiltinControlsItem(
    val key: String,
    val section: String,
    val visibleWhen: (BuiltinControlsState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (BuiltinControlsState) -> Boolean = { true }
    ) : BuiltinControlsItem(key, section, visibleWhen)

    data object ControllerOrder : BuiltinControlsItem("controllerOrder", "controllers")
    data object InputMapping : BuiltinControlsItem("inputMapping", "controllers")
    data object Rumble : BuiltinControlsItem("rumble", "controllers", { it.showRumble })
    data object AnalogAsDpad : BuiltinControlsItem("analogAsDpad", "sticks", { it.showStickMappings })
    data object DpadAsAnalog : BuiltinControlsItem("dpadAsAnalog", "sticks", { it.showStickMappings && it.showDpadAsAnalog })
    data object Hotkeys : BuiltinControlsItem("hotkeys", "hotkeys")
    data object LimitHotkeysToPlayer1 : BuiltinControlsItem("limitHotkeys", "hotkeys")
    data object ToggleFastForward : BuiltinControlsItem("toggleFastForward", "hotkeys")
    data object PreserveFastForwardPitch : BuiltinControlsItem("preserveFastForwardPitch", "hotkeys")
    data object ResetAllToGlobal : BuiltinControlsItem("resetAllToGlobal", "hotkeys", { it.showResetAll })

    data object SpeedrunStartOnReset : BuiltinControlsItem("speedrunStartOnReset", "speedrun")
    data object SpeedrunPanelSide : BuiltinControlsItem("speedrunPanelSide", "speedrun")
    data object SpeedrunPanelWidth : BuiltinControlsItem("speedrunPanelWidth", "speedrun")

    data object TouchEnabled : BuiltinControlsItem("touchEnabled", "touchControls")
    data object TouchOpacityLandscape : BuiltinControlsItem("touchOpacityLandscape", "touchControls", { it.touchEnabled })
    data object TouchOpacityPortrait : BuiltinControlsItem("touchOpacityPortrait", "touchControls", { it.touchEnabled })
    data object TouchSizeScale : BuiltinControlsItem("touchSizeScale", "touchControls", { it.touchEnabled })
    data object TouchHaptic : BuiltinControlsItem("touchHaptic", "touchControls", { it.touchEnabled })
    data object TouchFadeOnIdle : BuiltinControlsItem("touchFadeOnIdle", "touchControls", { it.touchEnabled })
    data object TouchSwapHanded : BuiltinControlsItem("touchSwapHanded", "touchControls", { it.touchEnabled })
    data object TouchLockOrientation : BuiltinControlsItem("touchLockOrientation", "touchControls")
    data object TouchMirror180 : BuiltinControlsItem("touchMirror180", "touchControls", { it.touchEnabled })
    data object TouchColouredFaceButtons : BuiltinControlsItem("touchColouredFaceButtons", "touchControls", { it.touchEnabled })
    data object TouchGenesis6Button : BuiltinControlsItem("touchGenesis6Button", "touchControls")
    data object TouchCustomizeLayouts : BuiltinControlsItem("touchCustomizeLayouts", "touchControls", { it.touchEnabled })

    companion object {
        private val ControllersHeader = Header(
            "controllersHeader",
            "controllers",
            R.string.settings_builtin_controls_section_controllers
        )
        private val SticksHeader = Header(
            "sticksHeader",
            "sticks",
            R.string.settings_builtin_controls_section_sticks
        ) { it.showStickMappings }
        private val HotkeysHeader =
            Header("hotkeysHeader", "hotkeys", R.string.settings_builtin_controls_section_hotkeys)
        private val SpeedrunHeader =
            Header("speedrunHeader", "speedrun", R.string.settings_builtin_controls_section_speedrun)
        private val TouchHeader =
            Header("touchControlsHeader", "touchControls", R.string.settings_builtin_controls_section_touch)

        val ALL: List<BuiltinControlsItem>
            get() = listOf(
                ControllersHeader,
                ControllerOrder,
                InputMapping,
                Rumble,
                SticksHeader,
                AnalogAsDpad,
                DpadAsAnalog,
                HotkeysHeader,
                Hotkeys,
                LimitHotkeysToPlayer1,
                ToggleFastForward,
                PreserveFastForwardPitch,
                ResetAllToGlobal,
                SpeedrunHeader,
                SpeedrunStartOnReset,
                SpeedrunPanelSide,
                SpeedrunPanelWidth,
                TouchHeader,
                TouchEnabled,
                TouchOpacityLandscape,
                TouchOpacityPortrait,
                TouchSizeScale,
                TouchHaptic,
                TouchFadeOnIdle,
                TouchSwapHanded,
                TouchLockOrientation,
                TouchMirror180,
                TouchColouredFaceButtons,
                TouchGenesis6Button,
                TouchCustomizeLayouts
            )
    }
}

private val builtinControlsLayout = SettingsLayout<BuiltinControlsItem, BuiltinControlsState>(
    allItems = BuiltinControlsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "controllers" -> R.string.settings_builtin_controls_section_controllers
            "sticks" -> R.string.settings_builtin_controls_section_sticks
            "hotkeys" -> R.string.settings_builtin_controls_section_hotkeys
            "speedrun" -> R.string.settings_builtin_controls_section_speedrun
            "touchControls" -> R.string.settings_builtin_controls_section_touch
            else -> null
        }
    }
)

internal fun builtinControlsMaxFocusIndex(state: BuiltinControlsState): Int =
    builtinControlsLayout.maxFocusIndex(state)

internal fun builtinControlsItemAtFocusIndex(index: Int, state: BuiltinControlsState): BuiltinControlsItem? =
    builtinControlsLayout.itemAtFocusIndex(index, state)

internal fun builtinControlsSections(state: BuiltinControlsState) =
    builtinControlsLayout.buildSections(state)

@Composable
fun BuiltinControlsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val controlsState = uiState.builtinControls
    val context = LocalContext.current
    val controllerOrder by viewModel.getControllerOrder().collectAsState(initial = emptyList())
    val hotkeys by viewModel.observeHotkeys().collectAsState(initial = emptyList())

    val videoState = uiState.builtinVideo
    val isGlobal = videoState.isGlobalContext
    val platformContext = videoState.currentPlatformContext
    val platformSettings = platformContext?.let {
        uiState.platformLibretro.platformSettings[it.platformId]
    }
    val effectiveRumble = if (isGlobal) controlsState.rumbleEnabled
        else platformSettings?.rumbleEnabled ?: controlsState.rumbleEnabled
    val platformSlug = platformContext?.platformSlug
    val platformHasAnalog = platformSlug != null && PlatformWeightRegistry.hasAnalogStick(platformSlug)
    val effectiveAnalogAsDpad = if (isGlobal) controlsState.analogAsDpad
        else platformSettings?.analogAsDpad
            ?: !platformHasAnalog
    val effectiveDpadAsAnalog = if (isGlobal) controlsState.dpadAsAnalog
        else platformSettings?.dpadAsAnalog ?: false

    val visibleItems = remember(controlsState) {
        builtinControlsLayout.visibleItems(controlsState)
    }
    val sections = remember(controlsState, context) {
        builtinControlsLayout.buildSections(controlsState, context)
    }

    fun isFocused(item: BuiltinControlsItem): Boolean =
        uiState.focusedIndex == builtinControlsLayout.focusIndexOf(item, controlsState)

    Box(modifier = Modifier.fillMaxSize()) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { builtinControlsLayout.focusToListIndex(it, controlsState) },
            itemKey = { it.key },
            isNavItem = { false },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
            when (item) {
                is BuiltinControlsItem.Header -> {
                    if (item.section != "controllers") {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    }
                    Text(
                        text = stringResource(item.titleRes).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = Dimens.spacingSm,
                            top = Dimens.spacingXs,
                            bottom = Dimens.spacingXs
                        )
                    )
                }

                BuiltinControlsItem.Rumble -> SwitchPreference(
                    title = stringResource(R.string.settings_builtin_controls_rumble_title),
                    subtitle = if (!isGlobal && platformSettings?.rumbleEnabled != null) {
                        stringResource(R.string.settings_builtin_controls_platform_override)
                    } else {
                        stringResource(R.string.settings_builtin_controls_rumble_subtitle)
                    },
                    isEnabled = effectiveRumble,
                    isFocused = isFocused(item),
                    onToggle = {
                        if (isGlobal) viewModel.setBuiltinRumbleEnabled(it)
                        else viewModel.updatePlatformControlSetting("rumbleEnabled", it)
                    }
                )

                BuiltinControlsItem.ControllerOrder -> NavigationPreference(
                    icon = Icons.Default.SortByAlpha,
                    title = stringResource(R.string.settings_builtin_controls_order_title),
                    subtitle = if (controllerOrder.isNotEmpty()) {
                        pluralStringResource(
                            R.plurals.settings_builtin_controls_order_assigned,
                            controllerOrder.size,
                            controllerOrder.size
                        )
                    } else {
                        stringResource(R.string.settings_builtin_controls_order_subtitle)
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.showControllerOrderModal() }
                )

                BuiltinControlsItem.InputMapping -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = stringResource(R.string.settings_builtin_controls_mapping_title),
                    subtitle = stringResource(R.string.settings_builtin_controls_mapping_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.showInputMappingModal() }
                )

                BuiltinControlsItem.AnalogAsDpad -> SwitchPreference(
                    title = stringResource(R.string.settings_builtin_controls_analog_as_dpad_title),
                    subtitle = if (!isGlobal && platformSettings?.analogAsDpad != null) {
                        stringResource(R.string.settings_builtin_controls_platform_override)
                    } else if (!isGlobal && !platformHasAnalog) {
                        stringResource(R.string.settings_builtin_controls_platform_default)
                    } else {
                        stringResource(R.string.settings_builtin_controls_analog_as_dpad_subtitle)
                    },
                    isEnabled = effectiveAnalogAsDpad,
                    isFocused = isFocused(item),
                    onToggle = {
                        if (isGlobal) viewModel.setBuiltinAnalogAsDpad(it)
                        else viewModel.updatePlatformControlSetting("analogAsDpad", it)
                    }
                )

                BuiltinControlsItem.DpadAsAnalog -> SwitchPreference(
                    title = stringResource(R.string.settings_builtin_controls_dpad_as_analog_title),
                    subtitle = if (!isGlobal && platformSettings?.dpadAsAnalog != null) {
                        stringResource(R.string.settings_builtin_controls_platform_override)
                    } else {
                        stringResource(R.string.settings_builtin_controls_dpad_as_analog_subtitle)
                    },
                    isEnabled = effectiveDpadAsAnalog,
                    isFocused = isFocused(item),
                    onToggle = {
                        if (isGlobal) viewModel.setBuiltinDpadAsAnalog(it)
                        else viewModel.updatePlatformControlSetting("dpadAsAnalog", it)
                    }
                )

                BuiltinControlsItem.Hotkeys -> NavigationPreference(
                    icon = Icons.Default.Keyboard,
                    title = stringResource(R.string.settings_builtin_controls_hotkeys_title),
                    subtitle = stringResource(R.string.settings_builtin_controls_hotkeys_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.showHotkeysModal() }
                )

                BuiltinControlsItem.LimitHotkeysToPlayer1 -> SwitchPreference(
                    title = stringResource(R.string.settings_builtin_controls_limit_hotkeys_title),
                    subtitle = stringResource(R.string.settings_builtin_controls_limit_hotkeys_subtitle),
                    isEnabled = controlsState.limitHotkeysToPlayer1,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinLimitHotkeysToPlayer1(it) }
                )

                BuiltinControlsItem.ToggleFastForward -> SwitchPreference(
                    title = stringResource(R.string.settings_builtin_controls_toggle_ff_title),
                    subtitle = stringResource(R.string.settings_builtin_controls_toggle_ff_subtitle),
                    isEnabled = controlsState.fastForwardMode == com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE,
                    isFocused = isFocused(item),
                    onToggle = { enabled ->
                        viewModel.setBuiltinFastForwardMode(
                            if (enabled) com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE
                            else com.nendo.argosy.data.local.entity.FastForwardMode.HOLD
                        )
                    }
                )

                BuiltinControlsItem.PreserveFastForwardPitch -> SwitchPreference(
                    title = stringResource(R.string.settings_builtin_controls_ff_pitch_title),
                    subtitle = stringResource(R.string.settings_builtin_controls_ff_pitch_subtitle),
                    isEnabled = controlsState.fastForwardPreservePitch,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinFastForwardPreservePitch(it) }
                )

                BuiltinControlsItem.ResetAllToGlobal -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    OptionItem(
                        label = stringResource(R.string.settings_builtin_controls_reset_all),
                        isFocused = isFocused(item),
                        isDangerous = true,
                        onClick = { viewModel.resetAllPlatformControlSettings() }
                    )
                }

                BuiltinControlsItem.SpeedrunStartOnReset -> SwitchPreference(
                    title = stringResource(R.string.settings_speedrun_start_on_reset_title),
                    subtitle = stringResource(R.string.settings_speedrun_start_on_reset_subtitle),
                    isEnabled = controlsState.speedrunStartOnReset,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSpeedrunStartOnReset(it) }
                )

                BuiltinControlsItem.SpeedrunPanelSide -> SwitchPreference(
                    title = stringResource(R.string.settings_speedrun_panel_side_title),
                    subtitle = stringResource(R.string.settings_speedrun_panel_side_subtitle),
                    isEnabled = controlsState.speedrunPanelSide == "Left",
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSpeedrunPanelSide(if (it) "Left" else "Right") }
                )

                BuiltinControlsItem.SpeedrunPanelWidth -> CyclePreference(
                    title = stringResource(R.string.settings_speedrun_panel_width_title),
                    subtitle = stringResource(R.string.settings_speedrun_panel_width_subtitle),
                    value = stringResource(
                        R.string.settings_builtin_controls_percent_value,
                        controlsState.speedrunPanelWidthPercent
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.adjustSpeedrunPanelWidth(5) },
                    onPrev = { viewModel.adjustSpeedrunPanelWidth(-5) }
                )

                BuiltinControlsItem.TouchEnabled -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_enabled_title),
                    subtitle = stringResource(R.string.settings_touch_enabled_subtitle),
                    isEnabled = controlsState.touchEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchEnabled(it) }
                )
                BuiltinControlsItem.TouchOpacityLandscape -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_opacity_landscape_title),
                    subtitle = stringResource(
                        R.string.settings_touch_current_percent,
                        (controlsState.touchOpacityLandscape * 100).toInt()
                    ),
                    isEnabled = controlsState.touchOpacityLandscape > 0.5f,
                    isFocused = isFocused(item),
                    onToggle = {
                        viewModel.setTouchOpacityLandscape(if (it) 0.7f else 0.4f)
                    }
                )
                BuiltinControlsItem.TouchOpacityPortrait -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_opacity_portrait_title),
                    subtitle = stringResource(
                        R.string.settings_touch_current_percent,
                        (controlsState.touchOpacityPortrait * 100).toInt()
                    ),
                    isEnabled = controlsState.touchOpacityPortrait > 0.7f,
                    isFocused = isFocused(item),
                    onToggle = {
                        viewModel.setTouchOpacityPortrait(if (it) 1.0f else 0.7f)
                    }
                )
                BuiltinControlsItem.TouchSizeScale -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_size_title),
                    subtitle = stringResource(
                        R.string.settings_touch_current_percent,
                        (controlsState.touchSizeScale * 100).toInt()
                    ),
                    isEnabled = controlsState.touchSizeScale > 1.0f,
                    isFocused = isFocused(item),
                    onToggle = {
                        viewModel.setTouchSizeScale(if (it) 1.2f else 1.0f)
                    }
                )
                BuiltinControlsItem.TouchHaptic -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_haptic_title),
                    subtitle = stringResource(R.string.settings_touch_haptic_subtitle),
                    isEnabled = controlsState.touchHaptic,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchHaptic(it) }
                )
                BuiltinControlsItem.TouchFadeOnIdle -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_fade_title),
                    subtitle = stringResource(R.string.settings_touch_fade_subtitle),
                    isEnabled = controlsState.touchFadeOnIdle,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchFadeOnIdle(it) }
                )
                BuiltinControlsItem.TouchSwapHanded -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_swap_handed_title),
                    subtitle = stringResource(R.string.settings_touch_swap_handed_subtitle),
                    isEnabled = controlsState.touchSwapHanded,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchSwapHanded(it) }
                )
                BuiltinControlsItem.TouchLockOrientation -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_lock_orientation_title),
                    subtitle = stringResource(R.string.settings_touch_lock_orientation_subtitle),
                    isEnabled = controlsState.touchLockOrientation,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchLockOrientation(it) }
                )
                BuiltinControlsItem.TouchMirror180 -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_mirror_title),
                    subtitle = stringResource(R.string.settings_touch_mirror_subtitle),
                    isEnabled = controlsState.touchMirror180,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchMirror180(it) }
                )
                BuiltinControlsItem.TouchColouredFaceButtons -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_coloured_faces_title),
                    subtitle = stringResource(R.string.settings_touch_coloured_faces_subtitle),
                    isEnabled = controlsState.touchColouredFaceButtons,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchColouredFaceButtons(it) }
                )
                BuiltinControlsItem.TouchGenesis6Button -> SwitchPreference(
                    title = stringResource(R.string.settings_touch_genesis6_title),
                    subtitle = stringResource(R.string.settings_touch_genesis6_subtitle),
                    isEnabled = controlsState.touchGenesis6Button,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchGenesis6Button(it) }
                )

                BuiltinControlsItem.TouchCustomizeLayouts -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = stringResource(R.string.settings_touch_customize_title),
                    subtitle = stringResource(R.string.settings_touch_customize_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.showTouchLayoutEditor() }
                )

                else -> {}
            }
        }

        if (controlsState.showControllerOrderModal) {
            ControllerOrderModal(
                existingOrder = controllerOrder,
                onAssign = { port, device -> viewModel.assignControllerToPort(port, device) },
                onClearAll = { viewModel.clearControllerOrder() },
                onDismiss = { viewModel.hideControllerOrderModal() }
            )
        }

        if (controlsState.showInputMappingModal) {
            InputMappingModal(
                controllers = viewModel.getConnectedControllers(),
                lockedPlatformIndex = if (!isGlobal && platformSlug != null) {
                    MappingPlatforms.indexForPlatformSlug(platformSlug)
                } else {
                    null
                },
                onGetMapping = { controller, platformId ->
                    viewModel.getControllerMapping(controller, platformId)
                },
                onSaveMapping = { controller, mapping, presetName, isAutoDetected, platformId ->
                    viewModel.saveControllerMapping(controller, mapping, presetName, isAutoDetected, platformId)
                },
                onApplyPreset = { controller, presetName ->
                    viewModel.applyControllerPreset(controller, presetName)
                },
                onDismiss = { viewModel.hideInputMappingModal() }
            )
        }

        if (controlsState.showHotkeysModal) {
            val coreInfo = if (!isGlobal && platformSlug != null) {
                LibretroCoreRegistry.getDefaultCoreForPlatform(platformSlug)
            } else {
                null
            }
            HotkeysModal(
                hotkeys = hotkeys,
                onSaveHotkey = { action, keyCodes, scopeType, scopeKey -> viewModel.saveHotkey(action, keyCodes, scopeType, scopeKey) },
                onClearHotkey = { action, scopeType, scopeKey -> viewModel.clearHotkey(action, scopeType, scopeKey) },
                onSetHoldMs = { action, holdMs, scopeType, scopeKey -> viewModel.setHotkeyHoldMs(action, holdMs, scopeType, scopeKey) },
                onDismiss = { viewModel.hideHotkeysModal() },
                coreId = coreInfo?.coreId,
                coreName = coreInfo?.displayName,
                platformSlug = if (!isGlobal) platformSlug else null,
                coreControls = coreInfo?.let { CoreControlManifestRegistry.getManifest(it.coreId)?.controls } ?: emptyList(),
                onSaveCoreControl = { retropadId, mode, keyCodes ->
                    coreInfo?.let { viewModel.saveCoreControlHotkey(it.coreId, retropadId, mode, keyCodes) }
                },
                onClearCoreBind = { id -> viewModel.deleteCoreBind(id) }
            )
        }

        if (controlsState.showTouchLayoutEditorModal) {
            com.nendo.argosy.ui.screens.touchlayout.TouchLayoutEditorModal(
                repository = viewModel.touchLayoutRepository,
                onDismiss = { viewModel.hideTouchLayoutEditor() }
            )
        }
    }
}
