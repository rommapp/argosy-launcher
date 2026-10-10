package com.nendo.argosy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun observedFocusScaleAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float>,
    label: String,
    onStateChanged: ((Boolean) -> Unit)?
): State<Float> {
    if (onStateChanged == null) {
        return animateFloatAsState(targetValue, animationSpec, label = label)
    }
    val threshold = Spring.DefaultDisplacementThreshold
    val animation = remember { Animatable(targetValue, Float.VectorConverter, threshold, label) }
    val spec by rememberUpdatedState(
        if (animationSpec is SpringSpec && animationSpec.visibilityThreshold != threshold) {
            spring(animationSpec.dampingRatio, animationSpec.stiffness, threshold)
        } else animationSpec
    )
    val report by rememberUpdatedState(onStateChanged)
    val state = animation.asState()
    val targets = remember { Channel<Float>(Channel.CONFLATED) }
    SideEffect {
        targets.trySend(targetValue)
        report?.invoke(focusScaleIsRunning(animation, targetValue))
    }
    LaunchedEffect(targets) { animateFocusScaleTargets(animation, targets) { spec } }
    LaunchedEffect(animation, targetValue) {
        snapshotFlow { focusScaleIsRunning(animation, targetValue) }.collect { report?.invoke(it) }
    }
    return state
}

internal fun focusScaleIsRunning(animation: Animatable<Float, AnimationVector1D>, target: Float): Boolean =
    animation.isRunning || animation.value != target

internal suspend fun animateFocusScaleTargets(
    animation: Animatable<Float, AnimationVector1D>,
    targets: ReceiveChannel<Float>,
    spec: () -> AnimationSpec<Float>
) = coroutineScope {
    for (target in targets) {
        val latest = targets.tryReceive().getOrNull() ?: target
        launch {
            if (latest != animation.targetValue) animation.animateTo(latest, spec())
        }
    }
}

internal class CarouselScaleAnimationTracker {
    private data class Report(val configurationScale: Float, val focusIndex: Int, val running: Boolean)
    private val entries = mutableMapOf<String, Report?>()

    fun register(key: String) { entries[key] = null }
    fun remove(key: String) { entries.remove(key) }
    fun report(key: String, configurationScale: Float, running: Boolean, focusIndex: Int = 0) {
        if (entries.containsKey(key)) entries[key] = Report(configurationScale, focusIndex, running)
    }

    fun isSettled(configurationScale: Float, focusIndex: Int = 0): Boolean = entries.isNotEmpty() && entries.values.all {
        it != null && it.configurationScale == configurationScale && it.focusIndex == focusIndex && !it.running
    }
}
