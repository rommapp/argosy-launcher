package com.nendo.argosy.ui.screens.media.modals

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.media.MediaDownloadOption
import com.nendo.argosy.ui.screens.media.MediaDownloadPrompt
import com.nendo.argosy.ui.screens.media.MediaDownloadScope
import com.nendo.argosy.ui.screens.media.MediaDownloadStep
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The download choice: what to fetch for a series, then at what quality.
 *
 * It renders whatever options the prompt carries and reports moves back out; the focused index lives
 * in the view model like every other selection in the app, so the same prompt drives touch and
 * gamepad from one source.
 */
@Composable
fun MediaDownloadModalHost(
    prompt: MediaDownloadPrompt?,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                currentOnMove(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                currentOnMove(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnConfirm()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
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

    ModalInputEffect(active = prompt != null, handler = inputHandler)

    val content = prompt ?: return
    val theme = LocalArgosyTheme.current
    val warningColor = LocalLauncherTheme.current.semanticColors.warning

    ModalScaffold(visible = true, onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content.subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = theme.textMute)
            }
            content.warning?.let {
                Spacer(Modifier.height(Dimens.spacingSm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            val listState = rememberLazyListState()
            FocusedScroll(listState = listState, focusedIndex = content.focusedIndex)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(
                    items = content.options,
                    key = { _, option -> option.quality?.name ?: option.scope?.name ?: option.label }
                ) { index, option ->
                    MediaDownloadOptionRow(
                        option = option,
                        step = content.step,
                        focused = index == content.focusedIndex,
                        onClick = {
                            onFocus(index)
                            onConfirm()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaDownloadOptionRow(
    option: MediaDownloadOption,
    step: MediaDownloadStep,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background by animateColorAsState(
        targetValue = if (focused) {
            theme.focusAccent.copy(alpha = 0.2f).compositeOver(theme.surfaceElevated)
        } else {
            theme.surfaceElevated
        },
        animationSpec = Motion.focusColorSpec,
        label = "media-download-option-bg"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !option.enabled -> theme.textMute
            focused -> lerp(theme.focusAccent, Color.White, 0.45f)
            else -> theme.textPrimary
        },
        animationSpec = Motion.focusColorSpec,
        label = "media-download-option-label"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeightLg)
            .clip(shape)
            .background(background)
            .border(
                width = Dimens.borderThin,
                color = if (focused) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(enabled = option.enabled, onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        val icon = when {
            option.scope == MediaDownloadScope.REMOVE -> Icons.Default.Delete
            step == MediaDownloadStep.QUALITY -> Icons.Default.HighQuality
            else -> Icons.Default.Download
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                color = labelColor,
                maxLines = 1
            )
            option.supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
