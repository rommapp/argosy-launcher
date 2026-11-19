package com.nendo.argosy.libretro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.libretro.SaveStateManager
import com.nendo.argosy.ui.components.NavDrawerRow
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.theme.motionEnter
import com.nendo.argosy.ui.theme.motionExit
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.verticalEdgeFade
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.collectLatest

sealed class InGameMenuAction {
    data object SwapDisc : InGameMenuAction()
    data object Resume : InGameMenuAction()
    data object QuickSave : InGameMenuAction()
    data object QuickLoad : InGameMenuAction()
    data object ManageStates : InGameMenuAction()
    data object Settings : InGameMenuAction()
    data object Cheats : InGameMenuAction()
    data object Achievements : InGameMenuAction()
    data object ViewManual : InGameMenuAction()
    data object ViewWalkthrough : InGameMenuAction()
    data object ToggleWalkthroughPanel : InGameMenuAction()
    data object Reset : InGameMenuAction()
    data object Quit : InGameMenuAction()
    data object Netplay : InGameMenuAction()
    data object CustomizeTouchControls : InGameMenuAction()
    data object ToggleSpeedrun : InGameMenuAction()
    data object SwapScreens : InGameMenuAction()
}

enum class NetplayMenuRole { Host, Guest }

enum class QuickHistoryFocus { NONE, BUTTON, STRIP }

/**
 * Connection quality tier. The constants were title-cased so `.name` could be printed
 * straight to the screen, which made the identifier and the copy the same string. [label]
 * is the half that shows.
 */
enum class NetplayQualityLabel(@StringRes val labelRes: Int) {
    Excellent(R.string.ingame_netplay_quality_excellent),
    Good(R.string.ingame_netplay_quality_good),
    Fair(R.string.ingame_netplay_quality_fair),
    Poor(R.string.ingame_netplay_quality_poor),
    Bad(R.string.ingame_netplay_quality_bad)
}

data class NetplayQualityInfo(
    val peerDisplayName: String,
    val role: NetplayMenuRole,
    val pingMs: Int?,
    val label: NetplayQualityLabel
) {
    companion object {
        fun labelForRttMs(pingMs: Int?): NetplayQualityLabel {
            if (pingMs == null) return NetplayQualityLabel.Bad
            return when {
                pingMs < 40 -> NetplayQualityLabel.Excellent
                pingMs < 80 -> NetplayQualityLabel.Good
                pingMs < 150 -> NetplayQualityLabel.Fair
                pingMs < 200 -> NetplayQualityLabel.Poor
                else -> NetplayQualityLabel.Bad
            }
        }
    }
}

@Composable
fun InGameMenu(
    gameName: String,
    coreName: String? = null,
    cheatsAvailable: Boolean = false,
    achievementsAvailable: Boolean = false,
    statesSupported: Boolean = false,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAction: (InGameMenuAction) -> Unit,
    isHardcoreMode: Boolean = false,
    hardcoreConfirmed: Boolean = false,
    availableDiscs: Int = 0,
    netplaySupported: Boolean = false,
    isInNetplaySession: Boolean = false,
    touchControlsVisible: Boolean = false,
    speedrunAvailable: Boolean = false,
    speedrunArmed: Boolean = false,
    hasQuickSave: Boolean = false,
    quickHistoryFocus: QuickHistoryFocus = QuickHistoryFocus.NONE,
    quickHistoryIndex: Int = 0,
    quickHistoryEntries: List<SaveStateManager.SlotInfo> = emptyList(),
    onQuickHistoryFocusChange: (QuickHistoryFocus, Int) -> Unit = { _, _ -> },
    onQuickHistoryLoad: (Int) -> Unit = {},
    twoColumnMenu: Boolean = false,
    manualAvailable: Boolean = false,
    walkthroughAvailable: Boolean = false,
    walkthroughPanelAvailable: Boolean = false,
    walkthroughPanelShown: Boolean = false,
    swapScreensAvailable: Boolean = false,
    openSection: InGameMenuSection? = null,
    onCloseSection: () -> Unit = {},
    sectionContent: @Composable (InGameMenuSection) -> InputHandler
): InputHandler {
    val menuItems: List<Pair<Int, InGameMenuAction>> = remember(
        swapScreensAvailable,
        manualAvailable,
        walkthroughAvailable,
        walkthroughPanelAvailable,
        walkthroughPanelShown,
        cheatsAvailable,
        achievementsAvailable,
        statesSupported,
        isHardcoreMode,
        availableDiscs,
        netplaySupported,
        isInNetplaySession,
        touchControlsVisible,
        speedrunAvailable,
        speedrunArmed,
        hasQuickSave
    ) {
        buildList {
            if (availableDiscs > 1 && !isInNetplaySession) {
                add(R.string.ingame_menu_swap_disc to InGameMenuAction.SwapDisc)
            }
            add(R.string.ingame_menu_resume to InGameMenuAction.Resume)
            val showStates = !isHardcoreMode && statesSupported && !isInNetplaySession
            if (showStates) {
                add(R.string.ingame_menu_quick_save to InGameMenuAction.QuickSave)
                add(R.string.ingame_menu_quick_load to InGameMenuAction.QuickLoad)
                add(R.string.ingame_menu_manage_states to InGameMenuAction.ManageStates)
            }
            if (!isInNetplaySession && cheatsAvailable) {
                add(R.string.ingame_menu_cheats to InGameMenuAction.Cheats)
            }
            if (achievementsAvailable) {
                add(R.string.ingame_menu_achievements to InGameMenuAction.Achievements)
            }
            if (manualAvailable) {
                add(R.string.ingame_menu_view_manual to InGameMenuAction.ViewManual)
            }
            if (walkthroughAvailable) {
                add(R.string.ingame_menu_view_walkthrough to InGameMenuAction.ViewWalkthrough)
                if (walkthroughPanelShown) {
                    add(R.string.ingame_menu_hide_walkthrough_panel to InGameMenuAction.ToggleWalkthroughPanel)
                } else if (walkthroughPanelAvailable) {
                    add(R.string.ingame_menu_walkthrough_beside_game to InGameMenuAction.ToggleWalkthroughPanel)
                }
            }
            if (netplaySupported) {
                add(R.string.ingame_menu_netplay to InGameMenuAction.Netplay)
            }
            add(R.string.ingame_menu_settings to InGameMenuAction.Settings)
            if (swapScreensAvailable) {
                add(R.string.ingame_menu_swap_screens to InGameMenuAction.SwapScreens)
            }
            if (speedrunAvailable && !isInNetplaySession) {
                val speedrunLabel = if (speedrunArmed) {
                    R.string.ingame_menu_stop_speedrun_timer
                } else {
                    R.string.ingame_menu_speedrun_timer
                }
                add(speedrunLabel to InGameMenuAction.ToggleSpeedrun)
            }
            if (touchControlsVisible) {
                add(R.string.ingame_menu_touch_controls to InGameMenuAction.CustomizeTouchControls)
            }
            if (!isInNetplaySession) {
                add(R.string.ingame_menu_reset to InGameMenuAction.Reset)
            }
            add(R.string.ingame_menu_quit to InGameMenuAction.Quit)
        }
    }

    LaunchedEffect(menuItems.size) {
        val clamped = focusedIndex.coerceIn(0, (menuItems.size - 1).coerceAtLeast(0))
        if (clamped != focusedIndex) onFocusChange(clamped)
    }

    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAction = rememberUpdatedState(onAction)
    val currentHasQuickSave = rememberUpdatedState(hasQuickSave)
    val currentQuickHistoryFocus = rememberUpdatedState(quickHistoryFocus)
    val currentQuickHistoryIndex = rememberUpdatedState(quickHistoryIndex)
    val currentQuickHistoryEntries = rememberUpdatedState(quickHistoryEntries)
    val currentOnQuickHistoryFocusChange = rememberUpdatedState(onQuickHistoryFocusChange)
    val currentOnQuickHistoryLoad = rememberUpdatedState(onQuickHistoryLoad)

    val columns = if (twoColumnMenu && LocalConfiguration.current.screenWidthDp >= DimensionTokens.Layout.menuBreakpointWide) 2 else 1

    val inputHandler = remember(menuItems, columns) {
        object : InputHandler {
            private val historyFocus: QuickHistoryFocus get() = currentQuickHistoryFocus.value

            private fun focusHistory(focus: QuickHistoryFocus, index: Int = 0) {
                currentOnQuickHistoryFocusChange.value(focus, index)
            }

            private fun collapseHistory() {
                if (historyFocus != QuickHistoryFocus.NONE) focusHistory(QuickHistoryFocus.NONE)
            }

            private fun enterStrip() {
                if (currentQuickHistoryEntries.value.isNotEmpty()) focusHistory(QuickHistoryFocus.STRIP)
            }

            override fun onUp(): InputResult {
                if (historyFocus == QuickHistoryFocus.STRIP) {
                    focusHistory(QuickHistoryFocus.BUTTON)
                    return InputResult.HANDLED
                }
                collapseHistory()
                val idx = currentFocusedIndex.value
                val newIndex = if (columns == 1) {
                    if (idx <= 0) menuItems.lastIndex else idx - 1
                } else {
                    val target = idx - columns
                    if (target >= 0) target else idx
                }
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                if (historyFocus == QuickHistoryFocus.BUTTON && currentQuickHistoryEntries.value.isNotEmpty()) {
                    enterStrip()
                    return InputResult.HANDLED
                }
                collapseHistory()
                val idx = currentFocusedIndex.value
                val newIndex = if (columns == 1) {
                    if (idx >= menuItems.lastIndex) 0 else idx + 1
                } else {
                    val target = idx + columns
                    when {
                        target <= menuItems.lastIndex -> target
                        idx < menuItems.lastIndex -> menuItems.lastIndex
                        else -> idx
                    }
                }
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                when (historyFocus) {
                    QuickHistoryFocus.STRIP -> {
                        val index = currentQuickHistoryIndex.value
                        if (index > 0) {
                            focusHistory(QuickHistoryFocus.STRIP, index - 1)
                        } else {
                            focusHistory(QuickHistoryFocus.BUTTON)
                        }
                        return InputResult.HANDLED
                    }
                    QuickHistoryFocus.BUTTON -> {
                        focusHistory(QuickHistoryFocus.NONE)
                        return InputResult.HANDLED
                    }
                    QuickHistoryFocus.NONE -> Unit
                }
                if (columns > 1) {
                    val idx = currentFocusedIndex.value
                    if (idx % columns != 0) {
                        val newIndex = idx - 1
                        val newAction = menuItems.getOrNull(newIndex)?.second
                        if (newAction == InGameMenuAction.QuickLoad && currentHasQuickSave.value) {
                            focusHistory(QuickHistoryFocus.BUTTON)
                        }
                        currentOnFocusChange.value(newIndex)
                    }
                }
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                when (historyFocus) {
                    QuickHistoryFocus.STRIP -> {
                        val index = currentQuickHistoryIndex.value
                        val next = (index + 1).coerceAtMost(currentQuickHistoryEntries.value.lastIndex)
                        if (next > index) focusHistory(QuickHistoryFocus.STRIP, next)
                        return InputResult.HANDLED
                    }
                    QuickHistoryFocus.BUTTON -> {
                        enterStrip()
                        return InputResult.HANDLED
                    }
                    QuickHistoryFocus.NONE -> Unit
                }
                val idx = currentFocusedIndex.value
                val action = menuItems.getOrNull(idx)?.second
                if (action == InGameMenuAction.QuickLoad && currentHasQuickSave.value) {
                    focusHistory(QuickHistoryFocus.BUTTON)
                } else if (columns > 1) {
                    if (idx % columns != columns - 1 && idx + 1 <= menuItems.lastIndex) {
                        currentOnFocusChange.value(idx + 1)
                    }
                }
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                when (historyFocus) {
                    QuickHistoryFocus.STRIP -> {
                        currentQuickHistoryEntries.value.getOrNull(currentQuickHistoryIndex.value)
                            ?.let { currentOnQuickHistoryLoad.value(it.slotNumber) }
                        return InputResult.HANDLED
                    }
                    QuickHistoryFocus.BUTTON -> {
                        enterStrip()
                        return InputResult.HANDLED
                    }
                    QuickHistoryFocus.NONE -> Unit
                }
                val action = menuItems.getOrNull(currentFocusedIndex.value)?.second
                if (action == InGameMenuAction.QuickLoad && !currentHasQuickSave.value) {
                    return InputResult.HANDLED
                }
                action?.let { currentOnAction.value(it) }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                when (historyFocus) {
                    QuickHistoryFocus.STRIP -> focusHistory(QuickHistoryFocus.BUTTON)
                    QuickHistoryFocus.BUTTON -> focusHistory(QuickHistoryFocus.NONE)
                    QuickHistoryFocus.NONE -> currentOnAction.value(InGameMenuAction.Resume)
                }
                return InputResult.HANDLED
            }
        }
    }

    val condensedWidth = if (columns > 1) {
        DimensionTokens.Layout.inGameMenuWidthWide.dp
    } else {
        DimensionTokens.Layout.inGameMenuWidth.dp
    }

    return InGameMenuPanel(
        menuItems = menuItems,
        openSection = openSection,
        condensedWidth = condensedWidth,
        listHandler = inputHandler,
        onFocusChange = onFocusChange,
        onAction = onAction,
        onCloseSection = onCloseSection,
        list = { collapsed ->
            InGameMenuList(
                gameName = gameName,
                coreName = coreName,
                hardcoreConfirmed = hardcoreConfirmed,
                menuItems = menuItems,
                columns = columns,
                collapsed = collapsed,
                openAction = openSection?.action,
                focusedIndex = focusedIndex,
                hasQuickSave = hasQuickSave,
                quickHistoryFocus = quickHistoryFocus,
                quickHistoryIndex = quickHistoryIndex,
                quickHistoryEntries = quickHistoryEntries,
                onFocusChange = onFocusChange,
                onQuickHistoryFocusChange = onQuickHistoryFocusChange,
                onQuickHistoryLoad = onQuickHistoryLoad,
                onAction = onAction,
                onCloseSection = onCloseSection
            )
        },
        sectionContent = sectionContent
    )
}

@Composable
private fun InGameMenuList(
    gameName: String,
    coreName: String?,
    hardcoreConfirmed: Boolean,
    menuItems: List<Pair<Int, InGameMenuAction>>,
    columns: Int,
    collapsed: Boolean,
    openAction: InGameMenuAction?,
    focusedIndex: Int,
    hasQuickSave: Boolean,
    quickHistoryFocus: QuickHistoryFocus,
    quickHistoryIndex: Int,
    quickHistoryEntries: List<SaveStateManager.SlotInfo>,
    onFocusChange: (Int) -> Unit,
    onQuickHistoryFocusChange: (QuickHistoryFocus, Int) -> Unit,
    onQuickHistoryLoad: (Int) -> Unit,
    onAction: (InGameMenuAction) -> Unit,
    onCloseSection: () -> Unit
) {
    val menuGridState = rememberLazyGridState()
    val gridColumns = if (collapsed) 1 else columns
    val reduced = reducedMotion()

    LaunchedEffect(focusedIndex, menuItems.size, gridColumns) {
        if (menuItems.isEmpty()) return@LaunchedEffect
        snapshotFlow { menuGridState.layoutInfo.viewportSize.height }
            .collectLatest {
                menuGridState.animateScrollToItemCentered(focusedIndex.coerceIn(0, menuItems.lastIndex), reduced)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.Top)
    ) {
        AnimatedVisibility(
            visible = !collapsed,
            enter = motionEnter(
                expandVertically(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeIn(tween(Motion.durationContent))
            ),
            exit = motionExit(
                shrinkVertically(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeOut(tween(Motion.durationContent))
            )
        ) {
            InGameMenuHeader(
                gameName = gameName,
                coreName = coreName,
                hardcoreConfirmed = hardcoreConfirmed
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            state = menuGridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalEdgeFade(menuGridState, fadeHeight = Dimens.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            itemsIndexed(
                items = menuItems,
                key = { _: Int, item: Pair<Int, InGameMenuAction> -> item.second.toString() }
            ) { index, item ->
                val (labelRes, action) = item
                val label = stringResource(labelRes)
                val rowFocused = !collapsed && index == focusedIndex
                val onRowClick = {
                    if (collapsed && action == openAction) onCloseSection() else onAction(action)
                }
                if (action == InGameMenuAction.QuickLoad && hasQuickSave) {
                    val historyFocus = if (rowFocused) quickHistoryFocus else QuickHistoryFocus.NONE
                    QuickLoadCell(
                        text = label,
                        historyFocus = historyFocus,
                        stripFocusIndex = quickHistoryIndex.takeIf { historyFocus == QuickHistoryFocus.STRIP },
                        isFocused = rowFocused && historyFocus == QuickHistoryFocus.NONE,
                        collapsed = collapsed,
                        entries = quickHistoryEntries,
                        onClick = onRowClick,
                        onHistoryClick = {
                            if (historyFocus == QuickHistoryFocus.NONE) {
                                onFocusChange(index)
                                onQuickHistoryFocusChange(QuickHistoryFocus.BUTTON, 0)
                            } else {
                                onQuickHistoryFocusChange(QuickHistoryFocus.NONE, 0)
                            }
                        },
                        onEntryClick = onQuickHistoryLoad
                    )
                } else {
                    NavDrawerRow(
                        icon = action.icon,
                        label = label,
                        isFocused = rowFocused,
                        isSelected = action == openAction,
                        onClick = onRowClick,
                        modifier = Modifier.padding(end = Dimens.spacingMd),
                        enabled = action != InGameMenuAction.QuickLoad,
                        labelVisible = !collapsed
                    )
                }
            }
        }
    }
}

@Composable
private fun InGameMenuHeader(
    gameName: String,
    coreName: String?,
    hardcoreConfirmed: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (hardcoreConfirmed) {
            Text(
                text = stringResource(R.string.ingame_menu_hardcore_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ColorTokens.Domain.AchievementTier.hardcore,
                modifier = Modifier
                    .padding(bottom = Dimens.spacingXs)
                    .background(
                        ColorTokens.Domain.AchievementTier.hardcore.copy(alpha = HARDCORE_BADGE_FILL_ALPHA),
                        RoundedCornerShape(Dimens.radiusSm)
                    )
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            )
        }
        Text(
            text = gameName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!coreName.isNullOrBlank()) {
            Text(
                text = coreName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CORE_NAME_ALPHA),
                maxLines = 1
            )
        }
    }
}

@Composable
fun DiscMenu(
    labels: List<String>,
    currentIndex: Int,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.ingame_disc_title)
): InputHandler {
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember(labels) {
        object : InputHandler {
            override fun onUp(): InputResult {
                val idx = currentFocusedIndex.value
                val newIndex = if (idx <= 0) labels.lastIndex else idx - 1
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                val idx = currentFocusedIndex.value
                val newIndex = if (idx >= labels.lastIndex) 0 else idx + 1
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                currentOnSelect.value(currentFocusedIndex.value)
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    val discConfiguration = LocalConfiguration.current
    val discBottomReserved = gripReserveBottomInset()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(bottom = discBottomReserved)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        val availableHeightDp = discConfiguration.screenHeightDp - discBottomReserved.value
        val maxHeightDp =
            (availableHeightDp * DimensionTokens.Layout.inGameMenuMaxHeightPct / 100f).dp
        val listState = rememberLazyListState()
        val reduced = reducedMotion()

        LaunchedEffect(focusedIndex, labels.size) {
            if (labels.isEmpty()) return@LaunchedEffect
            val target = focusedIndex.coerceIn(0, labels.lastIndex)
            if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }

        Surface(
            modifier = Modifier
                .widthIn(max = DimensionTokens.Layout.inGameMenuWidth.dp)
                .heightIn(max = maxHeightDp)
                .padding(12.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listItemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                        val text = if (index == currentIndex) {
                            stringResource(R.string.ingame_disc_current, label)
                        } else {
                            label
                        }
                        MenuButton(
                            text = text,
                            isFocused = index == focusedIndex,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun QuickLoadCell(
    text: String,
    historyFocus: QuickHistoryFocus,
    stripFocusIndex: Int?,
    isFocused: Boolean,
    collapsed: Boolean,
    entries: List<SaveStateManager.SlotInfo>,
    onClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onEntryClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        QuickLoadRow(
            text = text,
            isFocused = isFocused,
            collapsed = collapsed,
            historyFocus = historyFocus,
            onClick = onClick,
            onHistoryClick = onHistoryClick
        )
        AnimatedVisibility(
            visible = historyFocus != QuickHistoryFocus.NONE,
            enter = motionEnter(
                expandVertically(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeIn(tween(Motion.durationContent))
            ),
            exit = motionExit(
                shrinkVertically(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeOut(tween(Motion.durationContent))
            )
        ) {
            QuickLoadHistoryStrip(
                entries = entries,
                focusedIndex = stripFocusIndex,
                onLoad = onEntryClick,
                modifier = Modifier.padding(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingMd,
                    top = Dimens.spacingSm
                )
            )
        }
    }
}

@Composable
private fun QuickLoadRow(
    text: String,
    isFocused: Boolean,
    collapsed: Boolean,
    historyFocus: QuickHistoryFocus,
    onClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        NavDrawerRow(
            icon = InGameMenuAction.QuickLoad.icon,
            label = text,
            isFocused = isFocused,
            isSelected = false,
            onClick = onClick,
            modifier = Modifier.weight(1f),
            labelVisible = !collapsed
        )
        AnimatedVisibility(
            visible = !collapsed,
            enter = motionEnter(
                expandHorizontally(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeIn(tween(Motion.durationContent))
            ),
            exit = motionExit(
                shrinkHorizontally(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeOut(tween(Motion.durationContent))
            )
        ) {
            QuickHistoryButton(historyFocus = historyFocus, onClick = onHistoryClick)
        }
    }
}

@Composable
private fun QuickHistoryButton(
    historyFocus: QuickHistoryFocus,
    onClick: () -> Unit
) {
    val iconBackground = when (historyFocus) {
        QuickHistoryFocus.BUTTON -> MaterialTheme.colorScheme.primary
        QuickHistoryFocus.STRIP -> MaterialTheme.colorScheme.primaryContainer
        QuickHistoryFocus.NONE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = when (historyFocus) {
        QuickHistoryFocus.BUTTON -> MaterialTheme.colorScheme.onPrimary
        QuickHistoryFocus.STRIP -> MaterialTheme.colorScheme.onPrimaryContainer
        QuickHistoryFocus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(iconBackground)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = stringResource(R.string.ingame_menu_quick_save_history_action),
            tint = iconTint
        )
    }
}

@Composable
private fun MenuButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private const val HARDCORE_BADGE_FILL_ALPHA = 0.15f
private const val CORE_NAME_ALPHA = 0.4f
