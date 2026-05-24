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
import com.nendo.argosy.data.platform.PlatformWeightRegistry
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
        val title: String,
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
        private val ControllersHeader = Header("controllersHeader", "controllers", "Controllers")
        private val SticksHeader = Header("sticksHeader", "sticks", "Analog Sticks") { it.showStickMappings }
        private val HotkeysHeader = Header("hotkeysHeader", "hotkeys", "Hotkeys")
        private val TouchHeader = Header("touchControlsHeader", "touchControls", "Touch Controls")

        val ALL: List<BuiltinControlsItem> = listOf(
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
    sectionTitle = {
        when (it) {
            "controllers" -> "Controllers"
            "sticks" -> "Analog Sticks"
            "hotkeys" -> "Hotkeys"
            "touchControls" -> "Touch Controls"
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
    val sections = remember(controlsState) {
        builtinControlsLayout.buildSections(controlsState)
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
                        text = item.title,
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
                    title = "Rumble",
                    subtitle = if (!isGlobal && platformSettings?.rumbleEnabled != null)
                        "Platform override" else "Enable controller vibration feedback",
                    isEnabled = effectiveRumble,
                    isFocused = isFocused(item),
                    onToggle = {
                        if (isGlobal) viewModel.setBuiltinRumbleEnabled(it)
                        else viewModel.updatePlatformControlSetting("rumbleEnabled", it)
                    }
                )

                BuiltinControlsItem.ControllerOrder -> NavigationPreference(
                    icon = Icons.Default.SortByAlpha,
                    title = "Controller Order",
                    subtitle = if (controllerOrder.isNotEmpty()) {
                        "${controllerOrder.size} controller${if (controllerOrder.size > 1) "s" else ""} assigned"
                    } else {
                        "Set player order by pressing a button on each controller"
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.showControllerOrderModal() }
                )

                BuiltinControlsItem.InputMapping -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = "Input Mapping",
                    subtitle = "Remap buttons for each controller",
                    isFocused = isFocused(item),
                    onClick = { viewModel.showInputMappingModal() }
                )

                BuiltinControlsItem.AnalogAsDpad -> SwitchPreference(
                    title = "Left Stick as D-Pad",
                    subtitle = if (!isGlobal && platformSettings?.analogAsDpad != null)
                        "Platform override"
                    else if (!isGlobal && !platformHasAnalog)
                        "Platform default"
                    else "Map left analog stick to D-pad inputs",
                    isEnabled = effectiveAnalogAsDpad,
                    isFocused = isFocused(item),
                    onToggle = {
                        if (isGlobal) viewModel.setBuiltinAnalogAsDpad(it)
                        else viewModel.updatePlatformControlSetting("analogAsDpad", it)
                    }
                )

                BuiltinControlsItem.DpadAsAnalog -> SwitchPreference(
                    title = "D-Pad as Left Stick",
                    subtitle = if (!isGlobal && platformSettings?.dpadAsAnalog != null)
                        "Platform override" else "Map D-pad to left analog stick inputs",
                    isEnabled = effectiveDpadAsAnalog,
                    isFocused = isFocused(item),
                    onToggle = {
                        if (isGlobal) viewModel.setBuiltinDpadAsAnalog(it)
                        else viewModel.updatePlatformControlSetting("dpadAsAnalog", it)
                    }
                )

                BuiltinControlsItem.Hotkeys -> NavigationPreference(
                    icon = Icons.Default.Keyboard,
                    title = "Hotkeys",
                    subtitle = "Configure shortcuts for menu, fast forward, rewind",
                    isFocused = isFocused(item),
                    onClick = { viewModel.showHotkeysModal() }
                )

                BuiltinControlsItem.LimitHotkeysToPlayer1 -> SwitchPreference(
                    title = "Limit Hotkeys to Player 1",
                    subtitle = "Only player 1 controller can trigger hotkeys",
                    isEnabled = controlsState.limitHotkeysToPlayer1,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinLimitHotkeysToPlayer1(it) }
                )

                BuiltinControlsItem.ToggleFastForward -> SwitchPreference(
                    title = "Toggle Fast Forward",
                    subtitle = "Press once to start, again to stop (off = hold to fast forward)",
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
                    title = "Preserve Audio Pitch",
                    subtitle = "Keep pitch steady while fast forwarding. Uses extra CPU; off by default",
                    isEnabled = controlsState.fastForwardPreservePitch,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinFastForwardPreservePitch(it) }
                )

                BuiltinControlsItem.ResetAllToGlobal -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    OptionItem(
                        label = "Reset All to Global",
                        isFocused = isFocused(item),
                        isDangerous = true,
                        onClick = { viewModel.resetAllPlatformControlSettings() }
                    )
                }

                BuiltinControlsItem.TouchEnabled -> SwitchPreference(
                    title = "Show touch controls when no gamepad",
                    subtitle = "Display an on-screen overlay when no controller is connected",
                    isEnabled = controlsState.touchEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchEnabled(it) }
                )
                BuiltinControlsItem.TouchOpacityLandscape -> SwitchPreference(
                    title = "Landscape opacity",
                    subtitle = "Currently ${(controlsState.touchOpacityLandscape * 100).toInt()}%",
                    isEnabled = controlsState.touchOpacityLandscape > 0.5f,
                    isFocused = isFocused(item),
                    onToggle = {
                        viewModel.setTouchOpacityLandscape(if (it) 0.7f else 0.4f)
                    }
                )
                BuiltinControlsItem.TouchOpacityPortrait -> SwitchPreference(
                    title = "Portrait opacity",
                    subtitle = "Currently ${(controlsState.touchOpacityPortrait * 100).toInt()}%",
                    isEnabled = controlsState.touchOpacityPortrait > 0.7f,
                    isFocused = isFocused(item),
                    onToggle = {
                        viewModel.setTouchOpacityPortrait(if (it) 1.0f else 0.7f)
                    }
                )
                BuiltinControlsItem.TouchSizeScale -> SwitchPreference(
                    title = "Button size",
                    subtitle = "Currently ${(controlsState.touchSizeScale * 100).toInt()}%",
                    isEnabled = controlsState.touchSizeScale > 1.0f,
                    isFocused = isFocused(item),
                    onToggle = {
                        viewModel.setTouchSizeScale(if (it) 1.2f else 1.0f)
                    }
                )
                BuiltinControlsItem.TouchHaptic -> SwitchPreference(
                    title = "Haptic feedback",
                    subtitle = "Vibrate briefly on touch",
                    isEnabled = controlsState.touchHaptic,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchHaptic(it) }
                )
                BuiltinControlsItem.TouchFadeOnIdle -> SwitchPreference(
                    title = "Fade after inactivity",
                    subtitle = "Dim controls after 5s without input",
                    isEnabled = controlsState.touchFadeOnIdle,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchFadeOnIdle(it) }
                )
                BuiltinControlsItem.TouchSwapHanded -> SwitchPreference(
                    title = "Swap left/right inputs",
                    subtitle = "Mirror the layout horizontally",
                    isEnabled = controlsState.touchSwapHanded,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchSwapHanded(it) }
                )
                BuiltinControlsItem.TouchLockOrientation -> SwitchPreference(
                    title = "Lock orientation in-game",
                    subtitle = "Don't auto-rotate during play",
                    isEnabled = controlsState.touchLockOrientation,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchLockOrientation(it) }
                )
                BuiltinControlsItem.TouchMirror180 -> SwitchPreference(
                    title = "Mirror controls on 180° flip",
                    subtitle = "Keep controls on the same physical side when phone flips",
                    isEnabled = controlsState.touchMirror180,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchMirror180(it) }
                )
                BuiltinControlsItem.TouchColouredFaceButtons -> SwitchPreference(
                    title = "Coloured PSX face buttons",
                    subtitle = "Tint △ □ ○ ✕ with their canonical colours",
                    isEnabled = controlsState.touchColouredFaceButtons,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchColouredFaceButtons(it) }
                )
                BuiltinControlsItem.TouchGenesis6Button -> SwitchPreference(
                    title = "Genesis 6-button mode",
                    subtitle = "Show all six buttons for Genesis / Mega Drive",
                    isEnabled = controlsState.touchGenesis6Button,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setTouchGenesis6Button(it) }
                )

                BuiltinControlsItem.TouchCustomizeLayouts -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = "Customize layouts…",
                    subtitle = "Drag and resize touch controls per platform",
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
            HotkeysModal(
                hotkeys = hotkeys,
                onSaveHotkey = { action, keyCodes -> viewModel.saveHotkey(action, keyCodes) },
                onClearHotkey = { action -> viewModel.clearHotkey(action) },
                onSetHoldMs = { action, holdMs -> viewModel.setHotkeyHoldMs(action, holdMs) },
                onDismiss = { viewModel.hideHotkeysModal() }
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
