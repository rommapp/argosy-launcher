package com.nendo.argosy.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.nendo.argosy.ui.theme.generated.MotionTokens

fun Modifier.clickableNoFocus(onClick: () -> Unit): Modifier = composed {
    focusProperties { canFocus = false }.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

fun Modifier.clickableNoFocus(enabled: Boolean, onClick: () -> Unit): Modifier = composed {
    (if (enabled) focusProperties { canFocus = false } else Modifier).clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.clickableNoFocus(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = composed {
    focusProperties { canFocus = false }.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.clickableNoFocus(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = focusProperties { canFocus = false }.combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
    onLongClick = onLongClick
)

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.doubleTapNoFocus(onDoubleClick: () -> Unit): Modifier = composed {
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
        onDoubleClick = onDoubleClick
    )
}

fun Modifier.clickableNoFocus(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick
)

fun Modifier.touchOnly(onClick: () -> Unit): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    pointerInput(Unit) {
        detectTapGestures(onTap = { currentOnClick() })
    }
}

fun Modifier.touchOnly(onClick: () -> Unit, onLongPress: () -> Unit): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        detectTapGestures(onTap = { currentOnClick() }, onLongPress = { currentOnLongPress() })
    }
}

internal enum class ObservedTapStep { PENDING, TAP, ABANDON }

internal fun observedTapStep(
    travelled: Float,
    touchSlop: Float,
    released: Boolean,
    consumedAbove: Boolean
): ObservedTapStep = when {
    consumedAbove -> ObservedTapStep.ABANDON
    travelled > touchSlop -> ObservedTapStep.ABANDON
    released -> ObservedTapStep.TAP
    else -> ObservedTapStep.PENDING
}

/**
 * Reports a tap inside the element without consuming it, so the clickable content underneath
 * receives the same tap. A press that travels past touch slop is a scroll and reports nothing,
 * and so is a press an enclosing layer consumed. [onTap] runs before the content's own click
 * handler sees the release.
 */
fun Modifier.observeTap(onTap: () -> Unit): Modifier = composed {
    val currentOnTap by rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: break
                val step = observedTapStep(
                    travelled = (change.position - down.position).getDistance(),
                    touchSlop = slop,
                    released = change.changedToUpIgnoreConsumed(),
                    consumedAbove = change.isConsumed
                )
                if (step == ObservedTapStep.TAP) currentOnTap()
                if (step != ObservedTapStep.PENDING) break
            }
        }
    }
}

/**
 * Reports the first finger landing inside the element without consuming it, ahead of any
 * scroll or click the content runs on the same press.
 */
fun Modifier.observeTouchDown(onDown: () -> Unit): Modifier = composed {
    val currentOnDown by rememberUpdatedState(onDown)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            currentOnDown()
        }
    }
}

fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MotionTokens.Spring.focusSnappy,
        label = "argosy-press-scale"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
