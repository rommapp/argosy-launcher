package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

enum class ControllerDiagramSize { FULL, MINI }

/**
 * Face-button diamond with Start and Select pills below. Positions are physical; labels follow
 * [swaps], or the glyph swap composition locals when [swaps] is null. [onPress] null makes the
 * diagram display-only. [MINI][ControllerDiagramSize.MINI] draws the diamond alone.
 */
@Composable
fun ControllerDiagram(
    litPosition: ControllerPosition?,
    focused: Boolean,
    onPress: ((ControllerPosition) -> Unit)?,
    modifier: Modifier = Modifier,
    swaps: ButtonGlyphSwaps? = null,
    variant: ControllerDiagramSize = ControllerDiagramSize.FULL,
) {
    val resolved = swaps ?: ButtonGlyphSwaps(
        ab = LocalABIconsSwapped.current,
        xy = LocalXYIconsSwapped.current,
        startSelect = LocalSwapStartSelect.current,
    )
    val s = LocalUiScale.current.scale
    val mini = variant == ControllerDiagramSize.MINI
    val faceSize = ((if (mini) ComponentDefaults.Setup.miniFaceButtonDp else ComponentDefaults.Setup.faceButtonDp) * s).dp
    val gap = if (mini) Dimens.spacingXs else Dimens.spacingSm
    val step = faceSize + gap
    val faceStyle = if (mini) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleLarge
    Column(
        modifier = modifier
            .argosyFocusIndicators(
                focused = focused,
                indicators = FocusIndicators.ListRow,
                shape = RoundedCornerShape(Dimens.radiusPanel),
            )
            .padding(
                horizontal = if (mini) 0.dp else Dimens.spacingLg,
                vertical = if (mini) 0.dp else Dimens.spacingMd,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        Box(modifier = Modifier.size(step * 2 + faceSize)) {
            FacePositions.forEach { (position, column, row) ->
                DiagramButton(
                    position = position,
                    label = position.intentButton(resolved).name,
                    lit = position == litPosition,
                    onPress = onPress,
                    width = faceSize,
                    height = faceSize,
                    shape = CircleShape,
                    style = faceStyle,
                    description = stringResource(position.descriptionRes(), position.intentButton(resolved).name),
                    modifier = Modifier.offset(x = step * column, y = step * row),
                )
            }
        }
        if (!mini) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                MenuPositions.forEach { position ->
                    val label = stringResource(position.intentButton(resolved).menuLabelRes())
                    DiagramButton(
                        position = position,
                        label = label,
                        lit = position == litPosition,
                        onPress = onPress,
                        width = (ComponentDefaults.Setup.menuPillWidthDp * s).dp,
                        height = (ComponentDefaults.Setup.menuPillHeightDp * s).dp,
                        shape = RoundedCornerShape(Dimens.radiusPill),
                        style = MaterialTheme.typography.labelMedium,
                        description = stringResource(position.descriptionRes(), label),
                    )
                }
            }
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
    position: ControllerPosition,
    label: String,
    lit: Boolean,
    onPress: ((ControllerPosition) -> Unit)?,
    width: Dp,
    height: Dp,
    shape: Shape,
    style: TextStyle,
    description: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val colorSpec: AnimationSpec<Color> = if (reduced) snap() else tween(Motion.durationMicro, easing = Motion.argosyEase)
    val fill by animateColorAsState(
        targetValue = if (lit) theme.focusAccent else theme.surfaceElevated,
        animationSpec = colorSpec,
        label = "diagram-fill",
    )
    val labelColor by animateColorAsState(
        targetValue = if (lit) Color.White else theme.textPrimary,
        animationSpec = colorSpec,
        label = "diagram-label",
    )
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(fill)
            .border(width = Dimens.borderThin, color = if (lit) theme.focusAccent else theme.hairlineHigh, shape = shape)
            .clickableNoFocus(enabled = onPress != null) { onPress?.invoke(position) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = style, color = labelColor, maxLines = 1)
    }
}
