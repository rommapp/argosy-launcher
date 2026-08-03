package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.domain.model.CarouselConfig
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.delay

private const val PREVIEW_CAROUSEL_ITEMS = 8

/**
 * Resolved schematic geometry. Every value is the live home screen's own value multiplied by
 * [scale], so the preview is the real screen shrunk rather than a second set of proportions.
 */
@Immutable
internal data class HomeLayoutPreviewMetrics(
    val scale: Float,
    val coverAspectRatio: Float,
    val coverCornerRadius: Dp,
    val gap: Dp,
    val barHeight: Dp,
    val titleHeight: Dp,
    val surface: Color,
    val block: Color,
    val focus: Color,
    val text: Color
)

/**
 * Schematic of the home screen a [HomeLayoutSettings] produces: solid blocks for covers, short bars
 * for text, inside a frame shaped like the device's own screen. It draws no library data and owns
 * no state beyond its demonstration timers, so a settings pane and a setup wizard can both render
 * it from plain parameters.
 *
 * Proportions come from the helpers the live renderer uses - [carouselCardSize] and
 * [carouselContentPadding] for the rail, GridUtils for grid columns and spacing - so a layout
 * cannot look different here from how it will look on the home screen.
 *
 * @param animate false freezes every layout on its first frame, for callers that render the
 * schematic somewhere motion would be noise.
 * @param gridDensity the density the auto grid is measured at. Defaults to the token default
 * because the preview reads no repository.
 */
@Composable
fun HomeLayoutPreview(
    settings: HomeLayoutSettings,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    gridDensity: GridDensity = ComponentDefaults.Launcher.gridDensity
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val theme = LocalArgosyTheme.current
    val boxArt = LocalBoxArtStyle.current
    val frameShape = RoundedCornerShape(Dimens.radiusLg)

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(screenWidthDp.toFloat() / screenHeightDp.toFloat())
            .clip(frameShape)
            .background(theme.surfaceBase)
            .border(Dimens.borderThin, theme.hairlineHigh, frameShape)
            .clipToBounds()
    ) {
        val scale = maxWidth / screenWidthDp.dp
        val preview = HomeLayoutPreviewMetrics(
            scale = scale,
            coverAspectRatio = boxArt.aspectRatio,
            coverCornerRadius = boxArt.cornerRadiusDp * scale,
            gap = Dimens.spacingSm * scale,
            barHeight = lineHeightDp(MaterialTheme.typography.labelSmall) * scale,
            titleHeight = lineHeightDp(MaterialTheme.typography.headlineMedium) * scale,
            surface = theme.surfaceBase,
            block = theme.textDim.copy(alpha = ComponentDefaults.HomeLayoutPreview.restingBlockAlpha),
            focus = theme.focusAccent,
            text = theme.textMute
        )
        val edge = Dimens.spacingLg * scale
        val headerHeight = Dimens.headerHeight * scale
        val footerHeight = Dimens.footerHeight * scale
        val infoHeight = preview.titleHeight + Dimens.spacingXs * scale + preview.barHeight
        val frameWidth = maxWidth
        val frameHeight = maxHeight

        PreviewHeader(
            preview = preview,
            headerHeight = headerHeight,
            edge = edge,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = headerHeight, bottom = footerHeight)
        ) {
            when (settings.selected) {
                HomeLayoutKind.CAROUSEL -> CarouselSchematic(
                    config = settings.carousel,
                    preview = preview,
                    availableWidth = frameWidth,
                    availableHeight = frameHeight - headerHeight - infoHeight - footerHeight -
                        Dimens.spacingLg * scale - Dimens.spacingXl * scale,
                    animate = animate,
                    modifier = Modifier.fillMaxSize()
                )
                HomeLayoutKind.AUTO_GRID -> AutoGridSchematic(
                    config = settings.autoGrid,
                    preview = preview,
                    gridDensity = gridDensity,
                    animate = animate,
                    modifier = Modifier.padding(horizontal = edge)
                )
                HomeLayoutKind.CUSTOM_GRID -> CustomGridSchematic(
                    config = settings.customGrid,
                    preview = preview,
                    animate = animate,
                    modifier = Modifier.padding(horizontal = edge, vertical = preview.gap)
                )
            }
        }

        PreviewFooter(
            preview = preview,
            footerHeight = footerHeight,
            edge = edge,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PreviewHeader(
    preview: HomeLayoutPreviewMetrics,
    headerHeight: Dp,
    edge: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .padding(horizontal = edge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewBar(
            height = preview.barHeight,
            color = preview.text,
            widthFraction = ComponentDefaults.HomeLayoutPreview.headerBarWidthRatio,
            modifier = Modifier.weight(1f)
        )
        PreviewBar(
            height = preview.barHeight,
            color = preview.text,
            widthFraction = ComponentDefaults.HomeLayoutPreview.subtitleBarWidthRatio,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PreviewFooter(
    preview: HomeLayoutPreviewMetrics,
    footerHeight: Dp,
    edge: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(footerHeight)
            .padding(horizontal = edge),
        contentAlignment = Alignment.Center
    ) {
        PreviewBar(
            height = preview.barHeight,
            color = preview.text,
            widthFraction = ComponentDefaults.HomeLayoutPreview.footerBarWidthRatio,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PreviewBar(
    height: Dp,
    color: Color,
    widthFraction: Float,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(color)
        )
    }
}

/**
 * Rail schematic. The focused block is anchored by [CarouselConfig.focusPosition] through the same
 * [carouselContentPadding] the live rail uses, scales about the edge [CarouselConfig.rowAlignment]
 * rests the row on, and [CarouselConfig.inverted] flips the reading order relative to the ambient
 * layout direction instead of forcing left-to-right.
 */
@Composable
private fun CarouselSchematic(
    config: CarouselConfig,
    preview: HomeLayoutPreviewMetrics,
    availableWidth: Dp,
    availableHeight: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val cardSize = carouselCardSize(
        availableHeight = availableHeight,
        availableWidth = availableWidth,
        coverAspectRatio = preview.coverAspectRatio,
        restingScale = config.restingScale,
        minCardHeight = Dimens.gameCardHeight * HERO_MIN_CARD_SCALE * preview.scale
    )
    val metrics = CarouselMetrics(
        cardWidth = cardSize.width,
        cardHeight = cardSize.height,
        focusedCardWidth = cardSize.width,
        focusedCardHeight = cardSize.height,
        focusScale = config.focusScale,
        scalePivotY = config.rowAlignment.toScalePivotY(),
        anchor = when (config.focusPosition) {
            HomeFocusPosition.LEADING -> CarouselAnchor.START
            HomeFocusPosition.CENTER -> CarouselAnchor.CENTER
        },
        itemGap = cardSize.width * HERO_ITEM_GAP_CARD_RATIO,
        neighbourPush = if (config.neighbourPush) {
            neighbourPushFor(cardSize.width, config.focusScale)
        } else {
            0.dp
        },
        allowFocusOverflow = true
    )

    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(ComponentDefaults.HomeLayoutPreview.stepDwellMs.toLong())
            focusedIndex = (focusedIndex + 1).mod(PREVIEW_CAROUSEL_ITEMS)
        }
    }

    val listState = rememberLazyListState()
    val snapOffset = (metrics.anchor.snapOffsetPx * preview.scale).toInt()
    LaunchedEffect(focusedIndex, snapOffset) {
        listState.animateScrollToItem(focusedIndex, snapOffset)
    }

    val pushPx = with(LocalDensity.current) { metrics.neighbourPush.toPx() }
    val ambientDirection = LocalLayoutDirection.current
    val railDirection = if (config.inverted) invert(ambientDirection) else ambientDirection

    /**
     * The rail is reversed by flipping layout direction, but the push is a raw translation that the
     * flip does not touch, so it has to be mirrored to match or both neighbours crowd the focused
     * card instead of parting from it.
     */
    val pushAwayPx = if (railDirection == ambientDirection) pushPx else -pushPx
    val railHeight = cardSize.height * config.focusScale + Dimens.spacingMd * preview.scale

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spacingSm * preview.scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs * preview.scale)
        ) {
            PreviewBar(
                height = preview.titleHeight,
                color = preview.text,
                widthFraction = ComponentDefaults.HomeLayoutPreview.titleBarWidthRatio,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            )
            PreviewBar(
                height = preview.barHeight,
                color = preview.text,
                widthFraction = ComponentDefaults.HomeLayoutPreview.subtitleBarWidthRatio,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(modifier = Modifier.weight(1f))
        CompositionLocalProvider(LocalLayoutDirection provides railDirection) {
            LazyRow(
                state = listState,
                userScrollEnabled = false,
                contentPadding = carouselContentPadding(
                    metrics = metrics,
                    availableWidth = availableWidth,
                    startGutter = Dimens.spacingMd * preview.scale
                ),
                horizontalArrangement = Arrangement.spacedBy(metrics.itemGap),
                verticalAlignment = when (config.rowAlignment) {
                    HomeRowAlignment.TOP -> Alignment.Top
                    HomeRowAlignment.CENTER -> Alignment.CenterVertically
                    HomeRowAlignment.BOTTOM -> Alignment.Bottom
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(railHeight)
                    .graphicsLayer { clip = false }
            ) {
                items(PREVIEW_CAROUSEL_ITEMS, key = { it }) { index ->
                    val isFocused = index == focusedIndex
                    PreviewCoverBlock(
                        width = cardSize.width,
                        height = cardSize.height,
                        isFocused = isFocused,
                        focusScale = config.focusScale,
                        rowAlignment = config.rowAlignment,
                        pushPx = when {
                            index < focusedIndex -> -pushAwayPx
                            index > focusedIndex -> pushAwayPx
                            else -> 0f
                        },
                        showBadge = config.showPlatformBadge,
                        preview = preview,
                        modifier = Modifier.zIndex(if (isFocused) 1f else 0f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCoverBlock(
    width: Dp,
    height: Dp,
    isFocused: Boolean,
    focusScale: Float,
    rowAlignment: HomeRowAlignment,
    pushPx: Float,
    showBadge: Boolean,
    preview: HomeLayoutPreviewMetrics,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = Motion.focusSpring,
        label = "home-layout-preview-card-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isFocused) {
            ComponentDefaults.Focus.alphaFocused
        } else {
            ComponentDefaults.Focus.alphaUnfocused
        },
        animationSpec = Motion.focusSpring,
        label = "home-layout-preview-card-alpha"
    )
    val push by animateFloatAsState(
        targetValue = pushPx,
        animationSpec = Motion.focusSpring,
        label = "home-layout-preview-card-push"
    )
    val pivotY = when (rowAlignment) {
        HomeRowAlignment.TOP -> 0f
        HomeRowAlignment.CENTER -> 0.5f
        HomeRowAlignment.BOTTOM -> 1f
    }
    val shape = RoundedCornerShape(preview.coverCornerRadius)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, pivotY)
                translationX = push
                this.alpha = alpha
                clip = false
            }
            .size(width, height)
            .clip(shape)
            .background(if (isFocused) preview.focus else preview.block),
        contentAlignment = Alignment.TopStart
    ) {
        if (showBadge) {
            Box(
                modifier = Modifier
                    .padding(preview.gap)
                    .size(
                        width = width * ComponentDefaults.HomeLayoutPreview.badgeWidthRatio,
                        height = preview.barHeight
                    )
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(preview.surface)
            )
        }
    }
}

@Composable
private fun lineHeightDp(style: TextStyle): Dp {
    val lineHeight = style.lineHeight
    return if (lineHeight.isSp) {
        with(LocalDensity.current) { lineHeight.toDp() }
    } else {
        Dimens.spacingMd
    }
}

private fun invert(direction: LayoutDirection): LayoutDirection = when (direction) {
    LayoutDirection.Ltr -> LayoutDirection.Rtl
    LayoutDirection.Rtl -> LayoutDirection.Ltr
}
