package com.nendo.argosy.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
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
import com.nendo.argosy.ui.common.AlwaysCrossfadeFactory
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.SectionBreadcrumb
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.LocalSurfaceBackdrop
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
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
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.AddToCollectionModal
import com.nendo.argosy.ui.components.ChangelogModal
import com.nendo.argosy.ui.components.CollectionItem
import com.nendo.argosy.ui.components.FooterHint
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.components.CarouselAnchor
import com.nendo.argosy.ui.components.CarouselItem
import com.nendo.argosy.ui.components.CarouselMetrics
import com.nendo.argosy.ui.components.CarouselOverrides
import com.nendo.argosy.ui.components.CarouselRail
import com.nendo.argosy.ui.components.HomeAutoGrid
import com.nendo.argosy.ui.components.HomeCustomGridPage
import com.nendo.argosy.ui.components.HomeTilePickerModal
import com.nendo.argosy.ui.components.TileEditMode
import androidx.compose.foundation.layout.ColumnScope
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeRowAlignment
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.nendo.argosy.ui.components.HERO_MIN_CARD_SCALE
import com.nendo.argosy.ui.components.carouselCardSize
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.FooterVariant
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.MemcardPickerModal
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.components.YouTubeVideoPlayer
import com.nendo.argosy.ui.input.ChangelogInputHandler
import com.nendo.argosy.ui.input.DiscPickerInputHandler
import com.nendo.argosy.ui.input.HardcoreConflictInputHandler
import com.nendo.argosy.ui.input.LocalModifiedInputHandler
import com.nendo.argosy.ui.screens.media.components.MediaSignedOutState
import com.nendo.argosy.ui.screens.media.modals.MediaResumeModalHost
import com.nendo.argosy.domain.model.SyncProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    isDefaultView: Boolean,
    onGameSelect: (Long) -> Unit,
    onNavigateToLibrary: (platformId: Long?, sourceFilter: String?) -> Unit = { _, _ -> },
    onNavigateToCollections: () -> Unit = {},
    onNavigateToDefault: () -> Unit,
    onDrawerToggle: () -> Unit,
    onChangelogAction: (RequiredAction) -> Unit = {},
    onPlayMedia: (itemId: String, startOver: Boolean) -> Unit = { _, _ -> },
    onMediaSelect: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isAutoGrid = uiState.layoutKind == HomeLayoutKind.AUTO_GRID
    val isCustomGrid = uiState.layoutKind == HomeLayoutKind.CUSTOM_GRID
    val scope = rememberCoroutineScope()
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var skipNextProgrammaticScroll by remember { mutableStateOf(false) }
    var suppressVideoPreview by remember { mutableStateOf(false) }
    var videoPlayedForGameId by remember { mutableStateOf<Long?>(null) }
    val swipeThreshold = with(LocalDensity.current) { 50.dp.toPx() }

    val currentOnDrawerToggle by rememberUpdatedState(onDrawerToggle)

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(uiState.focusedGameIndex, uiState.currentRow, uiState.currentItems.size) }
            .collectLatest { (focusedIndex, _, itemsSize) ->
                if (itemsSize > 0) {
                    if (skipNextProgrammaticScroll) {
                        skipNextProgrammaticScroll = false
                    } else {
                        isProgrammaticScroll = true
                        listState.animateScrollToItem(
                            index = focusedIndex.coerceIn(0, itemsSize - 1),
                            scrollOffset = CarouselAnchor.START.snapOffsetPx
                        )
                        snapshotFlow { listState.isScrollInProgress }.first { !it }
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
                    val landedOn = if (
                        uiState.carouselConfig.focusPosition == HomeFocusPosition.CENTER
                    ) {
                        val centre = (viewportStart + layoutInfo.viewportEndOffset) / 2
                        visibleItems.minByOrNull {
                            kotlin.math.abs(it.offset + it.size / 2 - centre)
                        }
                    } else {
                        visibleItems.filter { it.offset >= viewportStart }.minByOrNull { it.offset }
                    }
                    if (landedOn != null && landedOn.index != uiState.focusedGameIndex) {
                        skipNextProgrammaticScroll = true
                        viewModel.setFocusIndex(landedOn.index)
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
                is HomeEvent.LaunchIntent -> {
                    try {
                        context.startActivity(event.intent, event.options)
                    } catch (_: Exception) { }
                }
                is HomeEvent.NavigateToLibrary -> {
                    onNavigateToLibrary(event.platformId, event.sourceFilter)
                }
                is HomeEvent.NavigateToCollections -> onNavigateToCollections()
                is HomeEvent.PlayMedia -> onPlayMedia(event.itemId, event.startOver)
                is HomeEvent.NavigateToMediaDetail -> onMediaSelect(event.itemId)
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

    val modalBlur by animateDpAsState(
        targetValue = if (uiState.showGameMenu || uiState.syncOverlayState != null || uiState.changelogEntry != null || uiState.discPickerState != null || uiState.memcardPickerState != null) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    val combinedBlur = modalBlur

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

    val memcardPickerInputHandler = remember(viewModel) {
        com.nendo.argosy.ui.input.MemcardPickerInputHandler(
            getCards = { uiState.memcardPickerState?.cards ?: emptyList() },
            getFocusIndex = { uiState.memcardPickerFocusIndex },
            onFocusChange = { viewModel.setMemcardPickerFocusIndex(it) },
            onSelect = { viewModel.selectMemcard(it) },
            onDismiss = { viewModel.dismissMemcardPicker() }
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
                inputDispatcher.removeModal(hardcoreConflictInputHandler)
            }
        }
    }

    DisposableEffect(isLocalModified) {
        onDispose {
            if (isLocalModified) {
                inputDispatcher.removeModal(localModifiedInputHandler)
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
                inputDispatcher.removeModal(discPickerInputHandler)
            }
        }
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
                inputDispatcher.removeModal(memcardPickerInputHandler)
            }
        }
    }

    DisposableEffect(uiState.changelogEntry) {
        onDispose {
            if (uiState.changelogEntry != null) {
                inputDispatcher.removeModal(changelogInputHandler)
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
        if (uiState.layoutKind != HomeLayoutKind.CAROUSEL) return@LaunchedEffect
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

    val saturationMatrix = remember(saturationFraction) {
        androidx.compose.ui.graphics.ColorMatrix().apply {
            setToSaturation(saturationFraction)
        }
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayBaseColor = if (isDarkTheme) Color.Black else Color.White

    val backdropEnabled = LocalSurfaceBackdrop.current.enabled
    val isGridLayout = uiState.layoutKind != HomeLayoutKind.CAROUSEL
    val showArtLayer = !isGridLayout &&
        (!backdropEnabled || uiState.homeBackgroundMode == HomeBackgroundMode.GAME_ART)

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
        Box(modifier = Modifier.fillMaxSize()) {
            if (backdropEnabled) {
                Box(modifier = Modifier.fillMaxSize().surfaceBackdrop(BackdropRole.CONTENT))
            }
            if (showArtLayer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = backgroundAlpha }
                ) {
                    if (effectiveBackgroundPath != null) {
                        val backgroundContext = LocalContext.current
                        val backgroundModel = rememberFileImageModel(effectiveBackgroundPath)
                        val backgroundRequest = remember(backgroundModel) {
                            ImageRequest.Builder(backgroundContext)
                                .data(backgroundModel)
                                .size(640, 360)
                                .transitionFactory(AlwaysCrossfadeFactory(380))
                                .build()
                        }
                        AsyncImage(
                            model = backgroundRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(saturationMatrix),
                            modifier = Modifier
                                .fillMaxSize()
                                .let {
                                    val totalBlur = backgroundBlurDp + combinedBlur
                                    if (totalBlur > 0.dp) it.blur(totalBlur) else it
                                }
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

        val edgeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }

        val swipeGestureModifier = Modifier
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        when {
                            totalDragY < -swipeThreshold -> viewModel.nextRow()
                            totalDragY > swipeThreshold -> viewModel.previousRow()
                        }
                    },
                    onVerticalDrag = { _, dragAmount -> totalDragY += dragAmount }
                )
            }
            .pointerInput(Unit) {
                var totalDragX = 0f
                var startX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        totalDragX = 0f
                        startX = offset.x
                    },
                    onDragEnd = {
                        if (startX < edgeThresholdPx && totalDragX > swipeThreshold) {
                            currentOnDrawerToggle()
                        }
                    },
                    onHorizontalDrag = { _, dragAmount -> totalDragX += dragAmount }
                )
            }

        var gameInfoHeight by remember { mutableStateOf(0.dp) }
        val defaultHeaderHeight = Dimens.headerHeight
        var headerBlockHeight by remember { mutableStateOf(defaultHeaderHeight) }
        val localDensity = LocalDensity.current

        BoxWithConstraints(modifier = Modifier
            .fillMaxSize()
            .then(swipeGestureModifier)
        ) {
            /**
             * A media row draws no info panel, so the space one would have taken is the row's to
             * use. Reading the last measured height there would reserve room for a panel that is
             * not on screen and shrink the rail for no reason.
             */
            val infoHeight = if (uiState.isMediaRow) 0.dp else gameInfoHeight
            val cardSize = rememberCarouselCardSize(
                availableHeight = maxHeight - headerBlockHeight - infoHeight -
                    Dimens.footerHeight - Dimens.spacingLg - Dimens.spacingXl,
                config = uiState.carouselConfig
            )
            val infoAtBottom = uiState.carouselConfig.rowAlignment == HomeRowAlignment.TOP
            val railHeight = when {
                isAutoGrid || isCustomGrid ->
                    (maxHeight - headerBlockHeight - Dimens.footerHeight - Dimens.spacingLg)
                        .coerceAtLeast(Dimens.spacingXl)
                infoAtBottom ->
                    (
                        maxHeight - headerBlockHeight - infoHeight -
                            Dimens.footerHeight - Dimens.spacingLg
                        ).coerceAtLeast(Dimens.spacingXl)
                else -> cardSize.height * uiState.carouselConfig.focusScale + Dimens.spacingMd
            }
            val isPortrait = maxWidth <= maxHeight
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { size ->
                        val measured = with(localDensity) { size.height.toDp() }
                        if (measured != headerBlockHeight) headerBlockHeight = measured
                    }
            ) {
                HomeHeader(
                    uiState = uiState,
                    onPreviousRow = viewModel::previousRow,
                    onNextRow = viewModel::nextRow,
                    onSelectRow = viewModel::selectRow,
                    isStacked = isPortrait,
                    headerOffset = videoModeHeaderOffset,
                    showSections = !isCustomGrid,
                    compact = isAutoGrid && !uiState.autoGridConfig.showTitles
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset(x = videoModeRailOffsetX, y = videoModeFooterOffset)
                    .padding(bottom = Dimens.spacingLg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = if (infoAtBottom && !isAutoGrid && !isCustomGrid) {
                                infoHeight
                            } else {
                                0.dp
                            }
                        )
                        .height(railHeight)
                ) {
                    when {
                        uiState.isLoading -> {
                            LoadingState()
                        }
                        isCustomGrid -> {
                            com.nendo.argosy.ui.components.CustomGridSurface(
                                state = uiState.customGrid,
                                contentFor = { tile -> uiState.tileContentFor(tile) },
                                laneCount = uiState.customGridConfig.laneCount,
                                onCellTap = { cell ->
                                    val grid = uiState.customGrid
                                    val onFocused = grid.tileAt(cell)
                                        ?.let { it == grid.focusedTile }
                                        ?: (cell == grid.cell)
                                    when {
                                        grid.isEditing -> viewModel.moveEditingTileTo(cell)
                                        onFocused -> inputHandler.onConfirm()
                                        else -> viewModel.setCustomGridCell(cell)
                                    }
                                },
                                onSwipePage = { delta -> viewModel.turnCustomGridPage(delta) },
                                onTileDrag = { cell -> viewModel.moveEditingTileTo(cell) },
                                onTileResize = { cell -> viewModel.resizeEditingTileTo(cell) },
                                onToggleEditMode = { viewModel.toggleTileEditMode() },
                                onCommitEdit = { viewModel.commitTileEdit() },
                                showEmptySlots = uiState.customGridConfig.showEmptySlots,
                                onShapeResolved = { columns, rows ->
                                    viewModel.setCustomGridShape(columns, rows)
                                },
                                onAddPage = { viewModel.confirmAddPage() },
                                onTileLongPress = { cell ->
                                    viewModel.setCustomGridCell(cell)
                                    viewModel.openTileMenu()
                                },
                                downloadIndicatorFor = { uiState.downloadIndicatorFor(it) },
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        uiState.isMediaRow && !uiState.isMediaSignedIn -> {
                            MediaSignedOutState()
                        }
                        uiState.isMediaRow && uiState.isMediaRowLoading &&
                            uiState.currentItems.isEmpty() -> {
                            MediaRowLoading()
                        }
                        uiState.isMediaRow && uiState.currentItems.isEmpty() -> {
                            MediaRowEmptyState(row = uiState.currentRow)
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
                        isAutoGrid -> {
                            HomeAutoGrid(
                                items = rememberHomeCarouselItems(
                                    items = uiState.currentItems,
                                    rowKey = uiState.currentRow.toString(),
                                    downloadIndicators = uiState.downloadIndicators,
                                    repairedCoverPaths = uiState.repairedCoverPaths
                                ),
                                focusedIndex = uiState.focusedGameIndex,
                                config = uiState.autoGridConfig,
                                gridState = gridState,
                                showPlatformBadge = uiState.currentRow !is HomeRow.Platform &&
                                    uiState.currentRow != HomeRow.Steam && uiState.currentRow != HomeRow.Android,
                                downloadIndicatorFor = { item ->
                                    (item as? CarouselItem.Game)
                                        ?.let { uiState.downloadIndicatorFor(it.game.id) }
                                        ?: GameDownloadIndicator.NONE
                                },
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onItemTap = { index -> viewModel.handleItemTap(index, onGameSelect) },
                                onItemLongPress = viewModel::handleItemLongPress,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            CarouselRail(
                                items = rememberHomeCarouselItems(
                                    items = uiState.currentItems,
                                    rowKey = uiState.currentRow.toString(),
                                    downloadIndicators = uiState.downloadIndicators,
                                    repairedCoverPaths = uiState.repairedCoverPaths
                                ),
                                focusedIndex = uiState.focusedGameIndex,
                                listState = listState,
                                metrics = CarouselMetrics.hero(
                                    cardWidth = cardSize.width,
                                    cardHeight = cardSize.height,
                                    config = uiState.carouselConfig
                                ),
                                overrides = CarouselOverrides(
                                    focusedScale = if (uiState.isVideoPreviewActive) 1f else null,
                                    unfocusedAlpha = if (uiState.isVideoPreviewActive) 0f else null,
                                    viewAllAlpha = if (uiState.isVideoPreviewActive) 0f else 1f
                                ),
                                showPlatformBadge = uiState.carouselConfig.showPlatformBadge &&
                                    uiState.currentRow !is HomeRow.Platform && uiState.currentRow != HomeRow.Steam && uiState.currentRow != HomeRow.Android,
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onItemTap = { index -> viewModel.handleItemTap(index, onGameSelect) },
                                onItemLongPress = viewModel::handleItemLongPress,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .height(railHeight)
                            )
                        }
                    }
                }

                val focusedGame = uiState.focusedGame
                if (isCustomGrid) {
                    val grid = uiState.customGrid
                    FooterHints(
                        hints = if (grid.isEditing) {
                            listOf(
                                InputButton.DPAD to grid.editLabel.orEmpty(),
                                InputButton.X to if (grid.editMode == TileEditMode.MOVE) {
                                    "Resize"
                                } else {
                                    "Move"
                                },
                                InputButton.A to "Place",
                                InputButton.B to "Cancel"
                            )
                        } else {
                            buildList {
                                add(InputButton.LB_RB to "Page")
                                grid.confirmLabel?.let { add(InputButton.A to it) }
                                add(InputButton.SELECT to "Options")
                            }
                        },
                        variant = FooterVariant.SUBTLE
                    )
                    FooterSpacer()
                } else if (uiState.isMediaRow || uiState.focusedMedia != null) {
                    val focusedMedia = uiState.focusedMedia
                    FooterHints(
                        hints = buildList {
                            if (isAutoGrid) {
                                add(InputButton.DPAD to "Item")
                                add(InputButton.LB_RB to "Section")
                            } else {
                                add(InputButton.DPAD_HORIZONTAL to "Item")
                                add(InputButton.DPAD_VERTICAL to "Row")
                            }
                            if (focusedMedia == null) {
                                add(InputButton.A to "Refresh")
                            } else {
                                add(
                                    InputButton.A to
                                        if (focusedMedia.hasResumePosition) "Resume" else "Play"
                                )
                                if (uiState.currentRow == HomeRow.Favorites) {
                                    add(InputButton.Y to "Unfavorite")
                                }
                                add(InputButton.X to "Details")
                            }
                        },
                        variant = FooterVariant.SUBTLE,
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> inputHandler.onConfirm()
                                InputButton.Y -> inputHandler.onSecondaryAction()
                                InputButton.X -> inputHandler.onContextMenu()
                                else -> Unit
                            }
                        }
                    )
                    FooterSpacer()
                } else if (focusedGame != null && !uiState.showGameMenu) {
                    if (!uiState.isVideoPreviewActive) {
                        FooterHints(
                            hints = listOf(
                                if (isAutoGrid) InputButton.DPAD to "Game" else InputButton.DPAD_HORIZONTAL to "Game",
                                if (isAutoGrid) InputButton.LB_RB to "Section" else InputButton.DPAD_VERTICAL to "Platform",
                                InputButton.A to when {
                                    focusedGame.needsInstall -> "Install"
                                    focusedGame.isDownloaded -> "Play"
                                    else -> "Download"
                                },
                                InputButton.Y to if (focusedGame.isFavorite) "Unfavorite" else "Favorite",
                                InputButton.X to "Details"
                            ),
                            variant = FooterVariant.SUBTLE,
                            onHintClick = { button ->
                                when (button) {
                                    InputButton.A -> {
                                        when {
                                            focusedGame.needsInstall -> viewModel.installApk(focusedGame.id)
                                            focusedGame.isDownloaded -> viewModel.launchGame(focusedGame.id)
                                            focusedGame.isSteamGame -> viewModel.queueSteamDownload(focusedGame.id)
                                            else -> viewModel.queueDownload(focusedGame.id)
                                        }
                                    }
                                    InputButton.Y -> viewModel.toggleFavorite(focusedGame.id)
                                    InputButton.X -> onGameSelect(focusedGame.id)
                                    else -> {}
                                }
                            }
                        )
                    }
                    FooterSpacer()
                } else {
                    val viewAll = uiState.focusedItem as? HomeRowItem.ViewAll
                    FooterHints(
                        hints = listOf(
                            if (isAutoGrid) {
                                InputButton.DPAD to "Game"
                            } else {
                                InputButton.DPAD_HORIZONTAL to "Game"
                            },
                            if (isAutoGrid) {
                                InputButton.LB_RB to "Section"
                            } else {
                                InputButton.DPAD_VERTICAL to "Platform"
                            },
                            InputButton.A to "Library"
                        ),
                        variant = FooterVariant.SUBTLE,
                        onHintClick = { button ->
                            if (button == InputButton.A) {
                                onNavigateToLibrary(viewAll?.platformId, viewAll?.sourceFilter)
                            }
                        }
                    )
                    FooterSpacer()
                }
            }

            if (!isAutoGrid && !isCustomGrid && !uiState.isMediaRow) {
            val gameInfoWidth by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(500),
                label = "gameInfoWidth"
            )
            val gameInfoTopPadding by animateDpAsState(
                targetValue = if (uiState.isVideoPreviewActive) {
                    Dimens.spacingMd
                } else {
                    (headerBlockHeight - Dimens.spacingLg).coerceAtLeast(Dimens.spacingMd)
                },
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
                title = uiState.focusedGame?.title ?: uiState.focusedMedia?.title ?: "",
                developer = uiState.focusedGame?.developer ?: uiState.focusedMedia?.subtitle,
                rating = uiState.focusedGame?.rating,
                userRating = uiState.focusedGame?.userRating ?: 0,
                userDifficulty = uiState.focusedGame?.userDifficulty ?: 0,
                achievementCount = uiState.focusedGame?.achievementCount ?: 0,
                earnedAchievementCount = uiState.focusedGame?.earnedAchievementCount ?: 0,
                showMetadata = !uiState.isVideoPreviewActive,
                textColorOverride = if (videoTextColor != Color.Unspecified) videoTextColor else null,
                placement = if (
                    !isPortrait &&
                    uiState.carouselConfig.focusPosition == HomeFocusPosition.CENTER
                ) {
                    GameInfoPlacement.SPLIT
                } else {
                    GameInfoPlacement.CENTERED
                },
                modifier = Modifier
                    .fillMaxWidth(
                        when {
                            isPortrait -> 1f
                            uiState.carouselConfig.focusPosition == HomeFocusPosition.CENTER ->
                                gameInfoWidth
                            else -> GAME_INFO_SIDE_WIDTH_FRACTION
                        }
                    )
                    .align(
                        gameInfoAlignment(
                            atBottom = uiState.carouselConfig.rowAlignment == HomeRowAlignment.TOP,
                            centred = isPortrait ||
                                uiState.carouselConfig.focusPosition == HomeFocusPosition.CENTER,
                            inverted = uiState.carouselConfig.inverted
                        )
                    )
                    .padding(
                        top = if (uiState.carouselConfig.rowAlignment == HomeRowAlignment.TOP) {
                            0.dp
                        } else {
                            gameInfoTopPadding
                        },
                        bottom = if (uiState.carouselConfig.rowAlignment == HomeRowAlignment.TOP) {
                            Dimens.footerHeight + Dimens.spacingLg
                        } else {
                            0.dp
                        }
                    )
                    .onSizeChanged { size ->
                        val measured = with(localDensity) { size.height.toDp() }
                        if (measured != gameInfoHeight) gameInfoHeight = measured
                    }
            )
            }
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
                    isPlatformRow = uiState.currentRow is HomeRow.Platform,
                    focusIndex = uiState.gameMenuFocusIndex,
                    onDismiss = { viewModel.toggleGameMenu() },
                    onPrimaryAction = {
                        viewModel.toggleGameMenu()
                        when {
                            focusedGame.needsInstall -> viewModel.installApk(focusedGame.id)
                            focusedGame.isDownloaded -> viewModel.launchGame(focusedGame.id)
                            focusedGame.isSteamGame -> viewModel.queueSteamDownload(focusedGame.id)
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
                    onResyncPlatform = {
                        viewModel.toggleGameMenu()
                        viewModel.syncPlatform(focusedGame.platformId, focusedGame.platformDisplayName)
                    },
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

        val pendingTileAdd = uiState.customGrid.pendingAdd
        if (pendingTileAdd != null) {
            com.nendo.argosy.ui.primitives.ArgosyConfirmModal(
                title = "Add to home grid?",
                message = "${pendingTileAdd.title} finished downloading.",
                confirmLabel = "Add",
                cancelLabel = "Not now",
                focusedIndex = uiState.customGrid.pendingAddFocusIndex,
                onConfirm = viewModel::confirmPendingTileAdd,
                onDismiss = viewModel::dismissPendingTileAdd
            )
        }

        if (uiState.customGrid.showMenu) {
            val menuTile = uiState.customGrid.focusedTile
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                title = menuTile?.let { uiState.tileContentFor(it)?.label }.orEmpty(),
                entries = uiState.customGrid.menuActions.map { it.label },
                focusIndex = uiState.customGrid.menuFocusIndex,
                onSelect = { index ->
                    viewModel.moveTileMenuFocus(index - uiState.customGrid.menuFocusIndex)
                    viewModel.confirmTileMenu()
                },
                onDismiss = viewModel::closeTileMenu,
                dangerFromIndex = uiState.customGrid.menuDangerFromIndex
            )
        }

        AnimatedVisibility(
            visible = uiState.showTilePicker,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HomeTilePickerModal(
                entries = uiState.tilePickerEntries,
                query = uiState.tilePickerQuery,
                focusIndex = uiState.tilePickerFocusIndex,
                onSelect = { entry -> viewModel.selectTilePickerEntry(entry) },
                onDismiss = viewModel::closeTilePicker,
                searchActive = uiState.customGrid.pickerSearchActive,
                onQueryChange = viewModel::setTilePickerQuery,
                category = uiState.customGrid.pickerCategory,
                categories = uiState.customGrid.pickerCategories,
                onSelectCategory = { viewModel.setTilePickerCategory(it) },
                canDeletePage = uiState.customGrid.canDeletePage,
                onDeletePage = viewModel::deleteCustomGridPage
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

        uiState.memcardPickerState?.let { pickerState ->
            MemcardPickerModal(
                cards = pickerState.cards,
                focusIndex = uiState.memcardPickerFocusIndex,
                selectedCardPath = null,
                onSelectCard = viewModel::selectMemcard,
                onDismiss = viewModel::dismissMemcardPicker
            )
        }

        MediaResumeModalHost(
            prompt = uiState.mediaResumePrompt,
            onStartOver = viewModel::startMediaOver,
            onResume = viewModel::resumeMedia,
            onDismiss = viewModel::dismissMediaResumePrompt
        )

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
    uiState: HomeUiState,
    onPreviousRow: () -> Unit,
    onNextRow: () -> Unit,
    onSelectRow: (HomeRow) -> Unit,
    isStacked: Boolean,
    headerOffset: androidx.compose.ui.unit.Dp = 0.dp,
    showSections: Boolean = true,
    compact: Boolean = false
) {
    val edge = if (compact) Dimens.spacingMd else Dimens.spacingLg
    val verticalEdge = if (compact) Dimens.spacingXs else Dimens.spacingLg
    if (!showSections) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edge, vertical = verticalEdge)
                .offset(y = headerOffset),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SystemStatusBar()
        }
        return
    }

    if (isStacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edge, vertical = verticalEdge)
                .offset(y = headerOffset),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SystemStatusBar()
            }
            PlatformBreadcrumb(
                uiState = uiState,
                onPreviousRow = onPreviousRow,
                onNextRow = onNextRow,
                onSelectRow = onSelectRow,
                fillAvailableWidth = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = edge, vertical = verticalEdge)
            .offset(y = headerOffset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlatformBreadcrumb(
            uiState = uiState,
            onPreviousRow = onPreviousRow,
            onNextRow = onNextRow,
            onSelectRow = onSelectRow,
            fillAvailableWidth = false,
            modifier = Modifier.weight(1f)
        )

        SystemStatusBar()
    }
}

@Composable
private fun PlatformBreadcrumb(
    uiState: HomeUiState,
    onPreviousRow: () -> Unit,
    onNextRow: () -> Unit,
    onSelectRow: (HomeRow) -> Unit,
    fillAvailableWidth: Boolean,
    modifier: Modifier = Modifier
) {
    val rows = uiState.availableRows
    SectionBreadcrumb(
        labels = rows.map { uiState.shortLabelFor(it) },
        currentIndex = rows.indexOf(uiState.currentRow).coerceAtLeast(0),
        onPrevious = onPreviousRow,
        onNext = onNextRow,
        onSelect = { index -> rows.getOrNull(index)?.let(onSelectRow) },
        fillAvailableWidth = fillAvailableWidth,
        modifier = modifier
    )
}

/**
 * Where the focused game's details sit relative to the rail.
 *
 * A centred focus leaves no room above the card for a centred block, so the two halves split to
 * either side of it; an edge-anchored focus keeps them together on the side the rail leaves free.
 */
enum class GameInfoPlacement { CENTERED, SPLIT }

/**
 * How much of the row's width the details block occupies when it sits to one side.
 */
private const val GAME_INFO_SIDE_WIDTH_FRACTION = 0.6f

/**
 * Width each half takes when the details split around a centred focus, leaving the remainder as a
 * gutter down the middle so neither half crowds the focused card.
 */
private const val GAME_INFO_SPLIT_WIDTH_FRACTION = 0.45f

/**
 * Which corner the details block occupies. It sits opposite the focused card, on the half of the
 * row the rail leaves free, and moves below the cards when they are hung from the top.
 *
 * Portrait passes [centred] regardless of the configured focus position: there is no free half
 * beside the rail on a narrow screen, so the block spans the full width above or below it.
 */
private fun gameInfoAlignment(
    atBottom: Boolean,
    centred: Boolean,
    inverted: Boolean
): Alignment = when {
    centred && atBottom -> Alignment.BottomCenter
    centred -> Alignment.TopCenter
    atBottom && inverted -> Alignment.BottomStart
    atBottom -> Alignment.BottomEnd
    inverted -> Alignment.TopStart
    else -> Alignment.TopEnd
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
    placement: GameInfoPlacement = GameInfoPlacement.SPLIT,
    modifier: Modifier = Modifier
) {
    val metadataAlpha by animateFloatAsState(
        targetValue = if (showMetadata) 1f else 0f,
        animationSpec = tween(500),
        label = "metadataAlpha"
    )

    val titleColor = textColorOverride ?: MaterialTheme.colorScheme.onSurface
    val subtitleColor = textColorOverride?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.onSurfaceVariant

    val isSplit = placement == GameInfoPlacement.SPLIT

    GameInfoLayout(
        isSplit = isSplit,
        modifier = modifier.padding(horizontal = Dimens.spacingXxl),
        details = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
        }
    ) {
        val hasBadges = rating != null || userRating > 0 || userDifficulty > 0 || achievementCount > 0
        if (hasBadges) {
            if (!isSplit) Spacer(modifier = Modifier.height(Dimens.spacingXs))
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
                            tint = textColorOverride ?: ALauncherColors.StarGold,
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
                            tint = textColorOverride ?: ALauncherColors.DifficultyRed,
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
                            tint = textColorOverride ?: ALauncherColors.TrophyAmber,
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

/**
 * Puts the title block and the badges either above one another or on opposite sides, so the same
 * content serves a rail that hugs an edge and one that sits in the middle.
 */
@Composable
private fun GameInfoLayout(
    isSplit: Boolean,
    modifier: Modifier = Modifier,
    details: @Composable () -> Unit,
    badges: @Composable ColumnScope.() -> Unit
) {
    if (isSplit) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(GAME_INFO_SPLIT_WIDTH_FRACTION),
                contentAlignment = Alignment.Center
            ) {
                details()
            }
            Spacer(modifier = Modifier.weight(1f - GAME_INFO_SPLIT_WIDTH_FRACTION * 2f))
            Box(
                modifier = Modifier.weight(GAME_INFO_SPLIT_WIDTH_FRACTION),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, content = badges)
            }
        }
        return
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        details()
        badges()
    }
}

/**
 * Carousel card size, driven by the height actually left over after the header, the game info
 * block and the footer, so the focused card at [CAROUSEL_FOCUS_SCALE] fills that space exactly
 * instead of overrunning the platform carousel on extended-widescreen or collapsing to a thin
 * strip when the window is tall. The width cap inside [carouselCardSize] keeps one card from
 * dominating the row on very tall windows, where height alone would size it wider than the screen.
 *
 * Callers must subtract a gap of their own for the focused card to grow into: the card scales from
 * its bottom edge, so without one the only clearance above it is what [CAROUSEL_CARD_SCALE] happens
 * to leave over, which at square ratios is a few dp and overlaps the game info.
 */
@Composable
private fun rememberCarouselCardSize(
    availableHeight: Dp,
    config: com.nendo.argosy.domain.model.CarouselConfig
): DpSize = carouselCardSize(
    availableHeight = availableHeight,
    availableWidth = LocalConfiguration.current.screenWidthDp.dp,
    coverAspectRatio = LocalBoxArtStyle.current.aspectRatio,
    restingScale = config.restingScale,
    minCardHeight = Dimens.gameCardHeight * HERO_MIN_CARD_SCALE
)

@Composable
private fun rememberHomeCarouselItems(
    items: List<HomeRowItem>,
    rowKey: String,
    downloadIndicators: Map<Long, GameDownloadIndicator>,
    repairedCoverPaths: Map<Long, String>
): List<CarouselItem> = remember(items, rowKey, downloadIndicators, repairedCoverPaths) {
    items.map { item ->
        when (item) {
            is HomeRowItem.Game -> CarouselItem.Game(
                key = "$rowKey-${item.game.id}",
                game = item.game,
                downloadIndicator = downloadIndicators[item.game.id] ?: GameDownloadIndicator.NONE,
                coverPathOverride = repairedCoverPaths[item.game.id]
            )
            is HomeRowItem.ViewAll -> CarouselItem.ViewAll(
                key = "$rowKey-viewall-${item.platformId ?: item.sourceFilter ?: "all"}"
            )
            is HomeRowItem.Media -> CarouselItem.Media(
                key = "$rowKey-${item.media.itemId}",
                media = item.media
            )
        }
    }
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
    isPlatformRow: Boolean,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onAddToCollection: () -> Unit,
    onRefresh: () -> Unit,
    onResyncPlatform: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onHide: () -> Unit
) {
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

    data class MenuEntry(
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val label: String,
        val isDangerous: Boolean = false,
        val onClick: () -> Unit
    )

    val options = buildList {
        add(MenuEntry(primaryIcon, primaryLabel, onClick = onPrimaryAction))
        add(MenuEntry(if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (game.isFavorite) "Unfavorite" else "Favorite", onClick = onFavorite))
        add(MenuEntry(Icons.Default.Info, "Details", onClick = onDetails))
        add(MenuEntry(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to Collection", onClick = onAddToCollection))
        if (game.isRommGame || game.isAndroidApp) {
            add(MenuEntry(Icons.Default.Refresh, "Refresh Data", onClick = onRefresh))
        }
        if (isPlatformRow && game.platformId > 0) {
            add(MenuEntry(Icons.Default.Refresh, "Resync Platform", onClick = onResyncPlatform))
        }
    }
    val dangerousOptions = buildList {
        if (game.isDownloaded || game.needsInstall) {
            add(MenuEntry(Icons.Default.DeleteOutline, "Delete Download", isDangerous = true, onClick = onDelete))
        }
        if (game.isAndroidApp) {
            add(MenuEntry(Icons.Default.Home, "Remove from Home", isDangerous = true, onClick = onRemoveFromHome))
        }
        add(MenuEntry(label = "Hide", isDangerous = true, onClick = onHide))
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val listState = rememberLazyListState()
    val listIndex = if (focusIndex < options.size) focusIndex else focusIndex + 1
    FocusedScroll(listState = listState, focusedIndex = listIndex)

    BoxWithConstraints(
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
                .heightIn(max = maxHeight * 0.85f)
        ) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(options) { index, entry ->
                    MenuOption(
                        icon = entry.icon,
                        label = entry.label,
                        isFocused = focusIndex == index,
                        onClick = entry.onClick
                    )
                }
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                itemsIndexed(dangerousOptions) { index, entry ->
                    MenuOption(
                        icon = entry.icon,
                        label = entry.label,
                        isFocused = focusIndex == options.size + index,
                        isDangerous = entry.isDangerous,
                        onClick = entry.onClick
                    )
                }
            }
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
        isDangerous && isFocused -> lerp(LocalArgosyTheme.current.destructive, Color.White, 0.45f)
        isDangerous -> LocalArgosyTheme.current.destructive
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        isDangerous && isFocused -> LocalArgosyTheme.current.destructive.copy(alpha = 0.15f)
        isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
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
