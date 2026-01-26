package com.nendo.argosy.libretro.ui.cheats

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.scanner.MemoryMatch
import com.nendo.argosy.libretro.scanner.MemoryScanner
import com.nendo.argosy.libretro.scanner.NarrowResult
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.touchOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CheatsTab(val label: String) {
    CHEATS("Cheats"),
    DISCOVER("Discover")
}

data class CheatDisplayItem(
    val id: Long,
    val description: String,
    val code: String,
    val enabled: Boolean,
    val isUserCreated: Boolean = false,
    val lastUsedAt: Long? = null
) {
    val address: Int? by lazy {
        code.substringBefore(':').toIntOrNull(16)
    }
}

@Composable
fun CheatsScreen(
    cheats: List<CheatDisplayItem>,
    scanner: MemoryScanner,
    initialTab: CheatsTab = CheatsTab.CHEATS,
    onToggleCheat: (Long, Boolean) -> Unit,
    onCreateCheat: (address: Int, value: Int, description: String) -> Unit,
    onUpdateCheat: (id: Long, description: String, code: String) -> Unit,
    onDeleteCheat: (Long) -> Unit,
    onGetRam: () -> ByteArray?,
    onTabChange: (CheatsTab) -> Unit = {},
    onDismiss: () -> Unit
): InputHandler {
    var currentTab by remember { mutableStateOf(initialTab) }
    var contentFocusIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var scanResults by remember { mutableStateOf(scanner.getResults()) }
    var valueSearchText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var ramError by remember { mutableStateOf<String?>(null) }
    var narrowError by remember { mutableStateOf<String?>(null) }
    var hasSnapshot by remember { mutableStateOf(scanner.hasSnapshot()) }
    var canCompare by remember { mutableStateOf(scanner.canCompare()) }
    var candidateCount by remember { mutableIntStateOf(scanner.getCandidateCount()) }

    val scope = rememberCoroutineScope()
    var showSearchDialog by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val filteredCheats = if (searchQuery.isBlank()) cheats else {
        cheats.filter { it.description.contains(searchQuery, ignoreCase = true) }
    }
    val currentFilteredCheats by rememberUpdatedState(filteredCheats)
    val knownAddresses = remember(cheats) {
        cheats.mapNotNull { cheat -> cheat.address?.let { it to cheat.description } }.toMap()
    }

    var showingResults by remember { mutableStateOf(false) }

    var editingCheat by remember { mutableStateOf<CheatDisplayItem?>(null) }
    var creatingCheatAddress by remember { mutableStateOf<Int?>(null) }
    var creatingCheatValue by remember { mutableStateOf<Int?>(null) }

    fun getDiscoverFocusableIndices(): List<Int> {
        val showActions = !hasSnapshot || canCompare
        return when {
            !hasSnapshot -> listOf(0)
            showActions && canCompare -> {
                if (scanResults.isNotEmpty()) listOf(0, 1, 2) else listOf(0, 1)
            }
            showingResults && scanResults.isNotEmpty() -> buildList {
                add(0)
                for (i in scanResults.indices) {
                    add(1 + i)
                }
            }
            else -> emptyList()
        }
    }

    fun getNextFocusIndex(current: Int, delta: Int): Int {
        return when (currentTab) {
            CheatsTab.CHEATS -> {
                val maxIndex = currentFilteredCheats.size
                (current + delta).coerceIn(0, maxIndex)
            }
            CheatsTab.DISCOVER -> {
                val focusable = getDiscoverFocusableIndices()
                if (focusable.isEmpty()) return current
                val currentPos = focusable.indexOf(current).takeIf { it >= 0 } ?: 0
                val newPos = (currentPos + delta).coerceIn(0, focusable.lastIndex)
                focusable[newPos]
            }
        }
    }

    fun setTab(tab: CheatsTab) {
        if (tab != currentTab) {
            currentTab = tab
            onTabChange(tab)
            narrowError = null
            contentFocusIndex = when (tab) {
                CheatsTab.DISCOVER -> getDiscoverFocusableIndices().firstOrNull() ?: 0
                else -> 0
            }
        }
    }

    fun handleTabChange(delta: Int) {
        val tabs = CheatsTab.entries
        val currentIndex = tabs.indexOf(currentTab)
        val newIndex = (currentIndex + delta).coerceIn(0, tabs.lastIndex)
        setTab(tabs[newIndex])
    }

    fun refreshScannerState() {
        hasSnapshot = scanner.hasSnapshot()
        canCompare = scanner.canCompare()
        candidateCount = scanner.getCandidateCount()
        scanResults = scanner.getResults()
    }

    fun handleDiscoverAction(focusIndex: Int) {
        if (isLoading) return

        val showActions = !hasSnapshot || canCompare
        narrowError = null

        scope.launch {
            when {
                !hasSnapshot -> {
                    if (focusIndex == 0) {
                        isLoading = true
                        val ram = withContext(Dispatchers.Default) { onGetRam() }
                        if (ram == null) {
                            ramError = "RAM not available for this core"
                            isLoading = false
                            return@launch
                        }
                        withContext(Dispatchers.Default) { scanner.takeSnapshot(ram) }
                        refreshScannerState()
                        isLoading = false
                        contentFocusIndex = getDiscoverFocusableIndices().firstOrNull() ?: 0
                    }
                }
                showActions && canCompare -> {
                    when (focusIndex) {
                        0 -> {
                            isLoading = true
                            val ram = withContext(Dispatchers.Default) { onGetRam() }
                            if (ram != null) {
                                if (scanResults.isEmpty()) {
                                    withContext(Dispatchers.Default) { scanner.compareChanged(ram) }
                                    refreshScannerState()
                                    showingResults = true
                                } else {
                                    val result = withContext(Dispatchers.Default) { scanner.narrowChanged(ram) }
                                    when (result) {
                                        is NarrowResult.Success -> {
                                            refreshScannerState()
                                            showingResults = true
                                        }
                                        is NarrowResult.NoChanges -> {
                                            narrowError = "No changes detected - play more first"
                                        }
                                        is NarrowResult.NotReady -> {
                                            narrowError = "Play more to narrow results"
                                        }
                                    }
                                }
                            }
                            isLoading = false
                            contentFocusIndex = 0
                        }
                        1 -> {
                            isLoading = true
                            val ram = withContext(Dispatchers.Default) { onGetRam() }
                            if (ram != null) {
                                if (scanResults.isEmpty()) {
                                    withContext(Dispatchers.Default) { scanner.compareSame(ram) }
                                    refreshScannerState()
                                    showingResults = true
                                } else {
                                    val result = withContext(Dispatchers.Default) { scanner.narrowSame(ram) }
                                    when (result) {
                                        is NarrowResult.Success -> {
                                            refreshScannerState()
                                            showingResults = true
                                        }
                                        is NarrowResult.NoChanges -> {
                                            narrowError = "No unchanged values - try 'Changed' instead"
                                        }
                                        is NarrowResult.NotReady -> {
                                            narrowError = "Play more to narrow results"
                                        }
                                    }
                                }
                            }
                            isLoading = false
                            contentFocusIndex = 0
                        }
                        2 -> {
                            showingResults = true
                            contentFocusIndex = 0
                        }
                    }
                }
                showingResults -> {
                    when (focusIndex) {
                        0 -> {
                            val value = valueSearchText.toIntOrNull()
                            if (value != null) {
                                isLoading = true
                                val ram = withContext(Dispatchers.Default) { onGetRam() }
                                if (ram != null) {
                                    withContext(Dispatchers.Default) { scanner.filterByValue(ram, value) }
                                }
                                refreshScannerState()
                                isLoading = false
                            }
                        }
                        else -> {
                            val resultIndex = focusIndex - 1
                            scanResults.getOrNull(resultIndex)?.let { match ->
                                if (knownAddresses.containsKey(match.address)) {
                                    narrowError = "Address already saved as cheat"
                                } else {
                                    creatingCheatAddress = match.address
                                    creatingCheatValue = match.currentValue
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun buildFooterHints(): List<Pair<InputButton, String>> = buildList {
        when (currentTab) {
            CheatsTab.CHEATS -> {
                if (contentFocusIndex == 0) {
                    add(InputButton.A to "Search")
                    if (searchQuery.isNotEmpty()) {
                        add(InputButton.X to "Clear")
                    }
                } else {
                    add(InputButton.A to "Toggle")
                    if (filteredCheats.getOrNull(contentFocusIndex - 1) != null) {
                        add(InputButton.X to "Edit")
                    }
                }
            }
            CheatsTab.DISCOVER -> {
                val showActions = !hasSnapshot || (canCompare && !showingResults)
                val inWaiting = hasSnapshot && !canCompare && !showingResults
                when {
                    !hasSnapshot -> {
                        add(InputButton.A to "Snapshot")
                    }
                    showActions && canCompare -> {
                        add(InputButton.A to "Select")
                    }
                    showingResults -> {
                        val onResult = contentFocusIndex >= 1
                        if (onResult) {
                            add(InputButton.A to "Save Cheat")
                        } else {
                            add(InputButton.A to "Filter")
                            add(InputButton.DPAD_HORIZONTAL to "Adjust")
                        }
                    }
                    inWaiting -> {
                        // No actions available, just waiting for game to run
                    }
                }
                if (hasSnapshot) {
                    add(InputButton.X to "Reset")
                }
            }
        }
        add(InputButton.B to "Back")
    }

    LaunchedEffect(Unit) {
        if (initialTab == CheatsTab.DISCOVER) {
            contentFocusIndex = getDiscoverFocusableIndices().firstOrNull() ?: 0
        }
    }

    LaunchedEffect(canCompare) {
        if (canCompare && currentTab == CheatsTab.DISCOVER) {
            showingResults = false
            contentFocusIndex = getDiscoverFocusableIndices().firstOrNull() ?: 0
        }
    }

    LaunchedEffect(currentFilteredCheats.size) {
        if (currentTab == CheatsTab.CHEATS && contentFocusIndex > currentFilteredCheats.size) {
            contentFocusIndex = currentFilteredCheats.size.coerceAtLeast(0)
        }
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (isLoading) return InputResult.HANDLED
                contentFocusIndex = getNextFocusIndex(contentFocusIndex, -1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                if (isLoading) return InputResult.HANDLED
                contentFocusIndex = getNextFocusIndex(contentFocusIndex, 1)
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                if (isLoading) return InputResult.HANDLED
                val showActions = !hasSnapshot || (canCompare && scanResults.isEmpty())
                val inResultsView = currentTab == CheatsTab.DISCOVER &&
                    hasSnapshot && scanResults.isNotEmpty() && !showActions
                if (inResultsView && contentFocusIndex == 0) {
                    val current = valueSearchText.toIntOrNull() ?: 0
                    valueSearchText = (current - 1).coerceAtLeast(0).toString()
                }
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (isLoading) return InputResult.HANDLED
                val showActions = !hasSnapshot || (canCompare && scanResults.isEmpty())
                val inResultsView = currentTab == CheatsTab.DISCOVER &&
                    hasSnapshot && scanResults.isNotEmpty() && !showActions
                if (inResultsView && contentFocusIndex == 0) {
                    val current = valueSearchText.toIntOrNull() ?: 0
                    valueSearchText = (current + 1).coerceAtMost(255).toString()
                }
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (isLoading) return InputResult.HANDLED
                when (currentTab) {
                    CheatsTab.CHEATS -> {
                        if (contentFocusIndex == 0) {
                            showSearchDialog = true
                        } else {
                            currentFilteredCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
                                onToggleCheat(cheat.id, !cheat.enabled)
                            }
                        }
                    }
                    CheatsTab.DISCOVER -> handleDiscoverAction(contentFocusIndex)
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                if (isLoading) return InputResult.HANDLED
                if (currentTab == CheatsTab.DISCOVER && showingResults) {
                    showingResults = false
                    contentFocusIndex = if (scanResults.isNotEmpty()) 2 else 0
                } else {
                    onDismiss()
                }
                return InputResult.HANDLED
            }
            override fun onSecondaryAction(): InputResult {
                if (isLoading) return InputResult.HANDLED
                when (currentTab) {
                    CheatsTab.CHEATS -> {
                        if (contentFocusIndex == 0) {
                            if (searchQuery.isNotEmpty()) {
                                searchQuery = ""
                            }
                        } else {
                            currentFilteredCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
                                editingCheat = cheat
                            }
                        }
                    }
                    CheatsTab.DISCOVER -> {
                        scanner.reset()
                        refreshScannerState()
                        showingResults = false
                        valueSearchText = ""
                        ramError = null
                        contentFocusIndex = getDiscoverFocusableIndices().firstOrNull() ?: 0
                    }
                }
                return InputResult.HANDLED
            }
            override fun onPrevSection(): InputResult {
                if (isLoading) return InputResult.HANDLED
                handleTabChange(-1)
                return InputResult.HANDLED
            }
            override fun onNextSection(): InputResult {
                if (isLoading) return InputResult.HANDLED
                handleTabChange(1)
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
            Column(modifier = Modifier.fillMaxSize().focusProperties { canFocus = false }) {
                TabHeader(
                    currentTab = currentTab,
                    onTabSelect = ::setTab,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxWidth().focusProperties { canFocus = false }) {
                    TabContent(
                        tab = currentTab,
                        filteredCheats = filteredCheats,
                        allCheats = cheats,
                        searchQuery = searchQuery,
                        onSearchClick = { showSearchDialog = true },
                        valueSearchText = valueSearchText,
                        onValueSearchChange = { valueSearchText = it },
                        hasSnapshot = hasSnapshot,
                        canCompare = canCompare,
                        candidateCount = candidateCount,
                        scanResults = scanResults,
                        knownAddresses = knownAddresses,
                        contentFocusIndex = contentFocusIndex,
                        onToggleCheat = onToggleCheat,
                        onDiscoverAction = ::handleDiscoverAction,
                        isLoading = isLoading,
                        ramError = ramError,
                        narrowError = narrowError,
                        showingResults = showingResults,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isLoading) {
                        LoadingOverlay()
                    }
                }
                FooterBar(
                    hints = buildFooterHints(),
                    onHintClick = { button ->
                        if (isLoading) return@FooterBar
                        when (button) {
                            InputButton.B -> {
                                if (currentTab == CheatsTab.DISCOVER && showingResults) {
                                    showingResults = false
                                    contentFocusIndex = if (scanResults.isNotEmpty()) 2 else 0
                                } else {
                                    onDismiss()
                                }
                            }
                            InputButton.X -> {
                                when (currentTab) {
                                    CheatsTab.CHEATS -> {
                                        if (contentFocusIndex == 0) {
                                            if (searchQuery.isNotEmpty()) {
                                                searchQuery = ""
                                            }
                                        } else {
                                            filteredCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
                                                editingCheat = cheat
                                            }
                                        }
                                    }
                                    CheatsTab.DISCOVER -> {
                                        scanner.reset()
                                        refreshScannerState()
                                        showingResults = false
                                        valueSearchText = ""
                                        ramError = null
                                        contentFocusIndex = getDiscoverFocusableIndices().firstOrNull() ?: 0
                                    }
                                }
                            }
                            InputButton.A -> {
                                when (currentTab) {
                                    CheatsTab.CHEATS -> {
                                        if (contentFocusIndex > 0) {
                                            filteredCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
                                                onToggleCheat(cheat.id, !cheat.enabled)
                                            }
                                        }
                                    }
                                    CheatsTab.DISCOVER -> handleDiscoverAction(contentFocusIndex)
                                }
                            }
                            else -> {}
                        }
                    }
                )
            }
        }
    }

    editingCheat?.let { cheat ->
        CheatEditDialog(
            cheatId = cheat.id,
            currentName = cheat.description,
            currentCode = cheat.code,
            onDismiss = { editingCheat = null },
            onSave = { name, code ->
                onUpdateCheat(cheat.id, name, code)
                editingCheat = null
            },
            onDelete = {
                val maxIndex = when (currentTab) {
                    CheatsTab.CHEATS -> filteredCheats.size - 1
                    CheatsTab.DISCOVER -> contentFocusIndex
                }
                if (contentFocusIndex > maxIndex) {
                    contentFocusIndex = maxIndex
                }
                onDeleteCheat(cheat.id)
                editingCheat = null
            }
        )
    }

    if (creatingCheatAddress != null && creatingCheatValue != null) {
        CheatCreateDialog(
            address = creatingCheatAddress!!,
            currentValue = creatingCheatValue!!,
            onDismiss = {
                creatingCheatAddress = null
                creatingCheatValue = null
            },
            onCreate = { name, value ->
                onCreateCheat(creatingCheatAddress!!, value, name)
                creatingCheatAddress = null
                creatingCheatValue = null
            }
        )
    }

    if (showSearchDialog) {
        SearchDialog(
            currentQuery = searchQuery,
            onDismiss = { showSearchDialog = false },
            onSearch = { query ->
                searchQuery = query
                showSearchDialog = false
            }
        )
    }

    return inputHandler
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                text = "Scanning memory...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TabHeader(
    currentTab: CheatsTab,
    onTabSelect: (CheatsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
            .focusProperties { canFocus = false },
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        CheatsTab.entries.forEach { tab ->
            TabIndicator(
                label = tab.label,
                isSelected = tab == currentTab,
                onClick = { onTabSelect(tab) }
            )
        }
    }
}

@Composable
private fun TabIndicator(
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

@Composable
private fun TabContent(
    tab: CheatsTab,
    filteredCheats: List<CheatDisplayItem>,
    allCheats: List<CheatDisplayItem>,
    searchQuery: String,
    onSearchClick: () -> Unit,
    valueSearchText: String,
    onValueSearchChange: (String) -> Unit,
    hasSnapshot: Boolean,
    canCompare: Boolean,
    candidateCount: Int,
    scanResults: List<MemoryMatch>,
    knownAddresses: Map<Int, String>,
    contentFocusIndex: Int,
    onToggleCheat: (Long, Boolean) -> Unit,
    onDiscoverAction: (Int) -> Unit,
    isLoading: Boolean,
    ramError: String?,
    narrowError: String?,
    showingResults: Boolean,
    modifier: Modifier = Modifier
) {
    when (tab) {
        CheatsTab.CHEATS -> AvailableTab(
            cheats = filteredCheats,
            allCheats = allCheats,
            searchQuery = searchQuery,
            focusedIndex = contentFocusIndex,
            onSearchClick = onSearchClick,
            onToggleCheat = onToggleCheat,
            modifier = modifier
        )
        CheatsTab.DISCOVER -> DiscoverTab(
            hasSnapshot = hasSnapshot,
            canCompare = canCompare,
            candidateCount = candidateCount,
            results = scanResults,
            knownAddresses = knownAddresses,
            valueSearchText = valueSearchText,
            onValueSearchChange = onValueSearchChange,
            focusedIndex = contentFocusIndex,
            onAction = onDiscoverAction,
            showingResults = showingResults,
            error = ramError,
            narrowError = narrowError,
            modifier = modifier
        )
    }
}
