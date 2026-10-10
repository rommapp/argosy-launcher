package com.nendo.argosy.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.nendo.argosy.ui.components.LocalFrostedBackdrop
import com.nendo.argosy.ui.components.frostedBackdropSource
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nendo.argosy.ui.common.AlwaysCrossfadeFactory
import com.nendo.argosy.ui.common.backgroundBlurDp
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.SectionBreadcrumb
import com.nendo.argosy.ui.icons.InputIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.LocalSurfaceBackdrop
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.nendo.argosy.R
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalPresenceEffect
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
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeLayoutKind
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.LocalFooterHost
import com.nendo.argosy.ui.components.NEW_BADGE_TOP_OVERFLOW
import com.nendo.argosy.ui.components.FooterVariant
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.MemcardPickerModal
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.components.LocalArtworkScraping
import com.nendo.argosy.ui.components.LocalStatusBarItems
import com.nendo.argosy.ui.components.hasStatusBarContent
import com.nendo.argosy.ui.components.YouTubeVideoPlayer
import com.nendo.argosy.ui.input.ChangelogInputHandler
import com.nendo.argosy.ui.input.DiscPickerInputHandler
import com.nendo.argosy.ui.screens.media.components.MediaSignedOutState
import com.nendo.argosy.ui.screens.media.modals.MediaResumeModalHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    isDefaultView: Boolean,
    onGameSelect: (Long) -> Unit,
    onNavigateToLibrary: (
        platformId: Long?,
        sourceFilter: String?,
        filters: String?
    ) -> Unit = { _, _, _ -> },
    onNavigateToCollections: (collectionId: Long) -> Unit = {},
    onNavigateToDefault: () -> Unit,
    onDrawerToggle: () -> Unit,
    onChangelogAction: (RequiredAction) -> Unit = {},
    onNavigateToSettings: (String) -> Unit = {},
    onPlayMedia: (itemId: String, startOver: Boolean) -> Unit = { _, _ -> },
    onMediaSelect: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasPresentationScreen by (
        com.nendo.argosy.DualScreenManagerHolder.instance?.hasPresentationScreen
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    val presentationShowsHints by (
        com.nendo.argosy.DualScreenManagerHolder.instance?.presentationShowsHints
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    var navigationPillWidth by remember { mutableStateOf(0.dp) }
    val downloadIndicators = viewModel.downloadIndicators.collectAsState()
    val mediaDownloadProgress = viewModel.mediaDownloadProgress.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isAutoGrid = uiState.layoutKind == HomeLayoutKind.AUTO_GRID
    val isCustomGrid = uiState.layoutKind == HomeLayoutKind.CUSTOM_GRID
    val quickNavigation by viewModel.quickNavigationEnabled.collectAsState()
    val rowButtons = if (quickNavigation) InputButton.LT_RT else InputButton.LB_RB
    val scope = rememberCoroutineScope()
    val overflowScrollState = rememberScrollState()
    var overflowScrollEnabled by remember { mutableStateOf(false) }
    var overflowViewportHeightPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(uiState.currentRow, overflowScrollEnabled) {
        overflowScrollState.scrollTo(0)
    }
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
                    onNavigateToLibrary(
                        event.platformId,
                        event.sourceFilter,
                        event.tileFilters?.let {
                            com.nendo.argosy.ui.screens.library.LibraryFilterArgs.encode(it)
                        }
                    )
                }
                is HomeEvent.NavigateToCollections -> onNavigateToCollections(event.collectionId)
                is HomeEvent.NavigateToSettings -> onNavigateToSettings(event.section)
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
            onDrawerToggle = onDrawerToggle,
            onScrollOverflow = { direction ->
                val canScroll = if (direction < 0) {
                    overflowScrollState.canScrollBackward
                } else {
                    overflowScrollState.canScrollForward
                }
                if (overflowScrollEnabled && overflowViewportHeightPx > 0f && canScroll) {
                    scope.launch { overflowScrollState.animateScrollBy(direction * overflowViewportHeightPx) }
                    true
                } else false
            }
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
                viewModel.republishCompanionDetail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
        viewModel.republishCompanionDetail()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    val siblingChoiceOpen = viewModel.siblingChoiceState.collectAsState().value != null
    val modalBlur by animateDpAsState(
        targetValue = if (
            uiState.showGameMenu || uiState.syncOverlayState != null || uiState.changelogEntry != null ||
            uiState.discPickerState != null || uiState.memcardPickerState != null || siblingChoiceOpen
        ) Motion.blurRadiusModal else 0.dp,
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

    val backgroundBlurDp = uiState.backgroundBlur.backgroundBlurDp
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
            val background = com.nendo.argosy.ui.common.rememberResolvedBackgroundPath(game.id, game.backgroundPath)
            val cover = com.nendo.argosy.ui.common.rememberResolvedCoverPath(game.id, game.coverPath)
            when {
                background?.startsWith("/") == true -> background
                cover?.startsWith("/") == true -> cover
                else -> background ?: cover
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
        val defaultHeaderHeight = Dimens.headerHeight
        var headerBlockHeight by remember { mutableStateOf(defaultHeaderHeight) }
        var dockHeight by remember { mutableStateOf(0.dp) }
        val localDensity = LocalDensity.current
    val edgeThresholdPx = with(LocalDensity.current) {
        ComponentDefaults.Carousel.edgeTouchHeightDp.dp.toPx()
    }

        val swipeGestureModifier = Modifier
            .pointerInput(swipeThreshold) {
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
            .pointerInput(swipeThreshold, edgeThresholdPx) {
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
        val frostedBackdrop = LocalFrostedBackdrop.current
        Box(
            modifier = Modifier.fillMaxSize().then(
                if (frostedBackdrop != null) Modifier.frostedBackdropSource(frostedBackdrop)
                else Modifier
            )
        ) {
            if (backdropEnabled) {
                Box(modifier = Modifier.fillMaxSize().surfaceBackdrop(BackdropRole.CONTENT))
            }
            if (isCustomGrid) {
                val pageSettings = uiState.customGrid.currentPageSettings
                Crossfade(
                    targetState = pageSettings.backgroundPath.takeIf { pageSettings.hasBackground },
                    animationSpec = tween(Motion.durationSlide, easing = Motion.argosyEase),
                    label = "page-backdrop",
                    modifier = Modifier.fillMaxSize()
                ) { path ->
                    if (path != null) {
                        val pageBlur = backgroundBlurDp + combinedBlur
                        com.nendo.argosy.ui.components.PageBackdrop(
                            path = path,
                            modifier = Modifier
                                .fillMaxSize()
                                .let { if (pageBlur > 0.dp) it.blur(pageBlur) else it }
                        )
                    }
                }
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

        BoxWithConstraints(modifier = Modifier
            .fillMaxSize()
            .then(swipeGestureModifier)
        ) {
            val availableCarouselWidth = maxWidth
            val showTitle = !presentationShowsHints && !isAutoGrid && !isCustomGrid && !uiState.isMediaRow
            val footerHeight = if (presentationShowsHints) {
                dockHeight + Dimens.spacingMd
            } else {
                LocalFooterHost.current.measuredHeight
            }
            val edge = if (maxWidth.value < ComponentDefaults.Carousel.compactWidthDp) {
                Dimens.spacingMd
            } else {
                Dimens.spacingLg
            }
            val gap = Dimens.spacingMd
            val fullTitleWidth = (maxWidth - edge * 2).coerceAtLeast(0.dp)
            val titleMetadata = if (showTitle) rememberHomeTitleMetadata(uiState) else emptyList()
            val aboveReserve = if (showTitle) rememberHomeTitleReserve(titleMetadata, fullTitleWidth) else 0.dp
            val mirrored = (LocalLayoutDirection.current == LayoutDirection.Rtl) xor uiState.carouselConfig.inverted
            val coverAspectRatio = LocalBoxArtStyle.current.aspectRatio
            var minimumRestingScaleSeen by remember { mutableFloatStateOf(uiState.carouselConfig.restingScale) }
            val springMinimumRestingScale = minOf(minimumRestingScaleSeen, uiState.carouselConfig.restingScale)
            androidx.compose.runtime.SideEffect {
                minimumRestingScaleSeen = springMinimumRestingScale
            }
            val scrollAnchorOffset = with(localDensity) {
                kotlin.math.abs(CarouselAnchor.START.snapOffsetPx).toDp()
            }
            fun resolveGeometry(reserve: Dp): HomeCarouselGeometry = homeCarouselGeometry(
                width = maxWidth.value,
                height = maxHeight.value,
                headerHeight = headerBlockHeight.value,
                footerHeight = footerHeight.value,
                edge = edge.value,
                gap = gap.value,
                titleReserve = reserve.value,
                aboveTitleReserve = aboveReserve.value,
                aspectRatio = coverAspectRatio,
                restingScale = uiState.carouselConfig.restingScale,
                springMinimumRestingScale = springMinimumRestingScale,
                rowAlignment = uiState.carouselConfig.rowAlignment,
                centeredFocus = uiState.carouselConfig.focusPosition == HomeFocusPosition.CENTER,
                mirrored = mirrored,
                badgeOverflow = NEW_BADGE_TOP_OVERFLOW.value,
                dampingRatio = MotionTokens.Spring.focusDampingRatio,
                neighbourPush = uiState.carouselConfig.neighbourPush,
                scrollAnchorOffset = scrollAnchorOffset.value,
                showTitle = showTitle
            )
            val candidate = resolveGeometry(aboveReserve)
            val sideReserve = if (showTitle && !candidate.titleAbove) {
                rememberHomeTitleReserve(titleMetadata, candidate.titleMaxWidth.dp)
            } else aboveReserve
            val fittedGeometry = resolveGeometry(sideReserve)
            val content = homeCarouselContent(
                fitted = fittedGeometry,
                width = maxWidth.value,
                height = maxHeight.value,
                headerHeight = headerBlockHeight.value,
                footerHeight = footerHeight.value,
                edge = edge.value,
                gap = gap.value,
                titleReserve = aboveReserve.value,
                aspectRatio = coverAspectRatio,
                restingScale = uiState.carouselConfig.restingScale,
                springMinimumRestingScale = springMinimumRestingScale,
                badgeOverflow = NEW_BADGE_TOP_OVERFLOW.value,
                dampingRatio = MotionTokens.Spring.focusDampingRatio,
                minimumFocusedExtent = ComponentDefaults.FrostedSurface.minimumTouchTargetDp.toFloat(),
                preferredCardHeight = (
                    Dimens.gameCardHeight * com.nendo.argosy.ui.components.HERO_MIN_CARD_SCALE
                ).value,
                enabled = showTitle
            )
            androidx.compose.runtime.SideEffect {
                overflowScrollEnabled = content.scrollable
                overflowViewportHeightPx = with(localDensity) { content.viewportHeight.dp.toPx() }
            }
            val geometry = content.geometry
            val cardSize = DpSize(geometry.cardWidth.dp, geometry.cardHeight.dp)
            val railHeight = if (isAutoGrid || isCustomGrid) {
                (maxHeight - headerBlockHeight - footerHeight - edge).coerceAtLeast(0.dp)
            } else geometry.railHeight.dp
            val railTop = if (isAutoGrid || isCustomGrid) headerBlockHeight else geometry.railTop.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = content.viewportTop.dp)
                    .height(content.viewportHeight.dp)
                    .then(if (content.scrollable) Modifier.verticalScroll(overflowScrollState) else Modifier)
            ) {
            Box(Modifier.fillMaxWidth().height(content.contentHeight.dp)) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset(y = railTop)
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
                        isCustomGrid -> {
                            val pageSettings = uiState.customGrid.currentPageSettings
                            com.nendo.argosy.ui.components.PageThemePlayer(
                                filePath = pageSettings.audioPath.takeIf {
                                    pageSettings.audioKind ==
                                        com.nendo.argosy.data.local.entity.PageAudioKind.THEME
                                }
                            )
                            com.nendo.argosy.ui.components.CustomGridSurface(
                                state = uiState.customGrid,
                                contentFor = { tile -> uiState.tileContentFor(tile, context) },
                                columns = uiState.customGridConfig.columns,
                                rows = uiState.customGridConfig.rows,
                                showCursor = !uiState.appBarFocused,
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
                                onTakeAudio = { viewModel.videoPreviewDelegate.holdForTileAudio() },
                                onReleaseAudio = { viewModel.videoPreviewDelegate.releaseTileAudio() },
                                onPlaybackPosition = { path, position ->
                                    viewModel.rememberTilePlaybackPosition(path, position)
                                },
                                showEmptySlots = uiState.customGridConfig.showEmptySlots,
                                onShapeResolved = viewModel::setCustomGridShape,
                                onAddPage = { viewModel.confirmAddPage() },
                                onHiddenTilesTap = viewModel::openHiddenCustomGridTiles,
                                onTileLongPress = { cell ->
                                    viewModel.setCustomGridCell(cell)
                                    viewModel.openTileMenu()
                                },
                                onBadgeTap = { index -> viewModel.engageRaTileAt(index) },
                                onBandTap = { inputHandler.onConfirm() },
                                downloadIndicatorFor = {
                                    downloadIndicators.value[it] ?: GameDownloadIndicator.NONE
                                },
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onPosterLoaded = viewModel::extractGradientForMedia,
                                useBoxArt = uiState.boxArt3d,
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
                        uiState.isPlatformRowLoading -> Unit
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
                                    repairedCoverPaths = uiState.repairedCoverPaths
                                ),
                                focusedIndex = uiState.focusedGameIndex,
                                config = uiState.autoGridConfig,
                                useBoxArt = uiState.boxArt3d,
                                gridState = gridState,
                                entryAnimationKey = (uiState.currentPlatform?.id ?: uiState.currentRow)
                                    .takeIf { uiState.autoGridConfig.showAllGames },
                                showPlatformBadge = uiState.currentRow !is HomeRow.Platform &&
                                    uiState.currentRow != HomeRow.Steam && uiState.currentRow != HomeRow.Android,
                                downloadIndicatorFor = { item ->
                                    when (item) {
                                        is CarouselItem.Game ->
                                            downloadIndicators.value[item.game.id]
                                                ?: GameDownloadIndicator.NONE
                                        is CarouselItem.Media ->
                                            mediaDownloadProgress.value.indicatorFor(item.media)
                                        else -> GameDownloadIndicator.NONE
                                    }
                                },
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onPosterLoaded = viewModel::extractGradientForMedia,
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
                                    repairedCoverPaths = uiState.repairedCoverPaths
                                ),
                                downloadIndicatorFor = { item ->
                                    when (item) {
                                        is CarouselItem.Game ->
                                            downloadIndicators.value[item.game.id]
                                                ?: GameDownloadIndicator.NONE
                                        is CarouselItem.Media ->
                                            mediaDownloadProgress.value.indicatorFor(item.media)
                                        else -> GameDownloadIndicator.NONE
                                    }
                                },
                                focusedIndex = uiState.focusedGameIndex,
                                listState = listState,
                                availableWidth = availableCarouselWidth,
                                onScaleAnimationsSettled = { focusScale, focusIndex ->
                                    if (
                                        focusScale == uiState.carouselConfig.focusScale &&
                                        focusIndex == uiState.focusedGameIndex
                                    ) {
                                        minimumRestingScaleSeen = uiState.carouselConfig.restingScale
                                    }
                                },
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
                                    uiState.currentRow !is HomeRow.Platform &&
                                    uiState.currentRow != HomeRow.Steam && uiState.currentRow != HomeRow.Android,
                                useBoxArt = uiState.boxArt3d,
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onPosterLoaded = viewModel::extractGradientForMedia,
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
                    val gridPageLabel = stringResource(R.string.home_footer_grid_page)
                    val gridFinishedLabel = stringResource(R.string.home_footer_grid_finished)
                    val gridRerollLabel = stringResource(R.string.home_footer_grid_reroll)
                    val gridSwapScreensLabel = stringResource(R.string.home_footer_grid_swap_screens)
                    val gridHoldSwapScreensLabel =
                        stringResource(R.string.home_footer_grid_hold_swap_screens)
                    val gridDetailsLabel = stringResource(R.string.home_footer_game_details)
                    val selectSwapMode = com.nendo.argosy.ui.dualscreen.selectSwapModeState()
                    val engagedFullscreenLabel =
                        stringResource(R.string.home_footer_grid_engaged_fullscreen)
                    val engagedIsMedia = grid.engagedTile?.target is
                        com.nendo.argosy.domain.model.HomeTileTargetRef.Media
                    FooterHints(
                        hints = when {
                            grid.engagedRaTile -> listOf(
                                InputButton.DPAD_HORIZONTAL to
                                    stringResource(R.string.home_footer_grid_engaged_browse),
                                InputButton.A to
                                    stringResource(R.string.home_footer_grid_engaged_details),
                                InputButton.B to
                                    stringResource(R.string.home_footer_grid_engaged_back)
                            )
                            grid.engagedTileId != null -> listOfNotNull(
                                InputButton.A to if (grid.engagedPaused) {
                                    stringResource(R.string.home_footer_grid_engaged_play)
                                } else {
                                    stringResource(R.string.home_footer_grid_engaged_pause)
                                },
                                InputButton.DPAD_HORIZONTAL to
                                    stringResource(R.string.home_footer_grid_engaged_seek),
                                if (engagedIsMedia) {
                                    InputButton.X to engagedFullscreenLabel
                                } else {
                                    null
                                },
                                InputButton.B to
                                    stringResource(R.string.home_footer_grid_engaged_back)
                            )
                            grid.mediaTileNotice != null ->
                                listOf(
                                    InputButton.DPAD_HORIZONTAL to
                                        stringResource(R.string.home_footer_media_notice_choose)
                                )
                            grid.isMediaSetupOpen && grid.mediaSetup?.step ==
                                com.nendo.argosy.ui.components.MediaTileStep.EPISODES ->
                                listOf(
                                    InputButton.A to
                                        stringResource(
                                            R.string.home_footer_media_setup_episode_tick
                                        ),
                                    InputButton.B to
                                        stringResource(
                                            R.string.home_footer_media_setup_episode_back
                                        )
                                )
                            grid.isMediaSetupOpen -> listOf(
                                InputButton.B to
                                    stringResource(R.string.home_footer_media_setup_back)
                            )
                            grid.showPicker -> listOf(
                                InputButton.LB_RB to
                                    stringResource(R.string.home_footer_tile_picker_tab),
                                InputButton.LT_RT to
                                    stringResource(R.string.home_footer_tile_picker_jump),
                                InputButton.Y to
                                    stringResource(R.string.home_footer_tile_picker_search)
                            )
                            grid.isEditing -> listOf(
                                InputButton.DPAD to
                                    grid.editLabelRes?.let { stringResource(it) }.orEmpty(),
                                InputButton.X to if (grid.editMode == TileEditMode.MOVE) {
                                    stringResource(R.string.home_footer_grid_edit_resize)
                                } else {
                                    stringResource(R.string.home_footer_grid_edit_move)
                                },
                                InputButton.A to
                                    stringResource(R.string.home_footer_grid_edit_place),
                                InputButton.B to
                                    stringResource(R.string.home_footer_grid_edit_cancel)
                            )
                            else -> buildList {
                                if (!grid.isScrolling) add(rowButtons to gridPageLabel)
                                grid.confirmLabelRes?.let {
                                    add(InputButton.A to stringResource(it))
                                }
                                if (grid.focusedCollection?.focusGameId != null) {
                                    add(InputButton.Y to gridFinishedLabel)
                                }
                                val feature = grid.focusedTile?.target
                                    as? com.nendo.argosy.domain.model.HomeTileTargetRef.Feature
                                if (feature?.kind == com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME) {
                                    add(InputButton.Y to gridRerollLabel)
                                }
                                if (grid.focusedGameId != null) {
                                    add(InputButton.X to gridDetailsLabel)
                                }
                                when (selectSwapMode) {
                                    com.nendo.argosy.data.preferences.SelectSwapMode.TAP ->
                                        add(InputButton.SELECT to gridSwapScreensLabel)
                                    com.nendo.argosy.data.preferences.SelectSwapMode.HOLD ->
                                        add(InputButton.SELECT to gridHoldSwapScreensLabel)
                                    null -> Unit
                                }
                            }
                        },
                        variant = FooterVariant.SUBTLE,
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> inputHandler.onConfirm()
                                InputButton.Y -> inputHandler.onSecondaryAction()
                                InputButton.X -> inputHandler.onContextMenu()
                                InputButton.SELECT -> com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                                else -> Unit
                            }
                        }
                    )
                    FooterSpacer()
                } else if (uiState.isMediaRow || uiState.focusedMedia != null) {
                    val focusedMedia = uiState.focusedMedia
                    val mediaSectionLabel = stringResource(R.string.home_footer_media_section)
                    val mediaRefreshLabel = stringResource(R.string.home_footer_media_refresh)
                    val mediaResumeLabel = stringResource(R.string.home_footer_media_resume)
                    val mediaPlayLabel = stringResource(R.string.home_footer_media_play)
                    val mediaUnfavoriteLabel =
                        stringResource(R.string.home_footer_media_unfavorite)
                    val mediaDetailsLabel = stringResource(R.string.home_footer_media_details)
                    FooterHints(
                        hints = buildList {
                            if (isAutoGrid) add(rowButtons to mediaSectionLabel)
                            if (focusedMedia == null) {
                                add(InputButton.A to mediaRefreshLabel)
                            } else {
                                add(
                                    InputButton.A to if (focusedMedia.hasResumePosition) {
                                        mediaResumeLabel
                                    } else {
                                        mediaPlayLabel
                                    }
                                )
                                if (uiState.currentRow == HomeRow.Favorites) {
                                    add(InputButton.Y to mediaUnfavoriteLabel)
                                }
                                add(InputButton.X to mediaDetailsLabel)
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
                            hints = listOfNotNull(
                                if (isAutoGrid) {
                                    rowButtons to
                                        stringResource(R.string.home_footer_game_section)
                                } else {
                                    null
                                },
                                InputButton.A to when {
                                    focusedGame.needsInstall ->
                                        stringResource(R.string.home_footer_game_install)
                                    focusedGame.isDownloaded ->
                                        stringResource(R.string.home_footer_game_play)
                                    downloadIndicators.value[focusedGame.id]?.isPaused == true ->
                                        stringResource(R.string.home_footer_game_resume_download)
                                    else -> stringResource(R.string.home_footer_game_download)
                                },
                                InputButton.Y to if (focusedGame.isFavorite) {
                                    stringResource(R.string.home_footer_game_unfavorite)
                                } else {
                                    stringResource(R.string.home_footer_game_favorite)
                                },
                                InputButton.X to stringResource(R.string.home_footer_game_details)
                            ),
                            variant = FooterVariant.SUBTLE,
                            onHintClick = { button ->
                                when (button) {
                                    InputButton.A -> viewModel.activateFocusedGame(focusedGame)
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
                            (if (isAutoGrid) InputButton.DPAD else InputButton.DPAD_HORIZONTAL)
                                to stringResource(R.string.home_footer_viewall_item),
                            if (isAutoGrid) {
                                rowButtons to
                                    stringResource(R.string.home_footer_viewall_section)
                            } else {
                                InputButton.DPAD_VERTICAL to
                                    stringResource(R.string.home_footer_viewall_platform)
                            },
                            InputButton.A to stringResource(R.string.home_footer_viewall_library)
                        ),
                        variant = FooterVariant.SUBTLE,
                        onHintClick = { button ->
                            if (button == InputButton.A) {
                                onNavigateToLibrary(viewAll?.platformId, viewAll?.sourceFilter, null)
                            }
                        }
                    )
                    FooterSpacer()
                }
            }

            if (showTitle) {
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

            (uiState.focusedGame?.title ?: uiState.focusedMedia?.title)?.let { title ->
                val game = uiState.focusedGame
                val titleWidth = geometry.titleMaxWidth.dp
                Layout(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        HomeTitleSection(
                            game = game,
                            title = title,
                            developer = game?.developer ?: uiState.focusedMedia?.subtitle,
                            friends = uiState.friendsFor(game),
                            maxWidth = titleWidth,
                            centered = geometry.titleAbove,
                            showMetadata = !uiState.isVideoPreviewActive,
                            textColorOverride = videoTextColor.takeIf { it != Color.Unspecified }
                        )
                    }
                ) { measurables, constraints ->
                    val section = measurables.single().measure(
                        Constraints(
                            maxWidth = titleWidth.roundToPx().coerceAtLeast(0)
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        section.place(
                            (geometry.titleCenterX.dp.toPx() - section.width / 2f).toInt(),
                            (geometry.titleCenterY.dp.toPx() - section.height / 2f).toInt()
                        )
                    }
                }
            }
            }
            }
            }
        }
        }

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(swipeGestureModifier)
                .onSizeChanged { size ->
                    val measured = with(localDensity) { size.height.toDp() }
                    if (measured != headerBlockHeight) headerBlockHeight = measured
                }
        ) {
            val isPortrait = maxWidth <= maxHeight
            HomeHeader(
                uiState = uiState,
                onPreviousRow = viewModel::previousRow,
                onNextRow = viewModel::nextRow,
                onSelectRow = viewModel::selectRow,
                isStacked = isPortrait,
                showStatus = !presentationShowsHints &&
                    (!isPortrait || com.nendo.argosy.ui.components.LocalTransientHomeStatusVisible.current),
                onNavigationWidthChanged = { navigationPillWidth = it },
                headerOffset = videoModeHeaderOffset,
                showSections = !isCustomGrid,
                compact = (isAutoGrid && !uiState.autoGridConfig.showTitles) ||
                    maxHeight < ComponentDefaults.FrostedSurface.compactHeaderViewportHeightDp.dp
            )
        }

        val dsmForFocus = com.nendo.argosy.DualScreenManagerHolder.instance
        val focusPickerOpen by (
            dsmForFocus?.focusPickerOpen
                ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
            ).collectAsState()
        val focusPickerIndex by (
            dsmForFocus?.focusPickerIndex
                ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
            ).collectAsState()
        val focusDisplays = remember(hasPresentationScreen, focusPickerOpen) {
            dsmForFocus?.focusableDisplays()?.map { screen ->
                com.nendo.argosy.ui.components.DisplayFocusTarget(screen.displayId, screen.number)
            }.orEmpty()
        }
        val canSwapRoles = com.nendo.argosy.ui.dualscreen.selectSwapModeState() != null
        if (presentationShowsHints) {
            com.nendo.argosy.ui.components.CompanionAppBar(
                apps = uiState.homeApps,
                onAppClick = { viewModel.launchTileApp(it) },
                focusedIndex = if (uiState.appBarFocused) {
                    uiState.appBarIndex
                } else {
                    com.nendo.argosy.ui.components.APP_BAR_NOTHING_FOCUSED
                },
                onOpenDrawer = { viewModel.openAppDrawer() },
                onAppLongPress = { packageName ->
                    val index = uiState.homeApps.indexOf(packageName)
                    if (index >= 0) {
                        viewModel.moveAppBarFocus(index - uiState.appBarIndex)
                        viewModel.openAppBarAppMenu()
                    }
                },
                onKeyboardToggle = {
                    com.nendo.argosy.DualScreenManagerHolder.instance?.toggleUpperKeyboard()
                },
                focusDisplays = focusDisplays,
                focusPickerOpen = focusPickerOpen,
                focusPickerIndex = focusPickerIndex,
                onFocusPickerToggle = dsmForFocus?.let {
                    { if (focusPickerOpen) it.closeFocusPicker() else it.openFocusPicker() }
                },
                onFocusPickerMove = { dsmForFocus?.moveFocusPicker(it) },
                onFocusDisplay = { displayId ->
                    dsmForFocus?.focusDisplay(displayId)
                    dsmForFocus?.closeFocusPicker()
                },
                onSwapRoles = dsmForFocus
                    ?.takeIf { canSwapRoles }
                    ?.let { { it.swapRoles() } },
                toolsOpen = uiState.appBarToolsOpen,
                toolIndex = uiState.appBarToolIndex,
                onToolsToggle = viewModel::toggleAppBarTools,
                onToolFocus = viewModel::focusAppBarTool,
                onToolsDismiss = viewModel::dismissAppBarTools,
                maximumWidth = navigationPillWidth.takeIf { it > 0.dp } ?: Dimens.breadcrumbMaxWidth,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingMd)
                    .onSizeChanged { size ->
                        val measured = with(localDensity) { size.height.toDp() }
                        if (measured != dockHeight) dockHeight = measured
                    }
            )
        }

        uiState.appDrawer?.let { drawer ->
            com.nendo.argosy.ui.components.CompanionAppDrawer(
                apps = drawer.apps,
                focusIndex = drawer.focusIndex,
                onLaunch = { packageName ->
                    val index = drawer.apps.indexOfFirst { it.packageName == packageName }
                    if (index >= 0) viewModel.moveAppDrawer(index - drawer.focusIndex)
                    viewModel.confirmAppDrawer()
                },
                onLongPress = { packageName ->
                    val index = drawer.apps.indexOfFirst { it.packageName == packageName }
                    if (index >= 0) viewModel.moveAppDrawer(index - drawer.focusIndex)
                    viewModel.openAppBarAppMenu()
                },
                onDismiss = { viewModel.dismissAppDrawer() }
            )
        }

        uiState.appBarMenu?.let { menu ->
            com.nendo.argosy.ui.components.AppLaunchMenu(
                appLabel = menu.label,
                rows = menu.rows,
                focusIndex = menu.focusIndex,
                isAppHidden = menu.isHidden,
                isOnSecondaryHome = menu.isPinned,
                onSelect = { index ->
                    viewModel.moveAppBarAppMenu(index - menu.focusIndex)
                    viewModel.confirmAppBarAppMenu()
                },
                onDismiss = { viewModel.dismissAppBarAppMenu() }
            )
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
                    isDownloadPaused = downloadIndicators.value[focusedGame.id]?.isPaused == true,
                    isPlatformRow = uiState.currentRow is HomeRow.Platform,
                    focusIndex = uiState.gameMenuFocusIndex,
                    onDismiss = { viewModel.toggleGameMenu() },
                    onPrimaryAction = {
                        viewModel.toggleGameMenu()
                        viewModel.activateFocusedGame(focusedGame)
                    },
                    playDisplays = uiState.gameMenuDisplays,
                    onPlayOnDisplay = { index ->
                        viewModel.moveGameMenuFocus(index + 1 - uiState.gameMenuFocusIndex)
                        viewModel.confirmGameMenuSelection(onGameSelect)
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
                    hasSiblingGroup = uiState.gameMenuHasSiblingGroup,
                    onActiveVariant = { viewModel.openActiveVariant(focusedGame.id) },
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

        val siblingChoiceState by viewModel.siblingChoiceState.collectAsState()
        com.nendo.argosy.ui.screens.common.SiblingChoiceModalHost(
            state = siblingChoiceState,
            onMove = viewModel::moveSiblingChoiceFocus,
            onFocus = viewModel::setSiblingChoiceFocus,
            onConfirm = viewModel::confirmSiblingChoice,
            onDismiss = viewModel::dismissSiblingChoice
        )

        val pendingTileAdd = uiState.customGrid.pendingAdd
        if (pendingTileAdd != null) {
            com.nendo.argosy.ui.primitives.ArgosyConfirmModal(
                title = stringResource(R.string.home_tile_add_title),
                message = stringResource(R.string.home_tile_add_message, pendingTileAdd.title),
                confirmLabel = stringResource(R.string.home_tile_add_confirm),
                cancelLabel = stringResource(R.string.home_tile_add_cancel),
                focusedIndex = uiState.customGrid.pendingAddFocusIndex,
                onConfirm = viewModel::confirmPendingTileAdd,
                onDismiss = viewModel::dismissPendingTileAdd
            )
        }

        if (uiState.customGrid.showMenu) {
            val grid = uiState.customGrid
            val menuTile = grid.focusedTile
            val hiddenFallback = stringResource(R.string.ui_custom_grid_hidden_tile_unnamed)
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                header = if (grid.menuListsHidden) {
                    stringResource(R.string.ui_custom_grid_hidden_tiles_title)
                } else {
                    stringResource(R.string.ui_custom_tile_menu_header)
                },
                title = if (grid.menuListsHidden) {
                    pluralStringResource(
                        R.plurals.ui_custom_grid_hidden_count,
                        grid.hiddenTiles.size,
                        grid.hiddenTiles.size
                    )
                } else {
                    menuTile?.let { uiState.tileContentFor(it, context)?.label }.orEmpty()
                },
                entries = if (grid.menuListsHidden) {
                    grid.hiddenTiles.map { uiState.tileContentFor(it, context)?.label ?: hiddenFallback }
                } else {
                    grid.menuActions.map { context.getString(it.labelRes) }
                },
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
                purpose = uiState.customGrid.pickerPurpose,
                canDeletePage = uiState.customGrid.canDeletePage,
                onDeletePage = viewModel::deleteCustomGridPage
            )
        }

        val mediaTileSetup = uiState.mediaTileSetup
        if (mediaTileSetup != null && uiState.showMediaTileSetup) {
            com.nendo.argosy.ui.components.MediaTileSetupModal(
                setup = mediaTileSetup,
                onSelect = viewModel::confirmMediaTileSetupAt,
                onCommit = {
                    viewModel.confirmMediaTileSetupAt(mediaTileSetup.picker.confirmIndex)
                },
                onDismiss = viewModel::backFromMediaTileSetup
            )
        }

        val featureTileSetup = uiState.featureTileSetup
        if (featureTileSetup != null && uiState.customGrid.isFeatureSetupOpen) {
            com.nendo.argosy.ui.components.FeatureTileSetupModal(
                setup = featureTileSetup,
                onSelect = viewModel::confirmFeatureTileSetupAt,
                onDismiss = viewModel::backFromFeatureTileSetup
            )
        }

        val mediaTileNotice = uiState.mediaTileNotice
        if (mediaTileNotice != null) {
            com.nendo.argosy.ui.primitives.ArgosyConfirmModal(
                title = if (mediaTileNotice.placesOnDecline) {
                    stringResource(R.string.home_media_tile_notice_local_title)
                } else {
                    stringResource(R.string.home_media_tile_notice_download_title)
                },
                message = listOfNotNull(
                    mediaTileNotice.message,
                    mediaTileNotice.warning
                ).joinToString("\n\n"),
                confirmLabel = stringResource(mediaTileNotice.confirmLabelRes),
                cancelLabel = stringResource(mediaTileNotice.declineLabelRes),
                focusedIndex = mediaTileNotice.buttonIndex,
                onConfirm = viewModel::confirmMediaTileNotice,
                onDismiss = viewModel::dismissMediaTileNotice
            )
        }

        if (uiState.showTileFileBrowser) {
            val choosingBackground = uiState.customGrid.pendingBackgroundPage != null
            com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
                mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
                title = if (choosingBackground) {
                    stringResource(R.string.home_tile_file_browser_backdrop_title)
                } else {
                    stringResource(R.string.home_tile_file_browser_video_title)
                },
                fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter(
                    extensions = if (choosingBackground) {
                        PAGE_BACKGROUND_EXTENSIONS
                    } else {
                        com.nendo.argosy.core.media.VideoFileTypes.EXTENSIONS
                    }
                ),
                onPathSelected = viewModel::placeLocalVideoTile,
                onDismiss = viewModel::closeTileFileBrowser
            )
        }

        uiState.customGrid.pageChooser?.let { chooser ->
            com.nendo.argosy.ui.components.PageChooserModal(
                state = chooser,
                onSelect = { index ->
                    viewModel.movePageChooserFocus(index - chooser.focusIndex)
                    viewModel.confirmPageChooser()
                },
                onQueryChange = viewModel::setPageChooserQuery,
                onDismiss = viewModel::closePageChooser
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
    showStatus: Boolean,
    onNavigationWidthChanged: (Dp) -> Unit,
    headerOffset: androidx.compose.ui.unit.Dp = 0.dp,
    showSections: Boolean = true,
    compact: Boolean = false
) {
    val statusVisible = showStatus && hasStatusBarContent(
        LocalStatusBarItems.current,
        LocalArtworkScraping.current
    )
    val edge = if (compact || isStacked) Dimens.spacingMd else Dimens.spacingLg
    val verticalEdge = if (compact) Dimens.spacingXs else edge
    if (!showSections && !statusVisible) return
    if (!showSections) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = edge, vertical = verticalEdge)
                .offset(y = headerOffset),
            horizontalArrangement = if (isStacked) Arrangement.Center else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (statusVisible) SystemStatusBar()
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
            if (statusVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SystemStatusBar()
                }
            }
            PlatformBreadcrumb(
                uiState = uiState,
                onPreviousRow = onPreviousRow,
                onNextRow = onNextRow,
                onSelectRow = onSelectRow,
                fillAvailableWidth = true,
                onPillWidthChanged = onNavigationWidthChanged,
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
            fillAvailableWidth = !statusVisible,
            onPillWidthChanged = onNavigationWidthChanged,
            modifier = Modifier.weight(1f)
        )

        if (statusVisible) SystemStatusBar()
    }
}

@Composable
private fun PlatformBreadcrumb(
    uiState: HomeUiState,
    onPreviousRow: () -> Unit,
    onNextRow: () -> Unit,
    onSelectRow: (HomeRow) -> Unit,
    fillAvailableWidth: Boolean,
    onPillWidthChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = uiState.availableRows
    SectionBreadcrumb(
        labels = rows.map { row ->
            row.shortLabelRes?.let { stringResource(it) } ?: uiState.shortLabelFor(row)
        },
        currentIndex = rows.indexOf(uiState.currentRow).coerceAtLeast(0),
        onPrevious = onPreviousRow,
        onNext = onNextRow,
        onSelect = { index -> rows.getOrNull(index)?.let(onSelectRow) },
        fillAvailableWidth = fillAvailableWidth,
        onPillWidthChanged = onPillWidthChanged,
        modifier = modifier
    )
}

private val PAGE_BACKGROUND_EXTENSIONS: Set<String> =
    setOf("png", "jpg", "jpeg", "webp", "gif") + com.nendo.argosy.core.media.VideoFileTypes.EXTENSIONS

@Composable
private fun rememberHomeCarouselItems(
    items: List<HomeRowItem>,
    rowKey: String,
    repairedCoverPaths: Map<Long, String>
): List<CarouselItem> = remember(items, rowKey, repairedCoverPaths) {
    items.map { item ->
        when (item) {
            is HomeRowItem.Game -> CarouselItem.Game(
                key = "$rowKey-${item.game.id}",
                game = item.game,
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
                    text = stringResource(R.string.home_empty_pinned_row, collectionName),
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
                    text = stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = if (isRommConfigured) {
                        stringResource(R.string.home_empty_message_configured)
                    } else {
                        stringResource(R.string.home_empty_message_unconfigured)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (isRommConfigured) {
                    Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    FooterHint(
                        button = InputButton.A,
                        action = stringResource(R.string.home_empty_sync_hint)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameSelectOverlay(
    game: HomeGameUi,
    isDownloadPaused: Boolean,
    isPlatformRow: Boolean,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    playDisplays: List<com.nendo.argosy.ui.components.AppLaunchTarget>,
    onPlayOnDisplay: (Int) -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onAddToCollection: () -> Unit,
    hasSiblingGroup: Boolean,
    onActiveVariant: () -> Unit,
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
        game.needsInstall -> stringResource(R.string.home_quick_actions_install)
        game.isDownloaded -> stringResource(R.string.home_quick_actions_play)
        isDownloadPaused -> stringResource(R.string.home_quick_actions_resume_download)
        else -> stringResource(R.string.home_quick_actions_download)
    }

    val favoriteLabel = if (game.isFavorite) {
        stringResource(R.string.home_quick_actions_unfavorite)
    } else {
        stringResource(R.string.home_quick_actions_favorite)
    }
    val detailsLabel = stringResource(R.string.home_quick_actions_details)
    val addToCollectionLabel = stringResource(R.string.home_quick_actions_add_to_collection)
    val activeVariantLabel = stringResource(R.string.home_quick_actions_active_variant)
    val refreshDataLabel = stringResource(R.string.home_quick_actions_refresh_data)
    val resyncPlatformLabel = stringResource(R.string.home_quick_actions_resync_platform)
    val deleteDownloadLabel = stringResource(R.string.home_quick_actions_delete_download)
    val removeFromHomeLabel = stringResource(R.string.home_quick_actions_remove_from_home)
    val hideLabel = stringResource(R.string.home_quick_actions_hide)

    data class MenuEntry(
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val label: String,
        val isDangerous: Boolean = false,
        val onClick: () -> Unit
    )

    val options = buildList {
        add(MenuEntry(primaryIcon, primaryLabel, onClick = onPrimaryAction))
        if (playDisplays.size > 1 && game.isDownloaded) {
            playDisplays.forEachIndexed { index, target ->
                add(
                    MenuEntry(
                        Icons.Default.Tv,
                        stringResource(R.string.home_quick_actions_play_on_screen, target.number),
                        onClick = { onPlayOnDisplay(index) }
                    )
                )
            }
        }
        add(
            MenuEntry(
                if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                favoriteLabel,
                onClick = onFavorite
            )
        )
        add(MenuEntry(Icons.Default.Info, detailsLabel, onClick = onDetails))
        add(
            MenuEntry(
                Icons.AutoMirrored.Filled.PlaylistAdd,
                addToCollectionLabel,
                onClick = onAddToCollection
            )
        )
        if (hasSiblingGroup) {
            add(MenuEntry(Icons.Default.Layers, activeVariantLabel, onClick = onActiveVariant))
        }
        if (game.isRommGame || game.isAndroidApp) {
            add(MenuEntry(Icons.Default.Refresh, refreshDataLabel, onClick = onRefresh))
        }
        if (isPlatformRow && game.platformId > 0) {
            add(MenuEntry(Icons.Default.Refresh, resyncPlatformLabel, onClick = onResyncPlatform))
        }
    }
    val dangerousOptions = buildList {
        if (game.isDownloaded || game.needsInstall) {
            add(
                MenuEntry(
                    Icons.Default.DeleteOutline,
                    deleteDownloadLabel,
                    isDangerous = true,
                    onClick = onDelete
                )
            )
        }
        if (game.isAndroidApp) {
            add(
                MenuEntry(
                    Icons.Default.Home,
                    removeFromHomeLabel,
                    isDangerous = true,
                    onClick = onRemoveFromHome
                )
            )
        }
        add(MenuEntry(label = hideLabel, isDangerous = true, onClick = onHide))
    }

    ModalPresenceEffect()
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
                text = stringResource(R.string.home_quick_actions_heading),
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
