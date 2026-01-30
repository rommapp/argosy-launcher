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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.input.HardcoreConflictInputHandler
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementsSection
import com.nendo.argosy.ui.screens.gamedetail.components.DescriptionSection
import com.nendo.argosy.ui.screens.gamedetail.components.GameDetailSkeleton
import com.nendo.argosy.ui.screens.gamedetail.components.GameHeader
import com.nendo.argosy.ui.screens.gamedetail.components.ScreenshotViewerOverlay
import com.nendo.argosy.ui.screens.gamedetail.components.ScreenshotsSection
import com.nendo.argosy.ui.screens.gamedetail.components.SnapState
import com.nendo.argosy.ui.components.DiscPickerModal
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
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun GameDetailScreen(
    gameId: Long,
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel()
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

    LaunchedEffect(Unit) {
        viewModel.launchEvents.collectLatest { event ->
            when (event) {
                is LaunchEvent.Launch -> {
                    try {
                        android.util.Log.d("GameDetailScreen", "Starting activity: ${event.intent}")
                        context.startActivity(event.intent)
                        android.util.Log.d("GameDetailScreen", "Activity started successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("GameDetailScreen", "Failed to start activity", e)
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
    val hasDescription = game?.description?.isNotEmpty() == true
    val hasScreenshots = game?.screenshots?.isNotEmpty() == true
    val hasAchievements = game?.achievements?.isNotEmpty() == true
    val screenshotCount = game?.screenshots?.size ?: 0
    val achievementColumnCount = game?.achievements?.chunked(3)?.size ?: 0

    val snapStates = remember(hasDescription, hasScreenshots, hasAchievements) {
        buildList {
            add(SnapState.TOP)
            if (hasDescription) add(SnapState.DESCRIPTION)
            if (hasScreenshots) add(SnapState.SCREENSHOTS)
            if (hasAchievements) add(SnapState.ACHIEVEMENTS)
        }
    }

    var currentSnapIndex by remember { mutableIntStateOf(0) }

    fun getSnapTarget(state: SnapState): Int = when (state) {
        SnapState.TOP -> 0
        SnapState.DESCRIPTION -> descriptionTopY.coerceAtLeast(0)
        SnapState.SCREENSHOTS -> screenshotTopY.coerceAtLeast(0)
        SnapState.ACHIEVEMENTS -> achievementTopY.coerceAtLeast(0)
    }

    LaunchedEffect(uiState.game?.id) {
        currentSnapIndex = 0
        scrollState.scrollTo(0)
        screenshotListState.scrollToItem(0)
        achievementListState.scrollToItem(0)
    }

    val inputHandler = remember(onBack, snapStates, screenshotCount, achievementColumnCount) {
        viewModel.createInputHandler(
            onBack = onBack,
            onSnapUp = {
                if (currentSnapIndex > 0) {
                    currentSnapIndex--
                    coroutineScope.launch {
                        scrollState.animateScrollTo(getSnapTarget(snapStates[currentSnapIndex]))
                    }
                    true
                } else false
            },
            onSnapDown = {
                if (currentSnapIndex < snapStates.lastIndex) {
                    currentSnapIndex++
                    coroutineScope.launch {
                        scrollState.animateScrollTo(getSnapTarget(snapStates[currentSnapIndex]))
                    }
                    true
                } else false
            },
            onSectionLeft = {
                coroutineScope.launch {
                    val snapState = snapStates.getOrElse(currentSnapIndex) { SnapState.TOP }
                    val targetSection = when (snapState) {
                        SnapState.TOP, SnapState.DESCRIPTION -> {
                            if (hasScreenshots) SnapState.SCREENSHOTS
                            else if (hasAchievements) SnapState.ACHIEVEMENTS
                            else null
                        }
                        SnapState.SCREENSHOTS -> SnapState.SCREENSHOTS
                        SnapState.ACHIEVEMENTS -> SnapState.ACHIEVEMENTS
                    }
                    when (targetSection) {
                        SnapState.SCREENSHOTS -> viewModel.moveScreenshotFocus(-1)
                        SnapState.ACHIEVEMENTS -> {
                            val current = achievementListState.firstVisibleItemIndex
                            val target = if (current > 0) current - 1 else achievementColumnCount - 1
                            achievementListState.scrollToItem(target)
                        }
                        else -> {}
                    }
                }
            },
            onSectionRight = {
                coroutineScope.launch {
                    val snapState = snapStates.getOrElse(currentSnapIndex) { SnapState.TOP }
                    val targetSection = when (snapState) {
                        SnapState.TOP, SnapState.DESCRIPTION -> {
                            if (hasScreenshots) SnapState.SCREENSHOTS
                            else if (hasAchievements) SnapState.ACHIEVEMENTS
                            else null
                        }
                        SnapState.SCREENSHOTS -> SnapState.SCREENSHOTS
                        SnapState.ACHIEVEMENTS -> SnapState.ACHIEVEMENTS
                    }
                    when (targetSection) {
                        SnapState.SCREENSHOTS -> viewModel.moveScreenshotFocus(1)
                        SnapState.ACHIEVEMENTS -> {
                            val isAtEnd = !achievementListState.canScrollForward
                            val current = achievementListState.firstVisibleItemIndex
                            val target = if (isAtEnd) 0 else current + 1
                            achievementListState.scrollToItem(target)
                        }
                        else -> {}
                    }
                }
            },
            onPrevGame = { viewModel.navigateToPreviousGame() },
            onNextGame = { viewModel.navigateToNextGame() },
            isInScreenshotsSection = {
                snapStates.getOrElse(currentSnapIndex) { SnapState.TOP } == SnapState.SCREENSHOTS
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

    val delegateSyncProgress = uiState.syncOverlayState?.syncProgress
    val isAnySyncing = uiState.isSyncing || uiState.syncOverlayState != null
    val effectiveSyncProgress = delegateSyncProgress ?: if (uiState.isSyncing) uiState.syncProgress else null
    val isHardcoreConflict = effectiveSyncProgress is SyncProgress.HardcoreConflict

    LaunchedEffect(isHardcoreConflict) {
        if (isHardcoreConflict) {
            viewModel.setHardcoreConflictFocusIndex(0)
            inputDispatcher.pushModal(hardcoreConflictInputHandler)
        }
    }

    DisposableEffect(isHardcoreConflict) {
        onDispose {
            if (isHardcoreConflict) {
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
                scrollState = scrollState,
                screenshotListState = screenshotListState,
                achievementListState = achievementListState,
                currentSnapState = snapStates.getOrElse(currentSnapIndex) { SnapState.TOP },
                onDescriptionPositioned = { descriptionTopY = it },
                onScreenshotPositioned = { screenshotTopY = it },
                onAchievementPositioned = { achievementTopY = it },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun GameDetailContent(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel,
    scrollState: ScrollState,
    screenshotListState: LazyListState,
    achievementListState: LazyListState,
    currentSnapState: SnapState,
    onDescriptionPositioned: (Int) -> Unit,
    onScreenshotPositioned: (Int) -> Unit,
    onAchievementPositioned: (Int) -> Unit,
    onBack: () -> Unit
) {
    val pickerState by viewModel.pickerModalDelegate.state.collectAsState()
    val isAnySyncing = uiState.isSyncing || uiState.syncOverlayState != null
    val showAnyOverlay = uiState.showMoreOptions || uiState.showPlayOptions ||
        uiState.showRatingsStatusMenu || pickerState.hasAnyPickerOpen ||
        uiState.showRatingPicker || uiState.showMissingDiscPrompt || isAnySyncing ||
        uiState.showSaveCacheDialog || uiState.showRenameDialog || uiState.showScreenshotViewer ||
        uiState.showExtractionFailedPrompt
    val modalBlur by animateDpAsState(
        targetValue = if (showAnyOverlay) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer - extends behind footer
        Box(modifier = Modifier.fillMaxSize().blur(modalBlur)) {
            val effectiveBackgroundPath = uiState.repairedBackgroundPath ?: game.backgroundPath
            if (effectiveBackgroundPath != null) {
                val imageData = if (effectiveBackgroundPath.startsWith("/")) {
                    java.io.File(effectiveBackgroundPath)
                } else {
                    effectiveBackgroundPath
                }
                AsyncImage(
                    model = imageData,
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

        // Content + Footer in Column (content doesn't flow behind footer)
        Column(modifier = Modifier.fillMaxSize().blur(modalBlur)) {
            val isDark = LocalLauncherTheme.current.isDarkTheme
            val fadeColor = if (isDark) Color.Black else Color.White

            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = Dimens.spacingXl, top = Dimens.spacingXl, end = Dimens.spacingXl, bottom = Dimens.spacingXl)
                ) {
                    GameHeader(game = game, uiState = uiState, viewModel = viewModel)

                    Spacer(modifier = Modifier.height(Dimens.spacingXl))

                    if (!game.description.isNullOrBlank()) {
                        DescriptionSection(
                            description = game.description,
                            onPositioned = onDescriptionPositioned
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    }

                    if (game.screenshots.isNotEmpty()) {
                        ScreenshotsSection(
                            screenshots = game.screenshots,
                            listState = screenshotListState,
                            currentSnapState = currentSnapState,
                            focusedIndex = uiState.focusedScreenshotIndex,
                            onScreenshotTap = { index -> viewModel.openScreenshotViewer(index) },
                            onPositioned = onScreenshotPositioned
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    }

                    if (game.achievements.isNotEmpty()) {
                        AchievementsSection(
                            achievements = game.achievements,
                            listState = achievementListState,
                            currentSnapState = currentSnapState,
                            onPositioned = onAchievementPositioned
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

            AnimatedVisibility(
                visible = !showAnyOverlay,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isInScreenshots = currentSnapState == SnapState.SCREENSHOTS && game.screenshots.isNotEmpty()
                val canShowPlayOptions = uiState.downloadStatus == GameDownloadStatus.DOWNLOADED &&
                    game.isBuiltInEmulator
                FooterBar(
                    hints = buildList {
                        add(InputButton.LB_RB to "Prev/Next Game")
                        add(InputButton.A to when {
                            isInScreenshots -> "View"
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
                        add(InputButton.B to "Back")
                        if (canShowPlayOptions) {
                            add(InputButton.X to "New Game")
                        }
                        add(InputButton.Y to if (uiState.game?.isFavorite == true) "Unfavorite" else "Favorite")
                    }
                )
            }
        }

        GameDetailModals(game = game, uiState = uiState, viewModel = viewModel, onBack = onBack)
    }
}

@Composable
private fun GameDetailModals(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel,
    onBack: () -> Unit
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
            updateCount = uiState.updateFiles.size + uiState.dlcFiles.size,
            onAction = { action -> viewModel.handleMoreOptionAction(action, onBack) },
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
        onTabSelect = viewModel::switchSaveTab,
        onEntryClick = viewModel::setSaveCacheFocusIndex,
        onEntryLongClick = viewModel::handleSaveCacheLongPress,
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
        hardcoreConflictFocusIndex = uiState.hardcoreConflictFocusIndex
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
            onDismiss = viewModel::closeScreenshotViewer
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
