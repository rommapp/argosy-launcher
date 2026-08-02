package com.nendo.argosy.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.MotionTokens

enum class MotionTier { Snappy, Comfortable, Reduced }

val LocalMotionTier = staticCompositionLocalOf { MotionTier.Comfortable }

object Motion {
    val argosyEase: Easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

    val durationMicro = MotionTokens.Tween.microMs
    val durationContent = MotionTokens.Tween.contentMs
    val durationSlide = MotionTokens.Tween.slideMs
    val durationPage = MotionTokens.Tween.pageMs
    val durationDrawer = MotionTokens.Tween.drawerMs

    val focusSpring: AnimationSpec<Float> = MotionTokens.Spring.focus
    val focusSpringDp: AnimationSpec<Dp> = MotionTokens.Spring.focusDp
    val focusColorSpec: AnimationSpec<Color> = MotionTokens.Spring.focusColor

    const val transitionDebounceMs = 200L

    const val focusScrollDebounceMs = 60L
    const val scrollPaddingPercent = 0.2f

    val blurRadiusModal = 8.dp
    val blurRadiusDrawer = 24.dp

    fun tierFocusSpring(tier: MotionTier): AnimationSpec<Float> = when (tier) {
        MotionTier.Snappy -> MotionTokens.Spring.focusSnappy
        MotionTier.Reduced -> MotionTokens.Spring.focusReduced
        MotionTier.Comfortable -> MotionTokens.Spring.focus
    }

    fun tierFocusSpringDp(tier: MotionTier): AnimationSpec<Dp> = when (tier) {
        MotionTier.Snappy -> MotionTokens.Spring.focusSnappyDp
        MotionTier.Reduced -> MotionTokens.Spring.focusReducedDp
        MotionTier.Comfortable -> MotionTokens.Spring.focusDp
    }
}
