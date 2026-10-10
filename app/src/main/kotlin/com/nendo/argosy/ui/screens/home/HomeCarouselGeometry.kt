package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.ui.components.HERO_MAX_WIDTH_FRACTION
import com.nendo.argosy.ui.components.HERO_START_PADDING_SCREEN_RATIO
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.tanh

internal const val HOME_COMPACT_WIDTH_DP = 600f

internal data class HomeCarouselGeometry(
    val cardWidth: Float,
    val cardHeight: Float,
    val railTop: Float,
    val railHeight: Float,
    val titleCenterX: Float,
    val titleCenterY: Float,
    val titleMaxWidth: Float,
    val titleAbove: Boolean
)

internal fun interruptedSpringMaximum(minimum: Float, maximum: Float, dampingRatio: Float): Float {
    require(dampingRatio > 0f)
    val gain = if (dampingRatio >= 1f) 1.0 else {
        1.0 / tanh(PI * dampingRatio / (2.0 * sqrt(1.0 - dampingRatio * dampingRatio)))
    }
    return ((minimum + maximum) / 2.0 + (maximum - minimum) * gain / 2.0).toFloat()
}

internal fun homeCarouselGeometry(
    width: Float,
    height: Float,
    headerHeight: Float,
    footerHeight: Float,
    edge: Float,
    gap: Float,
    titleReserve: Float,
    aboveTitleReserve: Float = titleReserve,
    aspectRatio: Float,
    restingScale: Float,
    springMinimumRestingScale: Float = restingScale,
    rowAlignment: HomeRowAlignment,
    centeredFocus: Boolean,
    mirrored: Boolean,
    badgeOverflow: Float,
    dampingRatio: Float,
    neighbourPush: Boolean = true,
    scrollAnchorOffset: Float = 0f,
    showTitle: Boolean = true
): HomeCarouselGeometry {
    val usableWidth = width.coerceAtLeast(0f)
    val contentTop = headerHeight.coerceIn(0f, height.coerceAtLeast(0f))
    val contentBottom = (height - footerHeight - edge).coerceAtLeast(contentTop)
    val available = contentBottom - contentTop
    val aspect = aspectRatio.takeIf { it > 0f && it.isFinite() } ?: 1f
    val focusScale = 1f / restingScale.coerceAtLeast(0.5f)
    val springFocusScale = 1f / minOf(restingScale, springMinimumRestingScale).coerceAtLeast(0.5f)
    val maximumScale = interruptedSpringMaximum(1f, springFocusScale, dampingRatio)
    val badgeReserve = badgeOverflow * maximumScale
    val artTop = contentTop + badgeReserve
    val artAvailable = (available - badgeReserve).coerceAtLeast(0f)
    val widthCap = usableWidth * HERO_MAX_WIDTH_FRACTION / aspect
    val aboveRemaining = (available - aboveTitleReserve.coerceAtLeast(0f) - gap).coerceAtLeast(0f)
    val aboveCardHeight = minOf((aboveRemaining / maximumScale - badgeOverflow).coerceAtLeast(0f), widthCap)
    val bottomSideHeightLimit = available - titleReserve - 2f * gap - 2f * badgeOverflow
    val fittedSideHeight = if (showTitle && width > height && !centeredFocus && rowAlignment == HomeRowAlignment.BOTTOM) {
        bottomSideHeightLimit
    } else Float.POSITIVE_INFINITY
    val sideCardHeight = minOf(available / maximumScale - badgeOverflow, widthCap, fittedSideHeight).coerceAtLeast(0f)
    val sideCardWidth = sideCardHeight * aspect
    val leading = maxOf(
        usableWidth * HERO_START_PADDING_SCREEN_RATIO,
        sideCardWidth * (focusScale - 1f) / 2f + gap
    )
    val focusedRight = leading + sideCardWidth * (1f + focusScale) / 2f
    val logicalCenterX = (focusedRight + usableWidth - edge) / 2f
    val restingTop = when (rowAlignment) {
        HomeRowAlignment.TOP -> artTop
        HomeRowAlignment.CENTER -> artTop + (artAvailable - sideCardHeight) / 2f
        HomeRowAlignment.BOTTOM -> contentBottom - sideCardHeight
    }
    val pivot = when (rowAlignment) {
        HomeRowAlignment.TOP -> 0f
        HomeRowAlignment.CENTER -> 0.5f
        HomeRowAlignment.BOTTOM -> 1f
    }
    val focusedTop = restingTop - sideCardHeight * (focusScale - 1f) * pivot
    val focusedBottom = restingTop + sideCardHeight + sideCardHeight * (focusScale - 1f) * (1f - pivot)
    val centerY = when (rowAlignment) {
        HomeRowAlignment.TOP -> (restingTop + sideCardHeight + focusedBottom) / 2f
        HomeRowAlignment.CENTER -> focusedTop
        HomeRowAlignment.BOTTOM -> (contentTop + restingTop) / 2f
    }
    val bandTop = centerY - titleReserve / 2f - gap
    val bandBottom = centerY + titleReserve / 2f + gap
    val crossesRestingBand = if (rowAlignment == HomeRowAlignment.BOTTOM) {
        sideCardHeight > bottomSideHeightLimit
    } else {
        bandBottom > restingTop - badgeOverflow && bandTop < restingTop + sideCardHeight
    }
    val settledSweptRight = if (crossesRestingBand) usableWidth else homeFocusedCardSweptRight(
        leading + scrollAnchorOffset, sideCardWidth, springFocusScale, dampingRatio, neighbourPush
    )
    val safeWidth = minOf(usableWidth / 2f, 2f * minOf(
        logicalCenterX - settledSweptRight - gap,
        usableWidth - edge - logicalCenterX
    )).coerceAtLeast(0f)
    val titleAbove = showTitle && (width <= height || centeredFocus || sideCardHeight < aboveCardHeight || safeWidth <= 0f ||
        bandTop < contentTop || bandBottom > contentBottom)
    if (showTitle && !titleAbove) {
        return HomeCarouselGeometry(
            sideCardWidth, sideCardHeight, artTop, artAvailable,
            if (mirrored) usableWidth - logicalCenterX else logicalCenterX,
            centerY, safeWidth, false
        )
    }

    val reserve = if (showTitle) aboveTitleReserve.coerceAtLeast(0f) else 0f
    val titleGap = if (showTitle) gap else 0f
    val remaining = (available - reserve - titleGap).coerceAtLeast(0f)
    val cardHeight = minOf((remaining / maximumScale - badgeOverflow).coerceAtLeast(0f), widthCap)
    val railHeight = minOf(cardHeight * maximumScale, remaining)
    val allocatedBadge = minOf(badgeReserve, (remaining - railHeight).coerceAtLeast(0f))
    val groupHeight = reserve + titleGap + allocatedBadge + railHeight
    val groupTop = when {
        showTitle && width >= height -> contentTop
        width < height || rowAlignment == HomeRowAlignment.CENTER -> contentTop + (available - groupHeight).coerceAtLeast(0f) / 2f
        rowAlignment == HomeRowAlignment.TOP -> contentTop
        else -> (contentBottom - groupHeight).coerceAtLeast(contentTop)
    }
    return HomeCarouselGeometry(
        cardHeight * aspect, cardHeight,
        groupTop + reserve + titleGap + allocatedBadge, railHeight,
        usableWidth / 2f, groupTop + reserve / 2f,
        (usableWidth - edge * 2f).coerceAtLeast(0f), titleAbove
    )
}

internal fun homeFocusedCardSweptRight(
    leading: Float,
    cardWidth: Float,
    focusScale: Float,
    dampingRatio: Float,
    neighbourPush: Boolean
): Float {
    val halfWidth = cardWidth / 2f
    val maximumRightExtent = if (neighbourPush) {
        interruptedSpringMaximum(halfWidth * (2f - focusScale), halfWidth * focusScale, dampingRatio)
    } else {
        halfWidth * interruptedSpringMaximum(1f, focusScale, dampingRatio)
    }
    return leading + halfWidth + maximumRightExtent
}

internal data class HomeFlowSize(val width: Int, val height: Int)

internal fun homeFlowSize(items: List<HomeFlowSize>, maxWidth: Int, horizontalGap: Int, verticalGap: Int): HomeFlowSize {
    var width = 0
    var height = 0
    var rowWidth = 0
    var rowHeight = 0
    items.forEach { item ->
        if (rowWidth > 0 && rowWidth + horizontalGap + item.width > maxWidth) {
            width = maxOf(width, rowWidth)
            height += rowHeight + verticalGap
            rowWidth = 0
            rowHeight = 0
        }
        rowWidth += (if (rowWidth == 0) 0 else horizontalGap) + item.width
        rowHeight = maxOf(rowHeight, item.height)
    }
    return HomeFlowSize(maxOf(width, rowWidth), height + rowHeight)
}
