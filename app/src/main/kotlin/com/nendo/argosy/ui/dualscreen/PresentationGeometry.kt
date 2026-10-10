package com.nendo.argosy.ui.dualscreen

internal data class PresentationSize(val width: Float, val height: Float)

internal fun fitPresentationArt(maxWidth: Float, maxHeight: Float, aspectRatio: Float): PresentationSize {
    if (maxWidth <= 0f || maxHeight <= 0f || !aspectRatio.isFinite() || aspectRatio <= 0f) {
        return PresentationSize(0f, 0f)
    }
    val width = minOf(maxWidth, maxHeight * aspectRatio)
    return PresentationSize(width, width / aspectRatio)
}

internal fun presentationTitleRadii(
    viewportWidth: Float,
    viewportHeight: Float,
    titleWidth: Float,
    titleHeight: Float,
    horizontalOverscan: Float,
    verticalOverscan: Float
): PresentationSize = PresentationSize(
    titleWidth.coerceAtLeast(0f) / 2f + viewportWidth.coerceAtLeast(0f) * horizontalOverscan,
    titleHeight.coerceAtLeast(0f) / 2f + viewportHeight.coerceAtLeast(0f) * verticalOverscan
)

internal fun presentationScrimAlpha(baseAlpha: Float, strengthScale: Float): Float =
    (baseAlpha * strengthScale).coerceIn(0f, 1f)
