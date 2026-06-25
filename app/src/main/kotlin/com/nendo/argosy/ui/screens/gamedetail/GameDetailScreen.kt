package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.input.HardcoreConflictInputHandler
import com.nendo.argosy.ui.input.LocalModifiedInputHandler
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementListOverlay
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementsSection
import com.nendo.argosy.ui.screens.gamedetail.components.ExpandedHeader
import com.nendo.argosy.ui.screens.gamedetail.components.StickyCollapsedHeader
import com.nendo.argosy.ui.screens.gamedetail.components.DescriptionSection
import com.nendo.argosy.ui.screens.gamedetail.components.GameDetailMenu
import com.nendo.argosy.ui.screens.gamedetail.components.GameDetailMenuState
import com.nendo.argosy.ui.screens.gamedetail.components.GameDetailSkeleton
import com.nendo.argosy.ui.screens.gamedetail.components.GameHeader
import com.nendo.argosy.ui.screens.gamedetail.components.MenuItem
import com.nendo.argosy.ui.screens.gamedetail.components.MenuLayoutState
import com.nendo.argosy.ui.screens.gamedetail.components.menuLayout
import com.nendo.argosy.ui.screens.gamedetail.components.ScreenshotViewerOverlay
import com.nendo.argosy.ui.screens.gamedetail.components.ScreenshotsSection
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.MemcardPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.CorePickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.EmulatorPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.ExtractionFailedModal
import com.nendo.argosy.ui.screens.gamedetail.modals.MissingDiscModal
import com.nendo.argosy.ui.screens.gamedetail.modals.StatusPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.SteamLauncherPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.MoreOptionsModal
import com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionsModal
import com.nendo.argosy.ui.screens.gamedetail.modals.RatingsStatusModal
import com.nendo.argosy.ui.screens.gamedetail.modals.PermissionRequiredModal
import com.nendo.argosy.ui.screens.gamedetail.modals.RatingPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.UpdatesPickerModal
import com.nendo.argosy.ui.common.savechannel.SaveChannelModal
import com.nendo.argosy.ui.components.AddToCollectionModal
import com.nendo.argosy.ui.components.CollectionItem
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.ArgosyViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun GameDetailScreen(
    gameId: Long,
    onBack: () -> Unit,
    onNavigateToPlatformSettings: (platformId: Long) -> Unit = {},
    viewModel: GameDetailViewModel = hiltViewModel(),
    argosyViewModel: ArgosyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val requestSafGrant by viewModel.requestSafGrant.collectAsState()
    val context = LocalContext.current

    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        viewModel.onSafGrantResult(uri)
    }

    LaunchedEffect(requestSafGrant) {
        if (requestSafGrant) {
            // Request access to storage ROOT, not Android/data
            // The manage=true parameter will extend this to Android/data
            val rootUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:"
            )
            safPickerLauncher.launch(rootUri)
        }
    }

    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    val pendingLaunch by argosyViewModel.pendingLaunch.collectAsState()
    LaunchedEffect(pendingLaunch, uiState.game?.id) {
        val pending = pendingLaunch ?: return@LaunchedEffect
        if (pending.gameId == gameId && uiState.game?.id == gameId) {
            argosyViewModel.consumePendingLaunch()
            viewModel.playGame(discId = pending.discId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchEvents.collectLatest { event ->
            when (event) {
                is LaunchEvent.LaunchIntent -> {
                    try {
                        if (!event.intent.getBooleanExtra("argosy.already_launched", false)) {
                            context.startActivity(event.intent, event.options)
                        }
                    } catch (e: Exception) {
                        viewModel.showLaunchError("Failed to launch: ${e.message}")
                    }
                }
                is LaunchEvent.NavigateBack -> onBack()
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val screenshotListState = rememberLazyListState()
    val achievementListState = rememberLazyListState()

    var descriptionTopY by remember { mutableIntStateOf(0) }
    var screenshotTopY by remember { mutableIntStateOf(0) }
    var achievementTopY by remember { mutableIntStateOf(0) }

    val game = uiState.game
    val hasDescription by remember { derivedStateOf { uiState.game?.description?.isNotEmpty() == true } }
    val hasScreenshots by remember { derivedStateOf { uiState.game?.screenshots?.isNotEmpty() == true } }
    val hasAchievements by remember { derivedStateOf { uiState.game?.achievements?.isNotEmpty() == true } }
    val hasSaveSync by remember {
        derivedStateOf {
            val s = uiState.saveStatusInfo?.status
            s != null &&
                s != com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NO_SAVE &&
                s != com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NOT_CONFIGURED
        }
    }
    val screenshotCount by remember { derivedStateOf { uiState.game?.screenshots?.size ?: 0 } }
    val achievementColumnCount by remember { derivedStateOf { uiState.game?.achievements?.chunked(3)?.size ?: 0 } }

    LaunchedEffect(uiState.game?.id) {
        scrollState.scrollTo(0)
        screenshotListState.scrollToItem(0)
        achievementListState.scrollToItem(0)
    }

    val inputHandler = remember(onBack, uiState.menuFocusIndex, screenshotCount, achievementColumnCount) {
        viewModel.createInputHandler(
            onBack = onBack,
            onNavigateToPlatformSettings = onNavigateToPlatformSettings,
            onSnapUp = {
                viewModel.moveMenuFocus(-1)
                true
            },
            onSnapDown = {
                viewModel.moveMenuFocus(1)
                true
            },
            onSectionLeft = {
                val layoutState = MenuLayoutState(
                    hasDescription = hasDescription,
                    hasScreenshots = hasScreenshots,
                    hasAchievements = hasAchievements,
                    hasSocialAccount = uiState.hasSocialAccount,
                    hasSaveSync = hasSaveSync
                )
                when (menuLayout.itemAtFocusIndex(uiState.menuFocusIndex, layoutState)) {
                    MenuItem.Screenshots -> if (screenshotCount > 0) {
                        val currentIndex = screenshotListState.firstVisibleItemIndex
                        val newIndex = (currentIndex - 1).coerceAtLeast(0)
                        coroutineScope.launch { screenshotListState.animateScrollToItem(newIndex) }
                    }
                    MenuItem.Achievements -> if (achievementColumnCount > 0) {
                        val currentIndex = achievementListState.firstVisibleItemIndex
                        val newIndex = (currentIndex - 1).coerceAtLeast(0)
                        coroutineScope.launch { achievementListState.animateScrollToItem(newIndex) }
                    }
                    else -> {}
                }
            },
            onSectionRight = {
                val layoutState = MenuLayoutState(
                    hasDescription = hasDescription,
                    hasScreenshots = hasScreenshots,
                    hasAchievements = hasAchievements,
                    hasSocialAccount = uiState.hasSocialAccount,
                    hasSaveSync = hasSaveSync
                )
                when (menuLayout.itemAtFocusIndex(uiState.menuFocusIndex, layoutState)) {
                    MenuItem.Screenshots -> if (screenshotCount > 0) {
                        val currentIndex = screenshotListState.firstVisibleItemIndex
                        val newIndex = (currentIndex + 1).coerceAtMost(screenshotCount - 1)
                        coroutineScope.launch { screenshotListState.animateScrollToItem(newIndex) }
                    }
                    MenuItem.Achievements -> if (achievementColumnCount > 0) {
                        val currentIndex = achievementListState.firstVisibleItemIndex
                        val newIndex = (currentIndex + 1).coerceAtMost(achievementColumnCount - 1)
                        coroutineScope.launch { achievementListState.animateScrollToItem(newIndex) }
                    }
                    else -> {}
                }
            },
            onPrevGame = { viewModel.navigateToPreviousGame() },
            onNextGame = { viewModel.navigateToNextGame() },
            isInScreenshotsSection = {
                val layoutState = MenuLayoutState(
                    hasDescription = hasDescription,
                    hasScreenshots = hasScreenshots,
                    hasAchievements = hasAchievements,
                    hasSocialAccount = uiState.hasSocialAccount,
                    hasSaveSync = hasSaveSync
                )
                menuLayout.itemAtFocusIndex(uiState.menuFocusIndex, layoutState) == MenuItem.Screenshots
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_GAME_DETAIL)
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_GAME_DETAIL)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hardcoreConflictInputHandler = remember(viewModel) {
        HardcoreConflictInputHandler(
            getFocusIndex = { uiState.hardcoreConflictFocusIndex },
            onFocusChange = viewModel::setHardcoreConflictFocusIndex,
            onKeepHardcore = viewModel::onKeepHardcore,
            onDowngradeToCasual = viewModel::onDowngradeToCasual,
            onKeepLocal = viewModel::onKeepLocal
        )
    }

    var localModifiedFocusIndex by remember { mutableIntStateOf(0) }
    val localModifiedInputHandler = remember(uiState.syncOverlayState) {
        LocalModifiedInputHandler(
            getFocusIndex = { localModifiedFocusIndex },
            onFocusChange = { localModifiedFocusIndex = it },
            onKeepLocal = { uiState.syncOverlayState?.onKeepLocalModified?.invoke() },
            onRestoreSelected = { uiState.syncOverlayState?.onRestoreSelected?.invoke() }
        )
    }

    val delegateSyncProgress = uiState.syncOverlayState?.syncProgress
    val isAnySyncing = uiState.isSyncing || uiState.syncOverlayState != null
    val effectiveSyncProgress = delegateSyncProgress ?: if (uiState.isSyncing) uiState.syncProgress else null
    val isHardcoreConflict = effectiveSyncProgress is SyncProgress.HardcoreConflict
    val isLocalModified = effectiveSyncProgress is SyncProgress.LocalModified

    LaunchedEffect(isHardcoreConflict) {
        if (isHardcoreConflict) {
            viewModel.setHardcoreConflictFocusIndex(0)
            inputDispatcher.pushModal(hardcoreConflictInputHandler)
        }
    }

    LaunchedEffect(isLocalModified) {
        if (isLocalModified) {
            localModifiedFocusIndex = 0
            inputDispatcher.pushModal(localModifiedInputHandler)
        }
    }

    DisposableEffect(isHardcoreConflict) {
        onDispose {
            if (isHardcoreConflict) {
                inputDispatcher.popModal()
            }
        }
    }

    DisposableEffect(isLocalModified) {
        onDispose {
            if (isLocalModified) {
                inputDispatcher.popModal()
            }
        }
    }

    val memcardPickerInputHandler = remember(viewModel) {
        com.nendo.argosy.ui.input.MemcardPickerInputHandler(
            getCards = { uiState.memcardPickerState?.cards ?: emptyList() },
            getFocusIndex = { uiState.memcardPickerFocusIndex },
            onFocusChange = { viewModel.setMemcardPickerFocusIndex(it) },
            onSelect = { viewModel.selectMemcard(it) },
            onDismiss = { viewModel.dismissMemcardPicker() }
        )
    }

    val showMemcardPicker = uiState.memcardPickerState != null
    LaunchedEffect(showMemcardPicker) {
        if (showMemcardPicker) {
            viewModel.setMemcardPickerFocusIndex(0)
            inputDispatcher.pushModal(memcardPickerInputHandler)
        }
    }

    DisposableEffect(showMemcardPicker) {
        onDispose {
            if (showMemcardPicker) {
                inputDispatcher.popModal()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading || game == null) {
            GameDetailSkeleton()
        } else {
            GameDetailContent(
                game = game,
                uiState = uiState,
                viewModel = viewModel,
                argosyViewModel = argosyViewModel,
                scrollState = scrollState,
                screenshotListState = screenshotListState,
                achievementListState = achievementListState,
                onDescriptionPositioned = { descriptionTopY = it },
                onScreenshotPositioned = { screenshotTopY = it },
                onAchievementPositioned = { achievementTopY = it },
                onBack = onBack,
                onNavigateToPlatformSettings = onNavigateToPlatformSettings,
                localModifiedFocusIndex = localModifiedFocusIndex
            )
        }
    }
}

@Composable
private fun GameDetailContent(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel,
    argosyViewModel: ArgosyViewModel,
    scrollState: ScrollState,
    screenshotListState: LazyListState,
    achievementListState: LazyListState,
    onDescriptionPositioned: (Int) -> Unit,
    onScreenshotPositioned: (Int) -> Unit,
    onAchievementPositioned: (Int) -> Unit,
    onBack: () -> Unit,
    onNavigateToPlatformSettings: (Long) -> Unit,
    localModifiedFocusIndex: Int
) {
    val coroutineScope = rememberCoroutineScope()
    val pickerState by viewModel.pickerModalDelegate.state.collectAsState()
    val isAnySyncing = uiState.isSyncing || uiState.syncOverlayState != null
    val showAnyOverlay = uiState.showMoreOptions || uiState.showPlayOptions ||
        uiState.showRatingsStatusMenu || pickerState.hasAnyPickerOpen ||
        uiState.showRatingPicker || uiState.showMissingDiscPrompt || isAnySyncing ||
        uiState.showSaveCacheDialog || uiState.showRenameDialog || uiState.showScreenshotViewer ||
        uiState.showExtractionFailedPrompt || uiState.showAchievementList
    val modalBlur by animateDpAsState(
        targetValue = if (showAnyOverlay) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    val combinedBlur = modalBlur

    var descriptionTopY by remember { mutableIntStateOf(0) }
    var screenshotTopY by remember { mutableIntStateOf(0) }
    var achievementTopY by remember { mutableIntStateOf(0) }

    val headerScrollThreshold = 200
    val isHeaderCollapsed = scrollState.value > headerScrollThreshold

    val contentHasSaveSync = uiState.saveStatusInfo?.status?.let {
        it != com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NO_SAVE &&
            it != com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NOT_CONFIGURED
    } ?: false

    val menuLayoutState = MenuLayoutState(
        hasDescription = !game.description.isNullOrBlank(),
        hasScreenshots = game.screenshots.isNotEmpty(),
        hasAchievements = game.achievements.isNotEmpty(),
        hasSocialAccount = uiState.hasSocialAccount,
        hasSaveSync = contentHasSaveSync
    )

    val menuDisplayState = GameDetailMenuState(
        focusedIndex = uiState.menuFocusIndex,
        isDownloaded = uiState.downloadStatus == GameDownloadStatus.DOWNLOADED,
        isDownloading = uiState.downloadStatus in listOf(
            GameDownloadStatus.QUEUED,
            GameDownloadStatus.DOWNLOADING,
            GameDownloadStatus.WAITING_FOR_STORAGE
        ),
        isExtracting = uiState.downloadStatus == GameDownloadStatus.EXTRACTING,
        downloadProgress = uiState.downloadProgress,
        isFavorite = game.isFavorite,
        saveStatus = uiState.saveStatusInfo,
        isSyncingSaves = uiState.isSyncingSaves,
        downloadSizeBytes = uiState.downloadSizeBytes,
        isPrivate = uiState.isPrivate
    )

    val focusedItem = menuLayout.itemAtFocusIndex(uiState.menuFocusIndex, menuLayoutState)

    // While a programmatic scroll triggered by menu focus is in flight, the
    // reverse-sync below must not infer a "visible section" from intermediate
    // scroll values -- a layout shift mid-animation (e.g. an achievements
    // refresh emitting and changing achievementTopY) would otherwise bounce
    // focus back to the previous section.
    var programmaticScrollInFlight by remember { mutableStateOf(false) }

    // Scroll to section when menu focus changes
    LaunchedEffect(uiState.menuFocusIndex) {
        try {
            programmaticScrollInFlight = true
            when (focusedItem) {
                MenuItem.Details -> scrollState.animateScrollTo(0)
                MenuItem.Description -> scrollState.animateScrollTo(descriptionTopY.coerceAtLeast(0))
                MenuItem.Screenshots -> scrollState.animateScrollTo(screenshotTopY.coerceAtLeast(0))
                MenuItem.Achievements -> scrollState.animateScrollTo(achievementTopY.coerceAtLeast(0))
                else -> {}
            }
        } finally {
            programmaticScrollInFlight = false
        }
    }

    // Sync menu focus with scroll position (reverse direction)
    @OptIn(FlowPreview::class)
    LaunchedEffect(scrollState, menuLayoutState.hasDescription, menuLayoutState.hasScreenshots, menuLayoutState.hasAchievements) {
        snapshotFlow { scrollState.value }
            .debounce(100)
            .distinctUntilChanged()
            .collect { scrollY ->
                if (programmaticScrollInFlight) return@collect
                val currentFocus = menuLayout.itemAtFocusIndex(uiState.menuFocusIndex, menuLayoutState)
                if (currentFocus !in listOf(MenuItem.Details, MenuItem.Description, MenuItem.Screenshots, MenuItem.Achievements)) {
                    return@collect
                }

                val visibleSection = when {
                    menuLayoutState.hasAchievements && scrollY >= achievementTopY - 100 -> MenuItem.Achievements
                    menuLayoutState.hasScreenshots && scrollY >= screenshotTopY - 100 -> MenuItem.Screenshots
                    menuLayoutState.hasDescription && scrollY >= descriptionTopY - 100 -> MenuItem.Description
                    else -> MenuItem.Details
                }

                if (visibleSection != currentFocus) {
                    val targetIndex = menuLayout.focusIndexOf(visibleSection, menuLayoutState)
                    if (targetIndex >= 0) {
                        viewModel.setMenuFocusIndex(targetIndex)
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer - extends behind footer
        Box(modifier = Modifier.fillMaxSize().blur(combinedBlur)) {
            val effectiveBackgroundPath = uiState.repairedBackgroundPath ?: game.backgroundPath
            if (effectiveBackgroundPath != null) {
                AsyncImage(
                    model = rememberFileImageModel(effectiveBackgroundPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(24.dp),
                    onError = {
                        if (uiState.repairedBackgroundPath == null && game.backgroundPath?.startsWith("/") == true) {
                            viewModel.repairBackgroundImage(game.id, game.backgroundPath)
                        }
                    }
                )
            }

            val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
            val overlayColor = if (isDarkTheme) Color.Black else Color.White

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                overlayColor.copy(alpha = if (isDarkTheme) 0.5f else 0.3f),
                                overlayColor.copy(alpha = if (isDarkTheme) 0.9f else 0.7f)
                            )
                        )
                    )
            )
        }

        // Main content: Collapsed Header + Left Menu (30%) + Right Content (70%)
        Column(modifier = Modifier.fillMaxSize().blur(combinedBlur)) {
            val isDark = LocalLauncherTheme.current.isDarkTheme
            val fadeColor = if (isDark) Color.Black else Color.White

            // Full-width collapsed header (pushes content down)
            StickyCollapsedHeader(
                game = game,
                isVisible = isHeaderCollapsed
            )

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val aspectRatio = maxWidth / maxHeight
                val isCompactMenu = aspectRatio <= 1.3f

                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Menu (compact: icon-only, normal: 30%)
                    Box(
                        modifier = Modifier
                            .then(
                                if (isCompactMenu) Modifier.width(56.dp)
                                else Modifier.fillMaxWidth(0.30f)
                            )
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        GameDetailMenu(
                            layoutState = menuLayoutState,
                            displayState = menuDisplayState,
                            onItemClick = { item ->
                                when (item) {
                                    MenuItem.Play -> viewModel.primaryAction()
                                    MenuItem.Saves -> viewModel.syncSavesNow()
                                    MenuItem.Favorite -> viewModel.toggleFavorite()
                                    MenuItem.Privacy -> viewModel.togglePrivacy()
                                    MenuItem.Options -> viewModel.toggleMoreOptions()
                                    MenuItem.Details -> coroutineScope.launch {
                                        scrollState.animateScrollTo(0)
                                    }
                                    MenuItem.Description -> coroutineScope.launch {
                                        scrollState.animateScrollTo(descriptionTopY.coerceAtLeast(0))
                                    }
                                    MenuItem.Screenshots -> viewModel.openScreenshotViewer()
                                    MenuItem.Achievements -> coroutineScope.launch {
                                        scrollState.animateScrollTo(achievementTopY.coerceAtLeast(0))
                                    }
                                }
                            },
                            onFocusChange = viewModel::setMenuFocusIndex,
                            isCompact = isCompactMenu,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = if (isCompactMenu) Dimens.spacingSm else Dimens.spacingXl,
                                    top = Dimens.spacingMd
                                )
                        )
                    }

                    // Right Content (70%)
                    Column(modifier = Modifier.weight(1f)) {

                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(start = Dimens.spacingMd, top = Dimens.spacingXl, end = Dimens.spacingXl, bottom = Dimens.spacingXl)
                            ) {
                                ExpandedHeader(game = game)

                                Spacer(modifier = Modifier.height(Dimens.spacingXl))

                            if (!game.description.isNullOrBlank()) {
                                DescriptionSection(
                                    description = game.description,
                                    onPositioned = { y ->
                                        descriptionTopY = y
                                        onDescriptionPositioned(y)
                                    }
                                )
                                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                            }

                            if (game.screenshots.isNotEmpty()) {
                                ScreenshotsSection(
                                    screenshots = game.screenshots,
                                    listState = screenshotListState,
                                    onScreenshotTap = { index -> viewModel.openScreenshotViewer(index) },
                                    onPositioned = { y ->
                                        screenshotTopY = y
                                        onScreenshotPositioned(y)
                                    },
                                    isActive = focusedItem == MenuItem.Screenshots,
                                    gameId = game.id,
                                    cacheEnabled = uiState.syncScreenshotsEnabled,
                                    onSectionFocus = {
                                        viewModel.setMenuFocusIndex(menuLayout.focusIndexOf(MenuItem.Screenshots, menuLayoutState))
                                    }
                                )
                                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                            }

                            if (game.achievements.isNotEmpty()) {
                                AchievementsSection(
                                    achievements = game.achievements,
                                    listState = achievementListState,
                                    onPositioned = { y ->
                                        achievementTopY = y
                                        onAchievementPositioned(y)
                                    },
                                    isActive = focusedItem == MenuItem.Achievements
                                )
                                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                            }
                            }

                            // Gradient fade at bottom of content
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(Dimens.spacingXxl)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                fadeColor.copy(alpha = 0.8f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !showAnyOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val canShowPlayOptions = uiState.downloadStatus == GameDownloadStatus.DOWNLOADED &&
                    game.isBuiltInEmulator
                FooterBar(
                    hints = buildList {
                        add(InputButton.LB_RB to "Prev/Next Game")
                        if (focusedItem == MenuItem.Screenshots || focusedItem == MenuItem.Achievements) {
                            add(InputButton.DPAD_HORIZONTAL to "Scroll")
                        }
                        when (focusedItem) {
                            MenuItem.Play -> add(InputButton.A to when {
                                isAnySyncing -> "Syncing..."
                                uiState.downloadStatus == GameDownloadStatus.DOWNLOADED -> "Play"
                                uiState.downloadStatus == GameDownloadStatus.NEEDS_INSTALL -> "Install"
                                uiState.downloadStatus == GameDownloadStatus.NOT_DOWNLOADED -> "Download"
                                uiState.downloadStatus == GameDownloadStatus.QUEUED -> "Queued"
                                uiState.downloadStatus == GameDownloadStatus.WAITING_FOR_STORAGE -> "No Space"
                                uiState.downloadStatus == GameDownloadStatus.DOWNLOADING -> "Downloading"
                                uiState.downloadStatus == GameDownloadStatus.EXTRACTING -> "Extracting"
                                uiState.downloadStatus == GameDownloadStatus.PAUSED -> "Paused"
                                else -> "Play"
                            })
                            MenuItem.Saves -> add(InputButton.A to if (uiState.isSyncingSaves) "Syncing..." else "Sync")
                            MenuItem.Favorite -> add(InputButton.A to if (game.isFavorite) "Unfavorite" else "Favorite")
                            MenuItem.Privacy -> add(InputButton.A to if (uiState.isPrivate) "Make Public" else "Make Private")
                            MenuItem.Options -> add(InputButton.A to "Options")
                            MenuItem.Screenshots -> add(InputButton.A to "View")
                            MenuItem.Achievements -> add(InputButton.A to "View All")
                            MenuItem.Details, MenuItem.Description, null -> {}
                        }
                        add(InputButton.B to "Back")
                        if (canShowPlayOptions && focusedItem == MenuItem.Play) {
                            add(InputButton.X to "New Game")
                        } else if (uiState.hasSocialAccount && game.igdbId != null) {
                            add(InputButton.X to if (uiState.isPrivate) "Make Public" else "Make Private")
                        }
                        add(InputButton.Y to if (game.isFavorite) "Unfavorite" else "Favorite")
                    },
                    onHintClick = { button ->
                        when (button) {
                            InputButton.A -> viewModel.executeMenuAction()
                            InputButton.B -> onBack()
                            InputButton.X -> {
                                if (canShowPlayOptions) viewModel.showPlayOptions()
                                else if (uiState.hasSocialAccount) viewModel.togglePrivacy()
                            }
                            InputButton.Y -> viewModel.toggleFavorite()
                            InputButton.LB -> viewModel.navigateToPreviousGame()
                            InputButton.RB -> viewModel.navigateToNextGame()
                            else -> {}
                        }
                    }
                )
            }
        }

        GameDetailModals(game = game, uiState = uiState, viewModel = viewModel, onBack = onBack, onNavigateToPlatformSettings = onNavigateToPlatformSettings, localModifiedFocusIndex = localModifiedFocusIndex)

        AchievementListOverlay(
            visible = uiState.showAchievementList,
            gameTitle = game.title,
            achievements = game.achievements,
            focusIndex = uiState.achievementListFocusIndex
        )
    }
}

@Composable
private fun GameDetailModals(
    onNavigateToPlatformSettings: (Long) -> Unit,
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    localModifiedFocusIndex: Int
) {
    val pickerState by viewModel.pickerModalDelegate.state.collectAsState()

    AnimatedVisibility(
        visible = uiState.showMoreOptions,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        MoreOptionsModal(
            game = game,
            focusIndex = uiState.moreOptionsFocusIndex,
            isDownloaded = uiState.downloadStatus == GameDownloadStatus.DOWNLOADED,
            hasVariants = uiState.hasVariants,
            updateCount = uiState.updateFiles.size + uiState.dlcFiles.size,
            onAction = { action -> viewModel.handleMoreOptionAction(action, onBack, onNavigateToPlatformSettings) },
            onDismiss = viewModel::toggleMoreOptions
        )
    }

    AnimatedVisibility(
        visible = uiState.showPlayOptions,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        PlayOptionsModal(
            focusIndex = uiState.playOptionsFocusIndex,
            hasSaves = uiState.hasCasualSaves,
            hasHardcoreSave = uiState.hasHardcoreSave,
            hasRASupport = uiState.hasRASupport,
            isRALoggedIn = uiState.isRALoggedIn,
            isOnline = uiState.isOnline,
            canSkipSync = uiState.isOnline,
            onAction = viewModel::handlePlayOption,
            onDismiss = viewModel::dismissPlayOptions
        )
    }

    AnimatedVisibility(
        visible = uiState.showRatingsStatusMenu,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        RatingsStatusModal(
            game = game,
            focusIndex = uiState.ratingsStatusFocusIndex,
            onAction = { action -> viewModel.handleMoreOptionAction(action, onBack) },
            onDismiss = viewModel::dismissRatingsStatusMenu
        )
    }

    AnimatedVisibility(
        visible = pickerState.showUpdatesPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        UpdatesPickerModal(
            files = uiState.updateFiles + uiState.dlcFiles,
            focusIndex = pickerState.updatesPickerFocusIndex,
            onDownload = viewModel::downloadUpdateFile,
            onDismiss = viewModel.pickerModalDelegate::dismissUpdatesPicker
        )
    }

    AnimatedVisibility(
        visible = pickerState.showEmulatorPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        EmulatorPickerModal(
            availableEmulators = pickerState.availableEmulators,
            currentEmulatorName = game.emulatorName,
            focusIndex = pickerState.emulatorPickerFocusIndex,
            onSelectEmulator = viewModel.pickerModalDelegate::selectEmulator,
            onDismiss = viewModel.pickerModalDelegate::dismissEmulatorPicker
        )
    }

    AnimatedVisibility(
        visible = pickerState.showSteamLauncherPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        SteamLauncherPickerModal(
            availableLaunchers = pickerState.availableSteamLaunchers,
            currentLauncherName = game.steamLauncherName,
            focusIndex = pickerState.steamLauncherPickerFocusIndex,
            onSelectLauncher = viewModel.pickerModalDelegate::selectSteamLauncher,
            onDismiss = viewModel.pickerModalDelegate::dismissSteamLauncherPicker
        )
    }

    AnimatedVisibility(
        visible = pickerState.showCorePicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        CorePickerModal(
            availableCores = pickerState.availableCores,
            selectedCoreId = uiState.selectedCoreId,
            focusIndex = pickerState.corePickerFocusIndex,
            onSelectCore = viewModel.pickerModalDelegate::selectCore,
            onDismiss = viewModel.pickerModalDelegate::dismissCorePicker
        )
    }

    AnimatedVisibility(
        visible = pickerState.showDiscPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        DiscPickerModal(
            discs = pickerState.discPickerOptions,
            focusIndex = pickerState.discPickerFocusIndex,
            onSelectDisc = viewModel.pickerModalDelegate::selectDisc,
            onDismiss = viewModel.pickerModalDelegate::dismissDiscPicker
        )
    }

    uiState.memcardPickerState?.let { pickerState ->
        MemcardPickerModal(
            cards = pickerState.cards,
            focusIndex = uiState.memcardPickerFocusIndex,
            selectedCardPath = null,
            onSelectCard = viewModel::selectMemcard,
            onDismiss = viewModel::dismissMemcardPicker
        )
    }

    AnimatedVisibility(
        visible = pickerState.showVariantPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        com.nendo.argosy.ui.screens.gamedetail.modals.VariantPickerModal(
            variants = pickerState.variantPickerOptions,
            focusIndex = pickerState.variantPickerFocusIndex,
            onSelectVariant = { viewModel.pickerModalDelegate.confirmVariantSelection() },
            onDownloadVariant = { fileId -> viewModel.downloadVariant(fileId) },
            onDismiss = viewModel.pickerModalDelegate::dismissVariantPicker
        )
    }

    AnimatedVisibility(
        visible = uiState.showRatingPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        RatingPickerModal(
            type = uiState.ratingPickerType,
            value = uiState.ratingPickerValue,
            onValueChange = viewModel::setRatingValue,
            onDismiss = viewModel::dismissRatingPicker
        )
    }

    AnimatedVisibility(
        visible = uiState.showStatusPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        StatusPickerModal(
            selectedValue = uiState.statusPickerValue,
            currentValue = uiState.game?.status,
            onSelect = viewModel::selectStatus,
            onDismiss = viewModel::dismissStatusPicker
        )
    }

    AnimatedVisibility(
        visible = uiState.showMissingDiscPrompt,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        MissingDiscModal(
            missingDiscNumbers = uiState.missingDiscNumbers,
            onDismiss = viewModel::dismissMissingDiscPrompt
        )
    }

    AnimatedVisibility(
        visible = uiState.showExtractionFailedPrompt && uiState.extractionFailedInfo != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        uiState.extractionFailedInfo?.let { info ->
            ExtractionFailedModal(
                info = info,
                focusIndex = uiState.extractionPromptFocusIndex,
                onRetry = viewModel::confirmExtractionPromptSelection,
                onRedownload = {
                    viewModel.moveExtractionPromptFocus(1)
                    viewModel.confirmExtractionPromptSelection()
                },
                onDismiss = viewModel::dismissExtractionPrompt
            )
        }
    }

    SaveChannelModal(
        state = uiState.saveChannel,
        savePath = uiState.saveChannel.savePath,
        onRenameTextChange = viewModel::updateRenameText,
        onSlotClick = viewModel::setSlotIndex,
        onHistoryClick = viewModel::setHistoryIndex,
        onTabSwitch = viewModel::switchSaveTab,
        onStateClick = viewModel::setSaveCacheFocusIndex,
        onDismissScreenshotPreview = viewModel::dismissScreenshotPreview,
        onDismiss = viewModel::dismissSaveCacheDialog
    )

    PermissionRequiredModal(
        isVisible = uiState.showPermissionModal,
        permissionType = uiState.permissionModalType,
        onGrantPermission = {
            when (uiState.permissionModalType) {
                PermissionModalType.STORAGE -> viewModel.openAllFilesAccessSettings()
                PermissionModalType.SAF -> viewModel.requestSafGrant()
            }
        },
        onDisableSync = viewModel::disableSaveSync,
        onDismiss = viewModel::dismissPermissionModal
    )

    val delegateOverlay = uiState.syncOverlayState
    val effectiveSyncProgress = delegateOverlay?.syncProgress
        ?: if (uiState.isSyncing) uiState.syncProgress else null

    SyncOverlay(
        syncProgress = effectiveSyncProgress,
        gameTitle = delegateOverlay?.gameTitle ?: game.title,
        onGrantPermission = delegateOverlay?.onGrantPermission,
        onDisableSync = delegateOverlay?.onDisableSync,
        onOpenSettings = delegateOverlay?.onOpenSettings,
        onSkip = delegateOverlay?.onSkip,
        onKeepHardcore = delegateOverlay?.onKeepHardcore ?: viewModel::onKeepHardcore,
        onDowngradeToCasual = delegateOverlay?.onDowngradeToCasual ?: viewModel::onDowngradeToCasual,
        onKeepLocal = delegateOverlay?.onKeepLocal ?: viewModel::onKeepLocal,
        onKeepLocalModified = delegateOverlay?.onKeepLocalModified,
        onRestoreSelected = delegateOverlay?.onRestoreSelected,
        hardcoreConflictFocusIndex = uiState.hardcoreConflictFocusIndex,
        localModifiedFocusIndex = localModifiedFocusIndex
    )

    AnimatedVisibility(
        visible = uiState.showScreenshotViewer,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        ScreenshotViewerOverlay(
            screenshots = game.screenshots,
            currentIndex = uiState.viewerScreenshotIndex,
            onNavigate = viewModel::moveViewerIndex,
            onDismiss = viewModel::closeScreenshotViewer,
            onSetBackground = viewModel::setCurrentScreenshotAsBackground
        )
    }

    AnimatedVisibility(
        visible = uiState.showAddToCollectionModal,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        AddToCollectionModal(
            collections = uiState.collections.map { c ->
                CollectionItem(c.id, c.name, c.isInCollection)
            },
            focusIndex = uiState.collectionModalFocusIndex,
            showCreateOption = true,
            onToggleCollection = viewModel::toggleGameInCollection,
            onCreate = viewModel::showCreateCollectionFromModal,
            onDismiss = viewModel::dismissAddToCollectionModal
        )
    }

    if (uiState.showCreateCollectionDialog) {
        CreateCollectionDialog(
            onDismiss = viewModel::hideCreateCollectionDialog,
            onCreate = { name ->
                viewModel.createCollectionFromModal(name)
                viewModel.hideCreateCollectionDialog()
            }
        )
    }
}
