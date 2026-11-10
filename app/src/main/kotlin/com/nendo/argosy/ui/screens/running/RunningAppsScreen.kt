package com.nendo.argosy.ui.screens.running

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import java.time.Instant
import com.nendo.argosy.R
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.libretro.slot.GameSlots
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.common.GameArtBackdrop
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberTaskSnapshotRequest
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.util.formatRelativeTime
import com.nendo.argosy.ui.common.rememberResolvedBackgroundPath
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.components.CustomTileMenuModal
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun RunningAppsScreen(
    onBack: () -> Unit,
    onOpenGame: (Long) -> Unit,
    viewModel: RunningAppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.openGame.collect { onOpenGame(it) }
    }
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) { viewModel.createInputHandler(onBack) }
    val menuInputHandler = remember(viewModel) { viewModel.createMenuInputHandler() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_RUNNING)
                    viewModel.onVisible()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.onHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_RUNNING)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onHidden()
        }
    }

    ModalInputEffect(active = uiState.menu != null, handler = menuInputHandler)

    val cardBounds = remember { mutableStateMapOf<Int, Rect>() }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenOrigin = it.boundsInRoot().topLeft }
    ) {
        FocusedGameBackdrop(gameId = uiState.focusedApp?.gameId)
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.running_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = LocalArgosyTheme.current.textPrimary,
                modifier = Modifier.padding(Dimens.spacingLg)
            )

            when {
                uiState.isLoading -> CenteredContent {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconLg),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.apps.isEmpty() -> CenteredContent {
                    Text(
                        text = stringResource(R.string.running_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalArgosyTheme.current.textDim
                    )
                }
                uiState.showsSnapshots -> RunningAppsCarousel(
                    uiState = uiState,
                    onTap = viewModel::switchTo,
                    onLongPress = viewModel::openMenu,
                    onSnapshotPlaced = { taskId, bounds -> cardBounds[taskId] = bounds },
                    modifier = Modifier.weight(1f)
                )
                else -> RunningAppsList(
                    uiState = uiState,
                    onTap = viewModel::switchTo,
                    onLongPress = viewModel::openMenu,
                    modifier = Modifier.weight(1f)
                )
            }

            val friends = uiState.focusedApp?.details?.friendsPlaying.orEmpty()
            if (friends.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.spacingMd),
                    contentAlignment = Alignment.Center
                ) {
                    FriendsPlayingRow(friends = friends, modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))
                }
            }

            if (uiState.apps.isNotEmpty()) {
                FooterHints(
                    leadingContent = { SlotCartridges(uiState.apps, uiState.focusedApp?.taskId) },
                    hints = listOf(
                        InputButton.X to stringResource(R.string.running_hint_close),
                        InputButton.Y to stringResource(R.string.running_hint_close_all)
                    ),
                    onHintClick = { button ->
                        when (button) {
                            InputButton.X -> viewModel.requestClose(uiState.focusedIndex)
                            InputButton.Y -> viewModel.requestCloseAll()
                            else -> Unit
                        }
                    }
                )
            }
            FooterSpacer()
        }

        uiState.resumingTaskId?.let { taskId ->
            val app = uiState.apps.firstOrNull { it.taskId == taskId }
            val from = cardBounds[taskId]
            if (app != null && from != null) {
                ResumeZoom(app = app, from = from.translate(-screenOrigin)) {
                    viewModel.onResumeZoomFinished(taskId)
                }
            } else {
                LaunchedEffect(taskId) { viewModel.onResumeZoomFinished(taskId) }
            }
        }

        val menu = uiState.menu
        val menuApp = uiState.menuApp
        if (menu != null && menuApp != null) {
            CustomTileMenuModal(
                header = menuApp.details?.platformName ?: menuApp.label,
                title = menuApp.gameTitle,
                entries = RunningMenuAction.entries.map { stringResource(it.labelRes) },
                focusIndex = menu.focusIndex,
                dangerFromIndex = RunningMenuAction.CLOSE.ordinal,
                onSelect = viewModel::selectMenuAction,
                onDismiss = viewModel::dismissMenu
            )
        }

        when (val confirm = uiState.confirm) {
            is RunningConfirm.CloseGame -> ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(R.string.running_confirm_close_title, confirm.title),
                message = stringResource(R.string.running_confirm_close_message),
                confirmLabel = stringResource(R.string.running_confirm_close_confirm),
                destructive = true,
                onConfirm = viewModel::confirmClose,
                onDismiss = viewModel::dismissConfirm
            )
            is RunningConfirm.CloseAll -> ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(R.string.running_confirm_close_all_title),
                message = stringResource(R.string.running_confirm_close_all_message),
                confirmLabel = stringResource(R.string.running_confirm_close_all_confirm),
                destructive = true,
                onConfirm = viewModel::confirmClose,
                onDismiss = viewModel::dismissConfirm
            )
            null -> Unit
        }
    }
}

@Composable
private fun FocusedGameBackdrop(gameId: Long?) {
    Crossfade(
        targetState = gameId,
        animationSpec = tween(Motion.durationSlide, easing = Motion.argosyEase),
        label = "running-backdrop",
        modifier = Modifier.fillMaxSize()
    ) { id ->
        if (id != null) {
            GameArtBackdrop(
                rememberResolvedBackgroundPath(id, null) ?: rememberResolvedCoverPath(id, null)
            )
        }
    }
}

@Composable
private fun SlotCartridges(apps: List<RunningAppUi>, focusedTaskId: Int?) {
    val loaded = apps.filter { (it.slotIndex ?: 0) > 0 }
    val usedUnits = loaded.sumOf { GameSlots.weightOf(it.platformSlug) }
    val openUnits = (GameSlots.CAPACITY_UNITS - usedUnits).coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        loaded.forEach { app -> Cartridge(app, focused = app.taskId == focusedTaskId) }
        repeat(openUnits) { OpenSlot() }
    }
}

@Composable
private fun Cartridge(app: RunningAppUi, focused: Boolean) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val weight = GameSlots.weightOf(app.platformSlug)
    val slug = app.platformSlug.orEmpty()
    val iconUri = remember(slug) { PlatformIconAssets.resolveAssetUri(context, slug) }
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val fill = theme.focusAccent.copy(
        alpha = if (focused) CARTRIDGE_FOCUSED_FILL_ALPHA else CARTRIDGE_FILL_ALPHA
    )
    Box(
        modifier = Modifier
            .width(Dimens.runningCartridgeWidth * weight + Dimens.spacingXs * (weight - 1))
            .height(Dimens.runningCartridgeHeight)
            .clip(shape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        if (iconUri != null) {
            AsyncImage(
                model = iconUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(CARTRIDGE_ICON_FRACTION)
            )
        } else {
            Text(
                text = PlatformDefinitions.getBySlug(slug)?.shortName ?: slug,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OpenSlot() {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier = Modifier
            .width(Dimens.runningCartridgeWidth)
            .height(Dimens.runningCartridgeHeight)
            .clip(shape)
            .background(LocalArgosyTheme.current.surfaceElevated)
    )
}

@Composable
private fun ColumnScope.CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun RunningAppsList(
    uiState: RunningAppsUiState,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.focusedIndex) {
        listState.animateScrollToItemCentered(uiState.focusedIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        itemsIndexed(uiState.apps, key = { _, app -> app.taskId }) { index, app ->
            RunningAppRow(
                app = app,
                isFocused = index == uiState.focusedIndex,
                onClick = { onTap(index) },
                onLongClick = { onLongPress(index) }
            )
        }
    }
}

@Composable
private fun RunningAppsCarousel(
    uiState: RunningAppsUiState,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onSnapshotPlaced: (taskId: Int, boundsInRoot: Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val cardWidth = maxWidth * CARD_WIDTH_FRACTION
        val listState = rememberLazyListState()
        LaunchedEffect(uiState.focusedIndex) {
            listState.animateScrollToItem(uiState.focusedIndex)
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(uiState.apps, key = { _, app -> app.taskId }) { index, app ->
                RunningAppCard(
                    app = app,
                    isFocused = index == uiState.focusedIndex,
                    onClick = { onTap(index) },
                    onLongClick = { onLongPress(index) },
                    closeStage = uiState.closing[app.taskId],
                    onSnapshotPlaced = { bounds -> onSnapshotPlaced(app.taskId, bounds) },
                    modifier = Modifier
                        .width(cardWidth)
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = tween(Motion.durationContent, easing = Motion.argosyEase),
                            fadeOutSpec = tween(Motion.durationContent, easing = Motion.argosyEase)
                        )
                )
            }
        }
    }
}

@Composable
private fun RunningAppCard(
    app: RunningAppUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    closeStage: MakeRoomStage?,
    onSnapshotPlaced: (boundsInRoot: Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val request = rememberTaskSnapshotRequest(app.snapshotPath, app.snapshotTakenAtMillis)
    val closed = closeStage == MakeRoomStage.CLOSED
    val closeMs = if (LocalMotionTier.current == MotionTier.Reduced) 0 else Motion.durationContent
    val scale by animateFloatAsState(
        targetValue = if (closed) ComponentDefaults.LaunchOverlay.makeRoomClosedScale else 1f,
        animationSpec = tween(closeMs, easing = Motion.argosyEase),
        label = "runningCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (closed) ComponentDefaults.LaunchOverlay.makeRoomClosedAlpha else 1f,
        animationSpec = tween(closeMs, easing = Motion.argosyEase),
        label = "runningCardAlpha"
    )
    val saturation by animateFloatAsState(
        targetValue = if (closed) ComponentDefaults.LaunchOverlay.makeRoomClosedSaturation else 1f,
        animationSpec = tween(closeMs, easing = Motion.argosyEase),
        label = "runningCardSaturation"
    )
    val filter = remember(saturation) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .onGloballyPositioned { onSnapshotPlaced(it.boundsInRoot()) }
                .argosyFocusIndicators(focused = isFocused && closeStage == null, indicators = CARD_FOCUS, shape = shape)
                .clip(shape)
                .background(theme.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = AppIconData(app.iconPackageName),
                    contentDescription = null,
                    colorFilter = filter,
                    modifier = Modifier
                        .size(Dimens.iconXl)
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                )
            }
            app.details?.playedThisRunMs?.takeIf { it >= MILLIS_PER_SECOND }?.let { playedMs ->
                PlayTimeChip(
                    playedMs = playedMs,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.spacingSm)
                )
            }
            if (closeStage != null) CloseStatus(closeStage)
        }
        Row(
            modifier = Modifier.height(Dimens.runningCardCaptionHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunningAppThumb(app = app, modifier = Modifier.fillMaxHeight())
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            RunningAppTitles(app = app, isFocused = isFocused, modifier = Modifier.weight(1f))
            if (isFocused) {
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                RunningAppStats(app)
            }
        }
    }
}

@Composable
private fun ResumeZoom(app: RunningAppUi, from: Rect, onFilled: () -> Unit) {
    val request = rememberTaskSnapshotRequest(app.snapshotPath, app.snapshotTakenAtMillis)
    val screenRequest = rememberTaskSnapshotRequest(app.screenSnapshotPath, app.screenSnapshotTakenAtMillis)
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val currentOnFilled by rememberUpdatedState(onFilled)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(Motion.durationSlide, easing = Motion.argosyEase))
        currentOnFilled()
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val full = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
        val fraction = progress.value
        val rect = lerp(from, full, fraction)
        Box(
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) })
                .clip(RoundedCornerShape(Dimens.radiusLg * (1f - fraction)))
                .background(Color.Black)
        ) {
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - fraction }
                )
            }
            if (screenRequest != null || request != null) {
                AsyncImage(
                    model = screenRequest ?: request,
                    contentDescription = null,
                    contentScale = if (screenRequest != null) ContentScale.FillBounds else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fraction }
                )
            }
        }
    }
}

@Composable
private fun PlayTimeChip(playedMs: Long, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val totalSeconds = playedMs / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val text = when {
        hours > 0 -> stringResource(R.string.running_card_time_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.running_card_time_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.running_card_time_seconds, seconds)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceBase.copy(alpha = PLAY_TIME_SCRIM_ALPHA))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = theme.textPrimary,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun CloseStatus(stage: MakeRoomStage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = ComponentDefaults.LaunchOverlay.makeRoomDeepScrimAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            if (stage == MakeRoomStage.CLOSED) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = LocalLauncherTheme.current.semanticColors.success,
                    modifier = Modifier.size(Dimens.iconMd)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconMd),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(
                    if (stage == MakeRoomStage.CLOSED) R.string.running_card_closed else R.string.running_card_saving
                ),
                style = MaterialTheme.typography.titleMedium,
                color = LocalArgosyTheme.current.textPrimary
            )
        }
    }
}

@Composable
private fun RunningAppThumb(app: RunningAppUi, modifier: Modifier = Modifier) {
    val gameId = app.gameId
    if (gameId == null) {
        AsyncImage(
            model = AppIconData(app.iconPackageName),
            contentDescription = null,
            modifier = modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        )
        return
    }
    val coverPath = rememberResolvedCoverPath(gameId, null)
    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(LocalArgosyTheme.current.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = rememberFileImageModel(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = AppIconData(app.iconPackageName),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(COVER_FALLBACK_ICON_FRACTION)
            )
        }
    }
}

@Composable
private fun RunningAppTitles(app: RunningAppUi, isFocused: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier) {
        Text(
            text = app.gameTitle ?: app.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = if (app.gameTitle != null) app.details?.platformName ?: app.label else null
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RunningAppStats(app: RunningAppUi) {
    val details = app.details
    Column(horizontalAlignment = Alignment.End) {
        if (details == null) {
            app.memoryMb?.let { megabytes -> StatText(stringResource(R.string.running_row_memory, megabytes)) }
            app.screenNumber?.let { number -> StatText(stringResource(R.string.running_row_screen, number)) }
            return@Column
        }
        details.lastSavedAtMillis?.let { savedAt ->
            val relative = formatRelativeTime(LocalContext.current, Instant.ofEpochMilli(savedAt).toString())
            StatText(stringResource(R.string.running_card_saved, relative))
        }
        if (details.raTotal > 0) RaProgress(details)
    }
}

@Composable
private fun StatText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1
    )
}

@Composable
private fun RaProgress(details: RunningGameDetails) {
    val tint = if (details.hardcore) {
        ColorTokens.Domain.AchievementTier.hardcore
    } else {
        ColorTokens.Domain.AchievementTier.softcore
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = stringResource(R.string.running_card_ra_progress, details.raEarned, details.raTotal),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
        ArgosyProgressBar(
            progress = details.raEarned.toFloat() / details.raTotal,
            tint = tint,
            modifier = Modifier.width(Dimens.runningRaBarWidth)
        )
        if (details.hardcore) {
            Text(
                text = stringResource(R.string.running_card_ra_hardcore),
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
private fun FriendsPlayingRow(friends: List<RunningFriendUi>, modifier: Modifier = Modifier) {
    val shown = friends.take(SHOWN_FRIENDS)
    val names = shown.map { it.displayName }
    val line = when (names.size) {
        1 -> stringResource(R.string.running_friends_one, names[0])
        2 -> stringResource(R.string.running_friends_two, names[0], names[1])
        else -> if (friends.size == SHOWN_FRIENDS) {
            stringResource(R.string.running_friends_three, names[0], names[1], names[2])
        } else {
            stringResource(R.string.running_friends_many, names[0], names[1], names[2])
        }
    }
    val overlap = Dimens.runningFriendAvatar / FRIEND_OVERLAP_DIVISOR
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(-overlap)) {
            shown.forEach { friend ->
                SocialAvatar(
                    displayName = friend.displayName,
                    avatarColor = friend.avatarColor,
                    size = Dimens.runningFriendAvatar,
                    avatarPngBase64 = friend.avatarPngBase64,
                    userId = friend.userId
                )
            }
            if (friends.size > SHOWN_FRIENDS) {
                Box(
                    modifier = Modifier
                        .size(Dimens.runningFriendAvatar)
                        .clip(CircleShape)
                        .background(LocalArgosyTheme.current.surfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.running_friends_more, friends.size - SHOWN_FRIENDS),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalArgosyTheme.current.textPrimary
                    )
                }
            }
        }
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalArgosyTheme.current.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RunningAppRow(
    app: RunningAppUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeightLg)
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunningAppThumb(app = app, modifier = Modifier.height(Dimens.runningCardCaptionHeight))
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        RunningAppTitles(app = app, isFocused = true, modifier = Modifier.weight(1f))
        if (isFocused) {
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            RunningAppStats(app)
        }
    }
}

private val CARD_FOCUS = FocusIndicators(halo = true, ring = true)
private const val CARD_WIDTH_FRACTION = 0.5f
private const val CARD_ASPECT = 16f / 9f
private const val COVER_ASPECT = 3f / 4f
private const val CARTRIDGE_ICON_FRACTION = 0.7f
private const val CARTRIDGE_FILL_ALPHA = 0.24f
private const val CARTRIDGE_FOCUSED_FILL_ALPHA = 0.5f
private const val COVER_FALLBACK_ICON_FRACTION = 0.7f
private const val SHOWN_FRIENDS = 3
private const val FRIEND_OVERLAP_DIVISOR = 3
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val PLAY_TIME_SCRIM_ALPHA = 0.85f
