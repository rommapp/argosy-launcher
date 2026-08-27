package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The row of section names with bumper arrows either side, shared by the single-screen home and the
 * dual-screen companion so the two cannot drift apart visually.
 *
 * Scroll position is local to the component: it takes only the labels and which one is current, and
 * reports taps back by index. Callers own the selection.
 */
@Composable
fun SectionBreadcrumb(
    labels: List<String>,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Int) -> Unit,
    fillAvailableWidth: Boolean,
    modifier: Modifier = Modifier
) {
    val currentIdx = currentIndex.coerceAtLeast(0)
    val navIconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Row(modifier = modifier) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Row(
                modifier = Modifier
                    .clickableNoFocus(onClick = onPrevious)
                    .padding(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = InputIcons.BumperLeft,
                    contentDescription = stringResource(R.string.ui_section_breadcrumb_previous),
                    tint = navIconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }

            val virtualMultiplier = 10000
            val virtualSize = if (labels.isNotEmpty()) labels.size * virtualMultiplier else 0

            fun virtualCenterFor(idx: Int): Int =
                if (labels.isNotEmpty()) (virtualMultiplier / 2) * labels.size + idx else 0

            var virtualPosition by remember { mutableStateOf(virtualCenterFor(currentIdx)) }
            var lastCurrentIdx by remember { mutableStateOf(currentIdx) }
            var lastLabelCount by remember { mutableStateOf(labels.size) }
            var snapNext by remember { mutableStateOf(true) }

            LaunchedEffect(labels.size) {
                if (labels.isEmpty()) return@LaunchedEffect
                if (labels.size != lastLabelCount) {
                    snapNext = true
                    virtualPosition = virtualCenterFor(currentIdx)
                    lastCurrentIdx = currentIdx
                    lastLabelCount = labels.size
                }
            }

            LaunchedEffect(currentIdx) {
                if (labels.isEmpty() || labels.size != lastLabelCount) return@LaunchedEffect
                val delta = when {
                    lastCurrentIdx == labels.lastIndex && currentIdx == 0 -> 1
                    lastCurrentIdx == 0 && currentIdx == labels.lastIndex -> -1
                    else -> currentIdx - lastCurrentIdx
                }
                virtualPosition += delta
                lastCurrentIdx = currentIdx
            }

            val breadcrumbListState = rememberLazyListState(
                initialFirstVisibleItemIndex = virtualCenterFor(currentIdx)
            )

            fun centerOffset(): Int {
                val info = breadcrumbListState.layoutInfo
                val viewportWidth = info.viewportSize.width
                val targetItem = info.visibleItemsInfo.firstOrNull { it.index == virtualPosition }
                val itemWidth = targetItem?.size
                    ?: info.visibleItemsInfo.firstOrNull()?.size
                    ?: 0
                return (viewportWidth - itemWidth) / 2
            }

            LaunchedEffect(virtualPosition) {
                if (snapNext) {
                    snapNext = false
                    breadcrumbListState.scrollToItem(virtualPosition, -centerOffset())
                } else {
                    breadcrumbListState.animateScrollToItem(virtualPosition, -centerOffset())
                }
            }

            val fadeBrush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black,
                0.85f to Color.Black,
                1f to Color.Transparent
            )

            Box(
                modifier = Modifier
                    .then(
                        if (fillAvailableWidth) Modifier.weight(1f)
                        else Modifier.widthIn(max = Dimens.breadcrumbMaxWidth)
                    )
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(Dimens.radiusMd)
                    )
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = fadeBrush,
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .padding(vertical = Dimens.spacingXs)
            ) {
                LazyRow(
                    state = breadcrumbListState,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    userScrollEnabled = false
                ) {
                    items(virtualSize) { virtualIndex ->
                        val realIndex = virtualIndex.mod(labels.size)
                        if (virtualIndex > 0) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = Dimens.spacingXs)
                            )
                        }
                        Text(
                            text = labels[realIndex],
                            style = if (virtualIndex == virtualPosition) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.labelMedium,
                            color = if (virtualIndex == virtualPosition) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier
                                .clickableNoFocus { onSelect(realIndex) }
                                .padding(horizontal = Dimens.spacingXs)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clickableNoFocus(onClick = onNext)
                    .padding(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = InputIcons.BumperRight,
                    contentDescription = stringResource(R.string.ui_section_breadcrumb_next),
                    tint = navIconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}
