package com.nendo.argosy.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

object Motion {
    // Unified animation specs for all focusable components
    val focusSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 400f)
    val focusSpringDp: AnimationSpec<Dp> = spring(dampingRatio = 0.6f, stiffness = 400f)

    // Scale
    const val scaleFocused = 1.1f
    const val scaleDefault = 1.0f

    // Alpha
    const val alphaFocused = 1f
    const val alphaUnfocused = 0.85f

    // Saturation
    const val saturationFocused = 1f
    const val saturationUnfocused = 0.3f

    // Glow
    const val glowAlphaFocused = 0.4f
    const val glowAlphaUnfocused = 0f
}
