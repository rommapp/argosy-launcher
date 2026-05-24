package com.nendo.argosy.libretro.touch

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun TouchLayoutEditor(
    spec: TouchLayoutSpec,
    initial: ResolvedLayout,
    onSave: (ResolvedLayout) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onTogglePreviewOrientation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val placements = remember { mutableStateMapOf<GroupId, GroupPlacement>() }
    LaunchedEffect(initial) {
        placements.clear()
        placements.putAll(initial.placements)
    }
    val visibleGroups = remember(spec) {
        buildList {
            if (spec.dpad != DpadStyle.None && spec.dpad != DpadStyle.AnalogOnly) add(GroupId.DPAD)
            when (spec.analog) {
                AnalogConfig.None -> Unit
                AnalogConfig.LeftOnly,
                AnalogConfig.LeftAndRight -> add(GroupId.LEFT_ANALOG)
            }
            add(GroupId.FACE)
            if (spec.shoulders != ShoulderShape.None) add(GroupId.SHOULDERS)
            add(GroupId.SYSTEM)
            if (spec.analog == AnalogConfig.LeftAndRight) add(GroupId.RIGHT_ANALOG)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val areaW = maxWidth
        val areaH = maxHeight
        val areaWPx = with(LocalDensity.current) { areaW.toPx() }
        val areaHPx = with(LocalDensity.current) { areaH.toPx() }
        val widgetPx = with(LocalDensity.current) { 110.dp.toPx() }
        visibleGroups.forEach { id ->
            val p = placements[id] ?: return@forEach
            EditWidget(
                id = id,
                placement = p,
                areaWPx = areaWPx,
                areaHPx = areaHPx,
                widgetBasePx = widgetPx,
                otherPlacements = placements.filterKeys { it != id }.values.toList(),
                onChange = { placements[id] = it }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onSave(ResolvedLayout(placements.toMap())) }) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            OutlinedButton(onClick = onReset) { Text("Reset") }
            onTogglePreviewOrientation?.let {
                OutlinedButton(onClick = it) { Text("Switch preview") }
            }
        }
    }
}

@Composable
private fun EditWidget(
    id: GroupId,
    placement: GroupPlacement,
    areaWPx: Float,
    areaHPx: Float,
    widgetBasePx: Float,
    otherPlacements: List<GroupPlacement>,
    onChange: (GroupPlacement) -> Unit
) {
    val density = LocalDensity.current
    val baseSize = 110.dp * placement.scale
    val baseSizePx = widgetBasePx * placement.scale
    val offX = with(density) { (areaWPx * placement.anchorX - baseSizePx / 2f).toDp() }
    val offY = with(density) { (areaHPx * placement.anchorY - baseSizePx / 2f).toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(
                density.run { offX.roundToPx() },
                density.run { offY.roundToPx() }
            ) }
            .size(baseSize)
            .background(Color(0x66000000))
            .drawBehind {
                drawRect(
                    color = Color(0xFFE6E6E6),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }
            .pointerInput(id, areaWPx, areaHPx, widgetBasePx, otherPlacements) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val proposedX = placement.anchorX + drag.x / areaWPx
                    val proposedY = placement.anchorY + drag.y / areaHPx
                    val resolved = resolveValidPosition(
                        currentX = placement.anchorX,
                        currentY = placement.anchorY,
                        proposedX = proposedX,
                        proposedY = proposedY,
                        scale = placement.scale,
                        areaWPx = areaWPx,
                        areaHPx = areaHPx,
                        widgetBasePx = widgetBasePx,
                        others = otherPlacements
                    )
                    onChange(placement.copy(anchorX = resolved.first, anchorY = resolved.second))
                }
            }
            .pointerInput(id, areaWPx, areaHPx, widgetBasePx, otherPlacements) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        val newScale = (placement.scale * zoom).coerceIn(0.5f, 2.0f)
                        if (!collides(placement.anchorX, placement.anchorY, newScale, areaWPx, areaHPx, widgetBasePx, otherPlacements)) {
                            onChange(placement.copy(scale = newScale))
                        }
                    }
                }
            }
    ) {
        Text(
            text = id.name,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

private fun resolveValidPosition(
    currentX: Float,
    currentY: Float,
    proposedX: Float,
    proposedY: Float,
    scale: Float,
    areaWPx: Float,
    areaHPx: Float,
    widgetBasePx: Float,
    others: List<GroupPlacement>
): Pair<Float, Float> {
    val halfPx = widgetBasePx * scale / 2f
    val minX = halfPx / areaWPx
    val maxX = 1f - halfPx / areaWPx
    val minY = halfPx / areaHPx
    val maxY = 1f - halfPx / areaHPx
    var clampedX = proposedX.coerceIn(minX, maxX)
    var clampedY = proposedY.coerceIn(minY, maxY)
    if (!collides(clampedX, clampedY, scale, areaWPx, areaHPx, widgetBasePx, others)) {
        return clampedX to clampedY
    }
    val tryX = clampedX
    val tryY = currentY.coerceIn(minY, maxY)
    if (!collides(tryX, tryY, scale, areaWPx, areaHPx, widgetBasePx, others)) {
        return tryX to tryY
    }
    val tryX2 = currentX.coerceIn(minX, maxX)
    val tryY2 = clampedY
    if (!collides(tryX2, tryY2, scale, areaWPx, areaHPx, widgetBasePx, others)) {
        return tryX2 to tryY2
    }
    return currentX to currentY
}

private fun collides(
    x: Float,
    y: Float,
    scale: Float,
    areaWPx: Float,
    areaHPx: Float,
    widgetBasePx: Float,
    others: List<GroupPlacement>
): Boolean {
    val r = widgetBasePx * scale / 2f
    val cx = x * areaWPx
    val cy = y * areaHPx
    for (o in others) {
        val or = widgetBasePx * o.scale / 2f
        val ox = o.anchorX * areaWPx
        val oy = o.anchorY * areaHPx
        val dx = cx - ox
        val dy = cy - oy
        val distSq = dx * dx + dy * dy
        val minDist = r + or
        if (distSq < minDist * minDist) return true
    }
    return false
}
