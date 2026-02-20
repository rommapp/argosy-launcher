package com.nendo.argosy.hardware

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.dualscreen.ControlRoleContent
import com.nendo.argosy.ui.dualscreen.ShowcaseViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailLowerScreen
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperScreen
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailUpperState
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.GameDetailOption
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcase
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualFilterCategory
import com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeUpperScreen
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.flow.StateFlow

enum class CompanionScreen { HOME, GAME_DETAIL }

@Composable
fun SecondaryHomeContent(
    isInitialized: Boolean,
    isArgosyForeground: Boolean,
    isGameActive: Boolean,
    isWizardActive: Boolean = false,
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

    val showLibrary = isInitialized && isArgosyForeground && !isGameActive && !isWizardActive
    val showCompanion = isInitialized && !showLibrary && !isWizardActive
    val showSplash = !isInitialized || isWizardActive

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
                ControlRoleContent(
                    currentScreen = currentScreen,
                    dualHomeViewModel = dualHomeViewModel,
                    dualGameDetailViewModel = dualGameDetailViewModel,
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
                    onDetailBack = onDetailBack,
                    onOptionAction = onOptionAction,
                    onScreenshotViewed = onScreenshotViewed,
                    onDimTapped = onDimTapped
                )
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
    isWizardActive: Boolean = false,
    showcaseViewModel: ShowcaseViewModel,
    viewModel: SecondaryHomeViewModel,
    homeApps: List<String>,
    showcaseState: StateFlow<DualHomeShowcaseState>,
    showcaseViewMode: StateFlow<String>,
    collectionShowcaseState: StateFlow<DualCollectionShowcaseState>,
    gameDetailState: StateFlow<DualGameDetailUpperState?>,
    onAppClick: (String) -> Unit
) {
    BackHandler(enabled = true) { }

    val showShowcase = isInitialized && !isGameActive && !isWizardActive
    val showSplash = !isInitialized || isWizardActive

    val showcase by showcaseState.collectAsState()
    val viewMode by showcaseViewMode.collectAsState()
    val collectionState by collectionShowcaseState.collectAsState()
    val detailState by gameDetailState.collectAsState()

    val drawerState by viewModel.uiState.collectAsState()
    val contentBlur by animateDpAsState(
        targetValue = if (drawerState.isDrawerOpen) Motion.blurRadiusDrawer else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "showcaseBlur"
    )

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
            Box(modifier = Modifier.blur(contentBlur)) {
                if (detailState != null) {
                    DualGameDetailUpperScreen(
                        state = detailState!!,
                        onModalRatingSelect = showcaseViewModel::onModalRatingSelect,
                        onModalStatusSelect = showcaseViewModel::onModalStatusSelect,
                        onModalEmulatorSelect = showcaseViewModel::onModalEmulatorSelect,
                        onModalCollectionToggle = showcaseViewModel::onModalCollectionToggle,
                        onModalCollectionShowCreate = showcaseViewModel::onModalCollectionShowCreate,
                        onModalCollectionCreate = showcaseViewModel::onModalCollectionCreate,
                        onModalCollectionCreateDismiss = showcaseViewModel::onModalCollectionCreateDismiss,
                        onSaveNameTextChange = showcaseViewModel::onSaveNameTextChange,
                        onSaveNameConfirm = showcaseViewModel::onSaveNameConfirm,
                        onModalDismiss = showcaseViewModel::onModalDismiss,
                        footerHints = {
                            FooterBar(
                                hints = listOf(
                                    InputButton.LB_RB to "Tab",
                                    InputButton.A to "Select",
                                    InputButton.B to "Back"
                                )
                            )
                        }
                    )
                } else if (viewMode == "COLLECTIONS") {
                    DualCollectionShowcase(
                        state = collectionState,
                        footerHints = {
                            FooterBar(
                                hints = listOf(
                                    InputButton.DPAD to "Navigate",
                                    InputButton.A to "Open",
                                    InputButton.B to "Back"
                                )
                            )
                        }
                    )
                } else {
                    val actionLabel = if (showcase.isDownloaded) "Play" else "Download"
                    DualHomeUpperScreen(
                        state = showcase,
                        footerHints = {
                            FooterBar(
                                hints = when (viewMode) {
                                    "COLLECTION_GAMES" -> listOf(
                                        InputButton.DPAD to "Navigate",
                                        InputButton.A to actionLabel,
                                        InputButton.X to "Details",
                                        InputButton.B to "Back"
                                    )
                                    "LIBRARY_GRID" -> listOf(
                                        InputButton.LB_RB to "Platform",
                                        InputButton.LT_RT to "Letter",
                                        InputButton.A to actionLabel,
                                        InputButton.X to "Details",
                                        InputButton.Y to "Filters",
                                        InputButton.B to "Back"
                                    )
                                    else -> listOf(
                                        InputButton.LB_RB to "Platform",
                                        InputButton.A to actionLabel,
                                        InputButton.X to "Details",
                                        InputButton.Y to if (showcase.isFavorite) "Unfavorite" else "Favorite",
                                        InputButton.DPAD_UP to "Collections",
                                        InputButton.SELECT to "Library"
                                    )
                                }
                            )
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isInitialized && !showShowcase,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashContent()
        }

        AnimatedVisibility(
            visible = drawerState.isDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
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
