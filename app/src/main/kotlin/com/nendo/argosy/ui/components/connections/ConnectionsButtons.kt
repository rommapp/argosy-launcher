package com.nendo.argosy.ui.components.connections

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.components.GamepadHoldTracker
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.rememberHoldProgress
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.actionButtonLabelColor
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
internal fun ForgetHoldButton(
    label: String,
    tracker: GamepadHoldTracker?,
    onHoldStart: () -> Unit,
    onConfirmed: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    var touchHeld by remember { mutableStateOf(false) }
    val progress = rememberHoldProgress(
        held = touchHeld || tracker?.isHeld == true,
        holdMs = ComponentDefaults.Connections.forgetHoldMs.toLong(),
        gamepadTracker = tracker,
        onHoldStart = onHoldStart,
        onConfirmed = {
            touchHeld = false
            onConfirmed()
        }
    )
    val fill = theme.destructive.copy(alpha = ComponentDefaults.HoldButton.fillAlpha)
    val radius = Dimens.radiusControl
    ActionButton(
        label = label,
        onClick = {},
        focused = true,
        accentColor = theme.destructive,
        glyph = InputButton.X,
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touchHeld = true
                    try {
                        waitForUpOrCancellation(PointerEventPass.Initial)
                    } finally {
                        touchHeld = false
                    }
                }
            }
            .drawWithContent {
                drawContent()
                if (progress > 0f) {
                    clipRect(right = size.width * progress) {
                        drawRoundRect(color = fill, cornerRadius = CornerRadius(radius.toPx()))
                    }
                }
            }
    )
}

@Composable
internal fun OutsideLaunchButton(label: String, onClick: () -> Unit) {
    val labelColor = actionButtonLabelColor(focused = true)
    ActionButton(onClick = onClick, focused = true) {
        InputGlyph(button = InputButton.A, tint = labelColor)
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}
