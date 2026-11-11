package com.nendo.argosy.libretro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.constrainHeight
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.ui.notification.noticeAlphaSpec
import com.nendo.argosy.ui.theme.ArgosyDarkTheme
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * [connected] false means the server never accepted a session for this launch.
 */
data class RAConnectionInfo(
    val isHardcore: Boolean,
    val earnedCount: Int,
    val totalCount: Int,
    val connected: Boolean = true
)

/**
 * Shows [connectionInfo] for [NotificationDuration.MEDIUM], then calls [onDismissed] once the
 * exit has finished. A new value while shown restarts the timer without replaying the entry.
 */
@Composable
fun RAConnectionStatus(
    connectionInfo: RAConnectionInfo?,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    isSecondaryDisplay: Boolean = false
) {
    val info = connectionInfo ?: return
    val dismissed by rememberUpdatedState(onDismissed)
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(info) {
        visible = true
        delay(NotificationDuration.MEDIUM.ms)
        visible = false
        delay(maxOf(MotionTokens.Tween.noticeRevealMs, Motion.durationContent).toLong())
        dismissed()
    }

    ArgosyDarkTheme(isSecondaryDisplay = isSecondaryDisplay) {
        RAConnectionBanner(visible = visible, info = info, modifier = modifier)
    }
}

@Composable
private fun RAConnectionBanner(visible: Boolean, info: RAConnectionInfo, modifier: Modifier) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val tint = info.tint()

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        val alpha by transition.animateFloat(
            transitionSpec = {
                noticeAlphaSpec(entering = targetState == EnterExitState.Visible, reduced = reduced)
            },
            label = "raStatusAlpha"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        val reveal by transition.animateFloat(
            transitionSpec = { tween(MotionTokens.Tween.noticeRevealMs, easing = Motion.argosyEase) },
            label = "raStatusReveal"
        ) { state -> if (reduced || state == EnterExitState.Visible) 1f else 0f }

        RAConnectionContainer(tint = tint, alpha = { alpha }, reveal = { reveal }) {
            RAConnectionContent(info = info, tint = tint)
        }
    }
}

@Composable
private fun RAConnectionInfo.tint(): Color {
    val semantic = LocalLauncherTheme.current.semanticColors
    return when {
        !connected -> semantic.warning
        isHardcore -> ColorTokens.Domain.AchievementTier.hardcore
        else -> semantic.success
    }
}

@Composable
private fun RAConnectionContainer(
    tint: Color,
    alpha: () -> Float,
    reveal: () -> Float,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = ComponentDefaults.Notification.surfaceAlpha)
    val edgeWidth = Dimens.borderMedium

    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha() }
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .drawWithContent {
                drawRect(tint.copy(alpha = ComponentDefaults.Notification.tintAlpha).compositeOver(surface))
                drawContent()
                val edgePx = edgeWidth.toPx()
                drawRect(
                    color = tint,
                    topLeft = Offset(0f, size.height - edgePx),
                    size = Size(size.width, edgePx)
                )
            }
            .revealFromTop(reveal)
    ) {
        content()
    }
}

private fun Modifier.revealFromTop(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val visibleHeight = constraints.constrainHeight((placeable.height * fraction()).roundToInt())
    layout(placeable.width, visibleHeight) {
        placeable.place(0, 0)
    }
}

@Composable
private fun RAConnectionContent(info: RAConnectionInfo, tint: Color) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = textColor.copy(alpha = ComponentDefaults.Notification.bodyAlpha)
    val verticalPadding = Dimens.spacingSm + Dimens.spacingXs

    Row(
        modifier = Modifier
            .widthIn(max = Dimens.modalWidth)
            .padding(
                start = Dimens.spacingMd,
                top = verticalPadding,
                end = Dimens.spacingMd,
                bottom = verticalPadding + Dimens.borderMedium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (info.connected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(Modifier.width(Dimens.spacingSm + Dimens.spacingXs))
        Column {
            Text(
                text = stringResource(
                    if (info.connected) R.string.ingame_raconnect_title else R.string.ingame_raconnect_unavailable_title
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (info.isHardcore) R.string.ingame_raconnect_hardcore else R.string.ingame_raconnect_casual
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                if (info.totalCount > 0) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor
                    )
                    Text(
                        text = stringResource(R.string.ingame_raconnect_earned, info.earnedCount, info.totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor
                    )
                }
            }
        }
    }
}
