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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.domain.model.CarouselConfig
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeRowAlignment
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.touchOnly

internal const val HERO_START_PADDING_SCREEN_RATIO = 0.09f
internal const val HERO_END_PADDING_SCREEN_RATIO = 0.65f
internal const val HERO_ITEM_GAP_CARD_RATIO = 0.13f
internal const val HERO_MAX_WIDTH_FRACTION = 0.28f
internal const val HERO_MIN_CARD_SCALE = 0.4f

/**
 * How far neighbours step aside for the focused card: exactly the half-width it grows past its own
 * slot, so the visible gap either side of it stays the layout's item gap whatever the sizes are.
 * Deriving it from the overflow rather than from the card width is what keeps the setting useful at
 * every resting size instead of only where the two happened to be in proportion.
 */
internal fun neighbourPushFor(cardWidth: Dp, focusScale: Float): Dp =
    (cardWidth * (focusScale - 1f) / 2f).coerceAtLeast(0.dp)

internal fun HomeRowAlignment.toScalePivotY(): Float = when (this) {
    HomeRowAlignment.TOP -> 0f
    HomeRowAlignment.CENTER -> 0.5f
    HomeRowAlignment.BOTTOM -> 1f
}

internal fun HomeRowAlignment.toVerticalAlignment(): Alignment.Vertical = when (this) {
    HomeRowAlignment.TOP -> Alignment.Top
    HomeRowAlignment.CENTER -> Alignment.CenterVertically
    HomeRowAlignment.BOTTOM -> Alignment.Bottom
}

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

    /**
     * A show or a film on one of home's media rails. It is its own member rather than a dressed-up
     * [Game] because the two share nothing but a rectangle: this one carries a poster, the episode a
     * press will start, how far through it the reader is and whether it is on the device.
     */
    data class Media(
        override val key: String,
        val media: HomeMediaUi
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
    /**
     * The edge the focused card grows away from, as a fraction down the card. It has to match the
     * rail's own alignment: a card aligned to the top of the row but scaling about its centre grows
     * back over the row's edge and is clipped there.
     */
    val scalePivotY: Float,
    val anchor: CarouselAnchor,
    val itemGap: Dp,
    val neighbourPush: Dp,
    val allowFocusOverflow: Boolean,
    /**
     * Reverses the reading order relative to the ambient layout direction, so it composes with a
     * right-to-left locale rather than cancelling it out.
     */
    val reversed: Boolean = false,
    /**
     * Where the resting cards sit against the taller focused one. Bottom stands them on a shelf,
     * top hangs them from a rail, centre splits the difference.
     */
    val verticalAlignment: Alignment.Vertical = Alignment.Bottom
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
            scalePivotY = config.rowAlignment.toScalePivotY(),
            anchor = when (config.focusPosition) {
                HomeFocusPosition.LEADING -> CarouselAnchor.START
                HomeFocusPosition.CENTER -> CarouselAnchor.CENTER
            },
            itemGap = cardWidth * HERO_ITEM_GAP_CARD_RATIO,
            neighbourPush = if (config.neighbourPush) {
                neighbourPushFor(cardWidth, config.focusScale)
            } else {
                0.dp
            },
            allowFocusOverflow = true,
            reversed = config.inverted,
            verticalAlignment = config.rowAlignment.toVerticalAlignment()
        )

        /**
         * The companion strip, sized from the height it is given so it fills its display the way
         * the hero rail fills a phone's. [availableHeight] of zero means the caller has not measured
         * yet and falls back to the card token, so the first frame is a sane strip rather than
         * nothing.
         */
        fun centered(
            coverAspectRatio: Float,
            config: CarouselConfig = CarouselConfig(),
            availableHeight: Dp = 0.dp,
            availableWidth: Dp = 0.dp
        ): CarouselMetrics {
            val fallback = ComponentDefaults.Carousel.companionCardWidth.dp / coverAspectRatio
            val cardSize = if (availableHeight > 0.dp && availableWidth > 0.dp) {
                carouselCardSize(
                    availableHeight = availableHeight,
                    availableWidth = availableWidth,
                    coverAspectRatio = coverAspectRatio,
                    restingScale = config.restingScale,
                    minCardHeight = fallback * HERO_MIN_CARD_SCALE
                )
            } else {
                DpSize(fallback * coverAspectRatio, fallback)
            }
            return CarouselMetrics(
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
                itemGap = ComponentDefaults.Carousel.companionCardGap.dp,
                neighbourPush = if (config.neighbourPush) {
                    neighbourPushFor(cardSize.width, config.focusScale)
                } else {
                    0.dp
                },
                allowFocusOverflow = true,
                reversed = config.inverted,
                verticalAlignment = config.rowAlignment.toVerticalAlignment()
            )
        }
    }
}

/**
 * Resting card size for a rail given the height it has to work with. The focused card always fills
 * that height, so the resting card is [restingScale] of it and the focus transform is whatever
 * closes the gap. [availableWidth] caps the card so one cover cannot dominate the row on very tall
 * windows, and [minCardHeight] is the floor below which the row collapses to a strip.
 *
 * Shared so a schematic preview and the live rail cannot disagree about proportions.
 */
internal fun carouselCardSize(
    availableHeight: Dp,
    availableWidth: Dp,
    coverAspectRatio: Float,
    restingScale: Float,
    minCardHeight: Dp
): DpSize {
    val heightDriven = availableHeight * restingScale
    val widthCap = (availableWidth * HERO_MAX_WIDTH_FRACTION) / coverAspectRatio
    val cardHeight = maxOf(minOf(heightDriven, widthCap), minCardHeight)
    return DpSize(cardHeight * coverAspectRatio, cardHeight)
}

/**
 * Content padding that places the focused card on [CarouselMetrics.anchor]. [startGutter] is the
 * clearance kept beyond the focused card's overhang so a scaled card never clips the leading edge.
 *
 * A reversed rail lays its first item out against the opposite edge while padding keeps its
 * left-and-right meaning, so the two are swapped: the leading anchor's gutter has to follow the
 * cards to whichever side they now begin on.
 */
internal fun carouselContentPadding(
    metrics: CarouselMetrics,
    availableWidth: Dp,
    startGutter: Dp
): PaddingValues = when (metrics.anchor) {
    CarouselAnchor.START -> {
        val leading = maxOf(
            availableWidth * HERO_START_PADDING_SCREEN_RATIO,
            metrics.cardWidth * (metrics.focusScale - 1f) / 2f + startGutter
        )
        val trailing = availableWidth * HERO_END_PADDING_SCREEN_RATIO
        if (metrics.reversed) {
            PaddingValues(start = trailing, end = leading)
        } else {
            PaddingValues(start = leading, end = trailing)
        }
    }
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
    useBoxArt: Boolean = false,
    viewAllStyle: ViewAllCardStyle = ViewAllCardStyle.OUTLINE_GRID,
    onItemTap: (Int) -> Unit = {},
    onItemLongPress: ((Int) -> Unit)? = null,
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, Bitmap) -> Unit)? = null
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val boxArtStyle = LocalBoxArtStyle.current
    /**
     * The push is a screen-space translation while the item order is not, so a reversed rail has to
     * flip it: the indices below the focused one sit to its right there, and pushing them the way
     * their index suggests drives both neighbours into it instead of apart.
     */
    val neighbourPushPx = with(LocalDensity.current) { metrics.neighbourPush.toPx() }

    val horizontalPadding = carouselContentPadding(
        metrics = metrics,
        availableWidth = screenWidth,
        startGutter = Dimens.spacingMd
    )
    val layoutDirection = LocalLayoutDirection.current
    val contentPadding = PaddingValues(
        start = horizontalPadding.calculateStartPadding(layoutDirection),
        end = horizontalPadding.calculateEndPadding(layoutDirection)
    )

    /**
     * Every card carries badge room above its artwork, so aligning the row to a top or centre edge
     * would align the reserved space rather than the art. Taking that back out here puts the
     * artwork where the alignment says it should be, and leaves the badge sitting in the gap.
     */
    val originShift = when (metrics.verticalAlignment) {
        Alignment.Top -> -NEW_BADGE_TOP_OVERFLOW
        Alignment.CenterVertically -> -NEW_BADGE_TOP_OVERFLOW / 2
        else -> 0.dp
    }

    LazyRow(
        state = listState,
        reverseLayout = metrics.reversed,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(metrics.itemGap),
        verticalAlignment = metrics.verticalAlignment,
        modifier = modifier
            .fillMaxWidth()
            .offset(y = originShift)
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val isFocused = index == focusedIndex
            val pushAway = if (metrics.reversed) -neighbourPushPx else neighbourPushPx
            val pushTargetPx = when {
                index < focusedIndex -> -pushAway
                index > focusedIndex -> pushAway
                else -> 0f
            }
            val translationX by animateFloatAsState(
                targetValue = pushTargetPx,
                animationSpec = Motion.focusSpring,
                label = "carouselPush"
            )
            val tapModifier = carouselTapModifier(tapMode, index, onItemTap, onItemLongPress)
            val placementModifier = Modifier
                .graphicsLayer { this.translationX = translationX }
                .then(
                    if (metrics.focusedOverlapsNeighbours) {
                        Modifier.zIndex(if (isFocused) 1f else 0f)
                    } else {
                        Modifier
                    }
                )
            when (item) {
                is CarouselItem.Game -> {
                    val cardModifier = placementModifier.then(tapModifier)
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
                        useBoxArt = useBoxArt,
                        onCoverLoadFailed = onCoverLoadFailed,
                        onCoverLoaded = onCoverLoaded,
                        modifier = cardModifier
                    )
                }
                is CarouselItem.Media -> {
                    CarouselMediaCard(
                        item = item,
                        isFocused = isFocused,
                        showFocusVisuals = showFocusVisuals,
                        metrics = metrics,
                        overrides = overrides,
                        modifier = placementModifier
                            .padding(top = NEW_BADGE_TOP_OVERFLOW)
                            .then(tapModifier)
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
                        scalePivotY = metrics.scalePivotY,
                        modifier = Modifier
                            .graphicsLayer {
                                this.translationX = translationX
                                alpha = viewAllAlpha
                            }
                            .padding(top = NEW_BADGE_TOP_OVERFLOW)
                            .size(metrics.cardWidth, metrics.cardHeight)
                    )
                }
            }
        }
    }
}

private fun carouselTapModifier(
    tapMode: CarouselTapMode,
    index: Int,
    onItemTap: (Int) -> Unit,
    onItemLongPress: ((Int) -> Unit)?
): Modifier = when (tapMode) {
    CarouselTapMode.CLICK -> if (onItemLongPress != null) {
        Modifier.clickableNoFocus(
            onClick = { onItemTap(index) },
            onLongClick = { onItemLongPress(index) }
        )
    } else {
        Modifier.clickableNoFocus { onItemTap(index) }
    }
    CarouselTapMode.TOUCH -> Modifier.touchOnly { onItemTap(index) }
}

/**
 * A poster fitted inside the slot the metrics describe. Fitting rather than filling is what keeps a
 * 2:3 poster from being cropped into the shape of whatever box art the reader has chosen, while the
 * slot itself, and so the rail's rhythm, stays the one the layout worked out.
 */
@Composable
private fun CarouselMediaCard(
    item: CarouselItem.Media,
    isFocused: Boolean,
    showFocusVisuals: Boolean,
    metrics: CarouselMetrics,
    overrides: CarouselOverrides,
    modifier: Modifier = Modifier
) {
    val maxWidth = if (isFocused) metrics.focusedCardWidth else metrics.cardWidth
    val maxHeight = if (isFocused) metrics.focusedCardHeight else metrics.cardHeight
    val cardSize = coverSizeWithin(maxWidth, maxHeight, mediaPosterAspectRatio)

    MediaCard(
        media = item.media,
        isFocused = isFocused && showFocusVisuals,
        focusScale = metrics.focusScale,
        scalePivotY = metrics.scalePivotY,
        scaleOverride = if (isFocused) overrides.focusedScale else null,
        alphaOverride = if (isFocused) overrides.focusedAlpha else overrides.unfocusedAlpha,
        modifier = modifier.size(cardSize.width, cardSize.height)
    )
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
    useBoxArt: Boolean,
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

    GameCardWithNewBadge(
        game = item.game,
        isFocused = isFocused && showFocusVisuals,
        cardWidth = cardSize.width,
        cardHeight = cardSize.height,
        focusScale = metrics.focusScale,
        scalePivotY = metrics.scalePivotY,
        downloadIndicator = item.downloadIndicator,
        showPlatformBadge = showPlatformBadge,
        useBoxArt = useBoxArt,
        coverPathOverride = item.coverPathOverride,
        onCoverLoadFailed = onCoverLoadFailed,
        onCoverLoaded = onCoverLoaded,
        scaleOverride = scaleOverride,
        alphaOverride = alphaOverride,
        modifier = modifier
    )
}
