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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.media.MediaResumePrompt
import com.nendo.argosy.ui.screens.media.formatPosition
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

private const val START_OVER_INDEX = 0
private const val RESUME_INDEX = 1
private const val OPTION_COUNT = 2

/**
 * The choice between picking up where playback stopped and going back to the beginning.
 *
 * Start Over sits first and holds focus when the prompt opens: a plain confirm already resumes, so
 * the only reason to be looking at this prompt is to do the other thing.
 *
 * It takes a [MediaResumePrompt] and three callbacks and nothing else, so any surface that can name
 * an item can raise it -- the browse grid, the detail screen, or a Home tile.
 */
@Composable
fun MediaResumeModalHost(
    prompt: MediaResumePrompt?,
    onStartOver: (String) -> Unit,
    onResume: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedIndex by remember { mutableIntStateOf(START_OVER_INDEX) }
    val currentOnStartOver by rememberUpdatedState(onStartOver)
    val currentOnResume by rememberUpdatedState(onResume)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentPrompt by rememberUpdatedState(prompt)

    LaunchedEffect(prompt) {
        if (prompt != null) focusedIndex = START_OVER_INDEX
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focusedIndex = (focusedIndex - 1).mod(OPTION_COUNT)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                focusedIndex = (focusedIndex + 1).mod(OPTION_COUNT)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                val itemId = currentPrompt?.itemId ?: return InputResult.HANDLED
                if (focusedIndex == START_OVER_INDEX) currentOnStartOver(itemId) else currentOnResume(itemId)
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

    MediaResumeModalContent(
        prompt = prompt,
        focusedIndex = focusedIndex,
        onStartOver = onStartOver,
        onResume = onResume,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

/**
 * The prompt's visuals alone, with the focused option handed in rather than held here. The
 * companion display renders this form and routes gamepad input through its own handler stack,
 * which has no input dispatcher for [MediaResumeModalHost] to push onto.
 */
@Composable
fun MediaResumeModalContent(
    prompt: MediaResumePrompt?,
    focusedIndex: Int,
    onStartOver: (String) -> Unit,
    onResume: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastPrompt by remember { mutableStateOf<MediaResumePrompt?>(null) }
    LaunchedEffect(prompt) {
        if (prompt != null) lastPrompt = prompt
    }

    val content = prompt ?: lastPrompt ?: return
    val theme = LocalArgosyTheme.current

    val subtitle = content.subtitle

    ModalScaffold(visible = prompt != null, onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute
                )
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            ResumeOptionRow(
                icon = Icons.Default.Replay,
                label = stringResource(R.string.media_resume_start_over_label),
                supporting = stringResource(R.string.media_resume_start_over_supporting),
                focused = focusedIndex == START_OVER_INDEX,
                onClick = { onStartOver(content.itemId) }
            )
            Spacer(Modifier.height(Dimens.spacingSm))
            ResumeOptionRow(
                icon = Icons.Default.PlayArrow,
                label = stringResource(R.string.media_resume_resume_label),
                supporting = stringResource(
                    R.string.media_resume_resume_supporting,
                    formatPosition(content.resumeTicks)
                ),
                focused = focusedIndex == RESUME_INDEX,
                onClick = { onResume(content.itemId) }
            )
        }
    }
}

@Composable
private fun ResumeOptionRow(
    icon: ImageVector,
    label: String,
    supporting: String,
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
        label = "resume-option-bg"
    )
    val labelColor by animateColorAsState(
        targetValue = if (focused) lerp(theme.focusAccent, Color.White, 0.45f) else theme.textPrimary,
        animationSpec = Motion.focusColorSpec,
        label = "resume-option-label"
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
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = labelColor, maxLines = 1)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
