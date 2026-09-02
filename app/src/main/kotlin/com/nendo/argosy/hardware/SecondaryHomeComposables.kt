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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.ui.dualscreen.ControlRoleContent
import com.nendo.argosy.ui.util.clickableNoFocus
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
import com.nendo.argosy.ui.dualscreen.media.DualMediaLowerScreen
import com.nendo.argosy.ui.dualscreen.media.DualMediaViewModel
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import kotlinx.coroutines.flow.StateFlow

enum class CompanionScreen { HOME, GAME_DETAIL }

/**
 * Whether the media panel is the surface the companion is showing right now. Both role renderers
 * and the activity's key-yield gate derive from this one predicate, so the screen that is drawn
 * and the screen that receives controller input cannot disagree.
 *
 * A live session does not exclude it. The panel takes the screen from the in-game dashboard the
 * same way it takes it from home, which is what makes a film reachable without leaving the game.
 */
fun mediaPanelIsSurface(
    isInitialized: Boolean,
    isMediaPanelVisible: Boolean,
    isWizardActive: Boolean,
    hasMediaViewModel: Boolean
): Boolean = isInitialized && isMediaPanelVisible && !isWizardActive && hasMediaViewModel

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
    currentScreen: CompanionScreen,
    dualGameDetailViewModel: DualGameDetailViewModel?,
    onAppClick: (String) -> Unit,
    onGameSelected: (Long) -> Unit,
    onViewAllClick: () -> Unit,
    onCollectionTapped: (Int) -> Unit,
    onGridGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    onFilterOptionTapped: (Int) -> Unit,
    onFilterCategoryTapped: (DualFilterCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onDetailBack: () -> Unit,
    onOptionAction: (DualGameDetailViewModel, GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onDimTapped: () -> Unit = {},
    onCustomGridActivate: () -> Unit = {},
    companionAchievements: List<AchievementUi> = emptyList(),
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    dualMediaViewModel: DualMediaViewModel? = null,
    isMediaPanelVisible: Boolean = false,
    mediaToggle: CompanionMediaToggle? = null,
    onMediaToggle: () -> Unit = {},
    onMediaRowTapped: (Int) -> Unit = {},
    onMediaRowConfirmed: (Int) -> Unit = {},
    onMediaSeasonSelected: (Int) -> Unit = {},
    onMediaEpisodeTapped: (String) -> Unit = {}
) {
    BackHandler(enabled = true) { }

    val showMedia = mediaPanelIsSurface(
        isInitialized = isInitialized,
        isMediaPanelVisible = isMediaPanelVisible,
        isWizardActive = isWizardActive,
        hasMediaViewModel = dualMediaViewModel != null
    )
    val showLibrary = isInitialized && isArgosyForeground && !isGameActive && !isWizardActive &&
        !showMedia
    val showCompanion = isInitialized && !showLibrary && !showMedia && !isWizardActive
    val showSplash = !isInitialized || isWizardActive

    val dualHomeState by dualHomeViewModel.uiState.collectAsState()
    val drawerState by viewModel.uiState.collectAsState()

    /**
     * The whole companion display refuses Compose focus, not just the home content inside it.
     *
     * A tap on anything clickable moves focus to that node, and a focused node eats the d-pad
     * before the dispatcher ever sees it - which is why closing the app drawer used to leave the
     * carousel taking several presses to wake up, one per focusable the traversal walked through.
     * Guarding a single screen only moved the leak to whatever renders beside it.
     */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (dualHomeState.isTextEntryActive) Modifier
                else Modifier.focusProperties { canFocus = false }
            )
            .surfaceBackdrop(BackdropRole.WALLPAPER)
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
            ControlRoleContent(
                currentScreen = currentScreen,
                dualHomeViewModel = dualHomeViewModel,
                dualGameDetailViewModel = dualGameDetailViewModel,
                homeApps = homeApps,
                onGameSelected = onGameSelected,
                onAppClick = onAppClick,
                onViewAllClick = onViewAllClick,
                onCollectionTapped = onCollectionTapped,
                onGridGameTapped = onGridGameTapped,
                onLetterClick = onLetterClick,
                onFilterOptionTapped = onFilterOptionTapped,
                onFilterCategoryTapped = onFilterCategoryTapped,
                onSearchQueryChange = onSearchQueryChange,
                onOpenDrawer = { viewModel.openDrawer() },
                onDetailBack = onDetailBack,
                onOptionAction = onOptionAction,
                onScreenshotViewed = onScreenshotViewed,
                onDimTapped = onDimTapped,
                onCustomGridActivate = onCustomGridActivate,
                mediaToggle = mediaToggle,
                onMediaToggle = onMediaToggle,
                dualMediaViewModel = dualMediaViewModel
            )
        }

        AnimatedVisibility(
            visible = showMedia,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (dualMediaViewModel != null) {
                val mediaState by dualMediaViewModel.uiState.collectAsState()
                val playerLocked = com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.mediaPlayerControlsLocked?.collectAsState()?.value == true
                Column(modifier = Modifier.fillMaxSize()) {
                    DualMediaLowerScreen(
                        state = mediaState,
                        isInteractive = true,
                        onRowTapped = onMediaRowTapped,
                        onRowConfirmed = onMediaRowConfirmed,
                        modifier = Modifier.weight(1f),
                        onSeasonSelected = onMediaSeasonSelected,
                        onEpisodeTapped = onMediaEpisodeTapped,
                        onBackTapped = {
                            com.nendo.argosy.DualScreenManagerHolder.instance
                                ?.setCompanionMediaVisible(false)
                        },
                        playerLocked = playerLocked
                    )
                    val showAppBar = com.nendo.argosy.DualScreenManagerHolder.instance
                        ?.isExternalDisplay != true
                    if (showAppBar) {
                        CompanionAppBar(
                            apps = homeApps,
                            onAppClick = onAppClick,
                            focusedIndex = -2,
                            onOpenDrawer = { viewModel.openDrawer() },
                            mediaToggle = mediaToggle,
                            onMediaToggle = onMediaToggle
                        )
                    }
                }
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
                onTabChanged = { viewModel.setCompanionPanel(it) },
                currentPanel = drawerState.companionPanel,
                achievements = companionAchievements,
                achievementFocusIndex = drawerState.companionAchievementFocusIndex,
                onAchievementTapped = { viewModel.setCompanionAchievementFocus(it) },
                onOpenDrawer = { viewModel.openDrawer() },
                onQuickSave = onQuickSave,
                onQuickLoad = onQuickLoad,
                onScreenshot = onScreenshot,
                mediaToggle = mediaToggle,
                onMediaToggle = onMediaToggle
            )
        }

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
                    .clickableNoFocus { viewModel.closeDrawer() }
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
    syncConflictState: StateFlow<com.nendo.argosy.ui.screens.common.SyncOverlayState?>,
    syncConflictFocusIndex: StateFlow<Int>,
    onAppClick: (String) -> Unit,
    dualMediaViewModel: DualMediaViewModel? = null,
    isMediaPanelVisible: Boolean = false,
    onMediaSeasonSelected: (Int) -> Unit = {},
    onMediaEpisodeTapped: (String) -> Unit = {}
) {
    BackHandler(enabled = true) { }

    val showMedia = mediaPanelIsSurface(
        isInitialized = isInitialized,
        isMediaPanelVisible = isMediaPanelVisible,
        isWizardActive = isWizardActive,
        hasMediaViewModel = dualMediaViewModel != null
    )
    val showShowcase = isInitialized && !isGameActive && !isWizardActive && !showMedia
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
            .focusProperties { canFocus = false }
            .surfaceBackdrop(BackdropRole.WALLPAPER)
    ) {
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashContent()
        }

        AnimatedVisibility(
            visible = showMedia,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (dualMediaViewModel != null) {
                val mediaState by dualMediaViewModel.uiState.collectAsState()
                DualMediaLowerScreen(
                    state = mediaState,
                    isInteractive = false,
                    onRowTapped = {},
                    onRowConfirmed = {},
                    modifier = Modifier.blur(contentBlur),
                    onSeasonSelected = onMediaSeasonSelected,
                    onEpisodeTapped = onMediaEpisodeTapped
                )
            }
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
                        onModalCoreSelect = showcaseViewModel::onModalCoreSelect,
                        onModalSavePathSelect = showcaseViewModel::onModalSavePathSelect,
                        onModalDisplayTargetSelect = showcaseViewModel::onModalDisplayTargetSelect,
                        onModalMemoryCardSelect = showcaseViewModel::onModalMemoryCardSelect,
                        onModalVariantSelect = showcaseViewModel::onModalVariantSelect,
                        onModalCollectionToggle = showcaseViewModel::onModalCollectionToggle,
                        onModalCollectionShowCreate = showcaseViewModel::onModalCollectionShowCreate,
                        onModalCollectionCreate = showcaseViewModel::onModalCollectionCreate,
                        onModalCollectionCreateDismiss = showcaseViewModel::onModalCollectionCreateDismiss,
                        onSaveNameTextChange = showcaseViewModel::onSaveNameTextChange,
                        onSaveNameConfirm = showcaseViewModel::onSaveNameConfirm,
                        onDiscSelect = showcaseViewModel::onDiscSelect,
                        onModalSteamInstallSelect = showcaseViewModel::onModalSteamInstallSelect,
                        onModalDismiss = showcaseViewModel::onModalDismiss,
                        onCoverSelect = showcaseViewModel::onCoverSelect,
                        onCoverQueryChange = showcaseViewModel::onCoverQueryChange,
                        onCoverSearch = showcaseViewModel::onCoverSearch,
                        onReviewSectionFocus = showcaseViewModel::onReviewSectionFocus,
                        onReviewVerdictSelect = showcaseViewModel::onReviewVerdictSelect,
                        onReviewVisibilitySelect = showcaseViewModel::onReviewVisibilitySelect,
                        onReviewBodyChange = showcaseViewModel::onReviewBodyChange,
                        onReviewConfirm = showcaseViewModel::onReviewConfirm,
                        onReviewSubmit = showcaseViewModel::onReviewSubmit,
                        onReviewDeletePrompt = showcaseViewModel::onReviewDeletePrompt,
                        onReviewDeleteConfirm = showcaseViewModel::onReviewDeleteConfirm,
                        onReviewDiscard = showcaseViewModel::onReviewDiscard,
                        onReviewConfirmDismiss = showcaseViewModel::onReviewConfirmDismiss,
                        onReviewBack = showcaseViewModel::onReviewBack,
                        footerHints = {
                            FooterBar(
                                hints = listOf(
                                    InputButton.LB_RB to
                                        stringResource(R.string.dual_showcase_detail_footer_tab),
                                    InputButton.A to
                                        stringResource(R.string.dual_showcase_detail_footer_select),
                                    InputButton.B to
                                        stringResource(R.string.dual_showcase_detail_footer_back)
                                )
                            )
                        }
                    )
                } else if (viewMode == "COLLECTIONS" || collectionState.focused) {
                    DualCollectionShowcase(
                        state = collectionState,
                        footerHints = {
                            FooterBar(
                                hints = listOf(
                                    InputButton.DPAD to stringResource(
                                        R.string.dual_showcase_collections_footer_navigate
                                    ),
                                    InputButton.A to stringResource(
                                        R.string.dual_showcase_collections_footer_open
                                    ),
                                    InputButton.B to stringResource(
                                        R.string.dual_showcase_collections_footer_back
                                    )
                                )
                            )
                        }
                    )
                } else {
                    DualHomeUpperScreen(
                        state = showcase,
                        footerHints = {
                            FooterBar(
                                hints = com.nendo.argosy.ui.dualscreen.companionHomeHints(
                                    viewMode = viewMode,
                                    isDownloaded = showcase.isDownloaded,
                                    isFavorite = showcase.isFavorite
                                )
                            )
                        }
                    )
                }
            }

        }

        val dualSyncOverlay by syncConflictState.collectAsState()
        val dualSyncFocusIndex by syncConflictFocusIndex.collectAsState()
        dualSyncOverlay?.let { conflictState ->
            val isHardcore = conflictState.syncProgress is com.nendo.argosy.domain.model.SyncProgress.HardcoreConflict
            com.nendo.argosy.ui.components.SyncOverlay(
                syncProgress = conflictState.syncProgress,
                gameTitle = conflictState.gameTitle,
                onKeepHardcore = conflictState.onKeepHardcore,
                onDowngradeToCasual = conflictState.onDowngradeToCasual,
                onKeepLocal = conflictState.onKeepLocal,
                onKeepLocalModified = conflictState.onKeepLocalModified,
                onRestoreSelected = conflictState.onRestoreSelected,
                hardcoreConflictFocusIndex = if (isHardcore) dualSyncFocusIndex else 0,
                localModifiedFocusIndex = if (!isHardcore) dualSyncFocusIndex else 0
            )
        }

        AnimatedVisibility(
            visible = isInitialized && !showShowcase && !showMedia,
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
                    .clickableNoFocus { viewModel.closeDrawer() }
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
    val savesSyncing by viewModel.savesSyncing.collectAsState()
    val stateEntries by viewModel.stateEntries.collectAsState()
    val selectedStateIndex by viewModel.selectedStateIndex.collectAsState()

    DualGameDetailLowerScreen(
        state = state,
        slots = slots,
        history = history,
        saveFocusColumn = state.saveFocusColumn,
        selectedSlotIndex = selectedSlotIndex,
        selectedHistoryIndex = selectedHistoryIndex,
        stateEntries = stateEntries,
        selectedStateIndex = selectedStateIndex,
        visibleOptions = visibleOptions,
        selectedScreenshotIndex = selectedScreenshotIndex,
        selectedOptionIndex = selectedOptionIndex,
        savesLoading = savesLoading,
        savesApplying = savesApplying,
        savesSyncing = savesSyncing,
        isDimmed = activeModal != ActiveModal.NONE,
        onDimTapped = onDimTapped,
        onTabChanged = { viewModel.setTab(it) },
        onSlotTapped = { index ->
            viewModel.moveSlotSelection(index - viewModel.selectedSlotIndex.value)
        },
        onHistoryTapped = { index ->
            viewModel.moveHistorySelection(index - viewModel.selectedHistoryIndex.value)
        },
        onStateTapped = { index -> viewModel.tapStateEntry(index) },
        onStateMenuSelect = { index ->
            viewModel.setStateMenuFocus(index)
            viewModel.confirmStateOverlay()
        },
        onStateMenuDismiss = { viewModel.dismissStateOverlay() },
        onStatePromptSelect = { index ->
            viewModel.setStatePromptFocus(index)
            viewModel.confirmStateOverlay()
        },
        onStatePromptDismiss = { viewModel.dismissStateOverlay() },
        stateMenuEntries = viewModel.stateMenuActions(),
        onScreenshotSelected = { index ->
            viewModel.setScreenshotIndex(index)
        },
        onScreenshotView = { index ->
            onScreenshotViewed(index)
        },
        onReviewTapped = { index -> viewModel.tapReviewEntry(index) },
        onOptionSelected = { option -> onOptionAction(option) },
        onBack = onBack
    )
}

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .surfaceBackdrop(BackdropRole.WALLPAPER),
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
