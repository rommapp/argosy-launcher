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
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nendo.argosy.ui.common.alwaysCrossfade
import com.nendo.argosy.ui.common.backgroundBlurDp
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.stackedScreenshotBackdrop
import com.nendo.argosy.ui.common.color
import com.nendo.argosy.ui.components.columns
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
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
import com.nendo.argosy.ui.input.ModalInputEffect
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
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.components.NEW_BADGE_TOP_OVERFLOW
import com.nendo.argosy.ui.components.FooterVariant
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.MemcardPickerModal
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.components.LocalArtworkScraping
import com.nendo.argosy.ui.components.LocalStatusBarItems
import com.nendo.argosy.ui.components.hasStatusBarContent
import com.nendo.argosy.ui.components.GameVideoPlayer
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
import com.nendo.argosy.ui.theme.animateModalBlur
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.modalScrim
import com.nendo.argosy.ui.theme.motionSpec
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.navigation.LocalNavTransitionOrigin
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch

private const val HOME_RETURN_ZOOM_SCALE = 1.04f
private const val HOME_BACKGROUND_CROSSFADE_MS = 380

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
    onNavigateToApps: () -> Unit = {},
    onPlayMedia: (itemId: String, startOver: Boolean) -> Unit = { _, _ -> },
    onMediaSelect: (String) -> Unit = {},
    videoPreviewBlocked: Boolean = false,
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
    val dockOnPresentation by (
        com.nendo.argosy.DualScreenManagerHolder.instance?.dockOnPresentation
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    val dockOnControl = presentationShowsHints && !dockOnPresentation
    var navigationPillWidth by remember { mutableStateOf(0.dp) }
    val downloadIndicators = viewModel.downloadIndicators.collectAsState()
    val streamFirst by viewModel.streamLauncher.streamFirst.collectAsState()
    val mediaDownloadProgress = viewModel.mediaDownloadProgress.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isAutoGrid = uiState.layoutKind == HomeLayoutKind.AUTO_GRID
    val isCustomGrid = uiState.layoutKind == HomeLayoutKind.CUSTOM_GRID
    val isColumns = uiState.layoutKind == HomeLayoutKind.COLUMNS
    val showDownloadedDot = !uiState.installedOnlyHome || uiState.currentRow == HomeRow.Recommendations
    val quickNavigation by viewModel.quickNavigationEnabled.collectAsState()
    val dockReorder by viewModel.dockReorder.collectAsState()
    val dockIsRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dockReorderInputHandler = remember(viewModel, dockIsRtl) {
        viewModel.createDockReorderInputHandler(dockIsRtl)
    }
    LaunchedEffect(dockOnControl) {
        if (!dockOnControl) viewModel.releaseAppBar()
    }
    com.nendo.argosy.ui.input.ModalInputEffect(
        active = dockOnControl && dockReorder?.controller == true && uiState.appBarFocused,
        handler = dockReorderInputHandler,
        onInputLost = viewModel::cancelAppBarReorder
    )
    if (dockOnControl && dockReorder?.controller == true) com.nendo.argosy.ui.input.ModalPresenceEffect()
    val rowButtons = if (presentationShowsHints || quickNavigation) InputButton.LT_RT else InputButton.LB_RB
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

    val toggleDrawer: () -> Unit = remember(onDrawerToggle, viewModel) {
        {
            viewModel.dismissAppBarTools()
            onDrawerToggle()
        }
    }
    val currentOnDrawerToggle by rememberUpdatedState(toggleDrawer)

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
                if (uiState.appBarFocused) viewModel.releaseAppBar()
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
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.LaunchIntent -> {
                    try {
                        context.startActivity(event.intent, event.options)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Failed to start activity", e)
                    }
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
                HomeEvent.NavigateToApps -> onNavigateToApps()
                is HomeEvent.PlayMedia -> onPlayMedia(event.itemId, event.startOver)
                is HomeEvent.NavigateToMediaDetail -> onMediaSelect(event.itemId)
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onGameSelect, toggleDrawer, isDefaultView, dockIsRtl) {
        viewModel.createInputHandler(
            isDefaultView = isDefaultView,
            onGameSelect = onGameSelect,
            onNavigateToDefault = onNavigateToDefault,
            onDrawerToggle = toggleDrawer,
            dockIsRtl = dockIsRtl,
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
    val returnZoom = remember { Animatable(1f) }
    var returnZoomPivot by remember { mutableStateOf(TransformOrigin.Center) }
    val transitionOrigin = LocalNavTransitionOrigin.current
    val skipReturnZoom by rememberUpdatedState(reducedMotion())
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
                viewModel.onResume()
                viewModel.refreshPlatforms()
                viewModel.refreshFavorites()
                viewModel.refreshRecentGames()
                viewModel.republishCompanionDetail()
                viewModel.onHomeShown()
                scope.launch {
                    if (returnZoom.value != 1f) {
                        returnZoom.animateTo(1f, tween(Motion.durationPage, easing = Motion.argosyEase))
                    }
                }
            }
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.cancelAppBarReorder()
                if (viewModel.onHomeStopped() && !skipReturnZoom) {
                    returnZoomPivot = (lifecycleOwner as? NavBackStackEntry)
                        ?.let { entry -> transitionOrigin?.pivotFor(entry) }
                        ?: TransformOrigin.Center
                    scope.launch { returnZoom.snapTo(HOME_RETURN_ZOOM_SCALE) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_HOME)
        viewModel.republishCompanionDetail()
        viewModel.onHomeShown()
        onDispose {
            viewModel.cancelAppBarReorder()
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
            viewModel.dismissAppBarTools()
            viewModel.onHomeHidden()
        }
    }

    val siblingChoiceOpen = viewModel.siblingChoiceState.collectAsState().value != null
    val modalOpen = uiState.showGameMenu || uiState.syncOverlayState != null ||
        uiState.changelogEntry != null || uiState.discPickerState != null ||
        uiState.memcardPickerState != null || siblingChoiceOpen
    val modalBlur by animateModalBlur(modalOpen)

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
            onDismiss = { viewModel.dismissDiscPicker() },
            wrapMode = { viewModel.menuWrapMode }
        )
    }

    val memcardPickerInputHandler = remember(viewModel) {
        com.nendo.argosy.ui.input.MemcardPickerInputHandler(
            getCards = { uiState.memcardPickerState?.cards ?: emptyList() },
            getFocusIndex = { uiState.memcardPickerFocusIndex },
            onFocusChange = { viewModel.setMemcardPickerFocusIndex(it) },
            onSelect = { viewModel.selectMemcard(it) },
            onDismiss = { viewModel.dismissMemcardPicker() },
            wrapMode = { viewModel.menuWrapMode }
        )
    }

    ModalInputEffect(
        active = uiState.changelogEntry != null,
        handler = changelogInputHandler,
        inputDispatcher = inputDispatcher
    )

    val showDiscPicker = uiState.discPickerState != null
    LaunchedEffect(showDiscPicker) {
        if (showDiscPicker) viewModel.setDiscPickerFocusIndex(0)
    }
    ModalInputEffect(
        active = showDiscPicker,
        handler = discPickerInputHandler,
        inputDispatcher = inputDispatcher
    )

    val showMemcardPicker = uiState.memcardPickerState != null
    LaunchedEffect(showMemcardPicker) {
        if (showMemcardPicker) viewModel.setMemcardPickerFocusIndex(0)
    }
    ModalInputEffect(
        active = showMemcardPicker,
        handler = memcardPickerInputHandler,
        inputDispatcher = inputDispatcher
    )

    LaunchedEffect(uiState.focusedGame?.id) {
        val currentGameId = uiState.focusedGame?.id
        if (currentGameId != videoPlayedForGameId) {
            videoPlayedForGameId = null
            suppressVideoPreview = false
        }
    }

    val dsmForFocus = com.nendo.argosy.DualScreenManagerHolder.instance
    val focusPickerOpen by (
        dsmForFocus?.focusPickerOpen
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
        ).collectAsState()
    val previewBlocked = videoPreviewBlocked || focusPickerOpen || uiState.showGameMenu ||
        uiState.appBarMenu != null ||
        uiState.showAddToCollectionModal || uiState.showCreateCollectionDialog ||
        uiState.discPickerState != null || uiState.memcardPickerState != null ||
        uiState.syncOverlayState != null || uiState.changelogEntry != null || siblingChoiceOpen ||
        dockReorder != null
    val currentPreviewBlocked by rememberUpdatedState(previewBlocked)
    val videoReduced = reducedMotion()

    LaunchedEffect(
        uiState.focusedGame?.id,
        uiState.focusedGame?.youtubeVideoId,
        uiState.focusedGame?.videoNormalizedPath,
        uiState.videoWallpaperEnabled,
        uiState.videoWallpaperDelayMs,
        uiState.layoutKind,
        previewBlocked,
        suppressVideoPreview,
        videoReduced
    ) {
        scheduleHomeVideoPreview(
            state = uiState,
            blocked = previewBlocked,
            suppressed = suppressVideoPreview,
            reducedMotion = videoReduced,
            alreadyPlayed = videoPlayedForGameId == uiState.focusedGame?.id,
            clipFor = viewModel::videoClipFor,
            canStart = { gameId ->
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    uiState.videoWallpaperEnabled && !currentPreviewBlocked &&
                    !suppressVideoPreview && videoPlayedForGameId != gameId
            },
            deactivate = viewModel::deactivateVideoPreview,
            start = { gameId, clip ->
                videoPlayedForGameId = gameId
                viewModel.startVideoPreviewLoading(gameId, clip)
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.dismissAppBarTools()
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
        animationSpec = motionSpec(tween(Motion.durationMedium)),
        label = "backgroundAlpha"
    )

    val videoModeFooterOffset by animateDpAsState(
        targetValue = if (uiState.isVideoPreviewActive) 48.dp else 0.dp,
        animationSpec = motionSpec(tween(Motion.durationMedium)),
        label = "footerOffset"
    )

    val videoModeHeaderOffset by animateDpAsState(
        targetValue = if (uiState.isVideoPreviewActive) (-60).dp else 0.dp,
        animationSpec = motionSpec(tween(Motion.durationMedium)),
        label = "headerOffset"
    )

    val videoModeRailOffsetX by animateDpAsState(
        targetValue = if (uiState.isVideoPreviewActive) (-40).dp else 0.dp,
        animationSpec = motionSpec(tween(Motion.durationMedium)),
        label = "railOffsetX"
    )

    val columnsTakeoverProgress by animateFloatAsState(
        targetValue = if (isColumns && uiState.videoPreviewPhase.isExpanded) 1f else 0f,
        animationSpec = motionSpec(tween(Motion.durationMedium, easing = Motion.argosyEase)),
        label = "columnsTakeover"
    )
    var focusedColumnBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    OfferColumnsLaunchHero(
        enabled = isColumns && !uiState.videoPreviewPhase.isExpanded,
        item = uiState.columns.focusedItem,
        columnBounds = focusedColumnBounds
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
    val drawsScreenArt = when (uiState.layoutKind) {
        HomeLayoutKind.CAROUSEL -> true
        HomeLayoutKind.AUTO_GRID, HomeLayoutKind.CUSTOM_GRID, HomeLayoutKind.COLUMNS -> false
    }
    val showArtLayer = drawsScreenArt &&
        (!backdropEnabled || uiState.homeBackgroundMode == HomeBackgroundMode.GAME_ART)

    var backgroundIsScreenshot = false
    val runningFrame = uiState.focusedGame
        ?.takeIf { uiState.useGameBackground }
        ?.let { com.nendo.argosy.ui.common.LocalRunningGames.current.markFor(it.id) }
        ?.takeIf { it.snapshotPath != null }
    val runningFrameKey = runningFrame?.let { "${it.snapshotPath}@${it.snapshotTakenAtMillis}" }
    val effectiveBackgroundPath = if (runningFrame != null) {
        runningFrame.snapshotPath
    } else if (uiState.useGameBackground) {
        uiState.focusedGame?.let { game ->
            val art = rememberHomeBackgroundArt(game)
            backgroundIsScreenshot = art?.isScreenshot == true
            art?.path
        }
    } else {
        uiState.customBackgroundPath
    }

    AnimatedContent(
        targetState = uiState.isLoading,
        transitionSpec = {
            fadeIn(tween(Motion.durationOverlay)) togetherWith fadeOut(tween(Motion.durationOverlay))
        },
        label = "loading"
    ) { isLoading ->
        if (isLoading) {
            SplashOverlay()
        } else {
    androidx.compose.runtime.CompositionLocalProvider(
        com.nendo.argosy.ui.screens.stream.LocalStreamFirst provides streamFirst
    ) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = returnZoom.value
                scaleY = returnZoom.value
                transformOrigin = returnZoomPivot
            }
    ) {
        val isPortrait = maxWidth <= maxHeight
        val compactHeader = (isAutoGrid && !uiState.autoGridConfig.showTitles) ||
            maxHeight < ComponentDefaults.FrostedSurface.compactHeaderViewportHeightDp.dp
        val headerHorizontalPadding = if (compactHeader || isPortrait) Dimens.spacingMd else Dimens.spacingLg
        val chromeVerticalPadding = if (compactHeader) {
            ComponentDefaults.FrostedSurface.compactChromeVerticalPaddingDp.dp * LocalUiScale.current.scale
        } else {
            headerHorizontalPadding
        }
        val defaultHeaderHeight = Dimens.headerHeight
        var headerBlockHeight by remember { mutableStateOf(defaultHeaderHeight) }
        var dockHeight by remember { mutableStateOf(0.dp) }
        val localDensity = LocalDensity.current
    val edgeThresholdPx = with(LocalDensity.current) {
        ComponentDefaults.Carousel.edgeTouchHeightDp.dp.toPx()
    }

        val swipeGestureModifier = Modifier
            .pointerInput(swipeThreshold, isColumns) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        when {
                            isColumns && totalDragY < -swipeThreshold -> viewModel.stepColumnGame(1)
                            isColumns && totalDragY > swipeThreshold -> viewModel.stepColumnGame(-1)
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
                        val pageBlur = backgroundBlurDp + modalBlur
                        com.nendo.argosy.ui.components.PageBackdrop(
                            path = path,
                            modifier = Modifier
                                .fillMaxSize()
                                .let { if (pageBlur > 0.dp) it.blur(pageBlur) else it }
                                .modalScrim(modalOpen)
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
                        val backgroundCrossfade = alwaysCrossfade(HOME_BACKGROUND_CROSSFADE_MS)
                        val backgroundRequest = remember(
                            backgroundModel, backgroundIsScreenshot, runningFrameKey, backgroundCrossfade
                        ) {
                            ImageRequest.Builder(backgroundContext)
                                .data(backgroundModel)
                                .size(640, 360)
                                .transitionFactory(backgroundCrossfade)
                                .apply { if (backgroundIsScreenshot) stackedScreenshotBackdrop() }
                                .apply {
                                    runningFrameKey?.let { key ->
                                        memoryCacheKey(key)
                                        diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                    }
                                }
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
                                    val totalBlur = backgroundBlurDp + modalBlur
                                    if (totalBlur > 0.dp) it.blur(totalBlur) else it
                                }
                                .modalScrim(modalOpen)
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

            if (!isColumns && !previewBlocked && (uiState.isVideoPreviewLoading || uiState.isVideoPreviewActive)) {
                val videoAlpha by animateFloatAsState(
                    targetValue = if (uiState.isVideoPreviewActive) 1f else 0f,
                    animationSpec = tween(Motion.durationMedium),
                    label = "videoAlpha"
                )
                uiState.videoPreviewClip?.let { clip ->
                    val videoId = clip.key
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = videoAlpha }
                    ) {
                        GameVideoPlayer(
                            clip = clip,
                            muted = uiState.muteVideoPreview,
                            onReady = {
                                viewModel.activateVideoPreview(
                                    videoId,
                                    currentPreviewBlocked,
                                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                                )
                            },
                            onError = {
                                if (viewModel.uiState.value.videoPreviewId == videoId) {
                                    viewModel.cancelVideoPreviewLoading()
                                }
                            }
                        )
                    }
                }
            }

        BoxWithConstraints(modifier = Modifier
            .fillMaxSize()
            .then(swipeGestureModifier)
        ) {
            val availableCarouselWidth = maxWidth
            val showTitle = !presentationShowsHints && !isAutoGrid && !isCustomGrid && !isColumns &&
                !uiState.isMediaRow
            val footerHeight = if (presentationShowsHints) {
                (if (dockOnControl) dockHeight else 0.dp) + chromeVerticalPadding
            } else {
                withNavBarClearance(Dimens.footerHeight)
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
                rowAlignment = uiState.carouselConfig.rowAlignment,
                centeredFocus = uiState.carouselConfig.focusPosition == HomeFocusPosition.CENTER,
                mirrored = mirrored,
                badgeOverflow = NEW_BADGE_TOP_OVERFLOW.value,
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
                badgeOverflow = NEW_BADGE_TOP_OVERFLOW.value,
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
            val fillsBody = isAutoGrid || isCustomGrid || isColumns
            val railHeight = if (fillsBody) {
                (maxHeight - headerBlockHeight - footerHeight - edge).coerceAtLeast(0.dp)
            } else geometry.railHeight.dp
            val railTop = if (fillsBody) headerBlockHeight else geometry.railTop.dp
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
                    .then(
                        if (isColumns) Modifier else Modifier.offset(x = videoModeRailOffsetX, y = videoModeFooterOffset)
                    )
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
                                        uiState.appBarFocused -> {
                                            viewModel.releaseAppBar()
                                            viewModel.setCustomGridCell(cell)
                                        }
                                        onFocused -> inputHandler.onConfirm()
                                        else -> viewModel.setCustomGridCell(cell)
                                    }
                                },
                                onSwipePage = { delta ->
                                    viewModel.releaseAppBar()
                                    viewModel.turnCustomGridPage(delta)
                                },
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
                                    viewModel.releaseAppBar()
                                    viewModel.setCustomGridCell(cell)
                                    viewModel.openTileMenu()
                                },
                                onBadgeTap = { index ->
                                    viewModel.releaseAppBar()
                                    viewModel.engageRaTileAt(index)
                                },
                                onBandTap = {
                                    viewModel.releaseAppBar()
                                    inputHandler.onConfirm()
                                },
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
                        isColumns && !uiState.columns.isLoaded -> LoadingState()
                        isColumns && uiState.columns.columns.isEmpty() -> EmptyState(
                            isRommConfigured = uiState.isRommConfigured,
                            currentRow = HomeRow.Continue,
                            isPinnedLoading = false,
                            onSync = { viewModel.syncFromRomm() }
                        )
                        isColumns -> HomeColumnsSurface(
                            uiState = uiState,
                            showFocus = !uiState.appBarFocused,
                            onSlotCount = viewModel::setColumnSlotCount,
                            onColumnTap = { offset ->
                                viewModel.releaseAppBar()
                                if (offset == 0) inputHandler.onConfirm() else viewModel.moveColumn(offset)
                            },
                            onColumnLongPress = {
                                viewModel.releaseAppBar()
                                inputHandler.onLongConfirm()
                            },
                            onStep = { delta ->
                                viewModel.releaseAppBar()
                                viewModel.stepColumnGame(delta)
                            },
                            onCoverLoadFailed = viewModel::repairCoverImage,
                            onCoverLoaded = viewModel::extractGradientForGame,
                            downloadIndicatorFor = { item ->
                                when (item) {
                                    is HomeRowItem.Game ->
                                        downloadIndicators.value[item.game.id] ?: GameDownloadIndicator.NONE
                                    is HomeRowItem.Media -> mediaDownloadProgress.value.indicatorFor(item.media)
                                    is HomeRowItem.ViewAll -> GameDownloadIndicator.NONE
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            takeoverProgress = { columnsTakeoverProgress },
                            focusedBackground = { item, frame ->
                                HomeColumnHero(item, frame)
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .onGloballyPositioned { focusedColumnBounds = it.boundsInRoot() }
                                )
                            }
                        )
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
                                showFocusVisuals = !uiState.appBarFocused,
                                useBoxArt = uiState.boxArt3d,
                                showDownloadedIndicator = showDownloadedDot,
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
                                showFocusVisuals = !uiState.appBarFocused,
                                availableWidth = availableCarouselWidth,
                                verticalContentPadding = railTop,
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
                                newBadgePlacement = uiState.newBadgePlacement,
                                showDownloadedIndicator = showDownloadedDot,
                                onCoverLoadFailed = viewModel::repairCoverImage,
                                onCoverLoaded = viewModel::extractGradientForGame,
                                onPosterLoaded = viewModel::extractGradientForMedia,
                                onItemTap = { index -> viewModel.handleItemTap(index, onGameSelect) },
                                onItemLongPress = viewModel::handleItemLongPress,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .requiredHeight(railHeight + railTop * 2)
                            )
                        }
                    }
                }

                val focusedGame = uiState.focusedGame
                val columnsSectionsLabel = stringResource(R.string.home_footer_columns_sections)
                if (dockReorder?.controller == true) {
                    FooterHints(
                        hints = com.nendo.argosy.ui.components.liftedReorderHints(
                            move = stringResource(R.string.dock_hint_move),
                            cancel = stringResource(R.string.dock_hint_cancel),
                            moveButton = InputButton.DPAD_HORIZONTAL
                        ),
                        forced = true,
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> dockReorderInputHandler.onConfirm()
                                InputButton.B -> dockReorderInputHandler.onBack()
                                else -> Unit
                            }
                        }
                    )
                    FooterSpacer()
                } else if (uiState.appBarFocused) {
                    if (uiState.appBarIndex in uiState.homeApps.indices) {
                        FooterHints(
                            forced = true,
                            hints = listOf(InputButton.A to stringResource(R.string.dock_hint_options)),
                            onHintClick = { if (it == InputButton.A) viewModel.openDockAppMenu() }
                        )
                        FooterSpacer()
                    }
                } else if (isColumns && focusedGame == null && uiState.focusedMedia == null) {
                    FooterSpacer()
                } else if (isCustomGrid) {
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
                            if (isColumns) add(InputButton.LT_RT to columnsSectionsLabel)
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
                    if (!uiState.videoChromeHidden) {
                        val focusedIndicator = downloadIndicators.value[focusedGame.id] ?: GameDownloadIndicator.NONE
                        FooterHints(
                            hints = listOfNotNull(
                                when {
                                    isAutoGrid -> rowButtons to stringResource(R.string.home_footer_game_section)
                                    isColumns -> InputButton.LT_RT to columnsSectionsLabel
                                    else -> null
                                },
                                InputButton.A to stringResource(
                                    when (
                                        homeGamePrimaryAction(
                                            game = focusedGame,
                                            isRunning = com.nendo.argosy.ui.common.LocalRunningGames.current
                                                .isRunning(focusedGame.id),
                                            indicator = focusedIndicator,
                                            streamLeads = streamFirst.leads(
                                                focusedGame.platformSlug,
                                                focusedGame.isRommGame,
                                                focusedGame.isOnDevice(focusedIndicator)
                                            )
                                        )
                                    ) {
                                        HomeGamePrimaryAction.RESUME_GAME -> R.string.home_footer_game_resume_game
                                        HomeGamePrimaryAction.INSTALL -> R.string.home_footer_game_install
                                        HomeGamePrimaryAction.PLAY -> R.string.home_footer_game_play
                                        HomeGamePrimaryAction.RESUME_DOWNLOAD ->
                                            R.string.home_footer_game_resume_download
                                        HomeGamePrimaryAction.STREAM -> R.string.stream_home_footer_stream
                                        HomeGamePrimaryAction.DOWNLOAD -> R.string.home_footer_game_download
                                    }
                                ),
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
                                (if (presentationShowsHints) rowButtons else InputButton.DPAD_VERTICAL) to
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
                animationSpec = motionSpec(tween(Motion.durationMedium)),
                label = "videoTitleBackgroundOffset"
            )
            val videoTextColor by animateColorAsState(
                targetValue = if (uiState.isVideoPreviewActive) Color.White else Color.Unspecified,
                animationSpec = motionSpec(tween(Motion.durationMedium)),
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
                            subtitle = if (game == null) uiState.focusedMedia?.subtitle else null,
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

        if (isColumns && !previewBlocked) {
            HomeColumnsTakeover(
                uiState = uiState,
                columnBounds = { focusedColumnBounds },
                progress = { columnsTakeoverProgress },
                onReady = { videoId ->
                    viewModel.activateVideoPreview(
                        videoId,
                        currentPreviewBlocked,
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    )
                },
                onError = { videoId ->
                    if (viewModel.uiState.value.videoPreviewId == videoId) viewModel.cancelVideoPreviewLoading()
                },
                onBeginTakeover = viewModel::beginVideoTakeover,
                onShowInfo = viewModel::showVideoTakeoverInfo,
                onFinishRestore = viewModel::finishVideoTakeoverRestore,
                onTap = {
                    viewModel.releaseAppBar()
                    if (!viewModel.restoreVideoTakeover()) inputHandler.onConfirm()
                },
                onLongPress = {
                    viewModel.releaseAppBar()
                    if (!viewModel.restoreVideoTakeover()) inputHandler.onLongConfirm()
                },
                onCoverLoadFailed = viewModel::repairCoverImage,
                onCoverLoaded = viewModel::extractGradientForGame
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -columnsTakeoverProgress * size.height
                    alpha = 1f - columnsTakeoverProgress
                }
                .then(swipeGestureModifier)
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
                showStatus = !presentationShowsHints &&
                    (!isPortrait || com.nendo.argosy.ui.components.LocalTransientHomeStatusVisible.current),
                onNavigationWidthChanged = { navigationPillWidth = it },
                edgePadding = ComponentDefaults.Carousel.headerEdgePaddingDp.dp,
                bottomPadding = chromeVerticalPadding,
                headerOffset = if (isColumns) 0.dp else videoModeHeaderOffset,
                showSections = !isCustomGrid && !isColumns
            )
        }

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
        if (dockOnControl) {
            com.nendo.argosy.ui.components.LauncherDock(
                apps = uiState.homeApps,
                reorder = dockReorder,
                onAppClick = { viewModel.launchTileApp(it) },
                onAppHold = { viewModel.openDockAppMenuFor(it) },
                onOpenDrawer = { viewModel.openAppsScreen() },
                focusDisplays = focusDisplays,
                focusPickerOpen = focusPickerOpen,
                focusPickerIndex = focusPickerIndex,
                canSwapRoles = canSwapRoles,
                toolsOpen = uiState.appBarToolsOpen,
                toolIndex = uiState.appBarToolIndex,
                onToolsToggle = viewModel::toggleAppBarTools,
                onToolFocus = viewModel::focusAppBarTool,
                onToolsDismiss = viewModel::dismissAppBarTools,
                topPadding = chromeVerticalPadding,
                focusedIndex = if (uiState.appBarFocused) {
                    uiState.appBarIndex
                } else {
                    com.nendo.argosy.ui.components.APP_BAR_NOTHING_FOCUSED
                },
                onKeyboardToggle = {
                    com.nendo.argosy.DualScreenManagerHolder.instance?.toggleUpperKeyboard()
                },
                maximumWidth = navigationPillWidth.takeIf { it > 0.dp } ?: Dimens.breadcrumbMaxWidth,
                onHeightChanged = { if (it != dockHeight) dockHeight = it },
                modifier = Modifier.align(Alignment.BottomCenter)
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
                        if (uiState.gameMenuStream?.leads == true) {
                            viewModel.downloadFocusedGame(focusedGame)
                        } else {
                            viewModel.activateFocusedGame(focusedGame)
                        }
                    },
                    stream = uiState.gameMenuStream,
                    onStream = {
                        viewModel.toggleGameMenu()
                        viewModel.streamLauncher.open(focusedGame.id)
                    },
                    playDisplays = uiState.gameMenuDisplays,
                    onPlayOnDisplay = { index ->
                        viewModel.moveGameMenuFocus(index + 1 - uiState.gameMenuFocusIndex)
                        viewModel.confirmGameMenuSelection(onGameSelect)
                    },
                    moveTargets = uiState.gameMenuMoveTargets.takeIf { uiState.gameMenuRunning != null }.orEmpty(),
                    onMoveToDisplay = { target ->
                        viewModel.moveRunningGame(target)
                        viewModel.toggleGameMenu()
                    },
                    isRunning = uiState.gameMenuRunning != null,
                    onCloseGame = {
                        viewModel.requestCloseRunningGame()
                        viewModel.toggleGameMenu()
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
                    onRemoveFromLibrary = {
                        viewModel.toggleGameMenu()
                        viewModel.removeFromLibrary(focusedGame.id)
                    },
                    onHide = {
                        viewModel.toggleGameMenu()
                        viewModel.hideGame(focusedGame.id)
                    }
                )
            }
        }

        com.nendo.argosy.ui.common.GameConfirmHost(viewModel.gameConfirm)
        com.nendo.argosy.ui.screens.running.RunningGameCloseHost(viewModel.runningGameMenu)
        com.nendo.argosy.ui.screens.stream.StreamLaunchHost(viewModel.streamLauncher)

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
                    grid.menuScreens.map { context.getString(grid.menuScreenLabelRes, it.number) } +
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

        val openFolder = uiState.customGrid.openFolder
        val folderOverlay = uiState.customGrid.folderOverlay
        if (openFolder != null && folderOverlay != null) {
            val folderTarget = openFolder.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Folder
            val folderLook = uiState.customGrid.openFolderAppearance
            com.nendo.argosy.ui.components.FolderOverlay(
                title = folderTarget?.name.orEmpty(),
                members = uiState.customGrid.openFolderMembers,
                contentFor = { tile -> uiState.tileContentFor(tile, context) },
                focusIndex = folderOverlay.focusIndex,
                isLoading = folderOverlay.isLoading,
                isAuto = uiState.customGrid.isOpenFolderAuto,
                columns = folderLook.memberSize.columns,
                accent = folderLook.accent.color,
                backdropPath = uiState.folderBackdropPath(openFolder),
                blurBackdrop = folderLook.backdrop == com.nendo.argosy.domain.model.FolderBackdropStyle.COVER_BLURRED,
                inputHandler = inputHandler.folderOverlayInput,
                onFocusMember = viewModel::focusFolderMember,
                onActivateFocused = { inputHandler.folderOverlayInput.onConfirm() },
                onRemoveFocused = if (uiState.customGrid.isOpenFolderAuto) {
                    null
                } else {
                    { viewModel.removeFocusedFolderMember() }
                },
                onDismiss = { viewModel.closeFolder() }
            )
        }

        val folderChooser = uiState.customGrid.folderChooser
        if (folderChooser != null) {
            com.nendo.argosy.ui.input.ModalInputEffect(active = true, handler = inputHandler.folderChooserInput)
            com.nendo.argosy.ui.components.CustomTileMenuModal(
                header = stringResource(R.string.ui_folder_chooser_title),
                title = null,
                entries = listOf(stringResource(R.string.ui_folder_chooser_new)) +
                    uiState.customGrid.folderChooserTargets.map {
                        (it.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Folder)?.name.orEmpty()
                    },
                focusIndex = folderChooser.focusIndex,
                onSelect = viewModel::confirmFolderChooserAt,
                onDismiss = viewModel::closeFolderChooser
            )
        }

        val appearanceFolder = uiState.customGrid.appearanceFolder
        val appearanceEditor = uiState.customGrid.folderAppearance
        if (appearanceFolder != null && appearanceEditor != null && uiState.customGrid.isFolderAppearanceShown) {
            com.nendo.argosy.ui.components.FolderAppearanceModal(
                folderName = (appearanceFolder.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Folder)
                    ?.name.orEmpty(),
                appearance = uiState.customGrid.appearanceOfEditedFolder,
                rows = uiState.customGrid.appearanceRows,
                focusIndex = appearanceEditor.focusIndex,
                inputHandler = inputHandler.folderAppearanceInput,
                onFocusRow = viewModel::focusFolderAppearanceRow,
                onCycle = viewModel::cycleFolderAppearanceRow,
                onActivate = viewModel::activateFolderAppearanceRow,
                onDismiss = { viewModel.closeFolderAppearance() }
            )
        }

        val folderNaming = uiState.customGrid.folderNaming
        if (folderNaming != null) {
            androidx.compose.runtime.key(folderNaming) {
                com.nendo.argosy.ui.screens.collections.dialogs.CollectionNameDialog(
                    title = stringResource(
                        if (folderNaming.folderId == null) {
                            R.string.ui_folder_name_title_new
                        } else {
                            R.string.ui_folder_name_title_rename
                        }
                    ),
                    label = stringResource(R.string.ui_folder_name_label),
                    confirmLabel = stringResource(R.string.ui_folder_name_confirm),
                    cancelLabel = stringResource(R.string.ui_folder_name_cancel),
                    initialName = folderNaming.initialName,
                    gamepadInput = true,
                    onDismiss = viewModel::cancelFolderNaming,
                    onSubmit = viewModel::submitFolderName
                )
            }
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
                searchFocused = uiState.customGrid.pickerSearchFocused,
                keyboardOpen = uiState.customGrid.pickerKeyboardOpen,
                onOpenKeyboard = viewModel::openTilePickerSearch,
                onCloseKeyboard = viewModel::closeTilePickerKeyboard,
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
            val choosingDecor = uiState.customGrid.pendingPicture != null
            com.nendo.argosy.ui.filebrowser.FileBrowserScreen(
                mode = com.nendo.argosy.ui.filebrowser.FileBrowserMode.FILE_SELECTION,
                title = when {
                    choosingBackground -> stringResource(R.string.home_tile_file_browser_backdrop_title)
                    choosingDecor -> stringResource(R.string.home_tile_file_browser_decor_title)
                    else -> stringResource(R.string.home_tile_file_browser_video_title)
                },
                fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter(
                    extensions = when {
                        choosingBackground -> PAGE_BACKGROUND_EXTENSIONS
                        choosingDecor -> DECOR_IMAGE_EXTENSIONS
                        else -> com.nendo.argosy.core.media.VideoFileTypes.EXTENSIONS
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
                    viewModel.focusPageChooserEntry(index)
                    viewModel.confirmPageChooser()
                },
                onQueryChange = viewModel::setPageChooserQuery,
                onOpenKeyboard = viewModel::openPageChooserKeyboard,
                onCloseKeyboard = viewModel::closePageChooserKeyboard,
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
    edgePadding: Dp,
    bottomPadding: Dp,
    headerOffset: androidx.compose.ui.unit.Dp = 0.dp,
    showSections: Boolean = true
) {
    val statusVisible = showStatus && hasStatusBarContent(
        LocalStatusBarItems.current,
        LocalArtworkScraping.current
    )
    if (!showSections && !statusVisible) return
    if (!showSections) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = edgePadding, top = edgePadding, end = edgePadding, bottom = bottomPadding)
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
                .padding(start = edgePadding, top = edgePadding, end = edgePadding, bottom = bottomPadding)
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
            .padding(start = edgePadding, top = edgePadding, end = edgePadding, bottom = bottomPadding)
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

private val DECOR_IMAGE_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp")

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
    stream: com.nendo.argosy.ui.screens.stream.StreamOffer?,
    onStream: () -> Unit,
    playDisplays: List<com.nendo.argosy.ui.components.AppLaunchTarget>,
    onPlayOnDisplay: (Int) -> Unit,
    moveTargets: List<com.nendo.argosy.ui.screens.running.RunningMoveTarget>,
    onMoveToDisplay: (com.nendo.argosy.ui.screens.running.RunningMoveTarget) -> Unit,
    isRunning: Boolean,
    onCloseGame: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onAddToCollection: () -> Unit,
    hasSiblingGroup: Boolean,
    onActiveVariant: () -> Unit,
    onRefresh: () -> Unit,
    onResyncPlatform: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onHide: () -> Unit
) {
    val primaryIcon = when {
        game.needsInstall -> Icons.Default.InstallMobile
        game.isDownloaded -> Icons.Default.PlayArrow
        else -> Icons.Default.Download
    }
    val primaryLabel = when {
        game.needsInstall -> stringResource(R.string.home_quick_actions_install)
        isRunning -> stringResource(R.string.home_quick_actions_resume_game)
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
    val removeFromLibraryLabel = stringResource(R.string.home_quick_actions_remove_from_library)
    val hideLabel = stringResource(R.string.home_quick_actions_hide)
    val closeGameLabel = stringResource(R.string.home_quick_actions_close_game)
    val moveLabels = moveTargets.map { stringResource(R.string.home_quick_actions_move_to_display, it.screenNumber) }

    data class MenuEntry(
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val label: String,
        val isDangerous: Boolean = false,
        val onClick: () -> Unit
    )

    val streamLabel = stream?.let {
        if (it.isLive) {
            stringResource(R.string.stream_entry_home_menu_resume)
        } else {
            stringResource(R.string.stream_entry_home_menu, it.containerLabel)
        }
    }
    val streamIcon = ImageVector.vectorResource(R.drawable.ic_stream_play_network)
    val options = buildList {
        val streamEntry = streamLabel?.let { MenuEntry(streamIcon, it, onClick = onStream) }
        if (stream?.leads == true && streamEntry != null) add(streamEntry)
        add(MenuEntry(primaryIcon, primaryLabel, onClick = onPrimaryAction))
        if (stream?.leads != true && streamEntry != null) add(streamEntry)
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
        moveTargets.forEachIndexed { index, target ->
            add(MenuEntry(Icons.Default.Tv, moveLabels[index], onClick = { onMoveToDisplay(target) }))
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
        if (game.canRemoveFromLibrary) {
            add(
                MenuEntry(
                    Icons.Default.RemoveCircleOutline,
                    removeFromLibraryLabel,
                    isDangerous = true,
                    onClick = onRemoveFromLibrary
                )
            )
        }
        add(MenuEntry(label = hideLabel, isDangerous = true, onClick = onHide))
        if (isRunning) {
            add(MenuEntry(Icons.Default.Close, closeGameLabel, isDangerous = true, onClick = onCloseGame))
        }
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
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLg))
                .clickableNoFocus {}
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
