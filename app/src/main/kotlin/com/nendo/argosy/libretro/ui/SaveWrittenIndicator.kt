package com.nendo.argosy.libretro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.reducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HELM_SPIN_DEGREES = 900f
private const val HELM_SPIN_MS = 800
private const val HELM_FADE_DELAY_MS = 600L
private const val HELM_FADE_MS = 200
private const val CHECK_DELAY_MS = 700L
private const val VISIBLE_MS = 1700L
private const val FADE_OUT_MS = 300

@Composable
fun SaveWrittenIndicator(writtenAt: Long, modifier: Modifier = Modifier) {
    val visibility = remember { Animatable(0f) }
    val helmRotation = remember { Animatable(0f) }
    val helmAlpha = remember { Animatable(1f) }
    val checkScale = remember { Animatable(0f) }
    val reduced = reducedMotion()

    LaunchedEffect(writtenAt) {
        if (writtenAt == 0L) return@LaunchedEffect
        if (reduced) {
            helmAlpha.snapTo(0f)
            checkScale.snapTo(1f)
            visibility.snapTo(1f)
            delay(VISIBLE_MS)
            visibility.snapTo(0f)
            return@LaunchedEffect
        }
        helmRotation.snapTo(0f)
        helmAlpha.snapTo(1f)
        checkScale.snapTo(0f)
        visibility.snapTo(1f)
        coroutineScope {
            launch { helmRotation.animateTo(HELM_SPIN_DEGREES, tween(HELM_SPIN_MS, easing = FastOutLinearInEasing)) }
            launch {
                delay(HELM_FADE_DELAY_MS)
                helmAlpha.animateTo(0f, tween(HELM_FADE_MS))
            }
            launch {
                delay(CHECK_DELAY_MS)
                checkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
        delay(VISIBLE_MS - CHECK_DELAY_MS)
        visibility.animateTo(0f, tween(FADE_OUT_MS))
    }

    if (visibility.value == 0f) return

    Box(
        modifier = modifier
            .padding(Dimens.spacingMd)
            .size(Dimens.iconMd)
            .alpha(visibility.value),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_helm),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(Dimens.iconMd)
                .rotate(helmRotation.value)
                .alpha(helmAlpha.value)
        )
        Image(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier
                .size(Dimens.iconMd)
                .scale(checkScale.value)
        )
    }
}
