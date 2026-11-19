package com.nendo.argosy.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.isReducedMotion
import com.nendo.argosy.ui.theme.generated.MotionTokens

enum class MotionTier { Snappy, Comfortable, Reduced }

val LocalMotionTier = staticCompositionLocalOf { MotionTier.Comfortable }

fun resolveMotionTier(reduceAnimations: Boolean, animatorsEnabled: Boolean): MotionTier =
    if (isReducedMotion(reduceAnimations, animatorsEnabled)) MotionTier.Reduced else MotionTier.Comfortable

@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean = LocalMotionTier.current == MotionTier.Reduced

@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    Motion.tierSpec(LocalMotionTier.current, spec)

@Composable
@ReadOnlyComposable
fun motionDuration(durationMs: Int): Int = Motion.tierDuration(LocalMotionTier.current, durationMs)

@Composable
@ReadOnlyComposable
fun motionEnter(enter: EnterTransition): EnterTransition = Motion.tierEnter(LocalMotionTier.current, enter)

@Composable
@ReadOnlyComposable
fun motionExit(exit: ExitTransition): ExitTransition = Motion.tierExit(LocalMotionTier.current, exit)

@Composable
fun InfiniteTransition.loopingFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>,
    label: String,
    stillValue: Float = initialValue
): State<Float> = if (reducedMotion()) {
    rememberUpdatedState(stillValue)
} else {
    animateFloat(initialValue, targetValue, animationSpec, label)
}

@Composable
fun animateModalBlur(
    active: Boolean,
    radius: Dp = Motion.blurRadiusModal,
    label: String = "modalBlur"
): State<Dp> = animateDpAsState(
    targetValue = if (active) Motion.tierModalBlur(LocalMotionTier.current, radius) else 0.dp,
    animationSpec = Motion.focusSpringDp,
    label = label
)

@Composable
fun Modifier.modalScrim(active: Boolean): Modifier {
    if (!active || !reducedMotion()) return this
    val scrim: Color = LocalLauncherTheme.current.overlayLight
    return drawWithContent {
        drawContent()
        drawRect(scrim)
    }
}

object Motion {
    val argosyEase: Easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

    val durationFast = MotionTokens.Tween.fastMs
    val durationMicro = MotionTokens.Tween.microMs
    val durationContent = MotionTokens.Tween.contentMs
    val durationSlide = MotionTokens.Tween.slideMs
    val durationPage = MotionTokens.Tween.pageMs
    val durationOverlay = MotionTokens.Tween.overlayMs
    val durationLinger = MotionTokens.Tween.lingerMs
    val durationDrawer = MotionTokens.Tween.drawerMs
    val durationMedium = MotionTokens.Tween.mediumMs
    val durationShimmer = MotionTokens.Tween.shimmerMs

    val focusSpring: AnimationSpec<Float>
        @Composable @ReadOnlyComposable get() = tierFocusSpring(LocalMotionTier.current)
    val focusSpringDp: AnimationSpec<Dp>
        @Composable @ReadOnlyComposable get() = tierFocusSpringDp(LocalMotionTier.current)
    val focusColorSpec: AnimationSpec<Color>
        @Composable @ReadOnlyComposable get() = tierFocusColorSpec(LocalMotionTier.current)

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

    fun tierFocusColorSpec(tier: MotionTier): AnimationSpec<Color> = when (tier) {
        MotionTier.Snappy -> MotionTokens.Spring.focusSnappyColor
        MotionTier.Reduced -> MotionTokens.Spring.focusReducedColor
        MotionTier.Comfortable -> MotionTokens.Spring.focusColor
    }

    fun <T> tierSpec(tier: MotionTier, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (tier == MotionTier.Reduced) snap() else spec

    fun tierDuration(tier: MotionTier, durationMs: Int): Int =
        if (tier == MotionTier.Reduced) 0 else durationMs

    fun tierEnter(tier: MotionTier, enter: EnterTransition): EnterTransition =
        if (tier == MotionTier.Reduced) EnterTransition.None else enter

    fun tierExit(tier: MotionTier, exit: ExitTransition): ExitTransition =
        if (tier == MotionTier.Reduced) ExitTransition.None else exit

    fun tierContent(tier: MotionTier, transform: ContentTransform): ContentTransform =
        if (tier == MotionTier.Reduced) EnterTransition.None togetherWith ExitTransition.None else transform

    fun tierModalBlur(tier: MotionTier, radius: Dp): Dp =
        if (tier == MotionTier.Reduced) 0.dp else radius
}
