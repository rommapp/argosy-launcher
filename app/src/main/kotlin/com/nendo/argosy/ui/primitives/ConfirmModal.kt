package com.nendo.argosy.ui.primitives

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus

/** V2 warning/confirm modal: symmetric fill-less bases, focus is the fill, red marks danger. */
@Composable
fun ArgosyConfirmModal(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    focusedIndex: Int,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    cancelLabel: String = stringResource(R.string.ui_confirm_modal_cancel),
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
    visible: Boolean = true,
) {
    val theme = LocalArgosyTheme.current
    val confirmIndex = if (neutralLabel != null) 2 else 1
    ModalScaffold(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
            )
            Spacer(Modifier.height(Dimens.spacingSm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
            )
            Spacer(Modifier.height(Dimens.spacingLg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End),
            ) {
                ModalActionButton(
                    label = cancelLabel,
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = focusedIndex == 0,
                    onClick = onDismiss,
                )
                if (neutralLabel != null) {
                    ModalActionButton(
                        label = neutralLabel,
                        tint = theme.focusAccent,
                        restLabelColor = theme.textPrimary,
                        focused = focusedIndex == 1,
                        onClick = { onNeutral?.invoke() },
                    )
                }
                ModalActionButton(
                    label = confirmLabel,
                    tint = if (destructive) LocalArgosyTheme.current.destructive else theme.focusAccent,
                    restLabelColor = if (destructive) LocalArgosyTheme.current.destructive else theme.textPrimary,
                    focused = focusedIndex == confirmIndex,
                    onClick = onConfirm,
                )
            }
        }
    }
}

/** Self-contained [ArgosyConfirmModal] that owns its focus index and captures gamepad input. */
@Composable
fun ArgosyConfirmModalHost(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    cancelLabel: String = stringResource(R.string.ui_confirm_modal_cancel),
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    val hasNeutral by rememberUpdatedState(neutralLabel != null)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnNeutral by rememberUpdatedState(onNeutral)

    LaunchedEffect(visible) {
        if (visible) focusedIndex = 0
    }

    val inputHandler = remember {
        object : InputHandler {
            private fun lastIndex(): Int = if (hasNeutral) 2 else 1

            override fun onLeft(): InputResult {
                focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                focusedIndex = (focusedIndex + 1).coerceAtMost(lastIndex())
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when (focusedIndex) {
                    0 -> currentOnDismiss()
                    lastIndex() -> currentOnConfirm()
                    else -> currentOnNeutral?.invoke()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onUp(): InputResult = InputResult.HANDLED
            override fun onDown(): InputResult = InputResult.HANDLED
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

    ArgosyConfirmModal(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        focusedIndex = focusedIndex,
        modifier = modifier,
        destructive = destructive,
        cancelLabel = cancelLabel,
        neutralLabel = neutralLabel,
        onNeutral = onNeutral,
        visible = visible,
    )
}

/** V2 modal action button: fill-less base, focus wash + tinted border, danger carried by tint. */
@Composable
internal fun ModalActionButton(
    label: String,
    tint: Color,
    restLabelColor: Color,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background by animateColorAsState(
        targetValue = if (focused) tint.copy(alpha = 0.2f).compositeOver(theme.surfaceElevated) else theme.surfaceElevated,
        animationSpec = Motion.focusColorSpec,
        label = "confirm-bg",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !enabled -> restLabelColor.copy(alpha = 0.4f)
            focused -> lerp(tint, Color.White, 0.45f)
            else -> restLabelColor
        },
        animationSpec = Motion.focusColorSpec,
        label = "confirm-label",
    )
    Box(
        modifier = modifier
            .heightIn(min = Dimens.buttonHeight)
            .clip(shape)
            .background(background)
            .border(
                width = Dimens.borderThin,
                color = if (focused) tint else theme.hairlineLow,
                shape = shape,
            )
            .clickableNoFocus(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.buttonPaddingH, vertical = Dimens.buttonPaddingV),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = labelColor, maxLines = 1)
    }
}
