package com.nendo.argosy.hardware

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailLowerScreen
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailTab
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperScreen
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.GameDetailOption
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcase
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualFilterCategory
import com.nendo.argosy.ui.dualscreen.home.DualHomeLowerContent
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeUpperScreen
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import kotlinx.coroutines.flow.StateFlow

enum class CompanionScreen { HOME, GAME_DETAIL }

@Composable
fun SecondaryHomeContent(
    isInitialized: Boolean,
    isArgosyForeground: Boolean,
    isGameActive: Boolean,
    companionInGameState: CompanionInGameState,
    companionSessionTimer: CompanionSessionTimer?,
    homeApps: List<String>,
    viewModel: SecondaryHomeViewModel,
    dualHomeViewModel: DualHomeViewModel,
    useDualScreenMode: Boolean,
    currentScreen: CompanionScreen,
    dualGameDetailViewModel: DualGameDetailViewModel?,
    onAppClick: (String) -> Unit,
    onGameSelected: (Long) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    onCollectionTapped: (Int) -> Unit,
    onGridGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    onFilterOptionTapped: (Int) -> Unit,
    onFilterCategoryTapped: (DualFilterCategory) -> Unit,
    onDetailBack: () -> Unit,
    onOptionAction: (DualGameDetailViewModel, GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onDimTapped: () -> Unit = {},
    onTabChanged: (CompanionPanel) -> Unit = {}
) {
    BackHandler(enabled = true) { }

    val showLibrary = isInitialized && isArgosyForeground && !isGameActive
    val showCompanion = isInitialized && !showLibrary
    val showSplash = !isInitialized

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashContent()
        }

        AnimatedVisibility(
            visible = showLibrary,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (useDualScreenMode) {
                when (currentScreen) {
                    CompanionScreen.HOME -> {
                        DualHomeLowerContent(
                            viewModel = dualHomeViewModel,
                            homeApps = homeApps,
                            onGameSelected = onGameSelected,
                            onAppClick = onAppClick,
                            onCollectionsClick = onCollectionsClick,
                            onLibraryToggle = onLibraryToggle,
                            onViewAllClick = onViewAllClick,
                            onCollectionTapped = onCollectionTapped,
                            onGridGameTapped = onGridGameTapped,
                            onLetterClick = onLetterClick,
                            onFilterOptionTapped = onFilterOptionTapped,
                            onFilterCategoryTapped = onFilterCategoryTapped,
                            onOpenDrawer = { viewModel.openDrawer() },
                            onDimTapped = onDimTapped
                        )
                    }
                    CompanionScreen.GAME_DETAIL -> {
                        if (dualGameDetailViewModel != null) {
                            val detailState by dualGameDetailViewModel.uiState.collectAsState()
                            key(detailState.gameId) {
                                DualGameDetailContent(
                                    viewModel = dualGameDetailViewModel,
                                    onOptionAction = { option ->
                                        onOptionAction(dualGameDetailViewModel, option)
                                    },
                                    onScreenshotViewed = onScreenshotViewed,
                                    onBack = onDetailBack,
                                    onDimTapped = onDimTapped
                                )
                            }
                        }
                    }
                }
            } else {
                SecondaryHomeScreen(viewModel = viewModel)
            }
        }

        AnimatedVisibility(
            visible = showCompanion,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CompanionContent(
                state = companionInGameState,
                sessionTimer = companionSessionTimer,
                homeApps = homeApps,
                onAppClick = onAppClick,
                onTabChanged = onTabChanged,
                onOpenDrawer = { viewModel.openDrawer() }
            )
        }

        val drawerState by viewModel.uiState.collectAsState()
        AnimatedVisibility(
            visible = drawerState.isDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.closeDrawer() }
                    )
            )
        }

        AnimatedVisibility(
            visible = drawerState.isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            com.nendo.argosy.ui.screens.secondaryhome.AllAppsDrawerOverlay(
                apps = drawerState.allApps,
                focusedIndex = drawerState.drawerFocusedIndex,
                screenWidthDp = drawerState.screenWidthDp,
                onPinToggle = { viewModel.togglePinFromDrawer(it) },
                onAppClick = { pkg ->
                    viewModel.closeDrawer()
                    onAppClick(pkg)
                },
                onClose = { viewModel.closeDrawer() }
            )
        }
    }
}

@Composable
fun ShowcaseRoleContent(
    isInitialized: Boolean,
    isArgosyForeground: Boolean,
    isGameActive: Boolean,
    showcaseState: StateFlow<DualHomeShowcaseState>,
    showcaseViewMode: StateFlow<String>,
    collectionShowcaseState: StateFlow<DualCollectionShowcaseState>,
    gameDetailState: StateFlow<DualGameDetailUpperState?>
) {
    BackHandler(enabled = true) { }

    val showShowcase = isInitialized && isArgosyForeground && !isGameActive
    val showSplash = !isInitialized

    val showcase by showcaseState.collectAsState()
    val viewMode by showcaseViewMode.collectAsState()
    val collectionState by collectionShowcaseState.collectAsState()
    val detailState by gameDetailState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashContent()
        }

        AnimatedVisibility(
            visible = showShowcase,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val emptyFooter: @Composable () -> Unit = {}

            if (detailState != null) {
                DualGameDetailUpperScreen(
                    state = detailState!!,
                    footerHints = emptyFooter
                )
            } else if (viewMode == "COLLECTIONS") {
                DualCollectionShowcase(
                    state = collectionState,
                    footerHints = emptyFooter
                )
            } else {
                DualHomeUpperScreen(
                    state = showcase,
                    footerHints = emptyFooter
                )
            }
        }

        AnimatedVisibility(
            visible = isInitialized && !showShowcase,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashContent()
        }
    }
}

@Composable
fun DualGameDetailContent(
    viewModel: DualGameDetailViewModel,
    onOptionAction: (GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onBack: () -> Unit,
    onDimTapped: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val slots by viewModel.saveSlots.collectAsState()
    val history by viewModel.saveHistory.collectAsState()
    val selectedSlotIndex by viewModel.selectedSlotIndex.collectAsState()
    val selectedHistoryIndex by viewModel.selectedHistoryIndex.collectAsState()
    val visibleOptions by viewModel.visibleOptions.collectAsState()
    val selectedScreenshotIndex by viewModel.selectedScreenshotIndex.collectAsState()
    val selectedOptionIndex by viewModel.selectedOptionIndex.collectAsState()
    val activeModal by viewModel.activeModal.collectAsState()
    val savesLoading by viewModel.savesLoading.collectAsState()
    val savesApplying by viewModel.savesApplying.collectAsState()

    DualGameDetailLowerScreen(
        state = state,
        slots = slots,
        history = history,
        saveFocusColumn = state.saveFocusColumn,
        selectedSlotIndex = selectedSlotIndex,
        selectedHistoryIndex = selectedHistoryIndex,
        visibleOptions = visibleOptions,
        selectedScreenshotIndex = selectedScreenshotIndex,
        selectedOptionIndex = selectedOptionIndex,
        savesLoading = savesLoading,
        savesApplying = savesApplying,
        isDimmed = activeModal != ActiveModal.NONE,
        onDimTapped = onDimTapped,
        onTabChanged = { viewModel.setTab(it) },
        onSlotTapped = { index ->
            viewModel.moveSlotSelection(index - viewModel.selectedSlotIndex.value)
        },
        onHistoryTapped = { index ->
            viewModel.moveHistorySelection(index - viewModel.selectedHistoryIndex.value)
        },
        onScreenshotSelected = { index ->
            viewModel.setScreenshotIndex(index)
        },
        onScreenshotView = { index ->
            onScreenshotViewed(index)
        },
        onOptionSelected = { option -> onOptionAction(option) },
        onBack = onBack
    )
}

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_helm),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            alpha = 0.6f
        )
    }
}
