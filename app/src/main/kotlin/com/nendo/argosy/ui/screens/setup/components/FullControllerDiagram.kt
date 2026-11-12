package com.nendo.argosy.ui.screens.setup.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A whole controller drawn on the Controllercons Series X outline, the one shape every layout
 * choice shares. The body, button wells and bumpers come from the art; the face buttons, sticks,
 * d-pad, menu buttons and triggers are live pieces placed over the art's own button positions.
 * Face labels follow [swaps], held inputs light from [reading], sticks and triggers draw its axis
 * deflection, and [swapped] outlines buttons a user swap moved.
 */
@Composable
fun FullControllerDiagram(
    reading: PadReading,
    swaps: ButtonGlyphSwaps,
    swapped: Set<InputButton>,
    modifier: Modifier = Modifier,
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val holdProgress = rememberHoldProgress(reading.holding)
    val art = (ComponentDefaults.Setup.padArtWidthDp * s).dp
    val unit = art / ART_VIEWPORT
    Box(modifier = modifier.size(width = art, height = unit * (ART_BOTTOM - ART_TOP)).clipToBounds()) {
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(art)
                .offset(y = -unit * ART_TOP),
        ) {
            ArtLayer(R.drawable.controllercons_series_body, theme.surfaceRaised)
            ArtLayer(R.drawable.controllercons_series_wells, theme.surfaceElevated)
            Bumper(InputButton.LB, LeftBumper, mirrored = false, unit, reading, holdProgress)
            Bumper(InputButton.RB, RightBumper, mirrored = true, unit, reading, holdProgress)
            Trigger(InputButton.LT, reading.axes.leftTrigger, LeftTrigger, mirrored = false, unit, reading, holdProgress)
            Trigger(InputButton.RT, reading.axes.rightTrigger, RightTrigger, mirrored = true, unit, reading, holdProgress)
            Stick(InputButton.LEFT_STICK_CLICK, reading.axes.leftX, reading.axes.leftY, LeftStick, unit, reading, holdProgress)
            Stick(InputButton.RIGHT_STICK_CLICK, reading.axes.rightX, reading.axes.rightY, RightStick, unit, reading, holdProgress)
            DPad(DPadCenter, unit, reading, holdProgress)
            val faceSize = unit * FACE_DIAMETER
            val faceGap = unit * (FACE_STEP - FACE_DIAMETER)
            Box(modifier = Modifier.placedAt(FaceCenter, unit, unit * (FACE_STEP * 2 + FACE_DIAMETER))) {
                FaceDiamond(
                    reading = reading,
                    swaps = swaps,
                    swapped = swapped,
                    holdProgress = holdProgress,
                    faceSize = faceSize,
                    gap = faceGap,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(
                modifier = Modifier.placedAt(MenuCenter, unit, unit * MENU_SPAN, unit * MENU_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                MenuPills(reading = reading, swaps = swaps, swapped = swapped, holdProgress = holdProgress)
            }
        }
    }
}

@Composable
private fun BoxScope.ArtLayer(@DrawableRes art: Int, tint: Color) {
    Image(
        painter = painterResource(art),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.matchParentSize(),
    )
}

@Composable
private fun BoxScope.Bumper(
    button: InputButton,
    area: ArtRect,
    mirrored: Boolean,
    unit: Dp,
    reading: PadReading,
    holdProgress: () -> Float,
) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val lit = reading.isLit(button)
    val holding = reading.holding == button
    val colorSpec: AnimationSpec<Color> = if (reduced) snap() else tween(Motion.durationMicro, easing = Motion.argosyEase)
    val fill by animateColorAsState(
        targetValue = if (lit) theme.focusAccent else theme.surfaceElevated,
        animationSpec = colorSpec,
        label = "bumper-fill",
    )
    val edge by animateColorAsState(
        targetValue = if (lit) theme.focusAccent else theme.hairlineHigh,
        animationSpec = colorSpec,
        label = "bumper-edge",
    )
    val data = stringResource(R.string.controllercons_series_bumper_path)
    val artPath = remember(data) { PathParser().parsePathString(data).toPath() }
    val outlineWidth = Dimens.borderThin
    val ringWidth = Dimens.borderThick
    val ringColor = theme.textPrimary
    Spacer(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val scaled = Path().apply {
                    addPath(artPath)
                    transform(Matrix().apply { scale(size.width / ART_VIEWPORT, size.height / ART_VIEWPORT) })
                    if (mirrored) transform(mirrorAcross(size.width))
                }
                val measure = PathMeasure().apply { setPath(scaled, true) }
                val segment = Path()
                val outline = Stroke(width = outlineWidth.toPx(), join = StrokeJoin.Round)
                val ring = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    drawPath(scaled, fill)
                    drawPath(scaled, edge, style = outline)
                    if (holding) {
                        segment.reset()
                        measure.getSegment(0f, measure.length * holdProgress(), segment, true)
                        drawPath(segment, ringColor, style = ring)
                    }
                }
            },
    )
    InputGlyph(
        button = button,
        size = Dimens.iconSm,
        tint = theme.textPrimary,
        modifier = Modifier.placedAt(area.labelAt, unit, Dimens.iconSm),
    )
}

@Composable
private fun Trigger(
    trigger: InputButton,
    depth: Float,
    area: ArtRect,
    mirrored: Boolean,
    unit: Dp,
    reading: PadReading,
    holdProgress: () -> Float,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(
        topStart = Dimens.radiusLg,
        topEnd = Dimens.radiusLg,
        bottomStart = Dimens.radiusSm,
        bottomEnd = Dimens.radiusSm,
    )
    val held = reading.isLit(trigger)
    val fraction = when {
        depth > 0f -> depth.coerceAtMost(1f)
        held -> 1f
        else -> 0f
    }
    Box(
        modifier = Modifier
            .placedAt(area.center, unit, unit * area.width, unit * area.height)
            .holdRing(reading.holding == trigger, holdProgress, shape, theme.textPrimary, Dimens.borderThick, mirrored)
            .clip(shape)
            .background(theme.surfaceElevated)
            .border(Dimens.borderThin, if (held) theme.focusAccent else theme.hairlineHigh, shape),
    ) {
        Box(
            modifier = Modifier
                .align(if (mirrored) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(theme.focusAccent),
        )
        InputGlyph(
            button = trigger,
            size = Dimens.iconSm,
            tint = theme.textPrimary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun Stick(
    click: InputButton,
    x: Float,
    y: Float,
    center: ArtPoint,
    unit: Dp,
    reading: PadReading,
    holdProgress: () -> Float,
) {
    val theme = LocalArgosyTheme.current
    val well = unit * STICK_DIAMETER
    val knob = unit * STICK_KNOB_DIAMETER
    val active = reading.isLit(click) || hypot(x, y) > STICK_ACTIVE
    PadSurface(
        lit = false,
        swapped = false,
        holding = reading.holding == click,
        holdProgress = holdProgress,
        width = well,
        height = well,
        shape = CircleShape,
        modifier = Modifier.placedAt(center, unit, well),
    ) {
        Box(
            modifier = Modifier
                .offset {
                    val travel = ((well - knob) / 2).toPx()
                    IntOffset(
                        (travel * x.coerceIn(-1f, 1f)).roundToInt(),
                        (travel * y.coerceIn(-1f, 1f)).roundToInt(),
                    )
                }
                .size(knob)
                .clip(CircleShape)
                .background(if (active) theme.focusAccent else theme.textDim),
        )
    }
}

@Composable
private fun DPad(center: ArtPoint, unit: Dp, reading: PadReading, holdProgress: () -> Float) {
    val key = unit * DPAD_KEY
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(modifier = Modifier.placedAt(center, unit, key * 3)) {
        DpadSlots.forEach { (button, column, row) ->
            PadSurface(
                lit = reading.isLit(button),
                swapped = false,
                holding = reading.holding == button,
                holdProgress = holdProgress,
                width = key,
                height = key,
                shape = shape,
                modifier = Modifier.offset(x = key * column, y = key * row),
            )
        }
    }
}

private fun Modifier.placedAt(center: ArtPoint, unit: Dp, width: Dp, height: Dp = width): Modifier =
    offset(x = unit * center.x - width / 2, y = unit * center.y - height / 2).size(width = width, height = height)

private data class ArtPoint(val x: Float, val y: Float)

private data class ArtRect(val center: ArtPoint, val width: Float, val height: Float, val labelAt: ArtPoint)

private data class DpadSlot(val button: InputButton, val column: Int, val row: Int)

private val DpadSlots = listOf(
    DpadSlot(InputButton.DPAD_UP, column = 1, row = 0),
    DpadSlot(InputButton.DPAD_LEFT, column = 0, row = 1),
    DpadSlot(InputButton.DPAD_RIGHT, column = 2, row = 1),
    DpadSlot(InputButton.DPAD_DOWN, column = 1, row = 2),
)

private const val ART_VIEWPORT = 64f
private const val ART_TOP = 3.5f
private const val ART_BOTTOM = 55f
private const val STICK_DIAMETER = 7.2f
private const val STICK_KNOB_DIAMETER = 3.6f
private const val STICK_ACTIVE = 0.25f
private const val FACE_DIAMETER = 4.46f
private const val FACE_STEP = 4.3f
private const val DPAD_KEY = 2.95f
private const val MENU_SPAN = 20f
private const val MENU_HEIGHT = 4f

private val LeftStick = ArtPoint(15.605f, 21.903f)
private val RightStick = ArtPoint(40.38f, 31.475f)
private val DPadCenter = ArtPoint(23.43f, 32.3f)
private val FaceCenter = ArtPoint(48.388f, 21.98f)
private val MenuCenter = ArtPoint(32f, 21.82f)
private val LeftBumper = ArtRect(ArtPoint(16.4f, 12.3f), 14f, 5.2f, ArtPoint(16.4f, 11.9f))
private val RightBumper = ArtRect(ArtPoint(47.6f, 12.3f), 14f, 5.2f, ArtPoint(47.6f, 11.9f))
private val LeftTrigger = ArtRect(ArtPoint(16.4f, 7.2f), 10f, 3.6f, ArtPoint(16.4f, 7.2f))
private val RightTrigger = ArtRect(ArtPoint(47.6f, 7.2f), 10f, 3.6f, ArtPoint(47.6f, 7.2f))
