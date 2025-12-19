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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.components.FooterHint
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SubtleFooterBar
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.launch

private const val SCROLL_OFFSET = -25

@Composable
fun HomeScreen(
    isDefaultView: Boolean,
    onGameSelect: (Long) -> Unit,
    onNavigateToLibrary: (platformId: String?, sourceFilter: String?) -> Unit = { _, _ -> },
    onNavigateToDefault: () -> Unit,
    onDrawerToggle: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var skipNextProgrammaticScroll by remember { mutableStateOf(false) }
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
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SHOWCASE)
                viewModel.onResume()
                viewModel.refreshPlatforms()
                viewModel.refreshFavorites()
                viewModel.refreshRecentGames()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SHOWCASE)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val modalBlur by animateDpAsState(
        targetValue = if (uiState.showGameMenu || uiState.syncOverlayState != null) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    val backgroundBlurDp = (uiState.backgroundBlur * 0.5f).dp
    val saturationFraction = uiState.backgroundSaturation / 100f
    val opacityFraction = uiState.backgroundOpacity / 100f
    val overlayAlphaTop = 0.3f + (1f - opacityFraction) * 0.4f
    val overlayAlphaBottom = 0.7f + (1f - opacityFraction) * 0.3f

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayBaseColor = if (isDarkTheme) Color.Black else Color.White

    val effectiveBackgroundPath = if (uiState.useGameBackground) {
        uiState.focusedGame?.backgroundPath
    } else {
        uiState.customBackgroundPath
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().blur(modalBlur)) {
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
        val cardWidth = screenWidth * 0.16f
        val cardHeight = cardWidth * 4f / 3f
        val focusScale = 1.8f
        val railHeight = cardHeight * focusScale + 16.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HomeHeader(
                    sectionTitle = uiState.rowTitle,
                    showPlatformNav = false
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(swipeGestureModifier)
                )

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
                            EmptyState(
                                isRommConfigured = uiState.isRommConfigured,
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
                                showPlatformBadge = uiState.currentRow !is HomeRow.Platform,
                                onItemTap = { index -> viewModel.handleItemTap(index, onGameSelect) },
                                onItemLongPress = viewModel::handleItemLongPress,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(railHeight * 0.4f)
                            .align(Alignment.TopCenter)
                            .then(swipeGestureModifier)
                    )
                }

                val focusedGame = uiState.focusedGame
                if (focusedGame != null && !uiState.showGameMenu) {
                    SubtleFooterBar(
                        hints = listOf(
                            InputButton.DPAD_HORIZONTAL to "Game",
                            InputButton.DPAD_VERTICAL to "Platform",
                            InputButton.SOUTH to if (focusedGame.isDownloaded) "Play" else "Download",
                            InputButton.NORTH to if (focusedGame.isFavorite) "Unfavorite" else "Favorite",
                            InputButton.WEST to "Details"
                        ),
                        onHintClick = { button ->
                            when (button) {
                                InputButton.SOUTH -> {
                                    if (focusedGame.isDownloaded) {
                                        viewModel.launchGame(focusedGame.id)
                                    } else {
                                        viewModel.queueDownload(focusedGame.id)
                                    }
                                }
                                InputButton.NORTH -> viewModel.toggleFavorite(focusedGame.id)
                                InputButton.WEST -> onGameSelect(focusedGame.id)
                                else -> {}
                            }
                        },
                        modifier = Modifier.padding(top = Dimens.spacingSm)
                    )
                } else {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            GameInfo(
                title = uiState.focusedGame?.title ?: "",
                developer = uiState.focusedGame?.developer,
                rating = uiState.focusedGame?.rating,
                userRating = uiState.focusedGame?.userRating ?: 0,
                userDifficulty = uiState.focusedGame?.userDifficulty ?: 0,
                achievementCount = uiState.focusedGame?.achievementCount ?: 0,
                earnedAchievementCount = uiState.focusedGame?.earnedAchievementCount ?: 0,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .align(Alignment.TopEnd)
                    .padding(top = 144.dp)
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
                    onPlayOrDownload = {
                        viewModel.toggleGameMenu()
                        if (focusedGame.isDownloaded) {
                            viewModel.launchGame(focusedGame.id)
                        } else {
                            viewModel.queueDownload(focusedGame.id)
                        }
                    },
                    onFavorite = { viewModel.toggleFavorite(focusedGame.id) },
                    onDetails = {
                        viewModel.toggleGameMenu()
                        onGameSelect(focusedGame.id)
                    },
                    onRefresh = { viewModel.refreshGameData(focusedGame.id) },
                    onDelete = {
                        viewModel.toggleGameMenu()
                        viewModel.deleteLocalFile(focusedGame.id)
                    },
                    onHide = {
                        viewModel.toggleGameMenu()
                        viewModel.hideGame(focusedGame.id)
                    }
                )
            }
        }

        SyncOverlay(
            syncProgress = uiState.syncOverlayState?.syncProgress,
            gameTitle = uiState.syncOverlayState?.gameTitle
        )
    }
}

@Composable
private fun HomeHeader(
    sectionTitle: String,
    showPlatformNav: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(
                targetState = sectionTitle,
                transitionSpec = {
                    (slideInVertically { -it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { it / 2 } + fadeOut(tween(150)))
                },
                label = "section"
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (showPlatformNav) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = InputIcons.BumperLeft,
                        contentDescription = "Previous platform",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        painter = InputIcons.BumperRight,
                        contentDescription = "Next platform",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (developer != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = developer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val hasBadges = rating != null || userRating > 0 || userDifficulty > 0 || achievementCount > 0
        if (hasBadges) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (rating != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${rating.toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (userRating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$userRating/10",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (userDifficulty > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$userDifficulty/10",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (achievementCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$earnedAchievementCount/$achievementCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onItemTap: (Int) -> Unit = {},
    onItemLongPress: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val cardWidth = screenWidth * 0.16f
    val cardHeight = cardWidth * 4f / 3f
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

            when (item) {
                is HomeRowItem.Game -> {
                    GameCard(
                        game = item.game,
                        isFocused = isFocused,
                        focusScale = focusScale,
                        scaleFromBottom = true,
                        downloadIndicator = downloadIndicatorFor(item.game.id),
                        showPlatformBadge = showPlatformBadge,
                        modifier = Modifier
                            .graphicsLayer { this.translationX = translationX }
                            .width(cardWidth)
                            .height(cardHeight)
                            .combinedClickable(
                                onClick = { onItemTap(index) },
                                onLongClick = { onItemLongPress(index) },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    )
                }
                is HomeRowItem.ViewAll -> {
                    ViewAllCard(
                        isFocused = isFocused,
                        onClick = { onItemTap(index) },
                        modifier = Modifier
                            .graphicsLayer { this.translationX = translationX }
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
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GridBox()
                    GridBox()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            .size(24.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                RoundedCornerShape(4.dp)
            )
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun EmptyState(
    isRommConfigured: Boolean,
    onSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No games yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(16.dp))
            FooterHint(button = InputButton.SOUTH, action = "Sync Library")
        }
    }
}

@Composable
private fun GameSelectOverlay(
    game: HomeGameUi,
    focusIndex: Int,
    onDismiss: () -> Unit,
    onPlayOrDownload: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit
) {
    var currentIndex = 0
    val playIdx = currentIndex++
    val favoriteIdx = currentIndex++
    val detailsIdx = currentIndex++
    val refreshIdx = if (game.isRommGame) currentIndex++ else -1
    val deleteIdx = if (game.isDownloaded) currentIndex++ else -1
    val hideIdx = currentIndex

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .width(350.dp)
        ) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            MenuOption(
                icon = if (game.isDownloaded) Icons.Default.PlayArrow else Icons.Default.Download,
                label = if (game.isDownloaded) "Play" else "Download",
                isFocused = focusIndex == playIdx,
                onClick = onPlayOrDownload
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
            if (game.isRommGame) {
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

            if (game.isDownloaded) {
                MenuOption(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete Download",
                    isFocused = focusIndex == deleteIdx,
                    isDangerous = true,
                    onClick = onDelete
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
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}
