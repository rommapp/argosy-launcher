package com.nendo.argosy.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Motion {
    val focusSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 400f)
    val focusSpringDp: AnimationSpec<Dp> = spring(dampingRatio = 0.6f, stiffness = 400f)
    val focusColorSpec: AnimationSpec<Color> = spring(dampingRatio = 0.6f, stiffness = 400f)

    const val transitionDebounceMs = 200L

    const val focusScrollDebounceMs = 60L
    const val scrollPaddingPercent = 0.2f

    val blurRadiusModal = 8.dp
    val blurRadiusDrawer = 24.dp
}
