package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val WHEEL_VISIBLE_ROWS = 3

/**
 * A vertical rolodex of options: the centre row is the selection and its neighbours preview what
 * one step in either direction lands on. The ends clamp rather than wrap - the first option has no
 * row above it and the last has none below, and the empty neighbour row says so honestly. This is
 * the deliberate exception to the project's wrap-with-.mod() navigation convention: a wheel holds
 * a value range with a floor and a ceiling, and wrapping would present the two extremes as
 * adjacent values.
 *
 * Touch rotates it - a tap on a neighbour steps to it, a drag walks the wheel a row at a time.
 * Gamepad rotation belongs to the caller's input handler, which changes [selectedIndex] through the
 * same [onSelect]; the wheel owns no focus and no state of its own, so [focused] is purely visual.
 * Its height is fixed at three rows, which is what keeps a bank of wheels from outgrowing a short
 * screen.
 */
@Composable
fun WheelPicker(
    options: List<String>,
    selectedIndex: Int,
    focused: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val rowHeight = Dimens.menuRowHeight
    val size = options.size
    val safeIndex = if (size == 0) 0 else selectedIndex.coerceIn(0, size - 1)
    val previousIndex = (safeIndex - 1).takeIf { it >= 0 }
    val nextIndex = (safeIndex + 1).takeIf { it < size }
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    val currentIndex by rememberUpdatedState(safeIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val dragRemainder = remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .height(rowHeight * WHEEL_VISIBLE_ROWS)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, shape = shape)
            .clip(shape)
            .background(theme.surfaceElevated)
            .pointerInput(size, rowHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragRemainder.floatValue = 0f },
                    onDragEnd = { dragRemainder.floatValue = 0f },
                    onDragCancel = { dragRemainder.floatValue = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    if (size <= 1) return@detectVerticalDragGestures
                    dragRemainder.floatValue += dragAmount
                    val steps = (dragRemainder.floatValue / rowHeightPx).toInt()
                    if (steps != 0) {
                        dragRemainder.floatValue -= steps * rowHeightPx
                        val target = (currentIndex - steps).coerceIn(0, size - 1)
                        if (target != currentIndex) currentOnSelect(target)
                    }
                }
            }
    ) {
        WheelNeighbourRow(
            label = previousIndex?.let { options[it] },
            rowHeight = rowHeight,
            onClick = { previousIndex?.let { onSelect(it) } }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = options.getOrNull(safeIndex).orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) theme.focusAccent else theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        WheelNeighbourRow(
            label = nextIndex?.let { options[it] },
            rowHeight = rowHeight,
            onClick = { nextIndex?.let { onSelect(it) } }
        )
    }
}

@Composable
private fun WheelNeighbourRow(
    label: String?,
    rowHeight: Dp,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickableNoFocus(enabled = label != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
