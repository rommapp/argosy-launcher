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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
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

    class Header(key: String, section: String, val title: String) : BuiltinControlsItem(key, section)

    data object Rumble : BuiltinControlsItem("rumble", "feedback")
    data object ControllerOrder : BuiltinControlsItem("controllerOrder", "controllers")
    data object InputMapping : BuiltinControlsItem("inputMapping", "controllers")
    data object AnalogAsDpad : BuiltinControlsItem("analogAsDpad", "sticks")
    data object DpadAsAnalog : BuiltinControlsItem("dpadAsAnalog", "sticks")
    data object Hotkeys : BuiltinControlsItem("hotkeys", "hotkeys")
    data object LimitHotkeysToPlayer1 : BuiltinControlsItem("limitHotkeys", "hotkeys")

    companion object {
        private val FeedbackHeader = Header("feedbackHeader", "feedback", "Feedback")
        private val ControllersHeader = Header("controllersHeader", "controllers", "Controllers")
        private val SticksHeader = Header("sticksHeader", "sticks", "Analog Sticks")
        private val HotkeysHeader = Header("hotkeysHeader", "hotkeys", "Hotkeys")

        val ALL: List<BuiltinControlsItem> = listOf(
            FeedbackHeader,
            Rumble,
            ControllersHeader,
            ControllerOrder,
            InputMapping,
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
    val listState = rememberLazyListState()
    val controlsState = uiState.builtinControls
    val controllerOrder by viewModel.getControllerOrder().collectAsState(initial = emptyList())
    val hotkeys by viewModel.observeHotkeys().collectAsState(initial = emptyList())

    val visibleItems = remember(controlsState) {
        builtinControlsLayout.visibleItems(controlsState)
    }
    val sections = remember(controlsState) {
        builtinControlsLayout.buildSections(controlsState)
    }

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
                    if (item.section != "feedback") {
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
                    subtitle = "Enable controller vibration feedback",
                    isEnabled = controlsState.rumbleEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinRumbleEnabled(it) }
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
                    subtitle = "Map left analog stick to D-pad inputs",
                    isEnabled = controlsState.analogAsDpad,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinAnalogAsDpad(it) }
                )

                BuiltinControlsItem.DpadAsAnalog -> SwitchPreference(
                    title = "D-Pad as Left Stick",
                    subtitle = "Map D-pad to left analog stick inputs",
                    isEnabled = controlsState.dpadAsAnalog,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinDpadAsAnalog(it) }
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
                onGetMapping = { controller -> viewModel.getControllerMapping(controller) },
                onSaveMapping = { controller, mapping, presetName, isAutoDetected ->
                    viewModel.saveControllerMapping(controller, mapping, presetName, isAutoDetected)
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
