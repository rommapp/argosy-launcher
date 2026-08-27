package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import androidx.compose.ui.platform.LocalConfiguration
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

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
    A, B, X, Y,
    DPAD, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_HORIZONTAL, DPAD_VERTICAL,
    LB, RB, LB_RB, LT, RT, LT_RT,
    START, SELECT
}

private enum class HintCategory { DPAD, BUMPER, SHOULDER_MENU, FACE }

private fun InputButton.category(): HintCategory = when (this) {
    InputButton.DPAD, InputButton.DPAD_UP, InputButton.DPAD_DOWN,
    InputButton.DPAD_LEFT, InputButton.DPAD_RIGHT,
    InputButton.DPAD_HORIZONTAL, InputButton.DPAD_VERTICAL -> HintCategory.DPAD
    InputButton.LB, InputButton.RB, InputButton.LB_RB -> HintCategory.BUMPER
    InputButton.LT, InputButton.RT, InputButton.LT_RT, InputButton.START, InputButton.SELECT -> HintCategory.SHOULDER_MENU
    InputButton.A, InputButton.B, InputButton.X, InputButton.Y -> HintCategory.FACE
}

/**
 * The single icon-side resolution of A/B, X/Y and Start/Select swaps. Call this; never copy the
 * mapping and never encode a swap by choosing a different InputButton.
 */
@Composable
fun InputButton.toPainter(): Painter? {
    val abIconsSwapped = LocalABIconsSwapped.current
    val xyIconsSwapped = LocalXYIconsSwapped.current
    val swapStartSelect = LocalSwapStartSelect.current
    return when (this) {
        InputButton.A -> if (abIconsSwapped) InputIcons.FaceRight else InputIcons.FaceBottom
        InputButton.B -> if (abIconsSwapped) InputIcons.FaceBottom else InputIcons.FaceRight
        InputButton.X -> if (xyIconsSwapped) InputIcons.FaceTop else InputIcons.FaceLeft
        InputButton.Y -> if (xyIconsSwapped) InputIcons.FaceLeft else InputIcons.FaceTop
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
        InputButton.LT_RT -> null
        InputButton.START -> if (swapStartSelect) InputIcons.Options else InputIcons.Menu
        InputButton.SELECT -> if (swapStartSelect) InputIcons.Menu else InputIcons.Options
    }
}

private fun InputButton.isComposite(): Boolean = this == InputButton.LB_RB || this == InputButton.LT_RT

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
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun CompositeButtonIcon(button: InputButton, iconColor: Color) {
    when (button) {
        InputButton.LB_RB -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = InputIcons.BumperLeft,
                    contentDescription = "LB",
                    tint = iconColor,
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodySmall,
                    color = iconColor,
                    modifier = Modifier.padding(horizontal = Dimens.borderMedium)
                )
                Icon(
                    painter = InputIcons.BumperRight,
                    contentDescription = "RB",
                    tint = iconColor,
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
            }
        }
        InputButton.LT_RT -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = InputIcons.TriggerLeft,
                    contentDescription = "LT",
                    tint = iconColor,
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodySmall,
                    color = iconColor,
                    modifier = Modifier.padding(horizontal = Dimens.borderMedium)
                )
                Icon(
                    painter = InputIcons.TriggerRight,
                    contentDescription = "RT",
                    tint = iconColor,
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
            }
        }
        else -> {}
    }
}

@Composable
private fun <T> filterHintsByWidth(
    hints: List<T>,
    buttonOf: (T) -> InputButton,
    labelOf: (T) -> String,
    priorityOf: (T) -> Int
): List<T> {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val paddingDp = 48
    val gapDp = 24
    val availableDp = screenWidthDp - paddingDp

    fun estimateWidth(button: InputButton, label: String): Int {
        val iconDp = if (button.isComposite()) 44 else 22
        val textDp = label.length * 7
        return iconDp + 4 + textDp
    }

    val totalWidth = hints.sumOf { estimateWidth(buttonOf(it), labelOf(it)) } +
        (hints.size - 1).coerceAtLeast(0) * gapDp

    if (totalWidth <= availableDp) return hints

    val sorted = hints.sortedByDescending { priorityOf(it) }
    var width = 0
    val fitting = mutableListOf<T>()
    for (hint in sorted) {
        val w = estimateWidth(buttonOf(hint), labelOf(hint)) +
            if (fitting.isNotEmpty()) gapDp else 0
        if (width + w > availableDp) break
        width += w
        fitting.add(hint)
    }
    return if (fitting.size >= 2) fitting else sorted.take(2)
}

private fun InputButton.faceButtonPriority(): Int = when (this) {
    InputButton.Y -> 0
    InputButton.X -> 1
    InputButton.B -> 2
    InputButton.A -> 3
    else -> 0
}

private fun InputButton.hidePriority(): Int = when (this) {
    InputButton.X, InputButton.Y -> 4
    InputButton.LB, InputButton.RB, InputButton.LB_RB,
    InputButton.LT, InputButton.RT, InputButton.LT_RT -> 3
    InputButton.START, InputButton.SELECT -> 2
    InputButton.A, InputButton.B -> 1
    else -> 0
}

/**
 * Whether the focused control already communicates this hint, so the bar can collapse.
 *
 * Keyed on the button alone, never on the label: every caller pairs "Back", "Select" and
 * "Close" with A, B or the d-pad anyway, and a translated label would match nothing.
 */
internal fun isObviousHint(button: InputButton): Boolean =
    button == InputButton.A || button == InputButton.B || button.isDpadButton()

@Composable
private fun footerCollapseProgress(quiet: Boolean): Float {
    val progress by animateFloatAsState(
        targetValue = if (quiet) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.durationContent, easing = Motion.argosyEase),
        label = "footerCollapse"
    )
    return progress
}

private fun Modifier.footerCollapse(progress: Float): Modifier = graphicsLayer {
    translationY = size.height * progress
    alpha = 1f - progress
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

    val quiet = hints.all { isObviousHint(it.first) }
    var displayHints by remember { mutableStateOf(hints) }
    if (!quiet) displayHints = hints
    val collapseProgress = footerCollapseProgress(quiet)

    val filteredHints = filterHintsByWidth(
        displayHints,
        buttonOf = { it.first },
        labelOf = { it.second },
        priorityOf = { it.first.hidePriority() }
    )

    val dpadHints = filteredHints.filter { it.first.category() == HintCategory.DPAD }
    val bumperHints = filteredHints.filter { it.first.category() == HintCategory.BUMPER }
    val shoulderHints = filteredHints.filter { it.first.category() == HintCategory.SHOULDER_MENU }
    val faceHints = filteredHints.filter { it.first.category() == HintCategory.FACE }
        .sortedBy { it.first.faceButtonPriority() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.footerHeight - Dimens.spacingSm - Dimens.borderMedium)
            .footerCollapse(collapseProgress)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.Top
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
    trailingContent: @Composable (() -> Unit)? = null,
    forceVisible: Boolean = false
) {
    val footerStyle = LocalFooterStyle.current
    val backgroundColor = if (footerStyle.useAccentColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val quiet = !forceVisible && hints.all { isObviousHint(it.button) }
    var displayHints by remember { mutableStateOf(hints) }
    if (!quiet) displayHints = hints
    val collapseProgress = footerCollapseProgress(quiet)

    val filteredHints = filterHintsByWidth(
        displayHints,
        buttonOf = { it.button },
        labelOf = { it.action },
        priorityOf = { it.button.hidePriority() }
    )

    val dpadHints = filteredHints.filter { it.button.category() == HintCategory.DPAD }
    val bumperHints = filteredHints.filter { it.button.category() == HintCategory.BUMPER }
    val shoulderHints = filteredHints.filter { it.button.category() == HintCategory.SHOULDER_MENU }
    val faceHints = filteredHints.filter { it.button.category() == HintCategory.FACE }
        .sortedBy { it.button.faceButtonPriority() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.footerHeight - Dimens.spacingSm - Dimens.borderMedium)
            .footerCollapse(collapseProgress)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.Top
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
        Modifier.clickableNoFocus { onHintClick(button) }
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

    val quiet = hints.all { isObviousHint(it.first) }
    var displayHints by remember { mutableStateOf(hints) }
    if (!quiet) displayHints = hints
    val collapseProgress = footerCollapseProgress(quiet)

    val filteredHints = filterHintsByWidth(
        displayHints,
        buttonOf = { it.first },
        labelOf = { it.second },
        priorityOf = { it.first.hidePriority() }
    )

    val dpadHints = filteredHints.filter { it.first.category() == HintCategory.DPAD }
    val faceHints = filteredHints.filter { it.first.category() == HintCategory.FACE }
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
            .footerCollapse(collapseProgress)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.Top
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
