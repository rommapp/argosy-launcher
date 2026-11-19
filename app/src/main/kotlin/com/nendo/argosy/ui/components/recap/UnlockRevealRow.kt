package com.nendo.argosy.ui.components.recap

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.SessionUnlock
import com.nendo.argosy.ui.common.recapTierColor
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.horizontalEdgeFade
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The recap's unlock row with the spotlight on [currentIndex]. [onCurrent] reports the revealing
 * tile during the reveal, then the centred one once an overflowing row's scroll settles. A change
 * of [scrubs] scrolls to [currentIndex]. With [revealOnVisible] the reveal holds its first frame
 * until the row first shows in its scroll viewport or the first scrub, whichever comes first.
 */
@Composable
fun UnlockRevealRow(
    unlocks: List<SessionUnlock>,
    currentIndex: Int,
    scrubs: Int,
    onCurrent: (Int) -> Unit,
    modifier: Modifier = Modifier,
    short: Boolean = false,
    revealOnVisible: Boolean = false
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileSize = if (short) {
            ComponentDefaults.UnlockRecap.shortTileSizeDp.dp
        } else {
            ComponentDefaults.UnlockRecap.tileSizeDp.dp
        }
        val rowHeight = if (short) {
            ComponentDefaults.UnlockRecap.shortRowHeightDp.dp
        } else {
            ComponentDefaults.UnlockRecap.rowHeightDp.dp
        }
        val gap = Dimens.spacingMd
        val edge = Dimens.spacingLg
        val staticRow = RecapLayout.unlockRowFits(
            count = unlocks.size,
            tileDp = tileSize.value,
            gapDp = gap.value,
            widthDp = (maxWidth - edge * 2).value
        )
        val centringPadding = ((maxWidth - tileSize) / 2).coerceAtLeast(edge)
        UnlockRevealLazyRow(
            unlocks = unlocks,
            currentIndex = currentIndex,
            scrubs = scrubs,
            onCurrent = onCurrent,
            staticRow = staticRow,
            tileSize = tileSize,
            rowHeight = rowHeight,
            gap = gap,
            sidePadding = if (staticRow) edge else centringPadding,
            revealOnVisible = revealOnVisible
        )
    }
}

@Composable
private fun UnlockRevealLazyRow(
    unlocks: List<SessionUnlock>,
    currentIndex: Int,
    scrubs: Int,
    onCurrent: (Int) -> Unit,
    staticRow: Boolean,
    tileSize: Dp,
    rowHeight: Dp,
    gap: Dp,
    sidePadding: Dp,
    revealOnVisible: Boolean
) {
    val timeline = UnlockRevealTimeline.Default
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val currentOnCurrent by rememberUpdatedState(onCurrent)
    val currentIndexState by rememberUpdatedState(currentIndex)
    val staticRowState by rememberUpdatedState(staticRow)
    var interrupted by remember(unlocks) { mutableStateOf(false) }
    var elapsedMs by remember(unlocks) { mutableLongStateOf(if (reduced) SETTLED_MS else 0L) }
    var revealDone by remember(unlocks) { mutableStateOf(reduced) }
    var gate by remember(unlocks) {
        mutableStateOf(if (revealOnVisible) UnlockRevealGate.OnVisible else UnlockRevealGate.Open)
    }

    LaunchedEffect(scrubs, dragged) {
        if (scrubs > 0 || dragged) {
            interrupted = true
            gate = UnlockRevealGate.Open
        }
    }

    LaunchedEffect(listState, unlocks) {
        snapshotFlow {
            when {
                !revealDone && !interrupted -> timeline.currentSlot(elapsedMs, unlocks.size)
                staticRowState -> null
                listState.isScrollInProgress -> null
                else -> listState.layoutInfo.centeredItemIndex()
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index -> currentOnCurrent(index) }
    }

    LaunchedEffect(unlocks, reduced, gate.started) {
        if (reduced || unlocks.isEmpty()) {
            elapsedMs = SETTLED_MS
            revealDone = true
            return@LaunchedEffect
        }
        if (!gate.started) return@LaunchedEffect
        val start = withFrameMillis { it }
        val end = timeline.revealEndMs(unlocks.size)
        var scrolledTo = 0
        while (isActive) {
            val now = withFrameMillis { it } - start
            val started = timeline.slotsStarted(now, unlocks.size)
            while (scrolledTo < started) {
                val target = scrolledTo
                if (!interrupted) launch { listState.animateScrollToItem(target) }
                scrolledTo++
            }
            elapsedMs = now
            if (now >= end) break
        }
        revealDone = true
    }

    LaunchedEffect(scrubs) {
        if (scrubs > 0) listState.animateScrollToItem(currentIndexState)
    }

    LazyRow(
        state = listState,
        userScrollEnabled = !staticRow,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .onGloballyPositioned { coordinates ->
                gate = gate.observed(coordinates.boundsInRoot().height, coordinates.size.height.toFloat())
            }
            .horizontalEdgeFade(listState, fadeWidth = ComponentDefaults.UnlockRecap.fadeWidthDp.dp),
        horizontalArrangement = if (staticRow) {
            Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(gap)
        },
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = sidePadding)
    ) {
        itemsIndexed(unlocks, key = { index, _ -> index }) { index, unlock ->
            UnlockTile(
                unlock = unlock,
                size = tileSize,
                spotlit = index == currentIndex,
                cursorTakenOver = interrupted,
                frame = { timeline.frameOf(index, elapsedMs) },
                onTap = {
                    interrupted = true
                    currentOnCurrent(index)
                    if (!staticRow) scope.launch { listState.animateScrollToItem(index) }
                }
            )
        }
    }
}

@Composable
private fun UnlockTile(
    unlock: SessionUnlock,
    size: Dp,
    spotlit: Boolean,
    cursorTakenOver: Boolean,
    frame: () -> UnlockTileFrame,
    onTap: () -> Unit
) {
    val tierColor = unlock.recapTierColor
    val blockColor = MaterialTheme.colorScheme.surfaceVariant
    val cornerRadius = Dimens.radiusMd
    val borderWidth = ComponentDefaults.UnlockRecap.tileBorderDp.dp
    val glowRadius = ComponentDefaults.UnlockRecap.tileGlowRadiusDp.dp
    val spotlight by animateFloatAsState(
        targetValue = if (spotlit) 1f else 0f,
        animationSpec = Motion.focusSpring,
        label = "recapTileSpotlight"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clickableNoFocus(onClick = onTap)
            .graphicsLayer {
                val scale = frame().scale
                scaleX = scale
                scaleY = scale
            }
            .drawWithCache {
                val strokePx = borderWidth.toPx()
                val radiusPx = cornerRadius.toPx()
                val border = tileBorderPath(this.size, strokePx / 2f, radiusPx)
                val measure = PathMeasure().apply { setPath(border, false) }
                val segment = Path()
                val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
                val glowPaint = Paint()
                glowPaint.asFrameworkPaint().maskFilter =
                    BlurMaskFilter(glowRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
                onDrawBehind {
                    val current = frame()
                    val ringFraction = if (cursorTakenOver) 1f else current.borderFraction
                    val revealAlpha = when {
                        !cursorTakenOver || current.borderFraction <= 0f -> 0f
                        current.borderFraction < 1f -> ComponentDefaults.UnlockRecap.dimmedAlpha
                        else -> ComponentDefaults.UnlockRecap.dimmedAlpha * current.glow
                    }
                    val glow = maxOf(current.glow, spotlight * ringFraction)
                    if (glow > 0f) {
                        glowPaint.color = tierColor.copy(alpha = ComponentDefaults.UnlockRecap.glowAlpha * glow)
                        drawIntoCanvas { canvas ->
                            canvas.drawRoundRect(0f, 0f, this.size.width, this.size.height, radiusPx, radiusPx, glowPaint)
                        }
                    }
                    drawRoundRect(color = blockColor, cornerRadius = CornerRadius(radiusPx, radiusPx))
                    drawPath(border, tierColor.copy(alpha = ComponentDefaults.UnlockRecap.trackAlpha), style = stroke)
                    if (revealAlpha > 0f) {
                        segment.reset()
                        measure.getSegment(0f, measure.length * current.borderFraction, segment, true)
                        drawPath(segment, tierColor.copy(alpha = revealAlpha), style = stroke)
                    }
                    if (ringFraction > 0f && spotlight > 0f) {
                        segment.reset()
                        measure.getSegment(0f, measure.length * ringFraction, segment, true)
                        drawPath(segment, tierColor.copy(alpha = spotlight), style = stroke)
                    }
                }
            }
    ) {
        AsyncImage(
            model = unlock.badgePath,
            contentDescription = unlock.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(ComponentDefaults.UnlockRecap.tileBadgeInsetDp.dp)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .graphicsLayer {
                    val dimmed = ComponentDefaults.UnlockRecap.dimmedAlpha
                    alpha = frame().badgeAlpha * (dimmed + (1f - dimmed) * spotlight)
                }
        )
    }
}

private fun tileBorderPath(size: Size, inset: Float, radius: Float): Path = Path().apply {
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    val corner = radius.coerceAtMost((right - left) / 2f).coerceAtLeast(0f)
    val diameter = corner * 2f
    moveTo(size.width / 2f, top)
    lineTo(right - corner, top)
    arcTo(Rect(right - diameter, top, right, top + diameter), -90f, 90f, false)
    lineTo(right, bottom - corner)
    arcTo(Rect(right - diameter, bottom - diameter, right, bottom), 0f, 90f, false)
    lineTo(left + corner, bottom)
    arcTo(Rect(left, bottom - diameter, left + diameter, bottom), 90f, 90f, false)
    lineTo(left, top + corner)
    arcTo(Rect(left, top, left + diameter, top + diameter), 180f, 90f, false)
    close()
}

private fun LazyListLayoutInfo.centeredItemIndex(): Int? {
    val center = (viewportStartOffset + viewportEndOffset) / 2
    return visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
}

private const val SETTLED_MS = Long.MAX_VALUE / 2
