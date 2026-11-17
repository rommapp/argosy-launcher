package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val GestureTokens = ComponentDefaults.QuickMenu

/**
 * A horizontal swipe that switches section: the content follows the finger and [onSwipe] receives
 * +1 for the next section and -1 for the previous one once the drag passes
 * [ComponentDefaults.QuickMenu.swipeCommitRatio] of the width or flings.
 */
fun Modifier.sectionSwipe(onSwipe: (Int) -> Unit): Modifier = composed {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val flingPx = with(LocalDensity.current) { GestureTokens.flingVelocityDp.dp.toPx() }
    this
        .onSizeChanged { width = it.width }
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDragEnd = {
                    val distance = offset.value
                    val velocity = tracker.calculateVelocity().x
                    val passed = abs(distance) > width * GestureTokens.swipeCommitRatio
                    val flung = abs(velocity) > flingPx && sign(velocity) == sign(distance)
                    scope.launch {
                        if (passed || flung) {
                            val direction = if (distance < 0f) 1 else -1
                            currentOnSwipe(direction)
                            offset.snapTo(distance + direction * width)
                        }
                        offset.animateTo(0f, tween(Motion.durationSlide, easing = Motion.argosyEase))
                    }
                },
                onDragCancel = {
                    scope.launch { offset.animateTo(0f, tween(Motion.durationSlide, easing = Motion.argosyEase)) }
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            )
        }
        .graphicsLayer { translationX = offset.value }
}

/**
 * Drags a sheet down and calls [onDismiss] past [ComponentDefaults.QuickMenu.dismissDragRatio] of
 * its height or on a downward fling. Scrolling content passes the drag on once it sits at its top.
 */
fun Modifier.dismissOnSwipeDown(onDismiss: () -> Unit): Modifier = composed {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(0) }
    val flingPx = with(LocalDensity.current) { GestureTokens.flingVelocityDp.dp.toPx() }
    val settle: (Float) -> Unit = { velocity ->
        scope.launch {
            if (offset.value > height * GestureTokens.dismissDragRatio || (velocity > flingPx && offset.value > 0f)) {
                currentOnDismiss()
                offset.snapTo(0f)
            } else {
                offset.animateTo(0f, tween(Motion.durationSlide, easing = Motion.argosyEase))
            }
        }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (offset.value <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = available.y.coerceAtLeast(-offset.value)
                scope.launch { offset.snapTo(offset.value + consumed) }
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                scope.launch { offset.snapTo(offset.value + available.y) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    this
        .onSizeChanged { height = it.height }
        .nestedScroll(connection)
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDragEnd = { settle(tracker.calculateVelocity().y) },
                onDragCancel = { settle(0f) },
                onVerticalDrag = { change, amount ->
                    if (amount < 0f && offset.value <= 0f) return@detectVerticalDragGestures
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo((offset.value + amount).coerceAtLeast(0f)) }
                }
            )
        }
        .graphicsLayer { translationY = offset.value }
}
