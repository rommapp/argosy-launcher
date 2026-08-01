package com.nendo.argosy.libretro.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.pocketTacoBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus

sealed class InGameMenuAction {
    data object SwapDisc : InGameMenuAction()
    data object Resume : InGameMenuAction()
    data object QuickSave : InGameMenuAction()
    data object QuickLoad : InGameMenuAction()
    data object QuickLoadHistory : InGameMenuAction()
    data object ManageStates : InGameMenuAction()
    data object Settings : InGameMenuAction()
    data object Cheats : InGameMenuAction()
    data object Reset : InGameMenuAction()
    data object Quit : InGameMenuAction()
    data object OpenToFriends : InGameMenuAction()
    data object InviteFriend : InGameMenuAction()
    data object ClearReservation : InGameMenuAction()
    data object CloseNetplaySession : InGameMenuAction()
    data object CustomizeTouchControls : InGameMenuAction()
    data object ToggleSpeedrun : InGameMenuAction()
}

enum class NetplayMenuRole { Host, Guest }

enum class NetplayQualityLabel { Excellent, Good, Fair, Poor, Bad }

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
    statesSupported: Boolean = false,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAction: (InGameMenuAction) -> Unit,
    isHardcoreMode: Boolean = false,
    availableDiscs: Int = 0,
    netplaySupported: Boolean = false,
    isInNetplaySession: Boolean = false,
    netplayRole: NetplayMenuRole? = null,
    netplaySessionIsReserved: Boolean = false,
    netplayQuality: NetplayQualityInfo? = null,
    touchControlsVisible: Boolean = false,
    speedrunAvailable: Boolean = false,
    speedrunArmed: Boolean = false,
    hasQuickSave: Boolean = false,
    quickHistoryFocused: Boolean = false,
    onQuickHistoryFocusChange: (Boolean) -> Unit = {},
    twoColumnMenu: Boolean = false
): InputHandler {
    val menuItems = remember(
        cheatsAvailable,
        statesSupported,
        isHardcoreMode,
        availableDiscs,
        netplaySupported,
        isInNetplaySession,
        netplayRole,
        netplaySessionIsReserved,
        touchControlsVisible,
        speedrunAvailable,
        speedrunArmed,
        hasQuickSave
    ) {
        buildList {
            if (availableDiscs > 1 && !isInNetplaySession) {
                add("Swap Disc" to InGameMenuAction.SwapDisc)
            }
            add("Resume" to InGameMenuAction.Resume)
            val showStates = !isHardcoreMode && statesSupported && !isInNetplaySession
            if (showStates) {
                add("Quick Save" to InGameMenuAction.QuickSave)
                add("Quick Load" to InGameMenuAction.QuickLoad)
                add("Manage States" to InGameMenuAction.ManageStates)
            }
            if (!isInNetplaySession && cheatsAvailable) {
                add("Cheats" to InGameMenuAction.Cheats)
            }
            if (netplaySupported) {
                if (isInNetplaySession) {
                    if (netplayRole == NetplayMenuRole.Host) {
                        add("Invite Friend..." to InGameMenuAction.InviteFriend)
                        if (netplaySessionIsReserved) {
                            add("Open to All Friends" to InGameMenuAction.ClearReservation)
                        }
                        add("Close Netplay Server" to InGameMenuAction.CloseNetplaySession)
                    } else {
                        add("Leave Netplay Session" to InGameMenuAction.CloseNetplaySession)
                    }
                } else {
                    add("Open Netplay Server" to InGameMenuAction.OpenToFriends)
                }
            }
            add("Settings" to InGameMenuAction.Settings)
            if (speedrunAvailable && !isInNetplaySession) {
                add((if (speedrunArmed) "Stop Speedrun Timer" else "Speedrun Timer") to InGameMenuAction.ToggleSpeedrun)
            }
            if (touchControlsVisible) {
                add("Touch Controls" to InGameMenuAction.CustomizeTouchControls)
            }
            if (!isInNetplaySession) {
                add("Reset" to InGameMenuAction.Reset)
            }
            add("Quit Game" to InGameMenuAction.Quit)
        }
    }

    LaunchedEffect(menuItems.size) {
        val clamped = focusedIndex.coerceIn(0, (menuItems.size - 1).coerceAtLeast(0))
        if (clamped != focusedIndex) onFocusChange(clamped)
    }

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAction = rememberUpdatedState(onAction)
    val currentHasQuickSave = rememberUpdatedState(hasQuickSave)
    val currentQuickHistoryFocused = rememberUpdatedState(quickHistoryFocused)
    val currentOnQuickHistoryFocusChange = rememberUpdatedState(onQuickHistoryFocusChange)

    val columns = if (twoColumnMenu && LocalConfiguration.current.screenWidthDp >= DimensionTokens.Layout.menuBreakpointWide) 2 else 1

    val inputHandler = remember(menuItems, columns) {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (currentQuickHistoryFocused.value) currentOnQuickHistoryFocusChange.value(false)
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
                if (currentQuickHistoryFocused.value) currentOnQuickHistoryFocusChange.value(false)
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
                if (currentQuickHistoryFocused.value) {
                    currentOnQuickHistoryFocusChange.value(false)
                    return InputResult.HANDLED
                }
                if (columns > 1) {
                    val idx = currentFocusedIndex.value
                    if (idx % columns != 0) {
                        val newIndex = idx - 1
                        val newAction = menuItems.getOrNull(newIndex)?.second
                        if (newAction == InGameMenuAction.QuickLoad && currentHasQuickSave.value) {
                            currentOnQuickHistoryFocusChange.value(true)
                        }
                        currentOnFocusChange.value(newIndex)
                    }
                }
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                val idx = currentFocusedIndex.value
                val action = menuItems.getOrNull(idx)?.second
                if (action == InGameMenuAction.QuickLoad && currentHasQuickSave.value && !currentQuickHistoryFocused.value) {
                    currentOnQuickHistoryFocusChange.value(true)
                } else if (columns > 1) {
                    if (idx % columns != columns - 1 && idx + 1 <= menuItems.lastIndex) {
                        if (currentQuickHistoryFocused.value) currentOnQuickHistoryFocusChange.value(false)
                        currentOnFocusChange.value(idx + 1)
                    }
                }
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                val action = menuItems.getOrNull(currentFocusedIndex.value)?.second
                if (action == InGameMenuAction.QuickLoad && !currentHasQuickSave.value) {
                    return InputResult.HANDLED
                }
                if (action == InGameMenuAction.QuickLoad && currentQuickHistoryFocused.value) {
                    currentOnAction.value(InGameMenuAction.QuickLoadHistory)
                } else {
                    action?.let { currentOnAction.value(it) }
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnAction.value(InGameMenuAction.Resume)
                return InputResult.HANDLED
            }
        }
    }

    val menuConfiguration = LocalConfiguration.current
    val menuBottomReserved = pocketTacoBottomInset()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(bottom = menuBottomReserved)
            .clickableNoFocus { currentOnAction.value(InGameMenuAction.Resume) },
        contentAlignment = Alignment.Center
    ) {
        val availableHeightDp = menuConfiguration.screenHeightDp - menuBottomReserved.value
        val maxHeightDp =
            (availableHeightDp * DimensionTokens.Layout.inGameMenuMaxHeightPct / 100f).dp
        val menuGridState = rememberLazyGridState()

        LaunchedEffect(focusedIndex, menuItems.size) {
            if (menuItems.isEmpty()) return@LaunchedEffect
            val target = focusedIndex.coerceIn(0, menuItems.lastIndex)
            val visibleItems = menuGridState.layoutInfo.visibleItemsInfo
            val viewportHeight = menuGridState.layoutInfo.viewportEndOffset
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size.height } / visibleItems.size
            } else 80
            val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
            menuGridState.animateScrollToItem(target, -targetOffset)
        }

        Surface(
            modifier = Modifier
                .widthIn(max = if (columns > 1) DimensionTokens.Layout.inGameMenuWidthWide.dp else DimensionTokens.Layout.inGameMenuWidth.dp)
                .heightIn(max = maxHeightDp)
                .padding(12.dp)
                .clickableNoFocus {}
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
                if (isHardcoreMode) {
                    Text(
                        text = "HARDCORE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier
                            .background(
                                Color(0xFFFFD700).copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = gameName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    if (!coreName.isNullOrBlank()) {
                        Text(
                            text = coreName,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = menuGridState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = menuItems,
                        key = { _: Int, item: Pair<String, InGameMenuAction> -> item.second.toString() }
                    ) { index, item ->
                        val (label, action) = item
                        when {
                            action == InGameMenuAction.QuickLoad && hasQuickSave -> {
                                QuickLoadRow(
                                    text = label,
                                    isFocused = index == focusedIndex && !quickHistoryFocused,
                                    historyFocused = index == focusedIndex && quickHistoryFocused,
                                    onClick = { onAction(action) },
                                    onHistoryClick = { onAction(InGameMenuAction.QuickLoadHistory) }
                                )
                            }
                            action == InGameMenuAction.QuickLoad -> {
                                MenuButton(
                                    text = label,
                                    isFocused = index == focusedIndex,
                                    enabled = false,
                                    onClick = {}
                                )
                            }
                            else -> {
                                MenuButton(
                                    text = label,
                                    isFocused = index == focusedIndex,
                                    onClick = { onAction(action) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    return inputHandler
}

@Composable
fun DiscMenu(
    labels: List<String>,
    currentIndex: Int,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Swap Disc"
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
    val discBottomReserved = pocketTacoBottomInset()

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

        LaunchedEffect(focusedIndex, labels.size) {
            if (labels.isEmpty()) return@LaunchedEffect
            listState.animateScrollToItem(focusedIndex.coerceIn(0, labels.lastIndex))
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
                        val text = if (index == currentIndex) "$label  (current)" else label
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
private fun NetplayQualityRow(info: NetplayQualityInfo) {
    val qualityColor = when (info.label) {
        NetplayQualityLabel.Excellent -> Color(0xFF22C55E)
        NetplayQualityLabel.Good -> Color(0xFF84CC16)
        NetplayQualityLabel.Fair -> Color(0xFFFBBF24)
        NetplayQualityLabel.Poor -> Color(0xFFF97316)
        NetplayQualityLabel.Bad -> Color(0xFFEF4444)
    }
    val pingText = info.pingMs?.let { "${it}ms" } ?: "--"
    val roleLabel = if (info.role == NetplayMenuRole.Host) "Guest" else "Host"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$roleLabel: ${info.peerDisplayName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$pingText  ${info.label.name}",
                style = MaterialTheme.typography.labelSmall,
                color = qualityColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickLoadRow(
    text: String,
    isFocused: Boolean,
    historyFocused: Boolean,
    onClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            MenuButton(
                text = text,
                isFocused = isFocused,
                onClick = onClick
            )
        }
        val iconBackground = if (historyFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val iconTint = if (historyFocused) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(iconBackground)
                .clickableNoFocus(onClick = onHistoryClick)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = "Quick save history",
                tint = iconTint
            )
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val backgroundColor = when {
        !enabled && isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isFocused -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickableNoFocus(enabled = enabled, onClick = onClick)
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
