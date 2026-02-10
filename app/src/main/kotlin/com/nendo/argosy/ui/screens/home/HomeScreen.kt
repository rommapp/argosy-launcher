package com.nendo.argosy.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.abs
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.icons.InputIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.ui.components.AddToCollectionModal
import com.nendo.argosy.ui.components.ChangelogModal
import com.nendo.argosy.ui.components.CollectionItem
import com.nendo.argosy.ui.components.FooterHint
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.GameCardWithNewBadge
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SubtleFooterBar
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.components.YouTubeVideoPlayer
import com.nendo.argosy.ui.input.ChangelogInputHandler
import com.nendo.argosy.ui.input.DiscPickerInputHandler
import com.nendo.argosy.ui.input.HardcoreConflictInputHandler
import com.nendo.argosy.ui.input.LocalModifiedInputHandler
import com.nendo.argosy.domain.model.SyncProgress
import kotlinx.coroutines.delay
import com.nendo.argosy.ui.ArgosyViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.launch

private const val SCROLL_OFFSET = -25

@Composable
fun HomeScreen(
    isDefaultView: Boolean,
    onGameSelect: (Long) -> Unit,
    onNavigateToLibrary: (platformId: Long?, sourceFilter: String?) -> Unit = { _, _ -> },
    onNavigateToDefault: () -> Unit,
    onDrawerToggle: () -> Unit,
    onChangelogAction: (RequiredAction) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    argosyViewModel: ArgosyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var skipNextProgrammaticScroll by remember { mutableStateOf(false) }
    var suppressVideoPreview by remember { mutableStateOf(false) }
    var videoPlayedForGameId by remember { mutableStateOf<Long?>(null) }
    val swipeThreshold = with(LocalDensity.current) { 50.dp.toPx() }

    val currentOnDrawerToggle by rememberUpdatedState(onDrawerToggle)

    LaunchedEffect(uiState.focusedGameIndex, uiState.currentRow, uiState.currentItems.size) {
        if (uiState.currentItems.isNotEmpty()) {
            if (skipNextProgrammaticScroll) {
                skipNextProgrammaticScroll = false
            } else {
                isProgrammaticScroll = true
                scope.launch {
                    listState.animateScrollToItem(
                        index = uiState.focusedGameIndex.coerceIn(0, uiState.currentItems.lastIndex),
                        scrollOffset = SCROLL_OFFSET
                    )
                    isProgrammaticScroll = false
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                isProgrammaticScroll,
                listState.layoutInfo
            )
        }.collect { (isScrolling, programmatic, layoutInfo) ->
            if (isScrolling && !programmatic) {
                val viewportStart = layoutInfo.viewportStartOffset
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isNotEmpty()) {
                    val firstFullyVisible = visibleItems
                        .filter { it.offset >= viewportStart }
                        .minByOrNull { it.offset }
                    if (firstFullyVisible != null && firstFullyVisible.index != uiState.focusedGameIndex) {
                        skipNextProgrammaticScroll = true
                        viewModel.setFocusIndex(firstFullyVisible.index)
                    }
                }
            }
        }
    }

    BackHandler(enabled = true) {
        // Prevent back from popping Home screen off nav stack
        // Home is the root destination - back should do nothing
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.LaunchGame -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        viewModel.showLaunchError("Failed to launch: ${e.message}")
                    }
                }
                is HomeEvent.NavigateToLibrary -> {
                    onNavigateToLibrary(event.platformId, event.sourceFilter)
                }
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onGameSelect, onDrawerToggle, isDefaultView) {
        viewModel.createInputHandler(
            isDefaultView = isDefaultView,
            onGameSelect = onGameSelect,
            onNavigateToDefault = onNavigateToDefault,
            onDrawerToggle = onDrawerToggle
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
                viewModel.onResume()
                viewModel.refreshPlatforms()
                viewModel.refreshFavorites()
                viewModel.refreshRecentGames()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isTransitioningToGame by argosyViewModel.isTransitioningToGame.collectAsState()
    val returningFromGame by argosyViewModel.returningFromGame.collectAsState()

    val modalBlur by animateDpAsState(
        targetValue = if (uiState.showGameMenu || uiState.syncOverlayState != null || uiState.changelogEntry != null || uiState.discPickerState != null) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    val gameTransitionBlur by animateDpAsState(
        targetValue = if (isTransitioningToGame || returningFromGame) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "gameTransitionBlur"
    )

    LaunchedEffect(returningFromGame) {
        if (returningFromGame) {
            delay(350)
            argosyViewModel.clearReturningFlag()
        }
    }

    val combinedBlur = maxOf(modalBlur, gameTransitionBlur)

    val changelogInputHandler = remember(viewModel) {
        ChangelogInputHandler(
            getEntry = { uiState.changelogEntry },
            onDismiss = { viewModel.dismissChangelog() },
            onAction = { action ->
                onChangelogAction(viewModel.handleChangelogAction(action))
            }
        )
    }

    val discPickerInputHandler = remember(viewModel) {
        DiscPickerInputHandler(
            getDiscs = { uiState.discPickerState?.discs ?: emptyList() },
            getFocusIndex = { uiState.discPickerFocusIndex },
            onFocusChange = { viewModel.setDiscPickerFocusIndex(it) },
            onSelect = { viewModel.selectDisc(it) },
            onDismiss = { viewModel.dismissDiscPicker() }
        )
    }

    var hardcoreConflictFocusIndex by remember { mutableStateOf(0) }
    val hardcoreConflictInputHandler = remember(uiState.syncOverlayState) {
        HardcoreConflictInputHandler(
            getFocusIndex = { hardcoreConflictFocusIndex },
            onFocusChange = { hardcoreConflictFocusIndex = it },
            onKeepHardcore = { uiState.syncOverlayState?.onKeepHardcore?.invoke() },
            onDowngradeToCasual = { uiState.syncOverlayState?.onDowngradeToCasual?.invoke() },
            onKeepLocal = { uiState.syncOverlayState?.onKeepLocal?.invoke() }
        )
    }

    var localModifiedFocusIndex by remember { mutableStateOf(0) }
    val localModifiedInputHandler = remember(uiState.syncOverlayState) {
        LocalModifiedInputHandler(
            getFocusIndex = { localModifiedFocusIndex },
            onFocusChange = { localModifiedFocusIndex = it },
            onKeepLocal = { uiState.syncOverlayState?.onKeepLocalModified?.invoke() },
            onRestoreSelected = { uiState.syncOverlayState?.onRestoreSelected?.invoke() }
        )
    }

    val isHardcoreConflict = uiState.syncOverlayState?.syncProgress is SyncProgress.HardcoreConflict
    val isLocalModified = uiState.syncOverlayState?.syncProgress is SyncProgress.LocalModified

    LaunchedEffect(isHardcoreConflict) {
        if (isHardcoreConflict) {
            hardcoreConflictFocusIndex = 0
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

    LaunchedEffect(uiState.changelogEntry) {
        if (uiState.changelogEntry != null) {
            inputDispatcher.pushModal(changelogInputHandler)
        }
    }

    LaunchedEffect(uiState.discPickerState) {
        if (uiState.discPickerState != null) {
            viewModel.setDiscPickerFocusIndex(0)
            inputDispatcher.pushModal(discPickerInputHandler)
        }
    }

    DisposableEffect(uiState.discPickerState) {
        onDispose {
            if (uiState.discPickerState != null) {
                inputDispatcher.popModal()
            }
        }
    }

    DisposableEffect(uiState.changelogEntry) {
        onDispose {
            if (uiState.changelogEntry != null) {
                inputDispatcher.popModal()
            }
        }
    }

    LaunchedEffect(uiState.focusedGame?.id) {
        val currentGameId = uiState.focusedGame?.id
        if (currentGameId != videoPlayedForGameId) {
            videoPlayedForGameId = null
            suppressVideoPreview = false
        }
    }

    LaunchedEffect(uiState.focusedGameIndex, uiState.focusedGame?.youtubeVideoId, uiState.videoWallpaperEnabled) {
        viewModel.deactivateVideoPreview()
        if (!uiState.videoWallpaperEnabled) return@LaunchedEffect
        val game = uiState.focusedGame ?: return@LaunchedEffect
        val videoId = game.youtubeVideoId ?: return@LaunchedEffect
        val shouldSkip = uiState.showGameMenu ||
            uiState.discPickerState != null ||
            suppressVideoPreview ||
            videoPlayedForGameId == game.id
        if (shouldSkip) {
            return@LaunchedEffect
        }
        delay(uiState.videoWallpaperDelayMs)
        val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val stillValid = isResumed &&
            uiState.videoWallpaperEnabled &&
            !suppressVideoPreview &&
            uiState.discPickerState == null &&
            videoPlayedForGameId != game.id
        if (stillValid) {
            videoPlayedForGameId = game.id
            viewModel.startVideoPreviewLoading(videoId)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.deactivateVideoPreview()
                    videoPlayedForGameId = uiState.focusedGame?.id
                }
                Lifecycle.Event.ON_RESUME -> {
                    suppressVideoPreview = true
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (uiState.isVideoPreviewActive) 0f else 1f,
        animationSpec = tween(500),
        label = "backgroundAlpha"
    )

    val videoModeFooterOffset by animateDpAsState(
        targetValue = if (uiState.isVideoPreviewActive) 48.dp else 0.dp,
        animationSpec = tween(500),
        label = "footerOffset"
    )

    val videoModeHeaderOffset by animateDpAsState(
        targetValue = if (uiState.isVideoPreviewActive) (-60).dp else 0.dp,
        animationSpec = tween(500),
        label = "headerOffset"
    )

    val videoModeRailOffsetX by animateDpAsState(
        targetValue = if (uiState.isVideoPreviewActive) (-40).dp else 0.dp,
        animationSpec = tween(500),
        label = "railOffsetX"
    )

    val backgroundBlurDp = (uiState.backgroundBlur * 0.5f).dp
    val saturationFraction = uiState.backgroundSaturation / 100f
    val opacityFraction = uiState.backgroundOpacity / 100f
    val overlayAlphaTop = 0.3f + (1f - opacityFraction) * 0.4f
    val overlayAlphaBottom = 0.7f + (1f - opacityFraction) * 0.3f

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayBaseColor = if (isDarkTheme) Color.Black else Color.White

    val effectiveBackgroundPath = if (uiState.useGameBackground) {
        uiState.focusedGame?.let { game ->
            when {
                game.backgroundPath?.startsWith("/") == true -> game.backgroundPath
                game.coverPath?.startsWith("/") == true -> game.coverPath
                else -> game.backgroundPath ?: game.coverPath
            }
        }
    } else {
        uiState.customBackgroundPath
    }

    AnimatedContent(
        targetState = uiState.isLoading,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        },
        label = "loading"
    ) { isLoading ->
        if (isLoading) {
            SplashOverlay()
        } else {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().blur(combinedBlur)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backgroundAlpha }
            ) {
                AnimatedContent(
                    targetState = effectiveBackgroundPath,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "background"
                ) { backgroundPath ->
                    if (backgroundPath != null) {
                        val imageData = if (backgroundPath.startsWith("/")) {
                            java.io.File(backgroundPath)
                        } else {
                            backgroundPath
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageData)
                                .size(640, 360)
                                .crossfade(300)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix().apply {
                                    setToSaturation(saturationFraction)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(backgroundBlurDp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        overlayBaseColor.copy(alpha = overlayAlphaTop),
                                        overlayBaseColor.copy(alpha = overlayAlphaBottom)
                                    )
                                )
                            )
                    )
                }
            }

            if (uiState.isVideoPreviewLoading || uiState.isVideoPreviewActive) {
                val videoAlpha by animateFloatAsState(
                    targetValue = if (uiState.isVideoPreviewActive) 1f else 0f,
                    animationSpec = tween(500),
                    label = "videoAlpha"
                )
                uiState.videoPreviewId?.let { videoId ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = videoAlpha }
                    ) {
                        YouTubeVideoPlayer(
                            videoId = videoId,
                            muted = uiState.muteVideoPreview,
                            onReady = { viewModel.activateVideoPreview() },
                            onError = { viewModel.cancelVideoPreviewLoading() }
                        )
                    }
                }
            }

        val edgeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

        val swipeGestureModifier = Modifier.pointerInput(Unit) {
            var totalDragX = 0f
            var totalDragY = 0f
            var startX = 0f
            detectDragGestures(
                onDragStart = { offset ->
                    totalDragX = 0f
                    totalDragY = 0f
                    startX = offset.x
                },
                onDragEnd = {
                    when {
                        startX < edgeThreshold && totalDragX > swipeThreshold -> currentOnDrawerToggle()
                        totalDragY < -swipeThreshold && abs(totalDragY) > abs(totalDragX) -> viewModel.nextRow()
                        totalDragY > swipeThreshold && abs(totalDragY) > abs(totalDragX) -> viewModel.previousRow()
                    }
                },
                onDrag = { _, dragAmount ->
                    totalDragX += dragAmount.x
                    totalDragY += dragAmount.y
                }
            )
        }

        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val boxArtStyle = LocalBoxArtStyle.current
        val carouselScale = 0.9f
        val cardWidth = screenWidth * 0.16f * carouselScale
        val cardHeight = cardWidth / boxArtStyle.aspectRatio
        val focusScale = 1.8f
        val railHeight = cardHeight * focusScale + 16.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                HomeHeader(
                    sectionTitle = uiState.rowTitle,
                    showPlatformNav = false,
                    headerOffset = videoModeHeaderOffset
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(swipeGestureModifier)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(x = videoModeRailOffsetX, y = videoModeFooterOffset)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(railHeight)
                ) {
                    when {
                        uiState.isLoading -> {
                            LoadingState()
                        }
                        uiState.currentItems.isEmpty() -> {
                            val pinId = when (val row = uiState.currentRow) {
                                is HomeRow.PinnedRegular -> row.pinId
                                is HomeRow.PinnedVirtual -> row.pinId
                                else -> null
                            }
                            EmptyState(
                                isRommConfigured = uiState.isRommConfigured,
                                currentRow = uiState.currentRow,
                                isPinnedLoading = pinId != null && pinId in uiState.pinnedGamesLoading,
                                onSync = { viewModel.syncFromRomm() }
                            )
                        }
                        else -> {
                            GameRail(
                                items = uiState.currentItems,
                                focusedIndex = uiState.focusedGameIndex,
                                listState = listState,
                                rowKey = uiState.currentRow.toString(),
                                downloadIndicatorFor = uiState::downloadIndicatorFor,
                                showPlatformBadge = uiState.currentRow !is HomeRow.Platform && uiState.currentRow != HomeRow.Steam && uiState.currentRow != HomeRow.Android,
                                repairedCoverPaths = uiState.repairedCoverPaths,
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onItemTap = { index -> viewModel.handleItemTap(index, onGameSelect) },
                                onItemLongPress = viewModel::handleItemLongPress,
                                isVideoPreviewActive = uiState.isVideoPreviewActive,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }
                }

                val focusedGame = uiState.focusedGame
                if (focusedGame != null && !uiState.showGameMenu) {
                    SubtleFooterBar(
                        hints = listOf(
                            InputButton.DPAD_HORIZONTAL to "Game",
                            InputButton.DPAD_VERTICAL to "Platform",
                            InputButton.A to when {
                                focusedGame.needsInstall -> "Install"
                                focusedGame.isDownloaded -> "Play"
                                else -> "Download"
                            },
                            InputButton.Y to if (focusedGame.isFavorite) "Unfavorite" else "Favorite",
                            InputButton.X to "Details"
                        ),
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> {
                                    when {
                                        focusedGame.needsInstall -> viewModel.installApk(focusedGame.id)
                                        focusedGame.isDownloaded -> viewModel.launchGame(focusedGame.id)
                                        else -> viewModel.queueDownload(focusedGame.id)
                                    }
                                }
                                InputButton.Y -> viewModel.toggleFavorite(focusedGame.id)
                                InputButton.X -> onGameSelect(focusedGame.id)
                                else -> {}
                            }
                        },
                        modifier = Modifier.padding(top = Dimens.spacingSm)
                    )
                } else {
                    Spacer(modifier = Modifier.height(Dimens.spacingXl))
                }
            }

            val gameInfoWidth by animateFloatAsState(
                targetValue = if (uiState.isVideoPreviewActive) 1f else 0.7f,
                animationSpec = tween(500),
                label = "gameInfoWidth"
            )
            val gameInfoTopPadding by animateDpAsState(
                targetValue = if (uiState.isVideoPreviewActive) Dimens.spacingMd else Dimens.headerHeight,
                animationSpec = tween(500),
                label = "gameInfoTopPadding"
            )
            val videoTitleBackgroundOffset by animateDpAsState(
                targetValue = if (uiState.isVideoPreviewActive) 0.dp else (-72).dp,
                animationSpec = tween(500),
                label = "videoTitleBackgroundOffset"
            )
            val videoTextColor by animateColorAsState(
                targetValue = if (uiState.isVideoPreviewActive) Color.White else Color.Unspecified,
                animationSpec = tween(500),
                label = "videoTextColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(y = videoTitleBackgroundOffset)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
                    .height(Dimens.headerHeight)
            )

            GameInfo(
                title = uiState.focusedGame?.title ?: "",
                developer = uiState.focusedGame?.developer,
                rating = uiState.focusedGame?.rating,
                userRating = uiState.focusedGame?.userRating ?: 0,
                userDifficulty = uiState.focusedGame?.userDifficulty ?: 0,
                achievementCount = uiState.focusedGame?.achievementCount ?: 0,
                earnedAchievementCount = uiState.focusedGame?.earnedAchievementCount ?: 0,
                showMetadata = !uiState.isVideoPreviewActive,
                textColorOverride = if (videoTextColor != Color.Unspecified) videoTextColor else null,
                modifier = Modifier
                    .fillMaxWidth(gameInfoWidth)
                    .align(if (uiState.isVideoPreviewActive) Alignment.TopCenter else Alignment.TopEnd)
                    .padding(top = gameInfoTopPadding)
            )
        }
        }

        AnimatedVisibility(
            visible = uiState.showGameMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val focusedGame = uiState.focusedGame
            if (focusedGame != null) {
                GameSelectOverlay(
                    game = focusedGame,
                    focusIndex = uiState.gameMenuFocusIndex,
                    onDismiss = { viewModel.toggleGameMenu() },
                    onPrimaryAction = {
                        viewModel.toggleGameMenu()
                        when {
                            focusedGame.needsInstall -> viewModel.installApk(focusedGame.id)
                            focusedGame.isDownloaded -> viewModel.launchGame(focusedGame.id)
                            else -> viewModel.queueDownload(focusedGame.id)
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(focusedGame.id) },
                    onDetails = {
                        viewModel.toggleGameMenu()
                        onGameSelect(focusedGame.id)
                    },
                    onAddToCollection = {
                        viewModel.toggleGameMenu()
                        viewModel.showAddToCollectionModal(focusedGame.id)
                    },
                    onRefresh = { viewModel.refreshGameData(focusedGame.id) },
                    onDelete = {
                        viewModel.toggleGameMenu()
                        viewModel.deleteLocalFile(focusedGame.id)
                    },
                    onRemoveFromHome = {
                        viewModel.toggleGameMenu()
                        viewModel.removeFromHome(focusedGame.id)
                    },
                    onHide = {
                        viewModel.toggleGameMenu()
                        viewModel.hideGame(focusedGame.id)
                    }
                )
            }
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
                }
            )
        }

        SyncOverlay(
            syncProgress = uiState.syncOverlayState?.syncProgress,
            gameTitle = uiState.syncOverlayState?.gameTitle,
            onGrantPermission = uiState.syncOverlayState?.onGrantPermission,
            onDisableSync = uiState.syncOverlayState?.onDisableSync,
            onOpenSettings = uiState.syncOverlayState?.onOpenSettings,
            onSkip = uiState.syncOverlayState?.onSkip,
            onKeepHardcore = uiState.syncOverlayState?.onKeepHardcore,
            onDowngradeToCasual = uiState.syncOverlayState?.onDowngradeToCasual,
            onKeepLocal = uiState.syncOverlayState?.onKeepLocal,
            onKeepLocalModified = uiState.syncOverlayState?.onKeepLocalModified,
            onRestoreSelected = uiState.syncOverlayState?.onRestoreSelected,
            hardcoreConflictFocusIndex = hardcoreConflictFocusIndex,
            localModifiedFocusIndex = localModifiedFocusIndex
        )

        uiState.discPickerState?.let { pickerState ->
            DiscPickerModal(
                discs = pickerState.discs,
                focusIndex = uiState.discPickerFocusIndex,
                onSelectDisc = viewModel::selectDisc,
                onDismiss = viewModel::dismissDiscPicker
            )
        }

        uiState.changelogEntry?.let { entry ->
            ChangelogModal(
                entry = entry,
                onDismiss = { viewModel.dismissChangelog() },
                onAction = { action ->
                    onChangelogAction(viewModel.handleChangelogAction(action))
                }
            )
        }
    }
        }
    }
}

@Composable
private fun SplashOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            Text(
                text = "ARGOSY",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 8.sp
            )
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconLg),
                color = MaterialTheme.colorScheme.onBackground,
                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun HomeHeader(
    sectionTitle: String,
    showPlatformNav: Boolean,
    headerOffset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val aspectRatioClass = com.nendo.argosy.ui.theme.LocalUiScale.current.aspectRatioClass
    val maxTitleLength = when (aspectRatioClass) {
        com.nendo.argosy.ui.theme.AspectRatioClass.ULTRA_TALL -> 12
        com.nendo.argosy.ui.theme.AspectRatioClass.TALL -> 16
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg)
            .offset(y = headerOffset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
        ) {
            AnimatedContent(
                targetState = sectionTitle,
                transitionSpec = {
                    (slideInVertically { -it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { it / 2 } + fadeOut(tween(150)))
                },
                label = "section"
            ) { title ->
                val displayTitle = if (maxTitleLength != null && title.length > maxTitleLength) {
                    title.take(maxTitleLength - 1) + "…"
                } else {
                    title
                }
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (showPlatformNav) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Icon(
                        painter = InputIcons.BumperLeft,
                        contentDescription = "Previous platform",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                    Icon(
                        painter = InputIcons.BumperRight,
                        contentDescription = "Next platform",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                }
            }
        }

        SystemStatusBar()
    }
}

@Composable
private fun GameInfo(
    title: String,
    developer: String?,
    rating: Float?,
    userRating: Int,
    userDifficulty: Int,
    achievementCount: Int,
    earnedAchievementCount: Int,
    showMetadata: Boolean = true,
    textColorOverride: Color? = null,
    modifier: Modifier = Modifier
) {
    val metadataAlpha by animateFloatAsState(
        targetValue = if (showMetadata) 1f else 0f,
        animationSpec = tween(500),
        label = "metadataAlpha"
    )

    val titleColor = textColorOverride ?: MaterialTheme.colorScheme.onSurface
    val subtitleColor = textColorOverride?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .padding(horizontal = Dimens.spacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GameTitle(
            title = title,
            titleStyle = MaterialTheme.typography.headlineMedium,
            titleColor = titleColor,
            textAlign = TextAlign.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        )

        if (developer != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = developer,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                modifier = Modifier.graphicsLayer { alpha = metadataAlpha }
            )
        }

        val hasBadges = rating != null || userRating > 0 || userDifficulty > 0 || achievementCount > 0
        if (hasBadges) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier.graphicsLayer { alpha = metadataAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
            ) {
                if (rating != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = textColorOverride ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimens.iconXs)
                        )
                        Text(
                            text = "${rating.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = subtitleColor
                        )
                    }
                }
                if (userRating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = textColorOverride ?: Color(0xFFFFD700),
                            modifier = Modifier.size(Dimens.iconXs)
                        )
                        Text(
                            text = "$userRating/10",
                            style = MaterialTheme.typography.labelMedium,
                            color = subtitleColor
                        )
                    }
                }
                if (userDifficulty > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = textColorOverride ?: Color(0xFFE53935),
                            modifier = Modifier.size(Dimens.iconXs)
                        )
                        Text(
                            text = "$userDifficulty/10",
                            style = MaterialTheme.typography.labelMedium,
                            color = subtitleColor
                        )
                    }
                }
                if (achievementCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = textColorOverride ?: Color(0xFFFFB300),
                            modifier = Modifier.size(Dimens.iconXs)
                        )
                        Text(
                            text = "$earnedAchievementCount/$achievementCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = subtitleColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameRail(
    items: List<HomeRowItem>,
    focusedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    rowKey: String,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator,
    showPlatformBadge: Boolean,
    repairedCoverPaths: Map<Long, String> = emptyMap(),
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, String) -> Unit)? = null,
    onItemTap: (Int) -> Unit = {},
    onItemLongPress: (Int) -> Unit = {},
    isVideoPreviewActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val boxArtStyle = LocalBoxArtStyle.current

    val carouselScale = 0.9f
    val cardWidth = screenWidth * 0.16f * carouselScale
    val cardHeight = cardWidth / boxArtStyle.aspectRatio
    val focusScale = 1.8f
    val railHeight = cardHeight * focusScale + 16.dp

    val focusSpacingPx = with(LocalDensity.current) { (cardWidth * 0.5f).toPx() }
    val itemSpacing = cardWidth * 0.13f
    val startPadding = screenWidth * 0.09f
    val endPadding = screenWidth * 0.65f

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .height(railHeight)
            .graphicsLayer { clip = false }
    ) {
        itemsIndexed(
            items,
            key = { _, item ->
                when (item) {
                    is HomeRowItem.Game -> "$rowKey-${item.game.id}"
                    is HomeRowItem.ViewAll -> "$rowKey-viewall-${item.platformId ?: item.sourceFilter ?: "all"}"
                }
            }
        ) { index, item ->
            val isFocused = index == focusedIndex
            val translationX by animateFloatAsState(
                targetValue = when {
                    index < focusedIndex -> -focusSpacingPx
                    index > focusedIndex -> focusSpacingPx
                    else -> 0f
                },
                animationSpec = Motion.focusSpring,
                label = "translationX"
            )

            val videoScaleOverride = if (isVideoPreviewActive && isFocused) 1.0f else null
            val videoAlphaOverride = if (isVideoPreviewActive && !isFocused) 0f else null

            when (item) {
                is HomeRowItem.Game -> {
                    GameCardWithNewBadge(
                        game = item.game,
                        isFocused = isFocused,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        focusScale = focusScale,
                        scaleFromBottom = true,
                        downloadIndicator = downloadIndicatorFor(item.game.id),
                        showPlatformBadge = showPlatformBadge,
                        coverPathOverride = repairedCoverPaths[item.game.id],
                        onCoverLoadFailed = onCoverLoadFailed,
                        onCoverLoaded = onCoverLoaded,
                        scaleOverride = videoScaleOverride,
                        alphaOverride = videoAlphaOverride,
                        modifier = Modifier
                            .graphicsLayer {
                                this.translationX = translationX
                            }
                            .zIndex(if (isFocused) 1f else 0f)
                            .combinedClickable(
                                onClick = { onItemTap(index) },
                                onLongClick = { onItemLongPress(index) },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    )
                }
                is HomeRowItem.ViewAll -> {
                    val viewAllAlpha by animateFloatAsState(
                        targetValue = if (isVideoPreviewActive) 0f else 1f,
                        animationSpec = Motion.focusSpring,
                        label = "viewAllAlpha"
                    )
                    ViewAllCard(
                        isFocused = isFocused,
                        onClick = { onItemTap(index) },
                        modifier = Modifier
                            .graphicsLayer {
                                this.translationX = translationX
                                alpha = viewAllAlpha
                            }
                            .width(cardWidth)
                            .height(cardHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewAllCard(
    isFocused: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.8f else 1f,
        animationSpec = spring(stiffness = 300f),
        label = "viewAllScale"
    )

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) onSurfaceColor else onSurfaceColor.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "viewAllBorder"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        onSurfaceColor.copy(alpha = 0.15f),
                        onSurfaceColor.copy(alpha = 0.05f)
                    )
                ),
                RoundedCornerShape(Dimens.radiusMd)
            )
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(Dimens.radiusLg)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.padding(bottom = Dimens.radiusLg)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                    GridBox()
                    GridBox()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                    GridBox()
                    GridBox()
                }
            }
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GridBox() {
    Box(
        modifier = Modifier
            .size(Dimens.iconMd)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                RoundedCornerShape(Dimens.radiusSm)
            )
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.gameCardHeight),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconXl),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun EmptyState(
    isRommConfigured: Boolean,
    currentRow: HomeRow,
    isPinnedLoading: Boolean,
    onSync: () -> Unit
) {
    val isPinnedRow = currentRow is HomeRow.PinnedRegular || currentRow is HomeRow.PinnedVirtual
    val collectionName = when (currentRow) {
        is HomeRow.PinnedRegular -> currentRow.name
        is HomeRow.PinnedVirtual -> currentRow.name
        else -> ""
    }

    when {
        isPinnedRow && isPinnedLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spacingXxl),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconLg),
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            }
        }
        isPinnedRow -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spacingXxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No games in $collectionName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spacingXxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No games yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = if (isRommConfigured) {
                        "Sync your library to get started"
                    } else {
                        "Connect to a Rom Manager server in Settings to get started"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (isRommConfigured) {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    FooterHint(button = InputButton.A, action = "Sync Library")
                }
            }
        }
    }
}

@Composable
private fun GameSelectOverlay(
    game: HomeGameUi,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onAddToCollection: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onHide: () -> Unit
) {
    var currentIndex = 0
    val playIdx = currentIndex++
    val favoriteIdx = currentIndex++
    val detailsIdx = currentIndex++
    val addToCollectionIdx = currentIndex++
    val refreshIdx = if (game.isRommGame || game.isAndroidApp) currentIndex++ else -1
    val deleteIdx = if (game.isDownloaded || game.needsInstall) currentIndex++ else -1
    val removeFromHomeIdx = if (game.isAndroidApp) currentIndex++ else -1
    val hideIdx = currentIndex

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val primaryIcon = when {
        game.needsInstall -> Icons.Default.InstallMobile
        game.isDownloaded -> Icons.Default.PlayArrow
        else -> Icons.Default.Download
    }
    val primaryLabel = when {
        game.needsInstall -> "Install"
        game.isDownloaded -> "Play"
        else -> "Download"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLg))
                .padding(Dimens.spacingLg)
                .width(Dimens.modalWidth)
        ) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            MenuOption(
                icon = primaryIcon,
                label = primaryLabel,
                isFocused = focusIndex == playIdx,
                onClick = onPrimaryAction
            )
            MenuOption(
                icon = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (game.isFavorite) "Unfavorite" else "Favorite",
                isFocused = focusIndex == favoriteIdx,
                onClick = onFavorite
            )
            MenuOption(
                icon = Icons.Default.Info,
                label = "Details",
                isFocused = focusIndex == detailsIdx,
                onClick = onDetails
            )
            MenuOption(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add to Collection",
                isFocused = focusIndex == addToCollectionIdx,
                onClick = onAddToCollection
            )
            if (game.isRommGame || game.isAndroidApp) {
                MenuOption(
                    icon = Icons.Default.Refresh,
                    label = "Refresh Data",
                    isFocused = focusIndex == refreshIdx,
                    onClick = onRefresh
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (game.isDownloaded || game.needsInstall) {
                MenuOption(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete Download",
                    isFocused = focusIndex == deleteIdx,
                    isDangerous = true,
                    onClick = onDelete
                )
            }
            if (game.isAndroidApp) {
                MenuOption(
                    icon = Icons.Default.Home,
                    label = "Remove from Home",
                    isFocused = focusIndex == removeFromHomeIdx,
                    isDangerous = true,
                    onClick = onRemoveFromHome
                )
            }
            MenuOption(
                label = "Hide",
                isFocused = focusIndex == hideIdx,
                isDangerous = true,
                onClick = onHide
            )
        }
    }
}

@Composable
private fun MenuOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.onErrorContainer
        isDangerous -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.errorContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoFocus(onClick = onClick)
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm + Dimens.borderMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}
