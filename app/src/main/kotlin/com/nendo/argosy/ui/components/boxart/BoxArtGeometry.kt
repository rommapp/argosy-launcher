package com.nendo.argosy.ui.components.boxart

import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.SystemIconPosition

internal data class BoxArtGeometry(
    val outerCornerRadiusPx: Float,
    val frameWidthPx: Float,
    val oneDpPx: Float,
    val badgeWidthPx: Float,
    val badgeHeightPx: Float,
    val scaledCornerRadiusPx: Float,
    val innerEffect: BoxArtInnerEffect,
    val innerEffectWidth: Float,
    val effectiveBadgePosition: SystemIconPosition
)
