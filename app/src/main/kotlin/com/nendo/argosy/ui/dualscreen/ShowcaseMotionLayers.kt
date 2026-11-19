package com.nendo.argosy.ui.dualscreen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.nendo.argosy.ui.common.alwaysCrossfade
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.stackedScreenshotBackdrop
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ShowcaseBackdrop(
    val request: ImageRequest,
    val onSuccess: (AsyncImagePainter.State.Success) -> Unit
)

@Composable
internal fun rememberShowcaseBackdrop(
    path: String,
    crossfadeMs: Int,
    stackedScreenshot: Boolean
): ShowcaseBackdrop {
    val context = LocalContext.current
    val model = rememberFileImageModel(path)
    val lastKey = remember { mutableStateOf<MemoryCache.Key?>(null) }
    val crossfade = alwaysCrossfade(crossfadeMs)
    val request = remember(model, crossfade, stackedScreenshot) {
        ImageRequest.Builder(context)
            .data(model)
            .placeholderMemoryCacheKey(Snapshot.withoutReadObservation { lastKey.value })
            .apply { if (stackedScreenshot) stackedScreenshotBackdrop() }
            .transitionFactory(crossfade)
            .build()
    }
    return remember(request) {
        ShowcaseBackdrop(request) { lastKey.value = it.result.memoryCacheKey }
    }
}

internal class ShowcaseLayer(
    val modifier: Modifier,
    private val fade: Animatable<Float, AnimationVector1D>
) {
    val alpha: Float get() = fade.value
}

@Composable
internal fun rememberShowcaseLayer(
    motion: ShowcaseMotionState,
    durations: ShowcaseMotionDurations,
    delayMs: Int,
    distanceDp: Int,
    horizontal: Boolean,
    onFinished: ((Long?) -> Unit)? = null
): ShowcaseLayer {
    val distancePx = with(LocalDensity.current) { distanceDp.dp.toPx() }
    val startOffset = if (horizontal) -distancePx else distancePx
    val fade = remember { Animatable(if (motion.phase == ShowcaseMotionPhase.AtRest) 1f else 0f) }
    val drift = remember {
        Animatable(if (motion.phase == ShowcaseMotionPhase.Entering) startOffset else 0f)
    }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(motion.phase, motion.contentKey) {
        when (motion.phase) {
            ShowcaseMotionPhase.Settling -> coroutineScope {
                launch { fade.moveTo(0f, durations.exitMs, 0, EaseIn) }
                launch { drift.moveTo(0f, durations.exitMs, 0, EaseIn) }
            }
            ShowcaseMotionPhase.Entering -> {
                fade.snapTo(0f)
                drift.snapTo(startOffset)
                coroutineScope {
                    launch { fade.moveTo(1f, durations.enterMs, delayMs, Motion.argosyEase) }
                    launch { drift.moveTo(0f, durations.enterMs, delayMs, Motion.argosyEase) }
                }
                finished?.invoke(motion.contentKey)
            }
            ShowcaseMotionPhase.AtRest -> {
                fade.snapTo(1f)
                drift.snapTo(0f)
            }
        }
    }
    return remember(fade, drift, horizontal) {
        val modifier = Modifier.graphicsLayer {
            alpha = fade.value
            if (horizontal) translationX = drift.value else translationY = drift.value
        }
        ShowcaseLayer(modifier, fade)
    }
}

@Composable
internal fun Modifier.showcaseFloat(
    motion: ShowcaseMotionState,
    durations: ShowcaseMotionDurations,
    enabled: Boolean
): Modifier {
    val halfTravel = with(LocalDensity.current) { durations.floatTravelDp.dp.toPx() } / 2f
    val offset = remember { Animatable(0f) }
    val floating = enabled && motion.phase == ShowcaseMotionPhase.AtRest && durations.floatEnabled
    LaunchedEffect(floating, halfTravel) {
        if (floating) {
            while (true) {
                offset.moveTo(-halfTravel, durations.floatSlideMs, 0, EaseInOut)
                delay(durations.floatHoldMs.toLong())
                offset.moveTo(halfTravel, durations.floatSlideMs, 0, EaseInOut)
                delay(durations.floatHoldMs.toLong())
            }
        } else {
            offset.moveTo(0f, durations.exitMs, 0, EaseInOut)
        }
    }
    return graphicsLayer { translationY = offset.value }
}

private suspend fun Animatable<Float, AnimationVector1D>.moveTo(
    target: Float,
    durationMs: Int,
    delayMs: Int,
    easing: Easing
) {
    if (durationMs <= 0) {
        if (delayMs > 0) delay(delayMs.toLong())
        snapTo(target)
    } else {
        animateTo(target, tween(durationMillis = durationMs, delayMillis = delayMs, easing = easing))
    }
}
