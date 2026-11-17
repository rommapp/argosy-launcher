package com.nendo.argosy.core.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion

const val SCREEN_SET_PROMPT_MS = 5000L

private const val PROMPT_TICK_MS = 50L

/**
 * Offers the display setup screen for a set of panels the device has not seen before, for as long
 * as the countdown runs. The prompt never blocks: the layout it is offering to change has already
 * been applied, so ignoring it leaves the device on the built-in-primary default.
 */
@Composable
fun ScreenSetPrompt(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var remaining by remember(visible) { mutableFloatStateOf(1f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val started = System.currentTimeMillis()
        while (remaining > 0f) {
            kotlinx.coroutines.delay(PROMPT_TICK_MS)
            val elapsed = System.currentTimeMillis() - started
            remaining = (1f - elapsed.toFloat() / SCREEN_SET_PROMPT_MS).coerceAtLeast(0f)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) +
            fadeIn(animationSpec = tween(Motion.durationSlide)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ),
        exit = slideOutHorizontally(targetOffsetX = { it / 3 }) +
            fadeOut(animationSpec = tween(Motion.durationMicro)) +
            scaleOut(targetScale = 0.95f),
        modifier = modifier
    ) {
        PromptBar(remaining = remaining)
    }
}

@Composable
private fun PromptBar(remaining: Float) {
    val theme = LocalArgosyTheme.current
    val accent = theme.focusAccent
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    val backgroundColor = accent.copy(alpha = 0.15f).compositeOver(baseColor)
    val fraction by animateFloatAsState(targetValue = remaining, label = "screenSetPromptCountdown")

    Column(
        modifier = Modifier
            .widthIn(max = Dimens.modalWidth - Dimens.headerHeight + Dimens.spacingSm)
            .clip(RoundedCornerShape(Dimens.spacingSm + Dimens.borderMedium))
            .background(backgroundColor)
            .padding(Dimens.spacingSm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_helm),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(Dimens.iconMd)
            )

            Spacer(modifier = Modifier.width(Dimens.spacingSm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notif_new_display_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InputGlyph(button = InputButton.X, tint = accent)
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = stringResource(R.string.notif_new_display_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textDim,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(Dimens.spacingXs))

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spacingXs)) {
            ArgosyProgressBar(progress = fraction, tint = accent)
        }
    }
}
