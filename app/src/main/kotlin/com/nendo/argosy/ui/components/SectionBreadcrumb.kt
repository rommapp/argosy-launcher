package com.nendo.argosy.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.TypographyTokens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The row of section names with trigger arrows either side, shared by the single-screen home and the
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
    modifier: Modifier = Modifier,
    onPillWidthChanged: ((Dp) -> Unit)? = null
) {
    if (labels.isEmpty()) return
    val currentIdx = currentIndex.coerceIn(labels.indices)
    val navIconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val previousDescription = stringResource(R.string.ui_section_breadcrumb_previous)
    val nextDescription = stringResource(R.string.ui_section_breadcrumb_next)
    val fadeWidth = Dimens.spacingMd
    val uiScale = LocalUiScale.current.scale
    val navigationMaxWidth = ComponentDefaults.FrostedSurface.navigationMaxWidthDp.dp * uiScale
    val navigationSlotWidth = minimumTouchTarget * uiScale
    val navigationHeight = frostedVisualChromeHeight
    val selectedTextStyle = chromeTextStyle(
        MaterialTheme.typography.titleMedium,
        TypographyTokens.titleMedium,
        ComponentDefaults.FrostedSurface.navigationSelectedFontSizeSp.sp
    )
    val inactiveTextStyle = chromeTextStyle(
        MaterialTheme.typography.bodyMedium,
        TypographyTokens.bodyMedium,
        ComponentDefaults.FrostedSurface.navigationInactiveFontSizeSp.sp
    )
    val separatorTextStyle = chromeTextStyle(
        MaterialTheme.typography.labelMedium,
        TypographyTokens.labelMedium,
        TypographyTokens.labelMedium.fontSize
    )
    val density = LocalDensity.current
    val resolvedTypeface by LocalFontFamilyResolver.current.resolve(
        fontFamily = selectedTextStyle.fontFamily,
        fontWeight = selectedTextStyle.fontWeight ?: FontWeight.Normal,
        fontStyle = selectedTextStyle.fontStyle ?: FontStyle.Normal,
        fontSynthesis = selectedTextStyle.fontSynthesis ?: FontSynthesis.All
    )
    val triggerFontSizePx = with(density) { selectedTextStyle.fontSize.toPx() }
    val triggerGlyphHeightPx = remember(resolvedTypeface, triggerFontSizePx) {
        val typeface = resolvedTypeface as? Typeface
        if (typeface == null) {
            triggerFontSizePx
        } else {
            val bounds = Rect()
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = triggerFontSizePx
            }.getTextBounds("H", 0, 1, bounds)
            bounds.height().toFloat().takeIf { it > 0f } ?: triggerFontSizePx
        }
    }
    val triggerPaintedHeight = with(density) { triggerGlyphHeightPx.toDp() }
    val triggerViewportScale = ComponentDefaults.FrostedSurface.navigationTriggerViewportToPaintedHeightRatio
    val triggerIconModifier = Modifier
        .size(triggerPaintedHeight)
        .requiredSize(triggerPaintedHeight * triggerViewportScale)

    Row(
        modifier = modifier.heightIn(min = minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Row(
                modifier = Modifier
                    .clickableNoFocus(onClick = onPrevious)
                    .semantics { contentDescription = previousDescription }
                    .sizeIn(minWidth = navigationSlotWidth, minHeight = navigationHeight)
                    .padding(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = InputIcons.TriggerLeft,
                    contentDescription = null,
                    tint = navIconTint,
                    modifier = triggerIconModifier
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

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = navigationMaxWidth)
                    .then(
                        if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier
                    )
                    .heightIn(min = navigationHeight)
                    .onSizeChanged { onPillWidthChanged?.invoke(with(density) { it.width.toDp() }) }
                    .frostedSurface(),
                contentAlignment = Alignment.Center
            ) {
                val itemMaxWidth = (maxWidth - fadeWidth * 2).coerceAtLeast(0.dp)
                LazyRow(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fade = fadeWidth.toPx().coerceAtMost(size.width / 2)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Black),
                                    startX = 0f,
                                    endX = fade
                                ),
                                size = Size(fade, size.height),
                                blendMode = BlendMode.DstIn
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(Color.Black, Color.Transparent),
                                    startX = size.width - fade,
                                    endX = size.width
                                ),
                                topLeft = Offset(size.width - fade, 0f),
                                size = Size(fade, size.height),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    state = breadcrumbListState,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    userScrollEnabled = false
                ) {
                    items(virtualSize, key = { it }) { virtualIndex ->
                        val realIndex = virtualIndex.mod(labels.size)
                        if (virtualIndex > 0) {
                            Text(
                                text = "·",
                                style = separatorTextStyle,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = Dimens.spacingXs)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clickableNoFocus { onSelect(realIndex) }
                                .widthIn(max = itemMaxWidth)
                                .sizeIn(minWidth = navigationSlotWidth, minHeight = navigationHeight)
                                .padding(horizontal = Dimens.spacingXs, vertical = Dimens.spacingSm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labels[realIndex],
                                style = if (virtualIndex == virtualPosition) selectedTextStyle else inactiveTextStyle,
                                color = if (virtualIndex == virtualPosition) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clickableNoFocus(onClick = onNext)
                    .semantics { contentDescription = nextDescription }
                    .sizeIn(minWidth = navigationSlotWidth, minHeight = navigationHeight)
                    .padding(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = InputIcons.TriggerRight,
                    contentDescription = null,
                    tint = navIconTint,
                    modifier = triggerIconModifier
                )
            }
        }
    }
}
