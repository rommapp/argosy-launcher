package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

data class FooterStyleConfig(
    val useAccentColor: Boolean = false
)

val LocalFooterStyle = staticCompositionLocalOf { FooterStyleConfig() }

data class FooterHintItem(
    val button: InputButton,
    val action: String,
    val enabled: Boolean = true
)

enum class InputButton {
    SOUTH, EAST, WEST, NORTH,
    DPAD, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_HORIZONTAL, DPAD_VERTICAL,
    LB, RB, LB_RB, LT, RT,
    START, SELECT
}

private enum class HintCategory { DPAD, BUMPER, SHOULDER_MENU, FACE }

private fun InputButton.category(): HintCategory = when (this) {
    InputButton.DPAD, InputButton.DPAD_UP, InputButton.DPAD_DOWN,
    InputButton.DPAD_LEFT, InputButton.DPAD_RIGHT,
    InputButton.DPAD_HORIZONTAL, InputButton.DPAD_VERTICAL -> HintCategory.DPAD
    InputButton.LB, InputButton.RB, InputButton.LB_RB -> HintCategory.BUMPER
    InputButton.LT, InputButton.RT, InputButton.START, InputButton.SELECT -> HintCategory.SHOULDER_MENU
    InputButton.SOUTH, InputButton.EAST, InputButton.WEST, InputButton.NORTH -> HintCategory.FACE
}

@Composable
private fun InputButton.toPainter(): Painter? {
    val abIconsSwapped = LocalABIconsSwapped.current
    val xyIconsSwapped = LocalXYIconsSwapped.current
    val swapStartSelect = LocalSwapStartSelect.current
    return when (this) {
        InputButton.SOUTH -> if (abIconsSwapped) InputIcons.FaceRight else InputIcons.FaceBottom
        InputButton.EAST -> if (abIconsSwapped) InputIcons.FaceBottom else InputIcons.FaceRight
        InputButton.WEST -> if (xyIconsSwapped) InputIcons.FaceTop else InputIcons.FaceLeft
        InputButton.NORTH -> if (xyIconsSwapped) InputIcons.FaceLeft else InputIcons.FaceTop
        InputButton.DPAD -> InputIcons.Dpad
        InputButton.DPAD_UP -> InputIcons.DpadUp
        InputButton.DPAD_DOWN -> InputIcons.DpadDown
        InputButton.DPAD_LEFT -> InputIcons.DpadLeft
        InputButton.DPAD_RIGHT -> InputIcons.DpadRight
        InputButton.DPAD_HORIZONTAL -> InputIcons.DpadHorizontal
        InputButton.DPAD_VERTICAL -> InputIcons.DpadVertical
        InputButton.LB -> InputIcons.BumperLeft
        InputButton.RB -> InputIcons.BumperRight
        InputButton.LB_RB -> null
        InputButton.LT -> InputIcons.TriggerLeft
        InputButton.RT -> InputIcons.TriggerRight
        InputButton.START -> if (swapStartSelect) InputIcons.Options else InputIcons.Menu
        InputButton.SELECT -> if (swapStartSelect) InputIcons.Menu else InputIcons.Options
    }
}

private fun InputButton.isComposite(): Boolean = this == InputButton.LB_RB

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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val footerStyle = LocalFooterStyle.current
    val disabledAlpha = 0.38f
    val iconColor = if (footerStyle.useAccentColor) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primary
    }.let { if (enabled) it else it.copy(alpha = disabledAlpha) }
    val textColor = if (footerStyle.useAccentColor) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }.let { if (enabled) it else it.copy(alpha = disabledAlpha) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (button.isComposite()) {
            CompositeButtonIcon(button, iconColor)
        } else {
            button.toPainter()?.let { painter ->
                Icon(
                    painter = painter,
                    contentDescription = button.name,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

@Composable
private fun CompositeButtonIcon(button: InputButton, iconColor: Color) {
    if (button == InputButton.LB_RB) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = InputIcons.BumperLeft,
                contentDescription = "LB",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "/",
                style = MaterialTheme.typography.bodySmall,
                color = iconColor,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            Icon(
                painter = InputIcons.BumperRight,
                contentDescription = "RB",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun InputButton.faceButtonPriority(): Int = when (this) {
    InputButton.NORTH -> 0
    InputButton.WEST -> 1
    InputButton.EAST -> 2
    InputButton.SOUTH -> 3
    else -> 0
}

@Composable
fun FooterBar(
    hints: List<Pair<InputButton, String>>,
    modifier: Modifier = Modifier,
    onHintClick: ((InputButton) -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val footerStyle = LocalFooterStyle.current
    val backgroundColor = if (footerStyle.useAccentColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val dpadHints = hints.filter { it.first.category() == HintCategory.DPAD }
    val bumperHints = hints.filter { it.first.category() == HintCategory.BUMPER }
    val shoulderHints = hints.filter { it.first.category() == HintCategory.SHOULDER_MENU }
    val faceHints = hints.filter { it.first.category() == HintCategory.FACE }
        .sortedBy { it.first.faceButtonPriority() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            dpadHints.forEach { (button, action) ->
                TappableFooterHint(button, action, onHintClick)
            }
            bumperHints.forEach { (button, action) ->
                TappableFooterHint(button, action, onHintClick)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shoulderHints.forEach { (button, action) ->
                TappableFooterHint(button, action, onHintClick)
            }
            faceHints.forEach { (button, action) ->
                TappableFooterHint(button, action, onHintClick)
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
fun FooterBarWithState(
    hints: List<FooterHintItem>,
    modifier: Modifier = Modifier,
    onHintClick: ((InputButton) -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val footerStyle = LocalFooterStyle.current
    val backgroundColor = if (footerStyle.useAccentColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val dpadHints = hints.filter { it.button.category() == HintCategory.DPAD }
    val bumperHints = hints.filter { it.button.category() == HintCategory.BUMPER }
    val shoulderHints = hints.filter { it.button.category() == HintCategory.SHOULDER_MENU }
    val faceHints = hints.filter { it.button.category() == HintCategory.FACE }
        .sortedBy { it.button.faceButtonPriority() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            dpadHints.forEach { hint ->
                TappableFooterHint(hint.button, hint.action, onHintClick, hint.enabled)
            }
            bumperHints.forEach { hint ->
                TappableFooterHint(hint.button, hint.action, onHintClick, hint.enabled)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shoulderHints.forEach { hint ->
                TappableFooterHint(hint.button, hint.action, onHintClick, hint.enabled)
            }
            faceHints.forEach { hint ->
                TappableFooterHint(hint.button, hint.action, onHintClick, hint.enabled)
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
private fun TappableFooterHint(
    button: InputButton,
    action: String,
    onHintClick: ((InputButton) -> Unit)?,
    enabled: Boolean = true
) {
    val clickModifier = if (onHintClick != null && enabled) {
        Modifier.clickable(
            onClick = { onHintClick(button) },
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        )
    } else {
        Modifier
    }

    FooterHint(
        button = button,
        action = action,
        modifier = clickModifier.padding(vertical = Dimens.spacingXs),
        enabled = enabled
    )
}

@Composable
fun SubtleFooterBar(
    hints: List<Pair<InputButton, String>>,
    modifier: Modifier = Modifier,
    onHintClick: ((InputButton) -> Unit)? = null
) {
    val footerStyle = LocalFooterStyle.current
    val dpadHints = hints.filter { it.first.category() == HintCategory.DPAD }
    val faceHints = hints.filter { it.first.category() == HintCategory.FACE }
        .sortedBy { it.first.faceButtonPriority() }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val backgroundColor = if (footerStyle.useAccentColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            dpadHints.forEach { (button, action) ->
                TappableFooterHint(button, action, onHintClick)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            faceHints.forEach { (button, action) ->
                TappableFooterHint(button, action, onHintClick)
            }
        }
    }
}
