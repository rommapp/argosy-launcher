package com.nendo.argosy.ui.screens.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nendo.argosy.data.music.library.MusicColumnBuilder
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.home.HomeColumnsGeometry
import com.nendo.argosy.ui.screens.home.homeColumnsGeometry
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.motionSpec
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val MusicColumnFocus = FocusIndicators(halo = true)

@Composable
fun MusicColumnsStrip(
    state: MusicUiState,
    onColumnTap: (fromFocus: Int) -> Unit,
    onColumnLongPress: (fromFocus: Int) -> Unit,
    onFlip: (delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = state.columns
    if (columns.isEmpty()) return
    BoxWithConstraints(modifier.clipToBounds()) {
        val slots = state.slotCount
        val gap = Dimens.spacingSm
        val columnHeight = maxHeight
        val geometry = squareFocusGeometry(maxWidth.value, columnHeight.value, slots, gap.value)
        val cursor = if (state.wraps) state.position else state.focusIndex
        val animated by animateFloatAsState(
            targetValue = cursor.toFloat(),
            animationSpec = motionSpec(tween(Motion.durationPage, easing = Motion.argosyEase)),
            label = "music-columns"
        )
        val reach = slots / 2 + 1
        val lo = floor(animated).toInt() - reach
        val hi = ceil(animated).toInt() + reach
        val visible = remember(lo, hi, state.wraps, columns.size) {
            if (state.wraps) (lo..hi).toList() else (lo..hi).filter { it in columns.indices }
        }
        visible.forEach { slot ->
            key(slot) {
                val column = columns[slot.mod(columns.size)]
                val fromFocus = slot - cursor
                val offset = slot - animated
                val x = geometry.startOf(offset).dp
                MusicColumnCard(
                    column = column,
                    isFocused = fromFocus == 0,
                    stack = state.focusedStack.takeIf { fromFocus == 0 },
                    onFlip = onFlip.takeIf { fromFocus == 0 && state.canFlip },
                    isPlaying = column.key == state.playing.sourceId,
                    sliver = abs(fromFocus) > slots / 2,
                    width = geometry.widthAt(offset).dp,
                    height = columnHeight,
                    onTap = { onColumnTap(fromFocus) },
                    onLongPress = { onColumnLongPress(fromFocus) },
                    modifier = Modifier.offset { IntOffset(x.roundToPx(), 0) }
                )
            }
        }
    }
}

private val MusicColumn.showsYearNumerals: Boolean
    get() = kind == MusicColumnKind.YEAR && key != MusicColumnBuilder.YEAR_NONE_KEY

internal fun squareFocusGeometry(
    width: Float,
    height: Float,
    slots: Int,
    gap: Float,
    minNeighbour: Float = ComponentDefaults.MusicColumns.neighbourMinWidthDp.toFloat()
): HomeColumnsGeometry {
    val base = homeColumnsGeometry(width, slots, gap)
    val others = slots - 1
    val available = width - width * ComponentDefaults.HomeColumns.sliverWidthRatio * 2 - gap * (slots + 1)
    if (others <= 0 || available <= height) return base
    val focused = minOf(height, available - minNeighbour * others)
    val neighbour = (available - focused) / others
    if (neighbour <= 0f) return base
    val focusScale = focused / neighbour
    if (focusScale <= 1f) return base
    return base.copy(columnWidth = neighbour, focusScale = focusScale)
}

@Composable
private fun MusicColumnCard(
    column: MusicColumn,
    isFocused: Boolean,
    stack: MusicStackUi?,
    onFlip: ((Int) -> Unit)?,
    isPlaying: Boolean,
    sliver: Boolean,
    width: Dp,
    height: Dp,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusPanel)
    val dim by animateFloatAsState(
        targetValue = when {
            isFocused -> 0f
            sliver -> ComponentDefaults.HomeColumns.sliverDimAlpha
            else -> ComponentDefaults.HomeColumns.unfocusedDimAlpha
        },
        animationSpec = motionSpec(tween(Motion.durationPage, easing = Motion.argosyEase)),
        label = "music-column-dim"
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .argosyFocusIndicators(focused = isFocused, indicators = MusicColumnFocus, shape = shape)
            .clip(shape)
            .background(theme.surfaceBase)
            .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
            .then(if (onFlip != null) Modifier.verticalFlipGesture(onFlip) else Modifier)
    ) {
        if (isFocused) {
            MusicFocusedColumnArt(column = column, stack = stack)
        } else {
            MusicSideColumnArt(column = column)
        }
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
        if (sliver) return@Box
        ColumnScrims(height = height)
        ColumnHeader(
            column = column,
            isFocused = isFocused,
            isPlaying = isPlaying,
            modifier = Modifier.align(Alignment.TopStart)
        )
        if (!isFocused) {
            ColumnFooter(column = column, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

private fun Modifier.verticalFlipGesture(onFlip: (Int) -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { ComponentDefaults.MusicColumns.flipDragDp.dp.toPx() }
    val latest by rememberUpdatedState(onFlip)
    pointerInput(threshold) {
        var travel = 0f
        detectVerticalDragGestures(
            onDragStart = { travel = 0f },
            onVerticalDrag = { change, amount ->
                change.consume()
                travel += amount
                if (abs(travel) >= threshold) {
                    latest(if (travel < 0f) 1 else -1)
                    travel = 0f
                }
            }
        )
    }
}

@Composable
private fun BoxScope.ColumnScrims(height: Dp) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height * ComponentDefaults.HomeColumns.topScrimHeightRatio)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = ComponentDefaults.HomeColumns.topScrimAlpha), Color.Transparent)
                )
            )
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height * ComponentDefaults.HomeColumns.bottomScrimHeightRatio)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = ComponentDefaults.HomeColumns.bottomScrimAlpha))
                )
            )
    )
}

@Composable
private fun ColumnHeader(column: MusicColumn, isFocused: Boolean, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (isPlaying || column.kind == MusicColumnKind.RECENT) {
                Box(
                    Modifier
                        .size(ComponentDefaults.HomeColumns.dotSizeActiveDp.dp)
                        .clip(CircleShape)
                        .background(theme.textPrimary)
                )
            }
            musicColumnBadge(column, isPlaying)?.let { badge ->
                Text(
                    text = badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = ComponentDefaults.HomeColumns.kickerTrackingRatio.em
                    ),
                    color = if (isFocused) theme.textPrimary else Color.White.copy(
                        alpha = ComponentDefaults.HomeColumns.secondaryTextAlpha
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (column.showsYearNumerals) {
            Text(
                text = musicColumnTitle(column),
                style = if (isFocused) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ColumnFooter(column: MusicColumn, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        if (!column.showsYearNumerals) {
            Text(
                text = musicColumnTitle(column),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = musicColumnSubtitle(column),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = ComponentDefaults.HomeColumns.secondaryTextAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun BoxScope.MusicNoArtGround(column: MusicColumn, large: Boolean) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(theme.surfaceRaised)
            .padding(Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        if (!column.showsYearNumerals) {
            Text(
                text = musicColumnTitle(column),
                style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = theme.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
