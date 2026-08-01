package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.ui.common.coverSizeWithin
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.domain.model.CarouselConfig
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.touchOnly

internal const val HERO_START_PADDING_SCREEN_RATIO = 0.09f
internal const val HERO_END_PADDING_SCREEN_RATIO = 0.65f
internal const val HERO_ITEM_GAP_CARD_RATIO = 0.13f
internal const val HERO_NEIGHBOUR_PUSH_CARD_RATIO = 0.5f
internal const val HERO_MAX_WIDTH_FRACTION = 0.28f
internal const val HERO_MIN_CARD_SCALE = 0.4f

/**
 * Where the focused card comes to rest in the viewport. The member carries the pixel offset that
 * the owner of the list state must pass to `animateScrollToItem`, because the snap rule and the
 * content padding have to agree or the focused card lands off-anchor.
 */
enum class CarouselAnchor(val snapOffsetPx: Int) {
    START(ComponentDefaults.Carousel.focusSnapOffsetPx),
    CENTER(0)
}

/**
 * How a card reacts to touch. TOUCH ignores non-touch pointers and carries no long press.
 */
enum class CarouselTapMode { CLICK, TOUCH }

sealed class CarouselItem {
    abstract val key: String

    data class Game(
        override val key: String,
        val game: HomeGameUi,
        val downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
        val coverPathOverride: String? = null
    ) : CarouselItem()

    data class ViewAll(
        override val key: String,
        val remainingCount: Int = 0
    ) : CarouselItem()
}

/**
 * Transient emphasis applied on top of the metrics, for callers that dim or freeze the rail while
 * something else owns the screen. A null value leaves the card's own focus animation in charge.
 */
data class CarouselOverrides(
    val focusedScale: Float? = null,
    val focusedAlpha: Float? = null,
    val unfocusedAlpha: Float? = null,
    val viewAllAlpha: Float = 1f
)

/**
 * Geometry of one rail. [hero] and [centered] are the two shipped layouts: hero grows the focused
 * card out of a bottom-anchored row and pushes its neighbours aside, centered swaps a wider card in
 * at a fixed midpoint. Callers may also build metrics directly.
 */
data class CarouselMetrics(
    val cardWidth: Dp,
    val cardHeight: Dp,
    val focusedCardWidth: Dp,
    val focusedCardHeight: Dp,
    val focusScale: Float,
    val scaleFromBottom: Boolean,
    val anchor: CarouselAnchor,
    val itemGap: Dp,
    val neighbourPush: Dp,
    val allowFocusOverflow: Boolean,
    /**
     * Reverses the reading order relative to the ambient layout direction, so it composes with a
     * right-to-left locale rather than cancelling it out.
     */
    val reversed: Boolean = false
) {
    val focusedOverlapsNeighbours: Boolean
        get() = focusScale > 1f || neighbourPush > 0.dp

    companion object {
        fun hero(
            cardWidth: Dp,
            cardHeight: Dp,
            config: CarouselConfig = CarouselConfig()
        ): CarouselMetrics = CarouselMetrics(
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            focusedCardWidth = cardWidth,
            focusedCardHeight = cardHeight,
            focusScale = config.focusScale,
            scaleFromBottom = config.rowAlignment == HomeRowAlignment.BOTTOM,
            anchor = when (config.focusPosition) {
                HomeFocusPosition.LEADING -> CarouselAnchor.START
                HomeFocusPosition.CENTER -> CarouselAnchor.CENTER
            },
            itemGap = cardWidth * HERO_ITEM_GAP_CARD_RATIO,
            neighbourPush = if (config.neighbourPush) {
                cardWidth * HERO_NEIGHBOUR_PUSH_CARD_RATIO
            } else {
                0.dp
            },
            allowFocusOverflow = true,
            reversed = config.inverted
        )

        fun centered(coverAspectRatio: Float): CarouselMetrics {
            val cardWidth = ComponentDefaults.Carousel.companionCardWidth.dp
            val focusedCardWidth = ComponentDefaults.Carousel.companionCardWidthFocused.dp
            return CarouselMetrics(
                cardWidth = cardWidth,
                cardHeight = cardWidth / coverAspectRatio,
                focusedCardWidth = focusedCardWidth,
                focusedCardHeight = focusedCardWidth / coverAspectRatio,
                focusScale = 1f,
                scaleFromBottom = false,
                anchor = CarouselAnchor.CENTER,
                itemGap = ComponentDefaults.Carousel.companionCardGap.dp,
                neighbourPush = 0.dp,
                allowFocusOverflow = false
            )
        }
    }
}

/**
 * Card size for a hero rail, driven by the height left over after the surrounding chrome so the
 * focused card at [focusScale] fills that space exactly. [availableWidth] caps the card so one
 * cover cannot dominate the row on very tall windows, and [minCardHeight] is the floor below which
 * the row collapses to a strip.
 *
 * Shared so a schematic preview and the live rail cannot disagree about proportions.
 */
internal fun carouselCardSize(
    availableHeight: Dp,
    availableWidth: Dp,
    coverAspectRatio: Float,
    focusScale: Float,
    restingScale: Float,
    minCardHeight: Dp
): DpSize {
    val heightDriven = (availableHeight / focusScale) * restingScale
    val widthCap = (availableWidth * HERO_MAX_WIDTH_FRACTION) / coverAspectRatio
    val cardHeight = maxOf(minOf(heightDriven, widthCap), minCardHeight)
    return DpSize(cardHeight * coverAspectRatio, cardHeight)
}

/**
 * Content padding that places the focused card on [CarouselMetrics.anchor]. [startGutter] is the
 * clearance kept beyond the focused card's overhang so a scaled card never clips the leading edge.
 */
internal fun carouselContentPadding(
    metrics: CarouselMetrics,
    availableWidth: Dp,
    startGutter: Dp
): PaddingValues = when (metrics.anchor) {
    CarouselAnchor.START -> PaddingValues(
        start = maxOf(
            availableWidth * HERO_START_PADDING_SCREEN_RATIO,
            metrics.cardWidth * (metrics.focusScale - 1f) / 2f + startGutter
        ),
        end = availableWidth * HERO_END_PADDING_SCREEN_RATIO
    )
    CarouselAnchor.CENTER -> PaddingValues(
        horizontal = (availableWidth - metrics.focusedCardWidth) / 2
    )
}

/**
 * Horizontal game rail shared by the launcher home row and the companion carousel. The rail owns
 * layout and card rendering only; the focus index, the list state and the scroll effects stay with
 * the caller, which must snap using [CarouselMetrics.anchor]'s `snapOffsetPx`.
 *
 * @param showFocusVisuals false suppresses the focus treatment on game cards while another region
 * holds input. The trailing View All card keeps its own focus marker either way.
 */
@Composable
fun CarouselRail(
    items: List<CarouselItem>,
    focusedIndex: Int,
    listState: LazyListState,
    metrics: CarouselMetrics,
    modifier: Modifier = Modifier,
    tapMode: CarouselTapMode = CarouselTapMode.CLICK,
    overrides: CarouselOverrides = CarouselOverrides(),
    showFocusVisuals: Boolean = true,
    showPlatformBadge: Boolean = true,
    showNewBadge: Boolean = true,
    viewAllStyle: ViewAllCardStyle = ViewAllCardStyle.OUTLINE_GRID,
    onItemTap: (Int) -> Unit = {},
    onItemLongPress: ((Int) -> Unit)? = null,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, Bitmap) -> Unit)? = null
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val boxArtStyle = LocalBoxArtStyle.current
    val neighbourPushPx = with(LocalDensity.current) { metrics.neighbourPush.toPx() }

    val contentPadding = carouselContentPadding(
        metrics = metrics,
        availableWidth = screenWidth,
        startGutter = Dimens.spacingMd
    )

    LazyRow(
        state = listState,
        reverseLayout = metrics.reversed,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(metrics.itemGap),
        verticalAlignment = when (metrics.anchor) {
            CarouselAnchor.START -> Alignment.Bottom
            CarouselAnchor.CENTER -> Alignment.CenterVertically
        },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (metrics.allowFocusOverflow) Modifier.graphicsLayer { clip = false } else Modifier
            )
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val isFocused = index == focusedIndex
            val pushTargetPx = when {
                index < focusedIndex -> -neighbourPushPx
                index > focusedIndex -> neighbourPushPx
                else -> 0f
            }
            val translationX by animateFloatAsState(
                targetValue = pushTargetPx,
                animationSpec = Motion.focusSpring,
                label = "carouselPush"
            )
            when (item) {
                is CarouselItem.Game -> {
                    val longPress = onItemLongPress
                    val tapModifier = when (tapMode) {
                        CarouselTapMode.CLICK -> if (longPress != null) {
                            Modifier.clickableNoFocus(
                                onClick = { onItemTap(index) },
                                onLongClick = { longPress(index) }
                            )
                        } else {
                            Modifier.clickableNoFocus { onItemTap(index) }
                        }
                        CarouselTapMode.TOUCH -> Modifier.touchOnly { onItemTap(index) }
                    }
                    val cardModifier = Modifier
                        .graphicsLayer { this.translationX = translationX }
                        .then(
                            if (metrics.focusedOverlapsNeighbours) {
                                Modifier.zIndex(if (isFocused) 1f else 0f)
                            } else {
                                Modifier
                            }
                        )
                        .then(tapModifier)
                    CarouselGameCard(
                        item = item,
                        isFocused = isFocused,
                        showFocusVisuals = showFocusVisuals,
                        metrics = metrics,
                        overrides = overrides,
                        nativeAspectRatio = boxArtStyle.nativeAspectRatio,
                        fallbackAspectRatio = boxArtStyle.aspectRatio,
                        showPlatformBadge = showPlatformBadge,
                        showNewBadge = showNewBadge,
                        onCoverLoadFailed = onCoverLoadFailed,
                        onCoverLoaded = onCoverLoaded,
                        modifier = cardModifier
                    )
                }
                is CarouselItem.ViewAll -> {
                    val viewAllAlpha by animateFloatAsState(
                        targetValue = overrides.viewAllAlpha,
                        animationSpec = Motion.focusSpring,
                        label = "carouselViewAllAlpha"
                    )
                    ViewAllCard(
                        isFocused = isFocused,
                        onClick = { onItemTap(index) },
                        style = viewAllStyle,
                        tapMode = tapMode,
                        remainingCount = item.remainingCount,
                        focusScale = metrics.focusScale,
                        scaleFromBottom = metrics.scaleFromBottom,
                        modifier = Modifier
                            .graphicsLayer {
                                this.translationX = translationX
                                alpha = viewAllAlpha
                            }
                            .size(metrics.cardWidth, metrics.cardHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselGameCard(
    item: CarouselItem.Game,
    isFocused: Boolean,
    showFocusVisuals: Boolean,
    metrics: CarouselMetrics,
    overrides: CarouselOverrides,
    nativeAspectRatio: Boolean,
    fallbackAspectRatio: Float,
    showPlatformBadge: Boolean,
    showNewBadge: Boolean,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val maxWidth = if (isFocused) metrics.focusedCardWidth else metrics.cardWidth
    val maxHeight = if (isFocused) metrics.focusedCardHeight else metrics.cardHeight
    val cardSize = if (nativeAspectRatio) {
        val ratio = rememberCoverAspectRatio(
            item.coverPathOverride ?: item.game.coverPath,
            fallbackAspectRatio
        )
        coverSizeWithin(maxWidth, maxHeight, ratio)
    } else {
        DpSize(maxWidth, maxHeight)
    }
    val scaleOverride = if (isFocused) overrides.focusedScale else null
    val alphaOverride = if (isFocused) overrides.focusedAlpha else overrides.unfocusedAlpha

    if (showNewBadge) {
        GameCardWithNewBadge(
            game = item.game,
            isFocused = isFocused && showFocusVisuals,
            cardWidth = cardSize.width,
            cardHeight = cardSize.height,
            focusScale = metrics.focusScale,
            scaleFromBottom = metrics.scaleFromBottom,
            downloadIndicator = item.downloadIndicator,
            showPlatformBadge = showPlatformBadge,
            coverPathOverride = item.coverPathOverride,
            onCoverLoadFailed = onCoverLoadFailed,
            onCoverLoaded = onCoverLoaded,
            scaleOverride = scaleOverride,
            alphaOverride = alphaOverride,
            modifier = modifier
        )
    } else {
        Box(modifier = Modifier.size(cardSize).then(modifier)) {
            GameCard(
                game = item.game,
                isFocused = isFocused && showFocusVisuals,
                modifier = Modifier.fillMaxSize(),
                focusScale = metrics.focusScale,
                scaleFromBottom = metrics.scaleFromBottom,
                downloadIndicator = item.downloadIndicator,
                showPlatformBadge = showPlatformBadge,
                coverPathOverride = item.coverPathOverride,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                scaleOverride = scaleOverride,
                alphaOverride = alphaOverride
            )
        }
    }
}
