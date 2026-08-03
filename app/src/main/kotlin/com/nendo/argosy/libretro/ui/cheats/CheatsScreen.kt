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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus
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

data class CheatVariantInfo(
    val region: String,
    val version: String,
    val cheatCount: Int
)

@Composable
fun CheatsScreen(
    cheats: List<CheatDisplayItem>,
    variants: List<CheatVariantInfo>,
    selectedVariant: Pair<String, String>?,
    scanner: MemoryScanner,
    initialTab: CheatsTab = CheatsTab.CHEATS,
    onToggleCheat: (Long, Boolean) -> Unit,
    onCreateCheat: (address: Int, value: Int, description: String) -> Unit,
    onUpdateCheat: (id: Long, description: String, code: String) -> Unit,
    onDeleteCheat: (Long) -> Unit,
    onSelectVariant: (region: String, version: String) -> Unit,
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
    val dialogInputHandler = remember { mutableStateOf<InputHandler?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showVariantModal by remember { mutableStateOf(false) }
    var variantFocusIndex by remember { mutableIntStateOf(0) }
    val hasMultipleVariants = variants.size > 1
    val needsVariantSelection = hasMultipleVariants && selectedVariant == null
    val currentNeedsVariantSelection by rememberUpdatedState(needsVariantSelection)
    val currentHasMultipleVariants by rememberUpdatedState(hasMultipleVariants)
    val currentVariants by rememberUpdatedState(variants)
    val currentSelectedVariant by rememberUpdatedState(selectedVariant)
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val filteredCheats = if (searchQuery.isBlank()) cheats else {
        cheats.filter { it.description.contains(searchQuery, ignoreCase = true) }
    }
    val sessionOrderKeys = remember { mutableMapOf<Long, CheatOrderKey>() }
    val orderingNow = remember { System.currentTimeMillis() }
    val cheatListItems = remember(cheats, searchQuery) {
        if (searchQuery.isBlank()) {
            buildSectionedList(cheats, orderingNow, sessionOrderKeys)
        } else {
            filteredCheats.mapIndexed { index, cheat -> CheatListItem.Cheat(cheat, index) }
        }
    }
    val displayCheats = remember(cheatListItems) {
        cheatListItems.filterIsInstance<CheatListItem.Cheat>().map { it.item }
    }
    val currentDisplayCheats by rememberUpdatedState(displayCheats)
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
                val maxIndex = currentDisplayCheats.size
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
                if (needsVariantSelection) {
                    add(InputButton.A to "Select")
                } else if (contentFocusIndex == 0) {
                    add(InputButton.A to "Search")
                    if (searchQuery.isNotEmpty()) {
                        add(InputButton.X to "Clear")
                    }
                } else {
                    add(InputButton.A to "Toggle")
                    if (displayCheats.getOrNull(contentFocusIndex - 1) != null) {
                        add(InputButton.X to "Edit")
                    }
                }
                if (hasMultipleVariants && !needsVariantSelection) {
                    add(InputButton.Y to "Version")
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
        add(InputButton.LB_RB to "Tab")
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

    LaunchedEffect(currentDisplayCheats.size) {
        if (currentTab == CheatsTab.CHEATS && contentFocusIndex > currentDisplayCheats.size) {
            contentFocusIndex = currentDisplayCheats.size.coerceAtLeast(0)
        }
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                dialogInputHandler.value?.let { return it.onUp() }
                if (isLoading) return InputResult.HANDLED
                if (showVariantModal) {
                    variantFocusIndex = (variantFocusIndex - 1).coerceAtLeast(0)
                    return InputResult.HANDLED
                }
                if (currentNeedsVariantSelection && currentTab == CheatsTab.CHEATS) {
                    contentFocusIndex = (contentFocusIndex - 1).coerceAtLeast(0)
                    return InputResult.HANDLED
                }
                contentFocusIndex = getNextFocusIndex(contentFocusIndex, -1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                dialogInputHandler.value?.let { return it.onDown() }
                if (isLoading) return InputResult.HANDLED
                if (showVariantModal) {
                    variantFocusIndex = (variantFocusIndex + 1).coerceAtMost(currentVariants.lastIndex)
                    return InputResult.HANDLED
                }
                if (currentNeedsVariantSelection && currentTab == CheatsTab.CHEATS) {
                    contentFocusIndex = (contentFocusIndex + 1).coerceAtMost(currentVariants.lastIndex)
                    return InputResult.HANDLED
                }
                contentFocusIndex = getNextFocusIndex(contentFocusIndex, 1)
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                dialogInputHandler.value?.let { return it.onLeft() }
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
                dialogInputHandler.value?.let { return it.onRight() }
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
                dialogInputHandler.value?.let { return it.onConfirm() }
                if (isLoading) return InputResult.HANDLED
                if (showVariantModal) {
                    currentVariants.getOrNull(variantFocusIndex)?.let { v ->
                        onSelectVariant(v.region, v.version)
                        showVariantModal = false
                    }
                    return InputResult.HANDLED
                }
                when (currentTab) {
                    CheatsTab.CHEATS -> {
                        if (currentNeedsVariantSelection) {
                            currentVariants.getOrNull(contentFocusIndex)?.let { v ->
                                onSelectVariant(v.region, v.version)
                            }
                        } else if (contentFocusIndex == 0) {
                            showSearchDialog = true
                        } else {
                            currentDisplayCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
                                onToggleCheat(cheat.id, !cheat.enabled)
                            }
                        }
                    }
                    CheatsTab.DISCOVER -> handleDiscoverAction(contentFocusIndex)
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                dialogInputHandler.value?.let { return it.onBack() }
                if (isLoading) return InputResult.HANDLED
                if (showVariantModal) {
                    showVariantModal = false
                    return InputResult.HANDLED
                }
                if (currentTab == CheatsTab.DISCOVER && showingResults) {
                    showingResults = false
                    contentFocusIndex = if (scanResults.isNotEmpty()) 2 else 0
                } else {
                    onDismiss()
                }
                return InputResult.HANDLED
            }
            override fun onSecondaryAction(): InputResult {
                dialogInputHandler.value?.let { return it.onSecondaryAction() }
                if (isLoading) return InputResult.HANDLED
                when (currentTab) {
                    CheatsTab.CHEATS -> {
                        if (contentFocusIndex == 0) {
                            if (searchQuery.isNotEmpty()) {
                                searchQuery = ""
                            }
                        } else {
                            currentDisplayCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
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
            override fun onContextMenu(): InputResult {
                dialogInputHandler.value?.let { return it.onContextMenu() }
                if (isLoading) return InputResult.HANDLED
                if (currentTab == CheatsTab.CHEATS && currentHasMultipleVariants && !currentNeedsVariantSelection) {
                    variantFocusIndex = currentVariants.indexOfFirst {
                        it.region == currentSelectedVariant?.first && it.version == currentSelectedVariant?.second
                    }.coerceAtLeast(0)
                    showVariantModal = true
                    return InputResult.HANDLED
                }
                return InputResult.UNHANDLED
            }
            override fun onPrevSection(): InputResult {
                dialogInputHandler.value?.let { return it.onPrevSection() }
                if (isLoading) return InputResult.HANDLED
                handleTabChange(-1)
                return InputResult.HANDLED
            }
            override fun onNextSection(): InputResult {
                dialogInputHandler.value?.let { return it.onNextSection() }
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
            .padding(bottom = gripReserveBottomInset())
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
                        listItems = cheatListItems,
                        filteredCheats = filteredCheats,
                        allCheats = cheats,
                        variants = variants,
                        selectedVariant = selectedVariant,
                        needsVariantSelection = needsVariantSelection,
                        searchQuery = searchQuery,
                        onSearchClick = { showSearchDialog = true },
                        onSelectVariant = onSelectVariant,
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
                                            displayCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
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
                                        if (needsVariantSelection) {
                                            variants.getOrNull(contentFocusIndex)?.let { v ->
                                                onSelectVariant(v.region, v.version)
                                            }
                                        } else if (contentFocusIndex > 0) {
                                            displayCheats.getOrNull(contentFocusIndex - 1)?.let { cheat ->
                                                onToggleCheat(cheat.id, !cheat.enabled)
                                            }
                                        }
                                    }
                                    CheatsTab.DISCOVER -> handleDiscoverAction(contentFocusIndex)
                                }
                            }
                            InputButton.Y -> {
                                if (currentTab == CheatsTab.CHEATS && hasMultipleVariants && !needsVariantSelection) {
                                    variantFocusIndex = variants.indexOfFirst {
                                        it.region == selectedVariant?.first && it.version == selectedVariant?.second
                                    }.coerceAtLeast(0)
                                    showVariantModal = true
                                }
                            }
                            else -> {}
                        }
                    }
                )
            }
        }
    }

    dialogInputHandler.value = when {
        editingCheat != null -> {
            val cheat = editingCheat!!
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
                        CheatsTab.CHEATS -> displayCheats.size - 1
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
        creatingCheatAddress != null && creatingCheatValue != null -> {
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
        showSearchDialog -> {
            SearchDialog(
                currentQuery = searchQuery,
                onDismiss = { showSearchDialog = false },
                onSearch = { query ->
                    searchQuery = query
                    showSearchDialog = false
                }
            )
        }
        else -> null
    }

    if (showVariantModal) {
        VariantSelectorModal(
            variants = variants,
            selectedRegion = selectedVariant?.first,
            selectedVersion = selectedVariant?.second,
            focusedIndex = variantFocusIndex,
            onSelect = { v ->
                onSelectVariant(v.region, v.version)
                showVariantModal = false
            },
            onDismiss = { showVariantModal = false }
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
    listItems: List<CheatListItem>,
    filteredCheats: List<CheatDisplayItem>,
    allCheats: List<CheatDisplayItem>,
    variants: List<CheatVariantInfo>,
    selectedVariant: Pair<String, String>?,
    needsVariantSelection: Boolean,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onSelectVariant: (String, String) -> Unit,
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
        CheatsTab.CHEATS -> {
            if (needsVariantSelection) {
                VariantPicker(
                    variants = variants,
                    focusedIndex = contentFocusIndex,
                    onSelect = { v -> onSelectVariant(v.region, v.version) },
                    modifier = modifier
                )
            } else {
                AvailableTab(
                    listItems = listItems,
                    cheats = filteredCheats,
                    allCheats = allCheats,
                    searchQuery = searchQuery,
                    focusedIndex = contentFocusIndex,
                    onSearchClick = onSearchClick,
                    onToggleCheat = onToggleCheat,
                    modifier = modifier
                )
            }
        }
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

@Composable
private fun VariantPicker(
    variants: List<CheatVariantInfo>,
    focusedIndex: Int,
    onSelect: (CheatVariantInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Dimens.spacingSm)
    ) {
        Text(
            text = "Multiple versions available",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Dimens.spacingXs)
        )
        Text(
            text = "Select the version that matches your ROM",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            modifier = Modifier.focusProperties { canFocus = false }
        ) {
            itemsIndexed(variants, key = { _, v -> "${v.region}|${v.version}" }) { index, variant ->
                VariantRow(
                    variant = variant,
                    isFocused = index == focusedIndex,
                    isSelected = false,
                    onSelect = { onSelect(variant) }
                )
            }
        }
    }
}

@Composable
private fun VariantSelectorModal(
    variants: List<CheatVariantInfo>,
    selectedRegion: String?,
    selectedVersion: String?,
    focusedIndex: Int,
    onSelect: (CheatVariantInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(Dimens.spacingLg)
                .clickableNoFocus { },
            shape = RoundedCornerShape(Dimens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(Dimens.spacingLg)) {
                Text(
                    text = "Select Version",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Dimens.spacingMd)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .focusProperties { canFocus = false }
                ) {
                    itemsIndexed(variants, key = { _, v -> "${v.region}|${v.version}" }) { index, variant ->
                        val isSelected = variant.region == selectedRegion && variant.version == selectedVersion
                        VariantRow(
                            variant = variant,
                            isFocused = index == focusedIndex,
                            isSelected = isSelected,
                            onSelect = { onSelect(variant) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantRow(
    variant: CheatVariantInfo,
    isFocused: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onSelect)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variant.region.ifBlank { "Unknown Region" },
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (variant.version.isNotBlank()) {
                Text(
                    text = variant.version,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${variant.cheatCount} cheats",
                style = MaterialTheme.typography.labelMedium,
                color = secondaryColor
            )
            if (isSelected) {
                Text(
                    text = "*",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
