package com.nendo.argosy.libretro.ui

import android.view.InputDevice
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.ControllerInfo
import com.nendo.argosy.data.repository.InputSource
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.components.ControllerOrderModal
import com.nendo.argosy.ui.screens.settings.components.HotkeysModal
import com.nendo.argosy.data.repository.MappingPlatforms
import com.nendo.argosy.ui.screens.settings.components.InputMappingModal
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsSection
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.touchOnly

enum class InGameSettingsTab(val label: String) {
    VIDEO("Video"),
    CONTROLS("Controls")
}

data class InGameControlsState(
    val rumbleEnabled: Boolean = true,
    val analogAsDpad: Boolean = false,
    val dpadAsAnalog: Boolean = false,
    val limitHotkeysToPlayer1: Boolean = true,
    val controllerOrderCount: Int = 0
)

sealed class InGameControlsAction {
    data class SetRumble(val enabled: Boolean) : InGameControlsAction()
    data class SetAnalogAsDpad(val enabled: Boolean) : InGameControlsAction()
    data class SetDpadAsAnalog(val enabled: Boolean) : InGameControlsAction()
    data class SetLimitHotkeys(val enabled: Boolean) : InGameControlsAction()
    data object ShowControllerOrder : InGameControlsAction()
    data object ShowInputMapping : InGameControlsAction()
    data object ShowHotkeys : InGameControlsAction()
}

data class InGameModalCallbacks(
    val controllerOrder: List<ControllerOrderEntity>,
    val hotkeys: List<HotkeyEntity>,
    val connectedControllers: List<ControllerInfo>,
    val onAssignController: (Int, InputDevice) -> Unit,
    val onClearControllerOrder: () -> Unit,
    val onGetMapping: suspend (ControllerInfo) -> Pair<Map<InputSource, Int>, String?>,
    val onSaveMapping: suspend (ControllerInfo, Map<InputSource, Int>, String?, Boolean) -> Unit,
    val onApplyPreset: suspend (ControllerInfo, String) -> Unit,
    val onSaveHotkey: suspend (HotkeyAction, List<Int>) -> Unit,
    val onClearHotkey: suspend (HotkeyAction) -> Unit
)

internal sealed class InGameControlsItem(
    val key: String,
    val section: String
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : InGameControlsItem(key, section)
    data object Rumble : InGameControlsItem("rumble", "feedback")
    data object ControllerOrder : InGameControlsItem("controllerOrder", "controllers")
    data object InputMapping : InGameControlsItem("inputMapping", "controllers")
    data object AnalogAsDpad : InGameControlsItem("analogAsDpad", "sticks")
    data object DpadAsAnalog : InGameControlsItem("dpadAsAnalog", "sticks")
    data object Hotkeys : InGameControlsItem("hotkeys", "hotkeys")
    data object LimitHotkeysToPlayer1 : InGameControlsItem("limitHotkeys", "hotkeys")

    companion object {
        val ALL = listOf(
            Header("feedbackHeader", "feedback", "Feedback"),
            Rumble,
            Header("controllersHeader", "controllers", "Controllers"),
            ControllerOrder,
            InputMapping,
            Header("sticksHeader", "sticks", "Analog Sticks"),
            AnalogAsDpad,
            DpadAsAnalog,
            Header("hotkeysHeader", "hotkeys", "Hotkeys"),
            Hotkeys,
            LimitHotkeysToPlayer1
        )
    }
}

private val PLATFORMS_WITH_ANALOG = setOf(
    "n64", "psx", "ps2", "psp", "gc", "wii", "dc", "saturn", "3ds", "nds"
)

private val controlsLayout = SettingsLayout<InGameControlsItem, Boolean>(
    allItems = InGameControlsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, hasAnalogStick ->
        when (item) {
            InGameControlsItem.DpadAsAnalog -> hasAnalogStick
            else -> true
        }
    },
    sectionOf = { it.section }
)

@Composable
fun InGameSettingsScreen(
    accessor: LibretroSettingsAccessor,
    platformSlug: String?,
    canEnableBFI: Boolean,
    controlsState: InGameControlsState,
    onControlsAction: (InGameControlsAction) -> Unit,
    modalCallbacks: InGameModalCallbacks,
    onDismiss: () -> Unit
): InputHandler {
    var currentTab by remember { mutableStateOf(InGameSettingsTab.VIDEO) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val videoListState = rememberLazyListState()
    val controlsListState = rememberLazyListState()
    var showControllerOrderModal by remember { mutableStateOf(false) }
    var showInputMappingModal by remember { mutableStateOf(false) }
    var showHotkeysModal by remember { mutableStateOf(false) }

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val currentControlsState = rememberUpdatedState(controlsState)
    val currentOnControlsAction = rememberUpdatedState(onControlsAction)

    val hasAnalogStick = remember(platformSlug) { platformSlug in PLATFORMS_WITH_ANALOG }
    val maxVideoFocusIndex = remember(platformSlug, canEnableBFI) {
        libretroSettingsMaxFocusIndex(platformSlug, canEnableBFI)
    }
    val controlsMaxFocusIndex = remember(hasAnalogStick) {
        controlsLayout.maxFocusIndex(hasAnalogStick)
    }

    fun getMaxFocusIndex(): Int = when (currentTab) {
        InGameSettingsTab.VIDEO -> maxVideoFocusIndex
        InGameSettingsTab.CONTROLS -> controlsMaxFocusIndex
    }

    fun getSettingAtIndex(index: Int): LibretroSettingDef? = when (currentTab) {
        InGameSettingsTab.VIDEO -> libretroSettingsItemAtFocusIndex(index, platformSlug, canEnableBFI)
        InGameSettingsTab.CONTROLS -> null
    }

    fun handleControlsConfirm() {
        val item = controlsLayout.itemAtFocusIndex(focusedIndex, hasAnalogStick) ?: return
        val state = currentControlsState.value
        val action = currentOnControlsAction.value
        when (item) {
            InGameControlsItem.Rumble -> action(InGameControlsAction.SetRumble(!state.rumbleEnabled))
            InGameControlsItem.ControllerOrder -> showControllerOrderModal = true
            InGameControlsItem.InputMapping -> showInputMappingModal = true
            InGameControlsItem.AnalogAsDpad -> action(InGameControlsAction.SetAnalogAsDpad(!state.analogAsDpad))
            InGameControlsItem.DpadAsAnalog -> action(InGameControlsAction.SetDpadAsAnalog(!state.dpadAsAnalog))
            InGameControlsItem.Hotkeys -> showHotkeysModal = true
            InGameControlsItem.LimitHotkeysToPlayer1 -> action(InGameControlsAction.SetLimitHotkeys(!state.limitHotkeysToPlayer1))
            else -> {}
        }
    }

    fun switchTab(direction: Int) {
        val tabs = InGameSettingsTab.entries
        val currentIndex = tabs.indexOf(currentTab)
        val newIndex = (currentIndex + direction + tabs.size) % tabs.size
        currentTab = tabs[newIndex]
        focusedIndex = 0
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                val newIndex = (focusedIndex - 1).coerceAtLeast(0)
                if (newIndex != focusedIndex) focusedIndex = newIndex
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                val newIndex = (focusedIndex + 1).coerceAtMost(getMaxFocusIndex())
                if (newIndex != focusedIndex) focusedIndex = newIndex
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                val setting = getSettingAtIndex(focusedIndex) ?: return InputResult.HANDLED
                if (setting.type is LibretroSettingDef.SettingType.Cycle) {
                    accessor.cycle(setting, -1)
                }
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                val setting = getSettingAtIndex(focusedIndex) ?: return InputResult.HANDLED
                if (setting.type is LibretroSettingDef.SettingType.Cycle) {
                    accessor.cycle(setting, 1)
                }
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when (currentTab) {
                    InGameSettingsTab.VIDEO -> {
                        val setting = getSettingAtIndex(focusedIndex) ?: return InputResult.HANDLED
                        when (setting.type) {
                            is LibretroSettingDef.SettingType.Switch -> accessor.toggle(setting)
                            is LibretroSettingDef.SettingType.Cycle -> accessor.cycle(setting, 1)
                        }
                    }
                    InGameSettingsTab.CONTROLS -> handleControlsConfirm()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }

            override fun onPrevSection(): InputResult {
                switchTab(-1)
                return InputResult.HANDLED
            }

            override fun onNextSection(): InputResult {
                switchTab(1)
                return InputResult.HANDLED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .heightIn(max = 550.dp)
                .padding(Dimens.spacingLg)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(Dimens.radiusLg),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
            ) {
                SettingsTabHeader(
                    currentTab = currentTab,
                    onTabSelect = { tab ->
                        currentTab = tab
                        focusedIndex = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusProperties { canFocus = false }
                ) {
                    when (currentTab) {
                        InGameSettingsTab.VIDEO -> {
                            LibretroSettingsSection(
                                accessor = accessor,
                                focusedIndex = focusedIndex,
                                platformSlug = platformSlug,
                                canEnableBFI = canEnableBFI,
                                listState = videoListState
                            )
                        }

                        InGameSettingsTab.CONTROLS -> {
                            InGameControlsSection(
                                state = controlsState,
                                focusedIndex = focusedIndex,
                                hasAnalogStick = hasAnalogStick,
                                onAction = { action ->
                                    when (action) {
                                        InGameControlsAction.ShowControllerOrder -> showControllerOrderModal = true
                                        InGameControlsAction.ShowInputMapping -> showInputMappingModal = true
                                        InGameControlsAction.ShowHotkeys -> showHotkeysModal = true
                                        else -> onControlsAction(action)
                                    }
                                },
                                listState = controlsListState
                            )
                        }
                    }
                }

                FooterBar(
                    hints = buildSettingsFooterHints(currentTab),
                    onHintClick = { button ->
                        when (button) {
                            InputButton.B -> currentOnDismiss.value()
                            InputButton.LB_RB -> {
                                switchTab(1)
                            }
                            else -> {}
                        }
                    }
                )
            }
        }

        if (showControllerOrderModal) {
            ControllerOrderModal(
                existingOrder = modalCallbacks.controllerOrder,
                onAssign = modalCallbacks.onAssignController,
                onClearAll = modalCallbacks.onClearControllerOrder,
                onDismiss = { showControllerOrderModal = false }
            )
        }

        if (showInputMappingModal) {
            InputMappingModal(
                controllers = modalCallbacks.connectedControllers,
                lockedPlatformIndex = MappingPlatforms.indexForPlatformSlug(platformSlug ?: ""),
                onGetMapping = modalCallbacks.onGetMapping,
                onSaveMapping = modalCallbacks.onSaveMapping,
                onApplyPreset = modalCallbacks.onApplyPreset,
                onDismiss = { showInputMappingModal = false }
            )
        }

        if (showHotkeysModal) {
            HotkeysModal(
                hotkeys = modalCallbacks.hotkeys,
                onSaveHotkey = modalCallbacks.onSaveHotkey,
                onClearHotkey = modalCallbacks.onClearHotkey,
                onDismiss = { showHotkeysModal = false }
            )
        }
    }

    return inputHandler
}

private fun buildSettingsFooterHints(tab: InGameSettingsTab): List<Pair<InputButton, String>> {
    return buildList {
        add(InputButton.LB_RB to "Tab")
        when (tab) {
            InGameSettingsTab.VIDEO -> {
                add(InputButton.DPAD_HORIZONTAL to "Adjust")
                add(InputButton.A to "Select")
            }
            InGameSettingsTab.CONTROLS -> {
                add(InputButton.A to "Select")
            }
        }
        add(InputButton.B to "Back")
    }
}

@Composable
private fun InGameControlsSection(
    state: InGameControlsState,
    focusedIndex: Int,
    hasAnalogStick: Boolean,
    onAction: (InGameControlsAction) -> Unit,
    listState: LazyListState
) {
    val visibleItems = remember(hasAnalogStick) { controlsLayout.visibleItems(hasAnalogStick) }
    val sections = remember(hasAnalogStick) { controlsLayout.buildSections(hasAnalogStick) }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { controlsLayout.focusToListIndex(it, hasAnalogStick) },
        sections = sections
    )

    fun isFocused(item: InGameControlsItem): Boolean =
        focusedIndex == controlsLayout.focusIndexOf(item, hasAnalogStick)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is InGameControlsItem.Header -> {
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

                InGameControlsItem.Rumble -> SwitchPreference(
                    title = "Rumble",
                    subtitle = "Enable controller vibration feedback",
                    isEnabled = state.rumbleEnabled,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetRumble(it)) }
                )

                InGameControlsItem.ControllerOrder -> NavigationPreference(
                    icon = Icons.Default.SortByAlpha,
                    title = "Controller Order",
                    subtitle = if (state.controllerOrderCount > 0) {
                        "${state.controllerOrderCount} controller${if (state.controllerOrderCount > 1) "s" else ""} assigned"
                    } else {
                        "Assign controller ports"
                    },
                    isFocused = isFocused(item),
                    onClick = { onAction(InGameControlsAction.ShowControllerOrder) }
                )

                InGameControlsItem.InputMapping -> NavigationPreference(
                    icon = Icons.Default.Gamepad,
                    title = "Input Mapping",
                    subtitle = "Remap buttons for each controller",
                    isFocused = isFocused(item),
                    onClick = { onAction(InGameControlsAction.ShowInputMapping) }
                )

                InGameControlsItem.AnalogAsDpad -> SwitchPreference(
                    title = "Left Stick as D-Pad",
                    subtitle = "Map left analog stick to D-pad inputs",
                    isEnabled = state.analogAsDpad,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetAnalogAsDpad(it)) }
                )

                InGameControlsItem.DpadAsAnalog -> SwitchPreference(
                    title = "D-Pad as Left Stick",
                    subtitle = "Map D-pad to left analog stick inputs",
                    isEnabled = state.dpadAsAnalog,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetDpadAsAnalog(it)) }
                )

                InGameControlsItem.Hotkeys -> NavigationPreference(
                    icon = Icons.Default.Keyboard,
                    title = "Hotkeys",
                    subtitle = "Configure shortcuts for menu, fast forward, rewind",
                    isFocused = isFocused(item),
                    onClick = { onAction(InGameControlsAction.ShowHotkeys) }
                )

                InGameControlsItem.LimitHotkeysToPlayer1 -> SwitchPreference(
                    title = "Limit Hotkeys to Player 1",
                    subtitle = "Only player 1 controller can trigger hotkeys",
                    isEnabled = state.limitHotkeysToPlayer1,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetLimitHotkeys(it)) }
                )
            }
        }
    }
}

@Composable
private fun SettingsTabHeader(
    currentTab: InGameSettingsTab,
    onTabSelect: (InGameSettingsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
            .focusProperties { canFocus = false },
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        InGameSettingsTab.entries.forEach { tab ->
            SettingsTabIndicator(
                label = tab.label,
                isSelected = tab == currentTab,
                onClick = { onTabSelect(tab) }
            )
        }
    }
}

@Composable
private fun SettingsTabIndicator(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.touchOnly(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    RoundedCornerShape(1.dp)
                )
        )
    }
}
