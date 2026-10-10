package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.components.HERO_MAX_WIDTH_FRACTION

internal data class HomeCarouselContent(
    val geometry: HomeCarouselGeometry,
    val viewportTop: Float,
    val viewportHeight: Float,
    val contentHeight: Float,
    val scrollable: Boolean
)

internal fun homeCarouselContent(
    fitted: HomeCarouselGeometry,
    width: Float,
    height: Float,
    headerHeight: Float,
    footerHeight: Float,
    edge: Float,
    gap: Float,
    titleReserve: Float,
    aspectRatio: Float,
    restingScale: Float,
    springMinimumRestingScale: Float,
    badgeOverflow: Float,
    dampingRatio: Float,
    minimumFocusedExtent: Float,
    preferredCardHeight: Float,
    enabled: Boolean
): HomeCarouselContent {
    val top = headerHeight.coerceIn(0f, height.coerceAtLeast(0f))
    val viewport = (height - footerHeight - edge - top).coerceAtLeast(0f)
    val aspect = aspectRatio.takeIf { it > 0f && it.isFinite() } ?: 1f
    val focusScale = 1f / restingScale.coerceAtLeast(0.5f)
    val focusedExtent = fitted.cardHeight * minOf(1f, aspect) * focusScale
    if (!enabled || (focusedExtent >= minimumFocusedExtent && titleReserve <= viewport)) {
        return HomeCarouselContent(fitted, 0f, height.coerceAtLeast(0f), height.coerceAtLeast(0f), false)
    }
    val maximumScale = interruptedSpringMaximum(
        1f, 1f / minOf(restingScale, springMinimumRestingScale).coerceAtLeast(0.5f), dampingRatio
    )
    val horizontalFit = minOf(
        width.coerceAtLeast(0f) * HERO_MAX_WIDTH_FRACTION / aspect,
        (width - edge * 2f).coerceAtLeast(0f) / (aspect * maximumScale)
    )
    val minimumCardHeight = minimumFocusedExtent / (focusScale * minOf(1f, aspect))
    val cardHeight = minOf(maxOf(preferredCardHeight, minimumCardHeight), horizontalFit)
    val reserve = titleReserve.coerceAtLeast(0f)
    val railTop = reserve + gap + badgeOverflow * maximumScale
    val railHeight = cardHeight * maximumScale
    val geometry = HomeCarouselGeometry(
        cardHeight * aspect, cardHeight, railTop, railHeight,
        width.coerceAtLeast(0f) / 2f, reserve / 2f,
        (width - edge * 2f).coerceAtLeast(0f), true
    )
    return HomeCarouselContent(geometry, top, viewport, maxOf(viewport, railTop + railHeight), true)
}
