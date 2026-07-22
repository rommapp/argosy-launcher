package com.nendo.argosy.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.nendo.argosy.ui.theme.generated.MotionTokens

fun Modifier.clickableNoFocus(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

fun Modifier.clickableNoFocus(enabled: Boolean, onClick: () -> Unit): Modifier = composed {
    clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.clickableNoFocus(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = composed {
    combinedClickable(
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
): Modifier = combinedClickable(
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

fun Modifier.touchOnly(onClick: () -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onTap = { onClick() })
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
