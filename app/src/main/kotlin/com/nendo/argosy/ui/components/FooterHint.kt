package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.input.LocalNintendoLayout
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.theme.Dimens

enum class InputButton {
    A, B, X, Y,
    DPAD, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_HORIZONTAL, DPAD_VERTICAL,
    LB, RB, LT, RT,
    START, SELECT
}

private enum class HintCategory { DPAD, SHOULDER_MENU, FACE }

private fun InputButton.category(): HintCategory = when (this) {
    InputButton.DPAD, InputButton.DPAD_UP, InputButton.DPAD_DOWN,
    InputButton.DPAD_LEFT, InputButton.DPAD_RIGHT,
    InputButton.DPAD_HORIZONTAL, InputButton.DPAD_VERTICAL -> HintCategory.DPAD
    InputButton.LB, InputButton.RB, InputButton.LT, InputButton.RT,
    InputButton.START, InputButton.SELECT -> HintCategory.SHOULDER_MENU
    InputButton.A, InputButton.B, InputButton.X, InputButton.Y -> HintCategory.FACE
}

@Composable
private fun InputButton.toPainter(): Painter {
    val nintendoLayout = LocalNintendoLayout.current
    val swapStartSelect = LocalSwapStartSelect.current
    return when (this) {
        InputButton.A -> if (nintendoLayout) InputIcons.FaceRight else InputIcons.FaceBottom
        InputButton.B -> if (nintendoLayout) InputIcons.FaceBottom else InputIcons.FaceRight
        InputButton.X -> InputIcons.FaceLeft
        InputButton.Y -> InputIcons.FaceTop
        InputButton.DPAD -> InputIcons.Dpad
        InputButton.DPAD_UP -> InputIcons.DpadUp
        InputButton.DPAD_DOWN -> InputIcons.DpadDown
        InputButton.DPAD_LEFT -> InputIcons.DpadLeft
        InputButton.DPAD_RIGHT -> InputIcons.DpadRight
        InputButton.DPAD_HORIZONTAL -> InputIcons.DpadHorizontal
        InputButton.DPAD_VERTICAL -> InputIcons.DpadVertical
        InputButton.LB -> InputIcons.BumperLeft
        InputButton.RB -> InputIcons.BumperRight
        InputButton.LT -> InputIcons.TriggerLeft
        InputButton.RT -> InputIcons.TriggerRight
        InputButton.START -> if (swapStartSelect) InputIcons.Options else InputIcons.Menu
        InputButton.SELECT -> if (swapStartSelect) InputIcons.Menu else InputIcons.Options
    }
}

private fun InputButton.isDpadButton(): Boolean = when (this) {
    InputButton.DPAD, InputButton.DPAD_UP, InputButton.DPAD_DOWN,
    InputButton.DPAD_LEFT, InputButton.DPAD_RIGHT,
    InputButton.DPAD_HORIZONTAL, InputButton.DPAD_VERTICAL -> true
    else -> false
}

@Composable
fun FooterHint(
    button: InputButton,
    action: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = button.toPainter(),
            contentDescription = button.name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun InputButton.faceButtonPriority(): Int = when (this) {
    InputButton.Y -> 0
    InputButton.X -> 1
    InputButton.B -> 2
    InputButton.A -> 3
    else -> 0
}

@Composable
fun FooterBar(
    hints: List<Pair<InputButton, String>>,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val dpadHints = hints.filter { it.first.category() == HintCategory.DPAD }
    val shoulderHints = hints.filter { it.first.category() == HintCategory.SHOULDER_MENU }
    val faceHints = hints.filter { it.first.category() == HintCategory.FACE }
        .sortedBy { it.first.faceButtonPriority() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            dpadHints.forEach { (button, action) ->
                FooterHint(button = button, action = action)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shoulderHints.forEach { (button, action) ->
                FooterHint(button = button, action = action)
            }
            faceHints.forEach { (button, action) ->
                FooterHint(button = button, action = action)
            }
            trailingContent?.invoke()
        }
    }
}
