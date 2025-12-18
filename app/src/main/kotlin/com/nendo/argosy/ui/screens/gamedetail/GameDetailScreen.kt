package com.nendo.argosy.ui.screens.gamedetail

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
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementsSection
import com.nendo.argosy.ui.screens.gamedetail.components.DescriptionSection
import com.nendo.argosy.ui.screens.gamedetail.components.GameDetailSkeleton
import com.nendo.argosy.ui.screens.gamedetail.components.GameHeader
import com.nendo.argosy.ui.screens.gamedetail.components.ScreenshotsSection
import com.nendo.argosy.ui.screens.gamedetail.components.SnapState
import com.nendo.argosy.ui.screens.gamedetail.modals.CorePickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.DiscPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.EmulatorPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.MissingDiscModal
import com.nendo.argosy.ui.screens.gamedetail.modals.MoreOptionsModal
import com.nendo.argosy.ui.screens.gamedetail.modals.PermissionRequiredModal
import com.nendo.argosy.ui.screens.gamedetail.modals.RatingPickerModal
import com.nendo.argosy.ui.common.savechannel.SaveChannelModal
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
    val context = LocalContext.current

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
                        SnapState.SCREENSHOTS -> {
                            val current = screenshotListState.firstVisibleItemIndex
                            val target = if (current > 0) current - 1 else screenshotCount - 1
                            screenshotListState.scrollToItem(target)
                        }
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
                        SnapState.SCREENSHOTS -> {
                            val isAtEnd = !screenshotListState.canScrollForward
                            val current = screenshotListState.firstVisibleItemIndex
                            val target = if (isAtEnd) 0 else current + 1
                            screenshotListState.scrollToItem(target)
                        }
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
            onNextGame = { viewModel.navigateToNextGame() }
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
                onAchievementPositioned = { achievementTopY = it }
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
    onAchievementPositioned: (Int) -> Unit
) {
    val showAnyOverlay = uiState.showMoreOptions || uiState.showEmulatorPicker || uiState.showCorePicker ||
        uiState.showRatingPicker || uiState.showDiscPicker || uiState.showMissingDiscPrompt || uiState.isSyncing ||
        uiState.showSaveCacheDialog || uiState.showRenameDialog
    val modalBlur by animateDpAsState(
        targetValue = if (showAnyOverlay) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().blur(modalBlur)) {
            if (game.backgroundPath != null) {
                val imageData = if (game.backgroundPath.startsWith("/")) {
                    java.io.File(game.backgroundPath)
                } else {
                    game.backgroundPath
                }
                AsyncImage(
                    model = imageData,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(24.dp)
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 80.dp)
            ) {
                GameHeader(game = game, uiState = uiState, viewModel = viewModel)

                Spacer(modifier = Modifier.height(32.dp))

                if (!game.description.isNullOrBlank()) {
                    DescriptionSection(
                        description = game.description,
                        onPositioned = onDescriptionPositioned
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (game.screenshots.isNotEmpty()) {
                    ScreenshotsSection(
                        screenshots = game.screenshots,
                        listState = screenshotListState,
                        currentSnapState = currentSnapState,
                        onPositioned = onScreenshotPositioned
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (game.achievements.isNotEmpty()) {
                    AchievementsSection(
                        achievements = game.achievements,
                        listState = achievementListState,
                        currentSnapState = currentSnapState,
                        onPositioned = onAchievementPositioned
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        GameDetailModals(game = game, uiState = uiState, viewModel = viewModel)

        AnimatedVisibility(
            visible = !showAnyOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FooterBar(
                hints = listOf(
                    InputButton.LB_RB to "Prev/Next Game",
                    InputButton.SOUTH to when {
                        uiState.isSyncing -> "Syncing..."
                        uiState.downloadStatus == GameDownloadStatus.DOWNLOADED -> "Play"
                        uiState.downloadStatus == GameDownloadStatus.NOT_DOWNLOADED -> "Download"
                        uiState.downloadStatus == GameDownloadStatus.QUEUED -> "Queued"
                        uiState.downloadStatus == GameDownloadStatus.WAITING_FOR_STORAGE -> "No Space"
                        uiState.downloadStatus == GameDownloadStatus.DOWNLOADING -> "Downloading"
                        uiState.downloadStatus == GameDownloadStatus.PAUSED -> "Paused"
                        else -> "Play"
                    },
                    InputButton.EAST to "Back",
                    InputButton.NORTH to if (uiState.game?.isFavorite == true) "Unfavorite" else "Favorite"
                )
            )
        }
    }
}

@Composable
private fun GameDetailModals(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel
) {
    AnimatedVisibility(
        visible = uiState.showMoreOptions,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        MoreOptionsModal(
            game = game,
            focusIndex = uiState.moreOptionsFocusIndex,
            isDownloaded = uiState.downloadStatus == GameDownloadStatus.DOWNLOADED
        )
    }

    AnimatedVisibility(
        visible = uiState.showEmulatorPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        EmulatorPickerModal(
            availableEmulators = uiState.availableEmulators,
            currentEmulatorName = game.emulatorName,
            focusIndex = uiState.emulatorPickerFocusIndex,
            onSelectEmulator = viewModel::selectEmulator,
            onDismiss = viewModel::dismissEmulatorPicker
        )
    }

    AnimatedVisibility(
        visible = uiState.showCorePicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        CorePickerModal(
            availableCores = uiState.availableCores,
            selectedCoreId = uiState.selectedCoreId,
            focusIndex = uiState.corePickerFocusIndex,
            onSelectCore = viewModel::selectCore,
            onDismiss = viewModel::dismissCorePicker
        )
    }

    AnimatedVisibility(
        visible = uiState.showRatingPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        RatingPickerModal(
            type = uiState.ratingPickerType,
            value = uiState.ratingPickerValue
        )
    }

    AnimatedVisibility(
        visible = uiState.showDiscPicker,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        DiscPickerModal(
            discs = uiState.discs,
            focusIndex = uiState.discPickerFocusIndex,
            onSelectDisc = viewModel::selectDiscAtIndex
        )
    }

    AnimatedVisibility(
        visible = uiState.showMissingDiscPrompt,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        MissingDiscModal(
            missingDiscNumbers = uiState.missingDiscNumbers
        )
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
        onGrantPermission = viewModel::openAllFilesAccessSettings,
        onDisableSync = viewModel::disableSaveSync,
        onDismiss = viewModel::dismissPermissionModal
    )

    SyncOverlay(
        syncProgress = if (uiState.isSyncing) uiState.syncProgress else null,
        gameTitle = game.title
    )
}
