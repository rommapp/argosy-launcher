package com.nendo.argosy.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavBackStackEntry
import com.nendo.argosy.ui.theme.Motion
import kotlin.math.sign

private const val DEPTH_OFFSET_DIVISOR = 4
private const val ORIGIN_SCALE = 0.85f

/**
 * Where the tile that opens a game sits on screen, so the game's detail page can grow out of it
 * and shrink back into it. A tile records itself while it holds focus; the record names the back
 * stack entry the tile was drawn in, and only a transition leaving or returning to that entry uses it.
 */
class NavTransitionOrigin {
    private var entryId: String? = null
    private var ownerKey: Any? = null
    private var centerInWindow: Offset = Offset.Unspecified
    private var hostBounds: Rect = Rect.Zero

    fun record(entryId: String, key: Any, center: Offset) {
        this.entryId = entryId
        ownerKey = key
        centerInWindow = center
    }

    fun release(key: Any) {
        if (ownerKey != key) return
        entryId = null
        ownerKey = null
        centerInWindow = Offset.Unspecified
    }

    fun updateHost(bounds: Rect) {
        hostBounds = bounds
    }

    fun pivotFor(entry: NavBackStackEntry): TransformOrigin? {
        if (entry.id != entryId || !centerInWindow.isSpecified) return null
        if (hostBounds.width <= 0f || hostBounds.height <= 0f) return null
        return TransformOrigin(
            ((centerInWindow.x - hostBounds.left) / hostBounds.width).coerceIn(0f, 1f),
            ((centerInWindow.y - hostBounds.top) / hostBounds.height).coerceIn(0f, 1f)
        )
    }
}

val LocalNavTransitionOrigin = staticCompositionLocalOf<NavTransitionOrigin?> { null }

/**
 * Marks this tile as the place its game's detail page grows from while [focused] holds. Outside a
 * navigation destination, or with no [LocalNavTransitionOrigin], it does nothing.
 */
@Composable
fun Modifier.navTransitionOrigin(key: Any, focused: Boolean): Modifier {
    val origin = LocalNavTransitionOrigin.current ?: return this
    val entry = LocalLifecycleOwner.current as? NavBackStackEntry ?: return this
    SideEffect { if (!focused) origin.release(key) }
    if (!focused) return this
    return onGloballyPositioned { coordinates ->
        origin.record(entry.id, key, coordinates.boundsInWindow().center)
    }
}

/**
 * Slides content in from the end edge when [depth] grows past the depth it was last shown at, and
 * from the start edge when it shrinks, each time [key] changes. Content is drawn from the first
 * frame; only its offset animates.
 */
@Composable
fun Modifier.depthEntryMotion(key: Any, depth: Int, reduced: Boolean): Modifier {
    val tracker = remember { DepthTracker(depth) }
    val direction = remember(key) { if (reduced) 0 else (depth - tracker.depth).sign }
    val progress = remember(key) { Animatable(if (direction == 0) 1f else 0f) }
    SideEffect { tracker.depth = depth }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(Motion.durationPage, easing = Motion.argosyEase))
    }
    val edge = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    return graphicsLayer {
        translationX = direction * edge * size.width * (1f - progress.value) / DEPTH_OFFSET_DIVISOR
    }
}

private class DepthTracker(var depth: Int)

private val siblingFadeIn: EnterTransition = fadeIn(tween(Motion.durationMicro))
private val siblingFadeOut: ExitTransition = fadeOut(tween(Motion.durationMicro))
private val depthFadeIn: EnterTransition = fadeIn(tween(Motion.durationFast))
private val depthFadeOut: ExitTransition = fadeOut(tween(Motion.durationFast))

private fun NavBackStackEntry.isRingPage(): Boolean {
    val route = destination.route ?: return false
    return NavRing.PAGES.any { NavRing.routeMatches(it.route, route) }
}

private fun NavBackStackEntry.isGameDetail(): Boolean =
    NavRing.routeMatches(Screen.GameDetail.route, destination.route)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isSiblingSwitch(): Boolean =
    initialState.isRingPage() && targetState.isRingPage()

private fun <T> depthSpec() = tween<T>(Motion.durationPage, easing = Motion.argosyEase)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.openingPivot(
    origin: NavTransitionOrigin
): TransformOrigin? = origin.pivotFor(initialState)?.takeIf { targetState.isGameDetail() }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.closingPivot(
    origin: NavTransitionOrigin
): TransformOrigin? = origin.pivotFor(targetState)?.takeIf { initialState.isGameDetail() }

fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(
    origin: NavTransitionOrigin,
    reduced: Boolean
): EnterTransition {
    if (reduced || isSiblingSwitch()) return siblingFadeIn
    val pivot = openingPivot(origin)
    if (pivot != null) {
        return scaleIn(depthSpec(), initialScale = ORIGIN_SCALE, transformOrigin = pivot) + depthFadeIn
    }
    return slideIntoContainer(SlideDirection.Start, depthSpec()) { it / DEPTH_OFFSET_DIVISOR } + depthFadeIn
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(
    origin: NavTransitionOrigin,
    reduced: Boolean
): ExitTransition {
    if (reduced || isSiblingSwitch()) return siblingFadeOut
    if (openingPivot(origin) != null) return depthFadeOut
    return slideOutOfContainer(SlideDirection.Start, depthSpec()) { it / DEPTH_OFFSET_DIVISOR } + depthFadeOut
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnter(
    origin: NavTransitionOrigin,
    reduced: Boolean
): EnterTransition {
    if (reduced || isSiblingSwitch()) return siblingFadeIn
    if (closingPivot(origin) != null) return depthFadeIn
    return slideIntoContainer(SlideDirection.End, depthSpec()) { it / DEPTH_OFFSET_DIVISOR } + depthFadeIn
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExit(
    origin: NavTransitionOrigin,
    reduced: Boolean
): ExitTransition {
    if (reduced || isSiblingSwitch()) return siblingFadeOut
    val pivot = closingPivot(origin)
    if (pivot != null) {
        return scaleOut(depthSpec(), targetScale = ORIGIN_SCALE, transformOrigin = pivot) +
            fadeOut(depthSpec())
    }
    return slideOutOfContainer(SlideDirection.End, depthSpec()) { it / DEPTH_OFFSET_DIVISOR } + depthFadeOut
}
