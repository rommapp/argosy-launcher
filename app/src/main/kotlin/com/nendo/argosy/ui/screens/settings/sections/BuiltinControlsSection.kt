package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.settings.BuiltinControlsState
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
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

    companion object {
        private val ControllersHeader = Header("controllersHeader", "controllers", "Controllers")
        private val SticksHeader = Header("sticksHeader", "sticks", "Analog Sticks") { it.showStickMappings }
        private val HotkeysHeader = Header("hotkeysHeader", "hotkeys", "Hotkeys")

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
            LimitHotkeysToPlayer1
        )
    }
}

private val builtinControlsLayout = SettingsLayout<BuiltinControlsItem, BuiltinControlsState>(
    allItems = BuiltinControlsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun builtinControlsMaxFocusIndex(
    state: BuiltinControlsState,
    videoState: BuiltinVideoState = BuiltinVideoState(),
    platformSettings: Map<Long, PlatformLibretroSettingsEntity> = emptyMap()
): Int {
    val base = builtinControlsLayout.maxFocusIndex(state)
    val platformContext = videoState.currentPlatformContext
    val hasControlOverrides = platformContext?.let {
        platformSettings[it.platformId]?.hasAnyControlOverrides()
    } == true
    return if (!videoState.isGlobalContext && hasControlOverrides) base + 1 else base
}

internal fun builtinControlsItemAtFocusIndex(index: Int, state: BuiltinControlsState): BuiltinControlsItem? =
    builtinControlsLayout.itemAtFocusIndex(index, state)

internal fun builtinControlsSections(state: BuiltinControlsState) =
    builtinControlsLayout.buildSections(state)

@Composable
fun BuiltinControlsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val controlsState = uiState.builtinControls
    val controllerOrder by viewModel.getControllerOrder().collectAsState(initial = emptyList())
    val hotkeys by viewModel.observeHotkeys().collectAsState(initial = emptyList())

    val videoState = uiState.builtinVideo
    val isGlobal = videoState.isGlobalContext
    val platformContext = videoState.currentPlatformContext
    val platformSettings = platformContext?.let {
        uiState.platformLibretro.platformSettings[it.platformId]
    }
    val hasControlOverrides = platformSettings?.hasAnyControlOverrides() == true

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

    val resetAllFocusIndex = builtinControlsLayout.maxFocusIndex(controlsState) + 1

    fun isFocused(item: BuiltinControlsItem): Boolean =
        uiState.focusedIndex == builtinControlsLayout.focusIndexOf(item, controlsState)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { builtinControlsLayout.focusToListIndex(it, controlsState) },
        sections = sections
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
        items(visibleItems, key = { it.key }) { item ->
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
            }
        }

        if (!isGlobal && hasControlOverrides) {
            item(key = "resetAllToGlobal") {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                OptionItem(
                    label = "Reset All to Global",
                    isFocused = uiState.focusedIndex == resetAllFocusIndex,
                    isDangerous = true,
                    onClick = { viewModel.resetAllPlatformControlSettings() }
                )
            }
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
                onDismiss = { viewModel.hideHotkeysModal() }
            )
        }
    }
}
