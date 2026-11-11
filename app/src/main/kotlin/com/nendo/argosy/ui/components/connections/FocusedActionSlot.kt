package com.nendo.argosy.ui.components.connections

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout

@Composable
internal fun FocusedActionSlot(
    shown: Boolean,
    animationSpec: AnimationSpec<Float>,
    rest: @Composable () -> Unit,
    action: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = animationSpec,
        label = "connections-action"
    )
    Layout(
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) }
            ) { rest() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
            ) { action() }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val restPlaceable = measurables[0].measure(loose)
        val actionPlaceable = measurables[1].measure(loose)
        val width = maxOf(restPlaceable.width, actionPlaceable.width)
        val height = maxOf(restPlaceable.height, actionPlaceable.height)
        layout(width, height) {
            if (progress < 1f) restPlaceable.place(width - restPlaceable.width, (height - restPlaceable.height) / 2)
            if (progress > 0f) actionPlaceable.place(width - actionPlaceable.width, (height - actionPlaceable.height) / 2)
        }
    }
}
