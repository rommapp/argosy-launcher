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
import com.nendo.argosy.ui.theme.Dimens

enum class InputButton {
    A, B, X, Y,
    DPAD, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_HORIZONTAL, DPAD_VERTICAL,
    LB, RB, LT, RT,
    START, SELECT
}

@Composable
private fun InputButton.toPainter(): Painter {
    val nintendoLayout = LocalNintendoLayout.current
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
        InputButton.START -> InputIcons.Menu
        InputButton.SELECT -> InputIcons.Options
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

@Composable
fun FooterBar(
    hints: List<Pair<InputButton, String>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        var lastWasDpad = false
        hints.forEachIndexed { index, (button, action) ->
            val isDpad = button.isDpadButton()
            if (index > 0 && lastWasDpad && !isDpad) {
                Spacer(modifier = Modifier.width(Dimens.spacingLg))
            }
            FooterHint(button = button, action = action)
            lastWasDpad = isDpad
        }
    }
}
