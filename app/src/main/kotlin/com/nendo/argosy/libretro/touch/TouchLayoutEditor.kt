package com.nendo.argosy.libretro.touch

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WidgetCenter(val key: String, val cxPx: Float, val cyPx: Float)

class TouchEditorState {
    val groupPlacements = mutableStateMapOf<GroupId, GroupPlacement>()
    val buttonOverrides = mutableStateMapOf<String, GroupPlacement>()
    fun reset(layout: ResolvedLayout) {
        groupPlacements.clear()
        groupPlacements.putAll(layout.placements)
        buttonOverrides.clear()
        buttonOverrides.putAll(layout.buttonOverrides)
    }
    fun snapshot(): ResolvedLayout =
        ResolvedLayout(groupPlacements.toMap(), buttonOverrides.toMap())
    fun clearOverrides() {
        buttonOverrides.clear()
    }
}

@Composable
fun rememberTouchEditorState(initial: ResolvedLayout): TouchEditorState {
    val state = remember { TouchEditorState() }
    LaunchedEffect(initial) { state.reset(initial) }
    return state
}

@Composable
fun TouchLayoutEditor(
    state: TouchEditorState,
    spec: TouchLayoutSpec,
    sizeScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val groupPlacements = state.groupPlacements
    val buttonOverrides = state.buttonOverrides

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { if (!it.isConsumed) it.consume() }
                    }
                }
            }
    ) {
        val areaW = maxWidth
        val areaH = maxHeight
        val density = LocalDensity.current
        val areaWPx = with(density) { areaW.toPx() }
        val areaHPx = with(density) { areaH.toPx() }
        val snapThresholdPx = with(density) { 3.dp.toPx() }

        val centers = mutableListOf<WidgetCenter>()
        groupPlacements[GroupId.DPAD]?.let { p ->
            centers += WidgetCenter("DPAD", p.anchorX * areaWPx, p.anchorY * areaHPx)
        }
        groupPlacements[GroupId.LEFT_ANALOG]?.let { p ->
            centers += WidgetCenter("LEFT_ANALOG", p.anchorX * areaWPx, p.anchorY * areaHPx)
        }
        groupPlacements[GroupId.RIGHT_ANALOG]?.let { p ->
            centers += WidgetCenter("RIGHT_ANALOG", p.anchorX * areaWPx, p.anchorY * areaHPx)
        }
        val faceLayouts = groupPlacements[GroupId.FACE]?.let { p ->
            layoutFaceSlots(spec, p, buttonOverrides, areaW, areaH, sizeScale)
        } ?: emptyList()
        faceLayouts.forEach { lay ->
            val cx = with(density) { (lay.offsetX + lay.widthDp / 2).toPx() }
            val cy = with(density) { (lay.offsetY + lay.heightDp / 2).toPx() }
            centers += WidgetCenter(lay.key, cx, cy)
        }
        val shoulderLayouts = if (spec.shoulders != ShoulderShape.None) {
            groupPlacements[GroupId.SHOULDERS]?.let { p ->
                layoutShoulderSlots(spec, p, buttonOverrides, areaW, areaH, sizeScale)
            } ?: emptyList()
        } else emptyList()
        shoulderLayouts.forEach { lay ->
            val cx = with(density) { (lay.offsetX + lay.widthDp / 2).toPx() }
            val cy = with(density) { (lay.offsetY + lay.heightDp / 2).toPx() }
            centers += WidgetCenter(lay.key, cx, cy)
        }
        val systemLayouts = groupPlacements[GroupId.SYSTEM]?.let { p ->
            layoutSystemSlots(spec, p, buttonOverrides, areaW, areaH, sizeScale)
        } ?: emptyList()
        systemLayouts.forEach { lay ->
            val cx = with(density) { (lay.offsetX + lay.widthDp / 2).toPx() }
            val cy = with(density) { (lay.offsetY + lay.heightDp / 2).toPx() }
            centers += WidgetCenter(lay.key, cx, cy)
        }

        if (spec.dpad != DpadStyle.None && spec.dpad != DpadStyle.AnalogOnly) {
            groupPlacements[GroupId.DPAD]?.let { p ->
                DragWidget(
                    key = "DPAD",
                    label = "D-PAD",
                    tint = Color(0x995574B5),
                    centerXPxInitial = p.anchorX * areaWPx,
                    centerYPxInitial = p.anchorY * areaHPx,
                    widthDp = 120.dp * sizeScale * p.scale,
                    heightDp = 120.dp * sizeScale * p.scale,
                    areaWPx = areaWPx,
                    areaHPx = areaHPx,
                    snapThresholdPx = snapThresholdPx,
                    siblings = centers,
                    currentScale = p.scale,
                    currentDisabled = p.disabled,
                    allowScale = false,
                    allowDisable = true,
                    onChange = { groupPlacements[GroupId.DPAD] = it }
                )
            }
        }
        when (spec.analog) {
            AnalogConfig.LeftOnly, AnalogConfig.LeftAndRight -> {
                groupPlacements[GroupId.LEFT_ANALOG]?.let { p ->
                    DragWidget(
                        key = "LEFT_ANALOG",
                        label = "L",
                        tint = Color(0x995574B5),
                        centerXPxInitial = p.anchorX * areaWPx,
                        centerYPxInitial = p.anchorY * areaHPx,
                        widthDp = 100.dp * sizeScale * p.scale,
                        heightDp = 100.dp * sizeScale * p.scale,
                        areaWPx = areaWPx,
                        areaHPx = areaHPx,
                        snapThresholdPx = snapThresholdPx,
                        siblings = centers,
                        currentScale = p.scale,
                        currentDisabled = p.disabled,
                        allowScale = false,
                        allowDisable = true,
                        onChange = { groupPlacements[GroupId.LEFT_ANALOG] = it }
                    )
                }
            }
            AnalogConfig.None -> {}
        }
        if (spec.analog == AnalogConfig.LeftAndRight) {
            groupPlacements[GroupId.RIGHT_ANALOG]?.let { p ->
                DragWidget(
                    key = "RIGHT_ANALOG",
                    label = "R",
                    tint = Color(0x995574B5),
                    centerXPxInitial = p.anchorX * areaWPx,
                    centerYPxInitial = p.anchorY * areaHPx,
                    widthDp = 100.dp * sizeScale * p.scale,
                    heightDp = 100.dp * sizeScale * p.scale,
                    areaWPx = areaWPx,
                    areaHPx = areaHPx,
                    snapThresholdPx = snapThresholdPx,
                    siblings = centers,
                    currentScale = p.scale,
                    currentDisabled = p.disabled,
                    allowScale = false,
                    allowDisable = true,
                    onChange = { groupPlacements[GroupId.RIGHT_ANALOG] = it }
                )
            }
        }

        spec.faceSlots.zip(faceLayouts).forEach { (slot, lay) ->
            val cx = with(density) { (lay.offsetX + lay.widthDp / 2).toPx() }
            val cy = with(density) { (lay.offsetY + lay.heightDp / 2).toPx() }
            DragWidget(
                key = lay.key,
                label = slot.label,
                tint = slot.tint ?: Color(0xCC1A1A1A),
                centerXPxInitial = cx,
                centerYPxInitial = cy,
                widthDp = lay.widthDp,
                heightDp = lay.heightDp,
                areaWPx = areaWPx,
                areaHPx = areaHPx,
                snapThresholdPx = snapThresholdPx,
                siblings = centers,
                currentScale = buttonOverrides[lay.key]?.scale ?: 1f,
                currentDisabled = buttonOverrides[lay.key]?.disabled ?: false,
                allowScale = true,
                allowDisable = true,
                onChange = { buttonOverrides[lay.key] = it }
            )
        }
        spec.shoulderSlots.zip(shoulderLayouts).forEach { (slot, lay) ->
            val cx = with(density) { (lay.offsetX + lay.widthDp / 2).toPx() }
            val cy = with(density) { (lay.offsetY + lay.heightDp / 2).toPx() }
            DragWidget(
                key = lay.key,
                label = slot.label,
                tint = Color(0xCC1A1A1A),
                centerXPxInitial = cx,
                centerYPxInitial = cy,
                widthDp = lay.widthDp,
                heightDp = lay.heightDp,
                areaWPx = areaWPx,
                areaHPx = areaHPx,
                snapThresholdPx = snapThresholdPx,
                siblings = centers,
                currentScale = buttonOverrides[lay.key]?.scale ?: 1f,
                currentDisabled = buttonOverrides[lay.key]?.disabled ?: false,
                allowScale = true,
                allowDisable = true,
                onChange = { buttonOverrides[lay.key] = it }
            )
        }
        spec.system.zip(systemLayouts).forEach { (slot, lay) ->
            val cx = with(density) { (lay.offsetX + lay.widthDp / 2).toPx() }
            val cy = with(density) { (lay.offsetY + lay.heightDp / 2).toPx() }
            DragWidget(
                key = lay.key,
                label = slot.label,
                tint = Color(0xCC1A1A1A),
                centerXPxInitial = cx,
                centerYPxInitial = cy,
                widthDp = lay.widthDp,
                heightDp = lay.heightDp,
                areaWPx = areaWPx,
                areaHPx = areaHPx,
                snapThresholdPx = snapThresholdPx,
                siblings = centers,
                currentScale = buttonOverrides[lay.key]?.scale ?: 1f,
                currentDisabled = buttonOverrides[lay.key]?.disabled ?: false,
                allowScale = true,
                allowDisable = true,
                onChange = { buttonOverrides[lay.key] = it }
            )
        }
    }
}

@Composable
fun TouchEditorToolbar(
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onTogglePreviewOrientation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC101010),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val compact = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            Button(onClick = onSave, contentPadding = compact) {
                Text("Save", style = TextStyle(fontSize = 12.sp))
            }
            OutlinedButton(onClick = onReset, contentPadding = compact) {
                Text("Reset", style = TextStyle(fontSize = 12.sp))
            }
            OutlinedButton(onClick = onCancel, contentPadding = compact) {
                Text("Cancel", style = TextStyle(fontSize = 12.sp))
            }
            onTogglePreviewOrientation?.let {
                OutlinedButton(onClick = it, contentPadding = compact) {
                    Text("Rotate", style = TextStyle(fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun DragWidget(
    key: String,
    label: String,
    tint: Color,
    centerXPxInitial: Float,
    centerYPxInitial: Float,
    widthDp: Dp,
    heightDp: Dp,
    areaWPx: Float,
    areaHPx: Float,
    snapThresholdPx: Float,
    siblings: List<WidgetCenter>,
    currentScale: Float,
    currentDisabled: Boolean,
    allowScale: Boolean,
    allowDisable: Boolean,
    onChange: (GroupPlacement) -> Unit
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.toPx() }
    val heightPx = with(density) { heightDp.toPx() }
    val cxState = remember(key) { mutableFloatStateOf(centerXPxInitial) }
    val cyState = remember(key) { mutableFloatStateOf(centerYPxInitial) }
    val scaleState = remember(key) { mutableFloatStateOf(currentScale) }
    val disabledState = remember(key) { mutableStateOf(currentDisabled) }
    LaunchedEffect(centerXPxInitial, centerYPxInitial, currentScale, currentDisabled, key) {
        cxState.floatValue = centerXPxInitial
        cyState.floatValue = centerYPxInitial
        scaleState.floatValue = currentScale
        disabledState.value = currentDisabled
    }
    val onChangeLatest = rememberUpdatedState(onChange)
    val siblingsLatest = rememberUpdatedState(siblings.filterNot { it.key == key })

    val offX = with(density) { (cxState.floatValue - widthPx / 2f).toDp() }
    val offY = with(density) { (cyState.floatValue - heightPx / 2f).toDp() }

    val alpha = if (disabledState.value) 0.3f else 1f
    val effectiveTint = if (disabledState.value) Color(0x55444444) else tint

    Box(
        modifier = Modifier
            .offset { IntOffset(density.run { offX.roundToPx() }, density.run { offY.roundToPx() }) }
            .size(width = widthDp, height = heightDp)
            .background(effectiveTint)
            .drawBehind {
                drawRect(
                    color = Color(0xFFE6E6E6).copy(alpha = alpha),
                    style = Stroke(width = 2f),
                    size = Size(this.size.width, this.size.height)
                )
                if (disabledState.value) {
                    drawLine(
                        color = Color(0xFFFF6464),
                        start = Offset(4f, 4f),
                        end = Offset(this.size.width - 4f, this.size.height - 4f),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color(0xFFFF6464),
                        start = Offset(this.size.width - 4f, 4f),
                        end = Offset(4f, this.size.height - 4f),
                        strokeWidth = 3f
                    )
                }
            }
            .pointerInput(key, areaWPx, areaHPx) {
                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                    var newCx = cxState.floatValue + pan.x
                    var newCy = cyState.floatValue + pan.y
                    val candidatesX = mutableListOf<Float>(areaWPx / 2f)
                    val candidatesY = mutableListOf<Float>(areaHPx / 2f)
                    siblingsLatest.value.forEach {
                        candidatesX += it.cxPx
                        candidatesY += it.cyPx
                    }
                    newCx = snapAxis(newCx, candidatesX, snapThresholdPx)
                    newCy = snapAxis(newCy, candidatesY, snapThresholdPx)
                    val halfW = widthPx / 2f
                    val halfH = heightPx / 2f
                    val edgePx = with(density) { 8.dp.toPx() }
                    val minCx = (edgePx + halfW).coerceAtMost(areaWPx / 2f)
                    val maxCx = (areaWPx - edgePx - halfW).coerceAtLeast(areaWPx / 2f)
                    val minCy = (edgePx + halfH).coerceAtMost(areaHPx / 2f)
                    val maxCy = (areaHPx - edgePx - halfH).coerceAtLeast(areaHPx / 2f)
                    newCx = newCx.coerceIn(minCx, maxCx)
                    newCy = newCy.coerceIn(minCy, maxCy)
                    cxState.floatValue = newCx
                    cyState.floatValue = newCy
                    if (allowScale && zoom != 1f) {
                        scaleState.floatValue = (scaleState.floatValue * zoom).coerceIn(0.5f, 2.0f)
                    }
                    onChangeLatest.value(
                        GroupPlacement(
                            anchorX = newCx / areaWPx,
                            anchorY = newCy / areaHPx,
                            scale = scaleState.floatValue,
                            disabled = disabledState.value
                        )
                    )
                }
            }
            .pointerInput(key) {
                if (!allowDisable) return@pointerInput
                detectTapGestures(onDoubleTap = {
                    disabledState.value = !disabledState.value
                    onChangeLatest.value(
                        GroupPlacement(
                            anchorX = cxState.floatValue / areaWPx,
                            anchorY = cyState.floatValue / areaHPx,
                            scale = scaleState.floatValue,
                            disabled = disabledState.value
                        )
                    )
                })
            }
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp)
        )
    }
}

private fun snapAxis(value: Float, candidates: List<Float>, threshold: Float): Float {
    var best = value
    var bestDist = threshold
    for (c in candidates) {
        val d = kotlin.math.abs(value - c)
        if (d < bestDist) {
            best = c
            bestDist = d
        }
    }
    return best
}
