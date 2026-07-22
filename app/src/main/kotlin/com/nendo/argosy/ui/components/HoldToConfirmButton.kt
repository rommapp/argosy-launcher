package com.nendo.argosy.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.delay

/**
 * Tracks a gamepad confirm-button hold from raw key events, with a repeat-stall fallback:
 * controllers that stop delivering key repeats without ever sending ACTION_UP are treated
 * as released once no key event has arrived within the stall window (grace window before
 * the first repeat is seen, since the initial system repeat delay is ~500ms).
 */
@Stable
class GamepadHoldTracker {
    var isHeld by mutableStateOf(false)
        private set

    private var lastEventAtMs = 0L
    private var sawRepeat = false

    fun onConfirmKeyDown(isRepeat: Boolean, nowMs: Long = SystemClock.uptimeMillis()) {
        if (!isHeld) {
            if (isRepeat) return
            isHeld = true
            sawRepeat = false
        } else if (isRepeat) {
            sawRepeat = true
        }
        lastEventAtMs = nowMs
    }

    fun onConfirmKeyUp() {
        isHeld = false
    }

    fun forceRelease() {
        isHeld = false
    }

    fun eventAtMs(): Long = lastEventAtMs

    fun isStalled(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        if (!isHeld) return false
        val window = if (sawRepeat) {
            ComponentDefaults.HoldButton.repeatStallMs
        } else {
            ComponentDefaults.HoldButton.repeatGraceMs
        }
        return nowMs - lastEventAtMs > window
    }
}

/**
 * Modal-only destructive commit button: fires [onConfirmed] exactly once after an
 * uninterrupted hold; releasing early resets progress. Touch holds directly; gamepad
 * holds feed in through [gamepadTracker] from the hosting modal's raw key listener.
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    isFocused: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gamepadTracker: GamepadHoldTracker? = null,
    onHoldStart: () -> Unit = {}
) {
    val theme = LocalArgosyTheme.current
    val abIconsSwapped = LocalABIconsSwapped.current
    var touchHeld by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val gamepadHeld = gamepadTracker?.isHeld == true
    val held = enabled && (gamepadHeld || touchHeld)
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)

    LaunchedEffect(held, gamepadTracker) {
        if (!held) {
            progress = 0f
            return@LaunchedEffect
        }
        currentOnHoldStart()
        val holdMs = ComponentDefaults.HoldButton.holdDurationMs.toLong()
        val tickMs = ComponentDefaults.HoldButton.tickMs.toLong()
        val startMs = SystemClock.uptimeMillis()
        while (true) {
            delay(tickMs)
            if (gamepadTracker != null && gamepadTracker.isStalled()) {
                gamepadTracker.forceRelease()
                return@LaunchedEffect
            }
            progress = ((SystemClock.uptimeMillis() - startMs).toFloat() / holdMs).coerceIn(0f, 1f)
            if (progress >= 1f) {
                if (gamepadTracker != null && gamepadTracker.isHeld) {
                    val markEventMs = gamepadTracker.eventAtMs()
                    while (gamepadTracker.eventAtMs() <= markEventMs) {
                        delay(tickMs)
                        if (!gamepadTracker.isHeld || gamepadTracker.isStalled()) {
                            gamepadTracker.forceRelease()
                            return@LaunchedEffect
                        }
                    }
                }
                touchHeld = false
                gamepadTracker?.forceRelease()
                currentOnConfirmed()
                return@LaunchedEffect
            }
        }
    }

    val displayProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = ComponentDefaults.HoldButton.tickMs,
            easing = LinearEasing
        ),
        label = "hold-progress"
    )

    val tint = theme.destructive
    val shape = RoundedCornerShape(ComponentDefaults.HoldButton.radius.dp)
    val contentAlpha = if (enabled) 1f else 0.4f
    val labelColor = when {
        !enabled -> theme.textPrimary.copy(alpha = contentAlpha)
        isFocused || held -> tint
        else -> theme.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentDefaults.HoldButton.height.dp)
            .argosyFocusIndicators(
                focused = isFocused && enabled,
                indicators = FocusIndicators(fill = true, ring = true),
                tint = tint,
                shape = shape
            )
            .clip(shape)
            .background(theme.surfaceElevated)
            .border(
                width = Dimens.borderThin,
                color = if (isFocused && enabled) tint else theme.hairlineLow,
                shape = shape
            )
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            touchHeld = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                touchHeld = false
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(displayProgress)
                    .background(tint.copy(alpha = ComponentDefaults.HoldButton.fillAlpha))
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            modifier = Modifier.padding(horizontal = Dimens.spacingMd)
        ) {
            Icon(
                painter = if (abIconsSwapped) InputIcons.FaceRight else InputIcons.FaceBottom,
                contentDescription = null,
                tint = labelColor.copy(alpha = 0.7f * contentAlpha),
                modifier = Modifier.size(Dimens.iconSm)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = labelColor,
                maxLines = 1
            )
        }
    }
}
