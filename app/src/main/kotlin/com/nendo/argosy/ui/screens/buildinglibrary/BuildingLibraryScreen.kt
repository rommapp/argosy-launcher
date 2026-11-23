package com.nendo.argosy.ui.screens.buildinglibrary

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.fileImageModel
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import kotlinx.coroutines.flow.MutableStateFlow
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The library sync as a cover wall filling in. [onBrowse] leaves for Home while the sync keeps
 * running, [onBack] returns to where the sync was started, and [onFinished] runs once the sync ends.
 */
@Composable
fun BuildingLibraryScreen(
    onBrowse: () -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: BuildingLibraryViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBrowse, onBack) { viewModel.createInputHandler(onBrowse = onBrowse, onBack = onBack) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_BUILDING_LIBRARY)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_BUILDING_LIBRARY)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == BuildingLibraryPhase.DONE) {
            delay(ComponentDefaults.BuildingLibrary.finishHoldMs.toLong())
            finish()
        }
    }

    val theme = LocalArgosyTheme.current
    val manager = DualScreenManagerHolder.instance
    val presenting by (manager?.presentationShowsHints ?: remember { MutableStateFlow(false) }).collectAsState()
    if (presenting) {
        PresentOnCompanion(LIBRARY_WALL_OWNER, PresentationSlot.LibraryWall(uiState.tiles))
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
    ) {
        if (presenting) {
            HexField(diagonal = false, modifier = Modifier.fillMaxSize())
            BuildingLibraryInfo(
                state = uiState,
                onBrowse = onBrowse,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.spacingXl)
            )
        } else if (maxWidth <= maxHeight) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth()) {
                    HexField(diagonal = false, modifier = Modifier.matchParentSize())
                    BuildingLibraryInfo(
                        state = uiState,
                        onBrowse = onBrowse,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimens.spacingXl)
                    )
                }
                LibraryCoverWall(
                    tiles = uiState.tiles,
                    columns = ComponentDefaults.BuildingLibrary.wallColumns,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        } else {
            LibraryCoverWall(
                tiles = uiState.tiles,
                columns = ComponentDefaults.BuildingLibrary.wallColumns,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(ComponentDefaults.BuildingLibrary.wallWidthRatio)
                    .fillMaxHeight()
            )
            HexField(diagonal = true, modifier = Modifier.fillMaxSize())
            BuildingLibraryInfo(
                state = uiState,
                onBrowse = onBrowse,
                modifier = Modifier
                    .fillMaxWidth(ComponentDefaults.BuildingLibrary.infoWidthRatio)
                    .fillMaxHeight()
                    .padding(Dimens.spacingXl)
            )
        }
    }
}

@Composable
private fun HexField(diagonal: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val reduced = reducedMotion()
    val density = LocalDensity.current
    val sizePx = with(density) { ComponentDefaults.BuildingLibrary.hexSizeDp.dp.toPx() }
    val gapPx = with(density) { ComponentDefaults.BuildingLibrary.hexGapDp.dp.toPx() }
    val pattern = remember(sizePx, gapPx, theme.textPrimary) { hexPattern(sizePx, gapPx, theme.textPrimary) }
    val waveSpeedPxPerMs = with(density) { ComponentDefaults.BuildingLibrary.waveSpeedDpPerSecond.dp.toPx() } / 1_000f
    val waveAmplitudePx = with(density) { ComponentDefaults.BuildingLibrary.waveAmplitudeDp.dp.toPx() }
    val pulses = remember { ArrayList<HexPulse>() }
    var nowMs by remember { mutableLongStateOf(0L) }
    var gridSize by remember { mutableStateOf(HexGridSize(0, 0, 0f, 0f)) }
    LaunchedEffect(reduced, gridSize, pattern) {
        pulses.clear()
        if (reduced || gridSize.columns == 0) return@LaunchedEffect
        val random = Random(System.nanoTime())
        val waves = ArrayList<HexWave>()
        var nextSparkMs = 0L
        var nextWaveMs = 0L
        var lastMs = -1L
        while (true) {
            withFrameNanos { nowMs = it / NANOS_PER_MILLI }
            val now = nowMs
            if (now >= nextSparkMs) {
                pulses.add(
                    HexPulse(
                        column = random.nextInt(gridSize.columns),
                        row = random.nextInt(gridSize.rows),
                        startMs = now,
                        peakAlpha = random.between(
                            ComponentDefaults.BuildingLibrary.hexPulseMinAlpha,
                            ComponentDefaults.BuildingLibrary.hexPulseMaxAlpha
                        )
                    )
                )
                nextSparkMs = now + ComponentDefaults.BuildingLibrary.hexSpawnMs
            }
            if (waves.size < ComponentDefaults.BuildingLibrary.waveMaxActive && now >= nextWaveMs) {
                val variation = ComponentDefaults.BuildingLibrary.waveSpeedVariation
                waves.add(
                    HexWave(
                        startMs = now,
                        startY = gridSize.heightPx + waveAmplitudePx,
                        speedPxPerMs = waveSpeedPxPerMs * random.between(1f - variation, 1f + variation),
                        amplitudePx = waveAmplitudePx,
                        wavelengthPx = gridSize.widthPx * random.between(
                            ComponentDefaults.BuildingLibrary.waveLengthMinRatio,
                            ComponentDefaults.BuildingLibrary.waveLengthMaxRatio
                        ),
                        phase = random.nextFloat() * TWO_PI
                    )
                )
                nextWaveMs = now + random.between(
                    ComponentDefaults.BuildingLibrary.waveSpawnMinMs.toFloat(),
                    ComponentDefaults.BuildingLibrary.waveSpawnMaxMs.toFloat()
                ).toLong()
            }
            if (lastMs >= 0) waves.forEach { wave -> sweepWave(wave, lastMs, now, pattern, gridSize, random, pulses) }
            waves.removeAll { it.baseY(now) + it.amplitudePx < 0f }
            pulses.removeAll { now - it.startMs >= ComponentDefaults.BuildingLibrary.hexFadeMs }
            lastMs = now
        }
    }
    Canvas(
        modifier.onSizeChanged {
            gridSize = HexGridSize(
                columns = ceil(it.width.toFloat() / pattern.pitchX).toInt() + 1,
                rows = ceil(it.height.toFloat() / pattern.pitchY).toInt() + 1,
                widthPx = it.width.toFloat(),
                heightPx = it.height.toFloat()
            )
        }
    ) {
        if (diagonal) drawRect(brush = splitBrush(size, theme.surfaceBase))
    }
    Canvas(
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val base = ComponentDefaults.BuildingLibrary.hexBaseAlpha
        drawRect(brush = pattern.brush, alpha = base)
        val time = nowMs
        pulses.forEach { pulse ->
            val progress = ((time - pulse.startMs).toFloat() / ComponentDefaults.BuildingLibrary.hexFadeMs).coerceIn(0f, 1f)
            val lift = (pulse.peakAlpha - base) * (1f - progress)
            if (lift > 0f) {
                translate(pattern.centerX(pulse.column, pulse.row), pattern.centerY(pulse.row)) {
                    drawPath(pattern.hex, theme.textPrimary.copy(alpha = lift))
                }
            }
        }
        if (diagonal) drawRect(brush = splitBrush(size, Color.Black), blendMode = BlendMode.DstIn)
    }
}

private data class HexPulse(val column: Int, val row: Int, val startMs: Long, val peakAlpha: Float)

private data class HexGridSize(val columns: Int, val rows: Int, val widthPx: Float, val heightPx: Float)

private class HexWave(
    val startMs: Long,
    val startY: Float,
    val speedPxPerMs: Float,
    val amplitudePx: Float,
    val wavelengthPx: Float,
    val phase: Float
) {
    fun baseY(timeMs: Long): Float = startY - speedPxPerMs * (timeMs - startMs)

    fun frontY(x: Float, timeMs: Long): Float = baseY(timeMs) + amplitudePx * sin(TWO_PI * x / wavelengthPx + phase)
}

private fun sweepWave(
    wave: HexWave,
    fromMs: Long,
    toMs: Long,
    pattern: HexPattern,
    grid: HexGridSize,
    random: Random,
    pulses: MutableList<HexPulse>
) {
    for (column in 0 until grid.columns) {
        val x = column * pattern.pitchX + pattern.pitchX / 4f
        val before = wave.frontY(x, fromMs)
        val after = wave.frontY(x, toMs)
        val firstRow = ceil(after / pattern.pitchY).toInt().coerceAtLeast(0)
        val lastRow = floor(before / pattern.pitchY).toInt().coerceAtMost(grid.rows - 1)
        for (row in firstRow..lastRow) {
            if (random.nextFloat() < ComponentDefaults.BuildingLibrary.waveHitChance) {
                pulses.add(
                    HexPulse(
                        column = column,
                        row = row,
                        startMs = toMs,
                        peakAlpha = random.between(
                            ComponentDefaults.BuildingLibrary.waveHitMinAlpha,
                            ComponentDefaults.BuildingLibrary.waveHitMaxAlpha
                        )
                    )
                )
            }
        }
    }
}

private fun Random.between(min: Float, max: Float): Float = min + nextFloat() * (max - min)

private class HexPattern(val hex: Path, val pitchX: Int, val pitchY: Int, val brush: Brush) {
    fun centerX(column: Int, row: Int): Float = column * pitchX + if (row % 2 == 1) pitchX / 2f else 0f
    fun centerY(row: Int): Float = row * pitchY.toFloat()
}

private fun hexPattern(sizePx: Float, gapPx: Float, color: Color): HexPattern {
    val radius = sizePx / 2f
    val hex = hexPath(radius)
    val pitchX = (SQRT_3 * radius + gapPx).roundToInt().coerceAtLeast(2)
    val pitchY = (1.5f * radius + gapPx * SQRT_3 / 2f).roundToInt().coerceAtLeast(1)
    val tile = ImageBitmap(pitchX, pitchY * 2)
    val canvas = androidx.compose.ui.graphics.Canvas(tile)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    listOf(
        0f to 0f, pitchX.toFloat() to 0f,
        pitchX / 2f to pitchY.toFloat(),
        0f to pitchY * 2f, pitchX.toFloat() to pitchY * 2f
    ).forEach { (x, y) ->
        canvas.save()
        canvas.translate(x, y)
        canvas.drawPath(hex, paint)
        canvas.restore()
    }
    return HexPattern(hex, pitchX, pitchY, ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)))
}

private fun hexPath(radius: Float): Path = Path().apply {
    for (corner in 0 until 6) {
        val angle = (PI / 3.0 * corner - PI / 6.0).toFloat()
        val x = radius * cos(angle)
        val y = radius * sin(angle)
        if (corner == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun splitBrush(size: Size, solid: Color): Brush {
    val topX = size.width * ComponentDefaults.BuildingLibrary.splitTopRatio
    val bottomX = size.width * ComponentDefaults.BuildingLibrary.splitBottomRatio
    val edgeX = bottomX - topX
    val edgeY = size.height
    val length = sqrt(edgeX * edgeX + edgeY * edgeY).coerceAtLeast(1f)
    val normalX = edgeY / length
    val normalY = -edgeX / length
    val falloff = size.width * ComponentDefaults.BuildingLibrary.splitFalloffRatio
    val start = Offset((topX + bottomX) / 2f, size.height / 2f)
    return Brush.linearGradient(
        colors = listOf(solid, solid.copy(alpha = 0f)),
        start = start,
        end = Offset(start.x + normalX * falloff, start.y + normalY * falloff)
    )
}

@Composable
private fun BuildingLibraryInfo(
    state: BuildingLibraryUiState,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.building_library_title),
            style = MaterialTheme.typography.headlineLarge,
            color = theme.textPrimary
        )
        Spacer(Modifier.height(Dimens.spacingSm))
        Text(
            text = when {
                state.phase == BuildingLibraryPhase.DONE -> stringResource(R.string.building_library_done)
                state.currentPlatform.isNotBlank() ->
                    stringResource(R.string.building_library_syncing_platform, state.currentPlatform)
                else -> stringResource(R.string.building_library_getting_ready)
            },
            style = MaterialTheme.typography.titleMedium,
            color = theme.textDim
        )
        Spacer(Modifier.height(Dimens.spacingLg))
        val fraction = state.gamesFraction
        if (fraction != null || state.phase == BuildingLibraryPhase.DONE) {
            LinearProgressIndicator(
                progress = { if (state.phase == BuildingLibraryPhase.DONE) 1f else fraction ?: 0f },
                color = theme.focusAccent,
                trackColor = theme.hairlineLow,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(
                color = theme.focusAccent,
                trackColor = theme.hairlineLow,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(Dimens.spacingMd))
        if (state.gamesTotal > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.building_library_games, state.gamesTotal, state.gamesDone, state.gamesTotal
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textPrimary
            )
        }
        if (state.platformsTotal > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.building_library_platforms, state.platformsTotal, state.platformsDone, state.platformsTotal
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMute
            )
        }
        if (state.phase != BuildingLibraryPhase.DONE) {
            Spacer(Modifier.height(Dimens.spacingXl))
            ActionButton(
                label = stringResource(R.string.building_library_browse),
                onClick = onBrowse,
                focused = true,
                primary = true,
                glyph = InputButton.A
            )
        }
    }
}

/**
 * The synced covers as a wall of [columns] that fills from the bottom row and glides up, for the
 * Building your Library screen and its presentation-screen slot.
 */
@Composable
fun LibraryCoverWall(tiles: List<LibraryWallTile>, columns: Int, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val reduced = reducedMotion()
    val gridState = rememberLazyGridState()
    val appeared = remember { HashSet<Long>() }
    BoxWithConstraints(modifier) {
        val gap = Dimens.spacingSm
        val tileWidthPx = with(LocalDensity.current) { ((maxWidth - gap * (columns + 1)) / columns).roundToPx() }
            .coerceAtLeast(1)
        val tileHeightPx = (tileWidthPx / ComponentDefaults.BuildingLibrary.coverAspectRatio).roundToInt()
        val shown = rememberLoadedWallTiles(tiles, tileWidthPx, tileHeightPx)
        val rowHeight = with(LocalDensity.current) { tileHeightPx.toDp() }
        val gapPx = with(LocalDensity.current) { gap.toPx() }
        val geometry = WallGeometry(columns, tileHeightPx + gapPx, tileHeightPx.toFloat(), gapPx)
        WallFollow(gridState, shown.size, geometry, reduced)
        TrimScrolledRows(gridState, shown, columns)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(gap),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown, key = { it.gameId }) { tile ->
                WallTile(
                    tile = tile,
                    widthPx = tileWidthPx,
                    heightPx = tileHeightPx,
                    animate = !reduced && appeared.add(tile.gameId)
                )
            }
            item(key = WALL_BOTTOM_ROW_KEY, span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(rowHeight))
            }
        }
        val fade = ComponentDefaults.BuildingLibrary.wallFadeHeightRatio
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to theme.surfaceBase,
                        fade to theme.surfaceBase.copy(alpha = 0f),
                        1f - fade to theme.surfaceBase.copy(alpha = 0f),
                        1f to theme.surfaceBase
                    )
                )
        )
    }
}

private data class WallGeometry(
    val columns: Int,
    val rowPitchPx: Float,
    val emptyRowPx: Float,
    val paddingPx: Float
)

@Composable
private fun WallFollow(gridState: LazyGridState, count: Int, geometry: WallGeometry, reduced: Boolean) {
    val shownCount by rememberUpdatedState(count)
    LaunchedEffect(gridState, geometry, reduced) {
        while (true) {
            try {
                var lastFrame = withFrameNanos { it }
                gridState.scroll {
                    while (true) {
                        val frame = withFrameNanos { it }
                        val seconds = (frame - lastFrame) / NANOS_PER_SECOND
                        lastFrame = frame
                        val remaining = wallScrollTarget(gridState, shownCount, geometry) -
                            wallScrollPosition(gridState, geometry)
                        if (remaining > 0f) {
                            val share = if (reduced) 1f else min(1f, seconds * ComponentDefaults.BuildingLibrary.wallFollowPerSecond)
                            scrollBy(remaining * share)
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
            }
        }
    }
}

private fun wallScrollPosition(gridState: LazyGridState, geometry: WallGeometry): Float =
    (gridState.firstVisibleItemIndex / geometry.columns) * geometry.rowPitchPx +
        gridState.firstVisibleItemScrollOffset

private fun wallScrollTarget(gridState: LazyGridState, count: Int, geometry: WallGeometry): Float {
    val filledRows = count.toFloat() / geometry.columns
    val contentEnd = geometry.paddingPx + filledRows * geometry.rowPitchPx + geometry.emptyRowPx + geometry.paddingPx
    return (contentEnd - gridState.layoutInfo.viewportSize.height).coerceAtLeast(0f)
}

@Composable
private fun TrimScrolledRows(gridState: LazyGridState, shown: SnapshotStateList<LibraryWallTile>, columns: Int) {
    LaunchedEffect(shown.size) {
        val over = shown.size - ComponentDefaults.BuildingLibrary.wallCoverLimit
        if (over < columns) return@LaunchedEffect
        val scrolledPast = gridState.firstVisibleItemIndex
        val removable = (min(over, scrolledPast) / columns) * columns
        if (removable == 0) return@LaunchedEffect
        val offset = gridState.firstVisibleItemScrollOffset
        shown.removeRange(0, removable)
        gridState.requestScrollToItem(scrolledPast - removable, offset)
    }
}

@Composable
private fun rememberLoadedWallTiles(
    tiles: List<LibraryWallTile>,
    widthPx: Int,
    heightPx: Int
): SnapshotStateList<LibraryWallTile> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loaded = remember { mutableStateListOf<LibraryWallTile>() }
    val requested = remember { HashSet<Long>() }
    val ready = remember { Channel<LibraryWallTile>(Channel.UNLIMITED) }
    LaunchedEffect(tiles) {
        tiles.filter { requested.add(it.gameId) }.forEach { tile ->
            scope.launch {
                val result = context.imageLoader.execute(wallRequest(context, tile, widthPx, heightPx))
                if (result is SuccessResult) ready.trySend(tile)
            }
        }
    }
    LaunchedEffect(ready) {
        for (tile in ready) {
            loaded.add(tile)
            delay(ComponentDefaults.BuildingLibrary.wallSpawnStepMs.toLong())
        }
    }
    return loaded
}

private fun wallRequest(context: Context, tile: LibraryWallTile, widthPx: Int, heightPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(fileImageModel(tile.coverPath))
        .size(widthPx, heightPx)
        .build()

@Composable
private fun WallTile(tile: LibraryWallTile, widthPx: Int, heightPx: Int, animate: Boolean) {
    val context = LocalContext.current
    val progress = remember(tile.gameId) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(tile.gameId) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(ComponentDefaults.AutoGrid.entryMs, easing = Motion.argosyEase))
        }
    }
    val startScale = ComponentDefaults.AutoGrid.entryStartScale
    val request = remember(tile, widthPx, heightPx) { wallRequest(context, tile, widthPx, heightPx) }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ComponentDefaults.BuildingLibrary.coverAspectRatio)
            .graphicsLayer {
                val p = progress.value
                alpha = p
                scaleX = startScale + (1f - startScale) * p
                scaleY = startScale + (1f - startScale) * p
            }
            .clip(RoundedCornerShape(Dimens.radiusSm))
    )
}

private const val NANOS_PER_SECOND = 1_000_000_000f
private val LIBRARY_WALL_OWNER = SlotOwner("building-library")
private const val NANOS_PER_MILLI = 1_000_000L
private val SQRT_3 = sqrt(3f)
private const val TWO_PI = (2.0 * PI).toFloat()
private const val WALL_BOTTOM_ROW_KEY = "wall-bottom-row"
