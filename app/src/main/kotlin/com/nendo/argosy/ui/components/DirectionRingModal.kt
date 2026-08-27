package com.nendo.argosy.ui.components

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.nendo.argosy.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

const val DIRECTION_STEP_FINE_DEGREES = 15f
const val DIRECTION_STEP_COARSE_DEGREES = 45f
private const val STICK_DEADZONE = 0.4f
private const val DEGREE_SIGN = "\u00B0"
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
private const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()

/** Compass ring picker for the backdrop drift angle: 0 is up, degrees advance clockwise. */
@Composable
fun DirectionRingModal(
    angle: Float,
    visible: Boolean,
    onCommit: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    if (!visibleState.currentState && !visibleState.targetState) return

    var workingAngle by remember { mutableFloatStateOf(angle.mod(360f)) }
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(visible) {
        if (visible) workingAngle = angle.mod(360f)
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onLeft(): InputResult {
                workingAngle = (workingAngle - DIRECTION_STEP_FINE_DEGREES).mod(360f)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                workingAngle = (workingAngle + DIRECTION_STEP_FINE_DEGREES).mod(360f)
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult {
                workingAngle = (workingAngle - DIRECTION_STEP_COARSE_DEGREES).mod(360f)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                workingAngle = (workingAngle + DIRECTION_STEP_COARSE_DEGREES).mod(360f)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnCommit(workingAngle)
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = visible, handler = inputHandler)

    val rawInput = LocalGamepadInputHandler.current
    DisposableEffect(visible, rawInput) {
        if (!visible || rawInput == null) return@DisposableEffect onDispose { }
        val listener: (MotionEvent) -> Boolean = { event ->
            val isJoystick = event.source and InputDevice.SOURCE_JOYSTICK != 0
            if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
                val x = event.getAxisValue(MotionEvent.AXIS_X)
                val y = event.getAxisValue(MotionEvent.AXIS_Y)
                if (hypot(x, y) > STICK_DEADZONE) {
                    workingAngle = (atan2(x, -y) * RADIANS_TO_DEGREES).mod(360f)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        rawInput.setRawMotionEventListener(listener)
        onDispose { rawInput.setRawMotionEventListener(null) }
    }

    Popup(properties = PopupProperties(focusable = false)) {
        val theme = LocalArgosyTheme.current
        val duration = Motion.durationDrawer / 2
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(duration, easing = Motion.argosyEase)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(duration, easing = Motion.argosyEase)),
            exit = fadeOut(animationSpec = tween(duration, easing = Motion.argosyEase)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(duration, easing = Motion.argosyEase)),
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(bottom = gripReserveBottomInset())
                    .clickableNoFocus { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.clickableNoFocus { }) {
                    GlassPanel {
                        Column(
                            modifier = Modifier.padding(Dimens.spacingLg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.ui_direction_ring_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textPrimary,
                            )
                            Spacer(Modifier.height(Dimens.spacingMd))
                            DirectionRing(
                                angle = workingAngle,
                                onAngleChange = { workingAngle = it },
                                onCommit = { currentOnCommit(it) },
                            )
                            Spacer(Modifier.height(Dimens.spacingMd))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                            ) {
                                RingHint(
                                    InputButton.DPAD,
                                    stringResource(R.string.ui_direction_ring_rotate)
                                )
                                RingHint(
                                    InputButton.A,
                                    stringResource(R.string.ui_direction_ring_set)
                                )
                                RingHint(
                                    InputButton.B,
                                    stringResource(R.string.ui_direction_ring_cancel)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionRing(
    angle: Float,
    onAngleChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val theme = LocalArgosyTheme.current
    val scale = LocalUiScale.current.scale
    val ringSize = (ComponentDefaults.SurfaceBackdrop.directionRingDiameter * scale).dp
    val dotDiameter = Dimens.dotLg
    val strokeWidth = Dimens.borderMedium
    val currentAngle by rememberUpdatedState(angle)
    val currentOnAngleChange by rememberUpdatedState(onAngleChange)
    val currentOnCommit by rememberUpdatedState(onCommit)
    Box(
        modifier = Modifier
            .size(ringSize)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { position ->
                    currentOnCommit(pointerAngle(position, size))
                })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        currentOnAngleChange(pointerAngle(change.position, size))
                    },
                    onDragEnd = { currentOnCommit(currentAngle) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotRadius = dotDiameter.toPx() / 2f
            val ringRadius = size.minDimension / 2f - dotRadius
            drawCircle(
                color = theme.hairlineHigh,
                radius = ringRadius,
                style = Stroke(width = strokeWidth.toPx()),
            )
            val angleRad = angle * DEGREES_TO_RADIANS
            drawCircle(
                color = theme.focusAccent,
                radius = dotRadius,
                center = center + Offset(sin(angleRad) * ringRadius, -cos(angleRad) * ringRadius),
            )
        }
        Text(
            text = "${angle.roundToInt().mod(360)}$DEGREE_SIGN",
            style = MaterialTheme.typography.titleSmall,
            color = theme.textDim,
        )
    }
}

@Composable
private fun RingHint(button: InputButton, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputGlyph(button = button, size = Dimens.iconSm)
        Spacer(Modifier.width(Dimens.spacingXs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LocalArgosyTheme.current.textDim,
        )
    }
}

private fun pointerAngle(position: Offset, size: IntSize): Float {
    val dx = position.x - size.width / 2f
    val dy = position.y - size.height / 2f
    return (atan2(dx, -dy) * RADIANS_TO_DEGREES).mod(360f)
}
