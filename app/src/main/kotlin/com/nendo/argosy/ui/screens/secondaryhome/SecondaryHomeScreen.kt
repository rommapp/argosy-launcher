package com.nendo.argosy.ui.screens.secondaryhome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SecondaryHomeScreen(
    viewModel: SecondaryHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SectionHeader(
            sectionTitle = uiState.currentSection?.title ?: "",
            onPrevious = { viewModel.previousSection() },
            onNext = { viewModel.nextSection() },
            hasPrevious = uiState.sections.size > 1,
            hasNext = uiState.sections.size > 1
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val gridState = rememberLazyGridState()

            LaunchedEffect(uiState.focusedGameIndex) {
                if (uiState.games.isNotEmpty()) {
                    gridState.animateScrollToItem(uiState.focusedGameIndex)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(uiState.columnsCount),
                state = gridState,
                contentPadding = PaddingValues(Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(uiState.games, key = { _, game -> game.id }) { index, game ->
                    GameGridItem(
                        game = game,
                        isFocused = index == uiState.focusedGameIndex,
                        isHoldingFromGamepad = index == uiState.focusedGameIndex && uiState.isHoldingFocusedGame,
                        onClick = {
                            viewModel.setFocusedGameIndex(index)
                            val (intent, options) = viewModel.getGameDetailIntent(game.id)
                            if (options != null) {
                                context.startActivity(intent, options)
                            } else {
                                context.startActivity(intent)
                            }
                        },
                        onLongPressAction = {
                            if (game.isPlayable) {
                                val (intent, options) = viewModel.launchGame(game.id)
                                intent?.let {
                                    if (options != null) {
                                        context.startActivity(it, options)
                                    } else {
                                        context.startActivity(it)
                                    }
                                }
                            } else {
                                viewModel.startDownload(game.id)
                            }
                        }
                    )
                }
            }
        }

        AppsRow(
            apps = uiState.homeApps,
            onAppClick = { packageName ->
                val (intent, options) = viewModel.getAppLaunchIntent(packageName)
                intent?.let {
                    if (options != null) {
                        context.startActivity(it, options)
                    } else {
                        context.startActivity(it)
                    }
                }
            },
            onEmptyClick = {
                val (intent, options) = viewModel.getAppsScreenIntent()
                if (options != null) {
                    context.startActivity(intent, options)
                } else {
                    context.startActivity(intent)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    sectionTitle: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .focusProperties { canFocus = false }
                .clickable(enabled = hasPrevious, onClick = onPrevious),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous section",
                tint = if (hasPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Text(
            text = sectionTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = Dimens.spacingLg)
                .weight(1f, fill = false),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .focusProperties { canFocus = false }
                .clickable(enabled = hasNext, onClick = onNext),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next section",
                tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun GameGridItem(
    game: SecondaryGameUi,
    isFocused: Boolean,
    isHoldingFromGamepad: Boolean = false,
    onClick: () -> Unit,
    onLongPressAction: () -> Unit
) {
    val imageData = game.coverPath?.let { path ->
        if (path.startsWith("/")) File(path) else path
    }
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    val isDownloading = game.downloadProgress != null
    val progress = game.downloadProgress ?: 0f

    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val whiteOverlay = remember { Animatable(0f) }
    var touchAnimationJob by remember { mutableStateOf<Job?>(null) }
    var gamepadAnimationJob by remember { mutableStateOf<Job?>(null) }
    var actionTriggered by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(isHoldingFromGamepad) {
        if (isHoldingFromGamepad) {
            isAnimating = true
            gamepadAnimationJob = launch {
                delay(250)
                scale.animateTo(
                    targetValue = 1.3f,
                    animationSpec = tween(durationMillis = 500, easing = EaseIn)
                )
            }
        } else if (gamepadAnimationJob != null) {
            gamepadAnimationJob?.cancel()
            gamepadAnimationJob = null
            launch { scale.animateTo(1f, tween(150)) }
            launch { alpha.animateTo(1f, tween(150)) }
            launch { whiteOverlay.animateTo(0f, tween(150)) }
            isAnimating = false
        }
    }

    val showDownloadProgress = isDownloading && !isAnimating

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused && !isAnimating) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(Dimens.radiusMd + 4.dp)
                    )
                } else Modifier
            )
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .pointerInput(game.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    actionTriggered = false

                    touchAnimationJob = scope.launch {
                        isAnimating = true

                        // Phase 1: Wait 250ms before starting scale animation
                        delay(250)

                        // Phase 2: Scale from 1.0 to 1.3 over 500ms (slow at first, accelerates)
                        scale.animateTo(
                            targetValue = 1.3f,
                            animationSpec = tween(durationMillis = 500, easing = EaseIn)
                        )

                        // Trigger the action at 750ms
                        actionTriggered = true
                        onLongPressAction()

                        // Phase 3: Quick scale to 2x while fading (fast at first, decelerates)
                        launch {
                            scale.animateTo(
                                targetValue = 2f,
                                animationSpec = tween(durationMillis = 100, easing = EaseOut)
                            )
                        }
                        launch {
                            alpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 100, easing = EaseOut)
                            )
                        }
                        launch {
                            whiteOverlay.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 100, easing = EaseOut)
                            )
                        }

                        // Wait for fade animation to complete, then reset
                        delay(100)
                        scale.snapTo(1f)
                        alpha.snapTo(1f)
                        whiteOverlay.snapTo(0f)
                        isAnimating = false
                    }

                    val up = waitForUpOrCancellation()

                    // Cancel animation and reset if released early (before action triggered)
                    if (!actionTriggered) {
                        touchAnimationJob?.cancel()
                        touchAnimationJob = null

                        scope.launch {
                            launch { scale.animateTo(1f, tween(150)) }
                            launch { alpha.animateTo(1f, tween(150)) }
                            launch { whiteOverlay.animateTo(0f, tween(150)) }
                            isAnimating = false
                        }

                        // If released quickly (before animation started significantly), treat as tap
                        if (up != null && scale.value < 1.1f) {
                            onClick()
                        }
                    }
                }
            }
            .padding(Dimens.spacingXs)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (showDownloadProgress) {
                    // Color image (bottom layer, visible from bottom up based on progress)
                    AsyncImage(
                        model = imageData,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val colorHeight = size.height * progress
                                clipRect(
                                    top = size.height - colorHeight,
                                    bottom = size.height
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    )
                    // Grayscale image (top layer, visible from top down based on remaining)
                    AsyncImage(
                        model = imageData,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val grayHeight = size.height * (1f - progress)
                                clipRect(
                                    top = 0f,
                                    bottom = grayHeight
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    )
                    // Progress indicator
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    // Non-downloading: grayscale if not playable, color if playable
                    AsyncImage(
                        model = imageData,
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (!game.isPlayable) ColorFilter.colorMatrix(grayscaleMatrix) else null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // White overlay for fade-to-white effect
                if (whiteOverlay.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = whiteOverlay.value))
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXs))

            Text(
                text = game.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AppsRow(
    apps: List<SecondaryAppUi>,
    onAppClick: (String) -> Unit,
    onEmptyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = Dimens.spacingSm)
    ) {
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .focusProperties { canFocus = false }
                    .clickable(onClick = onEmptyClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add apps for quick access",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        onClick = { onAppClick(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    app: SecondaryAppUi,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = AppIconData(app.packageName),
            contentDescription = app.label,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        )

        Spacer(modifier = Modifier.height(Dimens.spacingXs))

        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
