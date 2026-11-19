package com.nendo.argosy.ui.primitives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.motionEnter
import com.nendo.argosy.ui.theme.motionExit
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun ModalScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Boolean = true,
    maxWidth: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (visible) ModalPresenceEffect()
    val duration = Motion.durationDrawer / 2
    AnimatedVisibility(
        visible = visible,
        enter = motionEnter(
            fadeIn(animationSpec = tween(duration, easing = Motion.argosyEase)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(duration, easing = Motion.argosyEase))
        ),
        exit = motionExit(
            fadeOut(animationSpec = tween(duration, easing = Motion.argosyEase)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(duration, easing = Motion.argosyEase))
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (scrim) Modifier.background(Color.Black.copy(alpha = 0.55f))
                    else Modifier
                )
                .clickableNoFocus { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth ?: Dimens.modalWidthLg)
                    .clickableNoFocus { },
            ) {
                GlassPanel {
                    Box(content = content)
                }
            }
        }
    }
}
