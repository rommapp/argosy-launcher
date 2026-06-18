package com.nendo.argosy.libretro.ui

import android.view.InputDevice
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.CoreInputMode
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.libretro.coreoptions.CoreControlDef
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
import com.nendo.argosy.ui.screens.settings.CoreOptionViewItem
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.components.ControllerOrderModal
import com.nendo.argosy.ui.screens.settings.components.HotkeysModal
import com.nendo.argosy.data.repository.MappingPlatforms
import com.nendo.argosy.ui.screens.settings.components.InputMappingModal
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsAccessor
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsSection
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.touchOnly

enum class InGameSettingsTab(val label: String) {
    VIDEO("Video"),
    CONTROLS("Controls"),
    CORE_OPTIONS("Core Options")
}

data class InGameControlsState(
    val gameSpecificControls: Boolean = false,
    val supportsGameSpecificControls: Boolean = false,
    val rumbleEnabled: Boolean = true,
    val analogAsDpad: Boolean = false,
    val dpadAsAnalog: Boolean = false,
    val limitHotkeysToPlayer1: Boolean = true,
    val fastForwardMode: com.nendo.argosy.data.local.entity.FastForwardMode =
        com.nendo.argosy.data.local.entity.FastForwardMode.HOLD,
    val fastForwardPreservePitch: Boolean = false,
    val controllerOrderCount: Int = 0,
    val touchEnabled: Boolean = true,
    val touchOpacityLandscape: Float = 0.45f,
    val touchOpacityPortrait: Float = 1.0f,
    val touchSizeScale: Float = 1.0f,
    val touchHaptic: Boolean = true,
    val touchLockOrientation: Boolean = false,
    val touchGenesis6Button: Boolean = false
)

sealed class InGameControlsAction {
    data class SetGameSpecificControls(val enabled: Boolean) : InGameControlsAction()
    data class SetRumble(val enabled: Boolean) : InGameControlsAction()
    data class SetAnalogAsDpad(val enabled: Boolean) : InGameControlsAction()
    data class SetDpadAsAnalog(val enabled: Boolean) : InGameControlsAction()
    data class SetLimitHotkeys(val enabled: Boolean) : InGameControlsAction()
    data class SetFastForwardMode(
        val mode: com.nendo.argosy.data.local.entity.FastForwardMode
    ) : InGameControlsAction()
    data class SetFastForwardPreservePitch(val enabled: Boolean) : InGameControlsAction()
    data object ShowControllerOrder : InGameControlsAction()
    data object ShowInputMapping : InGameControlsAction()
    data object ShowHotkeys : InGameControlsAction()
    data class SetTouchEnabled(val enabled: Boolean) : InGameControlsAction()
    data class SetTouchHaptic(val enabled: Boolean) : InGameControlsAction()
    data class SetTouchLockOrientation(val enabled: Boolean) : InGameControlsAction()
    data class SetTouchGenesis6Button(val enabled: Boolean) : InGameControlsAction()
}

data class InGameModalCallbacks(
    val controllerOrder: List<ControllerOrderEntity>,
    val hotkeys: List<HotkeyEntity>,
    val connectedControllers: List<ControllerInfo>,
    val onAssignController: (Int, InputDevice) -> Unit,
    val onClearControllerOrder: () -> Unit,
    val onGetMapping: suspend (ControllerInfo, String?) -> Pair<Map<InputSource, Int>, String?>,
    val onSaveMapping: suspend (ControllerInfo, Map<InputSource, Int>, String?, Boolean, String?) -> Unit,
    val onApplyPreset: suspend (ControllerInfo, String) -> Unit,
    val onSaveHotkey: suspend (HotkeyAction, List<Int>) -> Unit,
    val onClearHotkey: suspend (HotkeyAction) -> Unit,
    val onSetHotkeyHoldMs: suspend (HotkeyAction, Long) -> Unit,
    val coreId: String? = null,
    val coreName: String? = null,
    val coreControls: List<CoreControlDef> = emptyList(),
    val onSaveCoreControl: suspend (Int, CoreInputMode, List<Int>) -> Unit = { _, _, _ -> },
    val onClearCoreBind: suspend (Long) -> Unit = {}
)

internal sealed class InGameControlsItem(
    val key: String,
    val section: String
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : InGameControlsItem(key, section)
    data object GameSpecificControls : InGameControlsItem("gameSpecificControls", "controllers")
    data object ControllerOrder : InGameControlsItem("controllerOrder", "controllers")
    data object InputMapping : InGameControlsItem("inputMapping", "controllers")
    data object Rumble : InGameControlsItem("rumble", "controllers")
    data object AnalogAsDpad : InGameControlsItem("analogAsDpad", "sticks")
    data object DpadAsAnalog : InGameControlsItem("dpadAsAnalog", "sticks")
    data object Hotkeys : InGameControlsItem("hotkeys", "hotkeys")
    data object LimitHotkeysToPlayer1 : InGameControlsItem("limitHotkeys", "hotkeys")
    data object ToggleFastForward : InGameControlsItem("toggleFastForward", "hotkeys")
    data object PreserveFastForwardPitch : InGameControlsItem("preserveFastForwardPitch", "hotkeys")
    data object TouchEnabled : InGameControlsItem("touchEnabled", "touchControls")
    data object TouchHaptic : InGameControlsItem("touchHaptic", "touchControls")
    data object TouchLockOrientation : InGameControlsItem("touchLockOrientation", "touchControls")
    data object TouchGenesis6Button : InGameControlsItem("touchGenesis6Button", "touchControls")

    companion object {
        val ALL = listOf(
            Header("controllersHeader", "controllers", "Controllers"),
            GameSpecificControls,
            ControllerOrder,
            InputMapping,
            Rumble,
            Header("sticksHeader", "sticks", "Analog Sticks"),
            AnalogAsDpad,
            DpadAsAnalog,
            Header("hotkeysHeader", "hotkeys", "Hotkeys"),
            Hotkeys,
            LimitHotkeysToPlayer1,
            ToggleFastForward,
            PreserveFastForwardPitch,
            Header("touchControlsHeader", "touchControls", "Touch Controls"),
            TouchEnabled,
            TouchHaptic,
            TouchLockOrientation,
            TouchGenesis6Button
        )
    }
}

internal data class InGameControlsVisibility(
    val hasAnalogStick: Boolean,
    val hasRumble: Boolean,
    val hasGame: Boolean
)

private val controlsLayout = SettingsLayout<InGameControlsItem, InGameControlsVisibility>(
    allItems = InGameControlsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, visibility ->
        when (item) {
            InGameControlsItem.Rumble -> visibility.hasRumble
            InGameControlsItem.DpadAsAnalog -> visibility.hasAnalogStick
            InGameControlsItem.GameSpecificControls -> visibility.hasGame
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
    menuWrapMode: com.nendo.argosy.data.preferences.MenuWrapMode = com.nendo.argosy.data.preferences.MenuWrapMode.HARD_STOP,
    controlsState: InGameControlsState,
    onControlsAction: (InGameControlsAction) -> Unit,
    coreOptions: List<CoreOptionViewItem>,
    coreOptionsSupported: Boolean,
    onCoreOptionCycle: (String, Int) -> Unit,
    onCoreOptionReset: (String) -> Unit,
    perGameSettingsSupported: Boolean = false,
    perGameSettingsEnabled: Boolean = false,
    onTogglePerGameSettings: (Boolean) -> Unit = {},
    modalCallbacks: InGameModalCallbacks,
    onDismiss: () -> Unit
): InputHandler {
    var currentTab by remember { mutableStateOf(InGameSettingsTab.VIDEO) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val videoListState = rememberLazyListState()
    val controlsListState = rememberLazyListState()
    val coreOptionsListState = rememberLazyListState()
    var showControllerOrderModal by remember { mutableStateOf(false) }
    var showInputMappingModal by remember { mutableStateOf(false) }
    var showHotkeysModal by remember { mutableStateOf(false) }

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val currentControlsState = rememberUpdatedState(controlsState)
    val currentOnControlsAction = rememberUpdatedState(onControlsAction)
    val currentCoreOptions = rememberUpdatedState(coreOptions)
    val currentCoreOptionsSupported = rememberUpdatedState(coreOptionsSupported)
    val currentOnCoreOptionCycle = rememberUpdatedState(onCoreOptionCycle)
    val currentOnCoreOptionReset = rememberUpdatedState(onCoreOptionReset)
    val currentPerGameSupported = rememberUpdatedState(perGameSettingsSupported)
    val currentPerGameEnabled = rememberUpdatedState(perGameSettingsEnabled)
    val currentOnTogglePerGame = rememberUpdatedState(onTogglePerGameSettings)

    val controlsVisibility = remember(platformSlug, controlsState.supportsGameSpecificControls) {
        InGameControlsVisibility(
            hasAnalogStick = platformSlug != null && PlatformWeightRegistry.hasAnalogStick(platformSlug),
            hasRumble = platformSlug != null && PlatformWeightRegistry.hasRumble(platformSlug),
            hasGame = controlsState.supportsGameSpecificControls
        )
    }
    val maxVideoFocusIndex = remember(platformSlug, canEnableBFI) {
        libretroSettingsMaxFocusIndex(platformSlug, canEnableBFI, showSavingSection = false)
    }
    val controlsMaxFocusIndex = remember(controlsVisibility) {
        controlsLayout.maxFocusIndex(controlsVisibility)
    }

    fun getMaxFocusIndex(): Int = when (currentTab) {
        InGameSettingsTab.VIDEO -> maxVideoFocusIndex
        InGameSettingsTab.CONTROLS -> controlsMaxFocusIndex
        InGameSettingsTab.CORE_OPTIONS -> {
            val offset = if (currentPerGameSupported.value) 1 else 0
            (currentCoreOptions.value.size - 1 + offset).coerceAtLeast(0)
        }
    }

    fun getSettingAtIndex(index: Int): LibretroSettingDef? = when (currentTab) {
        InGameSettingsTab.VIDEO -> libretroSettingsItemAtFocusIndex(index, platformSlug, canEnableBFI)
        InGameSettingsTab.CONTROLS -> null
        InGameSettingsTab.CORE_OPTIONS -> null
    }

    fun isTabEnabled(tab: InGameSettingsTab): Boolean =
        tab != InGameSettingsTab.CORE_OPTIONS || currentCoreOptionsSupported.value

    fun coreOptionKeyAt(index: Int): String? {
        val offset = if (currentPerGameSupported.value) 1 else 0
        if (offset == 1 && index == 0) return null
        return currentCoreOptions.value.getOrNull(index - offset)?.key
    }

    fun handleControlsConfirm() {
        val item = controlsLayout.itemAtFocusIndex(focusedIndex, controlsVisibility) ?: return
        val state = currentControlsState.value
        val action = currentOnControlsAction.value
        when (item) {
            InGameControlsItem.GameSpecificControls -> action(InGameControlsAction.SetGameSpecificControls(!state.gameSpecificControls))
            InGameControlsItem.Rumble -> action(InGameControlsAction.SetRumble(!state.rumbleEnabled))
            InGameControlsItem.ControllerOrder -> showControllerOrderModal = true
            InGameControlsItem.InputMapping -> showInputMappingModal = true
            InGameControlsItem.AnalogAsDpad -> action(InGameControlsAction.SetAnalogAsDpad(!state.analogAsDpad))
            InGameControlsItem.DpadAsAnalog -> action(InGameControlsAction.SetDpadAsAnalog(!state.dpadAsAnalog))
            InGameControlsItem.Hotkeys -> showHotkeysModal = true
            InGameControlsItem.LimitHotkeysToPlayer1 -> action(InGameControlsAction.SetLimitHotkeys(!state.limitHotkeysToPlayer1))
            InGameControlsItem.ToggleFastForward -> {
                val next = if (state.fastForwardMode == com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE) {
                    com.nendo.argosy.data.local.entity.FastForwardMode.HOLD
                } else {
                    com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE
                }
                action(InGameControlsAction.SetFastForwardMode(next))
            }
            InGameControlsItem.PreserveFastForwardPitch ->
                action(InGameControlsAction.SetFastForwardPreservePitch(!state.fastForwardPreservePitch))
            InGameControlsItem.TouchEnabled ->
                action(InGameControlsAction.SetTouchEnabled(!state.touchEnabled))
            InGameControlsItem.TouchHaptic ->
                action(InGameControlsAction.SetTouchHaptic(!state.touchHaptic))
            InGameControlsItem.TouchLockOrientation ->
                action(InGameControlsAction.SetTouchLockOrientation(!state.touchLockOrientation))
            InGameControlsItem.TouchGenesis6Button ->
                action(InGameControlsAction.SetTouchGenesis6Button(!state.touchGenesis6Button))
            else -> {}
        }
    }

    fun switchTab(direction: Int) {
        val tabs = InGameSettingsTab.entries
        var newIndex = tabs.indexOf(currentTab)
        repeat(tabs.size) {
            newIndex = (newIndex + direction).mod(tabs.size)
            if (isTabEnabled(tabs[newIndex])) {
                currentTab = tabs[newIndex]
                focusedIndex = 0
                return
            }
        }
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                val newIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    focusedIndex, -1, getMaxFocusIndex(), menuWrapMode
                )
                if (newIndex != focusedIndex) focusedIndex = newIndex
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                val newIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                    focusedIndex, 1, getMaxFocusIndex(), menuWrapMode
                )
                if (newIndex != focusedIndex) focusedIndex = newIndex
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (currentTab == InGameSettingsTab.CORE_OPTIONS) {
                    if (currentPerGameSupported.value && focusedIndex == 0) {
                        currentOnTogglePerGame.value(false)
                    } else {
                        coreOptionKeyAt(focusedIndex)?.let { currentOnCoreOptionCycle.value(it, -1) }
                    }
                    return InputResult.HANDLED
                }
                val setting = getSettingAtIndex(focusedIndex) ?: return InputResult.HANDLED
                if (accessor.isActionItem(setting)) return InputResult.HANDLED
                if (setting.type is LibretroSettingDef.SettingType.Cycle) {
                    accessor.cycle(setting, -1)
                }
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (currentTab == InGameSettingsTab.CORE_OPTIONS) {
                    if (currentPerGameSupported.value && focusedIndex == 0) {
                        currentOnTogglePerGame.value(true)
                    } else {
                        coreOptionKeyAt(focusedIndex)?.let { currentOnCoreOptionCycle.value(it, 1) }
                    }
                    return InputResult.HANDLED
                }
                val setting = getSettingAtIndex(focusedIndex) ?: return InputResult.HANDLED
                if (accessor.isActionItem(setting)) return InputResult.HANDLED
                if (setting.type is LibretroSettingDef.SettingType.Cycle) {
                    accessor.cycle(setting, 1)
                }
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when (currentTab) {
                    InGameSettingsTab.VIDEO -> {
                        val setting = getSettingAtIndex(focusedIndex) ?: return InputResult.HANDLED
                        if (accessor.isActionItem(setting)) {
                            accessor.onAction(setting)
                        } else when (setting.type) {
                            is LibretroSettingDef.SettingType.Switch -> accessor.toggle(setting)
                            is LibretroSettingDef.SettingType.Cycle -> accessor.cycle(setting, 1)
                        }
                    }
                    InGameSettingsTab.CONTROLS -> handleControlsConfirm()
                    InGameSettingsTab.CORE_OPTIONS ->
                        if (currentPerGameSupported.value && focusedIndex == 0) {
                            currentOnTogglePerGame.value(!currentPerGameEnabled.value)
                        } else {
                            coreOptionKeyAt(focusedIndex)?.let { currentOnCoreOptionCycle.value(it, 1) }
                        }
                }
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                if (currentTab != InGameSettingsTab.CORE_OPTIONS) return InputResult.UNHANDLED
                if (currentPerGameSupported.value && focusedIndex == 0) return InputResult.HANDLED
                val offset = if (currentPerGameSupported.value) 1 else 0
                val option = currentCoreOptions.value.getOrNull(focusedIndex - offset)
                    ?: return InputResult.HANDLED
                if (option.isOverridden) currentOnCoreOptionReset.value(option.key)
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
                    isTabEnabled = { isTabEnabled(it) },
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
                                showSavingSection = false,
                                listState = videoListState
                            )
                        }

                        InGameSettingsTab.CONTROLS -> {
                            InGameControlsSection(
                                state = controlsState,
                                focusedIndex = focusedIndex,
                                visibility = controlsVisibility,
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

                        InGameSettingsTab.CORE_OPTIONS -> {
                            InGameCoreOptionsSection(
                                options = coreOptions,
                                focusedIndex = focusedIndex,
                                onCycle = { onCoreOptionCycle(it, 1) },
                                onReset = onCoreOptionReset,
                                listState = coreOptionsListState,
                                perGameToggleVisible = perGameSettingsSupported,
                                perGameEnabled = perGameSettingsEnabled,
                                onTogglePerGame = onTogglePerGameSettings
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
                onSetHoldMs = modalCallbacks.onSetHotkeyHoldMs,
                onDismiss = { showHotkeysModal = false },
                coreId = modalCallbacks.coreId,
                coreName = modalCallbacks.coreName,
                coreControls = modalCallbacks.coreControls,
                onSaveCoreControl = modalCallbacks.onSaveCoreControl,
                onClearCoreBind = modalCallbacks.onClearCoreBind
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
            InGameSettingsTab.CORE_OPTIONS -> {
                add(InputButton.DPAD_HORIZONTAL to "Adjust")
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
    visibility: InGameControlsVisibility,
    onAction: (InGameControlsAction) -> Unit,
    listState: LazyListState
) {
    val visibleItems = remember(visibility) { controlsLayout.visibleItems(visibility) }
    val sections = remember(visibility) { controlsLayout.buildSections(visibility) }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { controlsLayout.focusToListIndex(it, visibility) },
        sections = sections
    )

    fun isFocused(item: InGameControlsItem): Boolean =
        focusedIndex == controlsLayout.focusIndexOf(item, visibility)

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

                InGameControlsItem.GameSpecificControls -> SwitchPreference(
                    title = "Game-specific controls",
                    subtitle = if (state.gameSpecificControls) {
                        "Remaps apply to this game only"
                    } else {
                        "Using your global controller mapping"
                    },
                    isEnabled = state.gameSpecificControls,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetGameSpecificControls(it)) }
                )

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

                InGameControlsItem.ToggleFastForward -> SwitchPreference(
                    title = "Toggle Fast Forward",
                    subtitle = "Press once to start, again to stop (off = hold to fast forward)",
                    isEnabled = state.fastForwardMode == com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE,
                    isFocused = isFocused(item),
                    onToggle = { enabled ->
                        onAction(InGameControlsAction.SetFastForwardMode(
                            if (enabled) com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE
                            else com.nendo.argosy.data.local.entity.FastForwardMode.HOLD
                        ))
                    }
                )

                InGameControlsItem.PreserveFastForwardPitch -> SwitchPreference(
                    title = "Preserve Audio Pitch",
                    subtitle = "Keep pitch steady while fast forwarding. Uses extra CPU; off by default",
                    isEnabled = state.fastForwardPreservePitch,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetFastForwardPreservePitch(it)) }
                )

                InGameControlsItem.TouchEnabled -> SwitchPreference(
                    title = "Show touch controls when no gamepad",
                    subtitle = "Display an on-screen overlay when no controller is connected",
                    isEnabled = state.touchEnabled,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetTouchEnabled(it)) }
                )

                InGameControlsItem.TouchHaptic -> SwitchPreference(
                    title = "Haptic feedback",
                    subtitle = "Vibrate briefly on touch",
                    isEnabled = state.touchHaptic,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetTouchHaptic(it)) }
                )

                InGameControlsItem.TouchLockOrientation -> SwitchPreference(
                    title = "Lock orientation in-game",
                    subtitle = "Don't auto-rotate during play",
                    isEnabled = state.touchLockOrientation,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetTouchLockOrientation(it)) }
                )

                InGameControlsItem.TouchGenesis6Button -> SwitchPreference(
                    title = "Genesis 6-button mode",
                    subtitle = "Show six face buttons for Genesis / Mega Drive",
                    isEnabled = state.touchGenesis6Button,
                    isFocused = isFocused(item),
                    onToggle = { onAction(InGameControlsAction.SetTouchGenesis6Button(it)) }
                )
            }
        }
    }
}

@Composable
private fun SettingsTabHeader(
    currentTab: InGameSettingsTab,
    isTabEnabled: (InGameSettingsTab) -> Boolean,
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
            val enabled = isTabEnabled(tab)
            SettingsTabIndicator(
                label = tab.label,
                isSelected = tab == currentTab,
                isEnabled = enabled,
                onClick = { if (enabled) onTabSelect(tab) }
            )
        }
    }
}

@Composable
private fun SettingsTabIndicator(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.touchOnly(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
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
