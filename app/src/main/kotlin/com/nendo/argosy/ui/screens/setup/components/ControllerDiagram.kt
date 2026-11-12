package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.input.ControllerPosition
import com.nendo.argosy.ui.input.intentButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

enum class ControllerDiagramSize { FULL, MINI }

/**
 * Face-button diamond with Start and Select pills below. Positions are physical; labels follow
 * [swaps], or the glyph swap composition locals when [swaps] is null. [reading] lights held
 * buttons and rings the one counting toward the end of a test; [swapped] outlines buttons a user
 * swap moved. [MINI][ControllerDiagramSize.MINI] draws the diamond alone.
 */
@Composable
fun ControllerDiagram(
    modifier: Modifier = Modifier,
    reading: PadReading = PadReading(),
    swaps: ButtonGlyphSwaps? = null,
    swapped: Set<InputButton> = emptySet(),
    variant: ControllerDiagramSize = ControllerDiagramSize.FULL,
) {
    val resolved = swaps ?: currentGlyphSwaps()
    val s = LocalUiScale.current.scale
    val mini = variant == ControllerDiagramSize.MINI
    val holdProgress = rememberHoldProgress(reading.holding)
    Column(
        modifier = modifier.padding(
            horizontal = if (mini) 0.dp else Dimens.spacingLg,
            vertical = if (mini) 0.dp else Dimens.spacingMd,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        FaceDiamond(
            reading = reading,
            swaps = resolved,
            swapped = swapped,
            holdProgress = holdProgress,
            faceSize = ((if (mini) ComponentDefaults.Setup.miniFaceButtonDp else ComponentDefaults.Setup.faceButtonDp) * s).dp,
            gap = if (mini) Dimens.spacingXs else Dimens.spacingSm,
            style = if (mini) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleLarge,
        )
        if (!mini) {
            MenuPills(reading = reading, swaps = resolved, swapped = swapped, holdProgress = holdProgress)
        }
    }
}

@Composable
internal fun rememberHoldProgress(holding: InputButton?): () -> Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(holding) {
        progress.snapTo(0f)
        if (holding != null) {
            progress.animateTo(1f, tween(ComponentDefaults.Setup.checkExitHoldMs, easing = LinearEasing))
        }
    }
    return remember(progress) { { progress.value } }
}

@Composable
internal fun FaceDiamond(
    reading: PadReading,
    swaps: ButtonGlyphSwaps,
    swapped: Set<InputButton>,
    holdProgress: () -> Float,
    faceSize: Dp,
    gap: Dp,
    style: TextStyle,
) {
    val step = faceSize + gap
    Box(modifier = Modifier.size(step * 2 + faceSize)) {
        FacePositions.forEach { (position, column, row) ->
            val intent = position.intentButton(swaps)
            DiagramButton(
                label = intent.name,
                lit = reading.isLit(intent),
                swapped = intent in swapped,
                holding = reading.holding == intent,
                holdProgress = holdProgress,
                width = faceSize,
                height = faceSize,
                shape = CircleShape,
                style = style,
                description = stringResource(position.descriptionRes(), intent.name),
                modifier = Modifier.offset(x = step * column, y = step * row),
            )
        }
    }
}

@Composable
internal fun MenuPills(
    reading: PadReading,
    swaps: ButtonGlyphSwaps,
    swapped: Set<InputButton>,
    holdProgress: () -> Float,
) {
    val s = LocalUiScale.current.scale
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
        MenuPositions.forEach { position ->
            val intent = position.intentButton(swaps)
            val label = stringResource(intent.menuLabelRes())
            DiagramButton(
                label = label,
                lit = reading.isLit(intent),
                swapped = intent in swapped,
                holding = reading.holding == intent,
                holdProgress = holdProgress,
                width = (ComponentDefaults.Setup.menuPillWidthDp * s).dp,
                height = (ComponentDefaults.Setup.menuPillHeightDp * s).dp,
                shape = RoundedCornerShape(Dimens.radiusPill),
                style = MaterialTheme.typography.labelMedium,
                description = stringResource(position.descriptionRes(), label),
            )
        }
    }
}

private data class FaceSlot(val position: ControllerPosition, val column: Int, val row: Int)

private val FacePositions = listOf(
    FaceSlot(ControllerPosition.TOP, column = 1, row = 0),
    FaceSlot(ControllerPosition.LEFT, column = 0, row = 1),
    FaceSlot(ControllerPosition.RIGHT, column = 2, row = 1),
    FaceSlot(ControllerPosition.BOTTOM, column = 1, row = 2),
)

private val MenuPositions = listOf(ControllerPosition.MENU_LEFT, ControllerPosition.MENU_RIGHT)

private const val FLIP_EDGE_DEGREES = 90f

private fun ControllerPosition.descriptionRes(): Int = when (this) {
    ControllerPosition.TOP -> R.string.setup_diagram_position_top_desc
    ControllerPosition.LEFT -> R.string.setup_diagram_position_left_desc
    ControllerPosition.RIGHT -> R.string.setup_diagram_position_right_desc
    ControllerPosition.BOTTOM -> R.string.setup_diagram_position_bottom_desc
    ControllerPosition.MENU_LEFT -> R.string.setup_diagram_position_menu_left_desc
    ControllerPosition.MENU_RIGHT -> R.string.setup_diagram_position_menu_right_desc
}

private fun InputButton.menuLabelRes(): Int =
    if (this == InputButton.START) R.string.setup_diagram_start else R.string.setup_diagram_select

@Composable
private fun DiagramButton(
    label: String,
    lit: Boolean,
    swapped: Boolean,
    holding: Boolean,
    holdProgress: () -> Float,
    width: Dp,
    height: Dp,
    shape: Shape,
    style: TextStyle,
    description: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val flip = remember { Animatable(0f) }
    var shownLabel by remember { mutableStateOf(label) }
    LaunchedEffect(label) {
        if (label == shownLabel) return@LaunchedEffect
        if (reduced) {
            shownLabel = label
            return@LaunchedEffect
        }
        val half = tween<Float>(Motion.durationSlide / 2, easing = Motion.argosyEase)
        flip.animateTo(FLIP_EDGE_DEGREES, half)
        shownLabel = label
        flip.snapTo(-FLIP_EDGE_DEGREES)
        flip.animateTo(0f, half)
    }
    val labelColor by animateColorAsState(
        targetValue = if (lit) Color.White else theme.textPrimary,
        animationSpec = if (reduced) snap() else tween(Motion.durationMicro, easing = Motion.argosyEase),
        label = "diagram-label",
    )
    PadSurface(
        lit = lit,
        swapped = swapped,
        holding = holding,
        holdProgress = holdProgress,
        width = width,
        height = height,
        shape = shape,
        description = description,
        modifier = modifier.graphicsLayer { rotationY = flip.value },
    ) {
        Text(text = shownLabel, style = style, color = labelColor, maxLines = 1)
    }
}

@Composable
internal fun PadSurface(
    lit: Boolean,
    swapped: Boolean,
    holding: Boolean,
    holdProgress: () -> Float,
    width: Dp,
    height: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val colorSpec: AnimationSpec<Color> = if (reduced) snap() else tween(Motion.durationMicro, easing = Motion.argosyEase)
    val fill by animateColorAsState(
        targetValue = if (lit) theme.focusAccent else theme.surfaceElevated,
        animationSpec = colorSpec,
        label = "diagram-fill",
    )
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .holdRing(holding, holdProgress, shape, theme.textPrimary, Dimens.borderThick)
            .clip(shape)
            .background(fill)
            .border(
                width = if (swapped) Dimens.borderMedium else Dimens.borderThin,
                color = if (lit || swapped) theme.focusAccent else theme.hairlineHigh,
                shape = shape,
            )
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

internal fun Modifier.holdRing(
    active: Boolean,
    progress: () -> Float,
    shape: Shape,
    color: Color,
    width: Dp,
    mirrored: Boolean = false,
): Modifier = if (!active) {
    this
} else {
    drawWithCache {
        val outline = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
            if (mirrored) transform(mirrorAcross(size.width))
        }
        val measure = PathMeasure().apply { setPath(outline, false) }
        val segment = Path()
        val stroke = Stroke(width = width.toPx(), cap = StrokeCap.Round)
        onDrawWithContent {
            drawContent()
            segment.reset()
            measure.getSegment(0f, measure.length * progress(), segment, true)
            drawPath(segment, color = color, style = stroke)
        }
    }
}

internal fun mirrorAcross(width: Float): androidx.compose.ui.graphics.Matrix =
    androidx.compose.ui.graphics.Matrix().apply {
        translate(x = width)
        scale(x = -1f)
    }
