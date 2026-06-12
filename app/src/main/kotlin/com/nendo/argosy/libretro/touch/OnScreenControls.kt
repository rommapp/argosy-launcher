package com.nendo.argosy.libretro.touch

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun OnScreenControls(
    retroView: GLRetroView,
    spec: TouchLayoutSpec,
    resolved: ResolvedLayout,
    opacity: Float,
    sizeScale: Float,
    haptic: Boolean,
    onKey: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember { findVibrator(context) }
    val fireHaptic: () -> Unit = remember(haptic) {
        { if (haptic) vibrateClick(vibrator) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .alpha(opacity)
    ) {
        val areaW = maxWidth
        val areaH = maxHeight

        resolved.get(GroupId.SHOULDERS)?.takeIf { !it.disabled }?.let { placement ->
            ShoulderBar(
                spec = spec,
                placement = placement,
                overrides = resolved.buttonOverrides,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic,
                onKey = onKey
            )
        }

        resolved.get(GroupId.LEFT_ANALOG)?.takeIf { !it.disabled }?.let { placement ->
            AnalogStick(
                retroView = retroView,
                placement = placement,
                source = GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale
            )
        }

        resolved.get(GroupId.DPAD)?.takeIf { !it.disabled }?.let { placement ->
            DpadCross(
                retroView = retroView,
                placement = placement,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic,
                asAnalog = spec.dpad == DpadStyle.AnalogOnly
            )
        }

        resolved.get(GroupId.RIGHT_ANALOG)?.takeIf { !it.disabled }?.let { placement ->
            AnalogStick(
                retroView = retroView,
                placement = placement,
                source = GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale
            )
        }

        resolved.get(GroupId.FACE)?.takeIf { !it.disabled }?.let { placement ->
            FaceCluster(
                spec = spec,
                placement = placement,
                overrides = resolved.buttonOverrides,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic,
                onKey = onKey
            )
        }

        resolved.get(GroupId.SYSTEM)?.takeIf { !it.disabled }?.let { placement ->
            SystemBar(
                spec = spec,
                placement = placement,
                overrides = resolved.buttonOverrides,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic,
                onKey = onKey
            )
        }
    }
}

@Composable
private fun DpadCross(
    retroView: GLRetroView,
    placement: GroupPlacement,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit,
    asAnalog: Boolean = false
) {
    val density = LocalDensity.current
    val baseSize = 120.dp * placement.scale * sizeScale
    val edge = 8.dp
    val offsetX = (areaW * placement.anchorX - baseSize / 2)
        .coerceIn(edge, (areaW - baseSize - edge).coerceAtLeast(edge))
    val offsetY = (areaH * placement.anchorY - baseSize / 2)
        .coerceIn(edge, (areaH - baseSize - edge).coerceAtLeast(edge))
    val motionSource = if (asAnalog) GLRetroView.MOTION_SOURCE_ANALOG_LEFT else GLRetroView.MOTION_SOURCE_DPAD
    var dxState by remember { mutableStateOf(0) }
    var dyState by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .offset { IntOffset(density.run { offsetX.roundToPx() }, density.run { offsetY.roundToPx() }) }
            .size(baseSize)
            .drawBehind { drawDpad(size, dxState, dyState) }
            .pointerInput(motionSource) {
                awaitPointerEventScope {
                    var lastX = 0
                    var lastY = 0
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.firstOrNull { it.pressed }
                        if (pressed != null) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val rel = pressed.position - center
                            val dead = size.width * 0.18f
                            val dx = if (rel.x > dead) 1 else if (rel.x < -dead) -1 else 0
                            val dy = if (rel.y > dead) 1 else if (rel.y < -dead) -1 else 0
                            if (dx != lastX || dy != lastY) {
                                if (lastX == 0 && lastY == 0 && (dx != 0 || dy != 0)) fireHaptic()
                                retroView.sendMotionEvent(motionSource, dx.toFloat(), dy.toFloat())
                                lastX = dx
                                lastY = dy
                                dxState = dx
                                dyState = dy
                            }
                        } else if (lastX != 0 || lastY != 0) {
                            retroView.sendMotionEvent(motionSource, 0f, 0f)
                            lastX = 0
                            lastY = 0
                            dxState = 0
                            dyState = 0
                        }
                    }
                }
            }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDpad(
    size: Size,
    pressedDx: Int = 0,
    pressedDy: Int = 0
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val arm = size.width * 0.35f
    val armLen = arm * 1.4f
    val face = Color(0xFFE6E6E6)
    val border = Color(0xFF1A1A1A)
    val pressTint = Color(0xFF7AA8FF)
    val corner = androidx.compose.ui.geometry.CornerRadius(arm * 0.20f, arm * 0.20f)

    val horizTopLeft = Offset(center.x - armLen, center.y - arm / 2f)
    val horizSize = Size(armLen * 2f, arm)
    val vertTopLeft = Offset(center.x - arm / 2f, center.y - armLen)
    val vertSize = Size(arm, armLen * 2f)

    drawRoundRect(color = border, topLeft = horizTopLeft, size = horizSize, cornerRadius = corner)
    drawRoundRect(color = border, topLeft = vertTopLeft, size = vertSize, cornerRadius = corner)

    val innerInset = 3f
    drawRoundRect(
        color = face,
        topLeft = Offset(horizTopLeft.x + innerInset, horizTopLeft.y + innerInset),
        size = Size(horizSize.width - innerInset * 2, horizSize.height - innerInset * 2),
        cornerRadius = corner
    )
    drawRoundRect(
        color = face,
        topLeft = Offset(vertTopLeft.x + innerInset, vertTopLeft.y + innerInset),
        size = Size(vertSize.width - innerInset * 2, vertSize.height - innerInset * 2),
        cornerRadius = corner
    )

    if (pressedDx != 0 || pressedDy != 0) {
        val hCorner = androidx.compose.ui.geometry.CornerRadius(arm * 0.15f, arm * 0.15f)
        val m = arm * 0.14f
        val pillLong = armLen - m * 2f
        val pillShort = arm - m * 2f
        when {
            pressedDy < 0 -> drawRoundRect(
                color = pressTint,
                topLeft = Offset(center.x - arm / 2f + m, center.y - armLen + m),
                size = Size(pillShort, pillLong),
                cornerRadius = hCorner
            )
            pressedDy > 0 -> drawRoundRect(
                color = pressTint,
                topLeft = Offset(center.x - arm / 2f + m, center.y + m),
                size = Size(pillShort, pillLong),
                cornerRadius = hCorner
            )
        }
        when {
            pressedDx < 0 -> drawRoundRect(
                color = pressTint,
                topLeft = Offset(center.x - armLen + m, center.y - arm / 2f + m),
                size = Size(pillLong, pillShort),
                cornerRadius = hCorner
            )
            pressedDx > 0 -> drawRoundRect(
                color = pressTint,
                topLeft = Offset(center.x + m, center.y - arm / 2f + m),
                size = Size(pillLong, pillShort),
                cornerRadius = hCorner
            )
        }
    }

    val centerDotR = arm * 0.18f
    drawCircle(color = border, radius = centerDotR, center = center)
}

@Composable
private fun AnalogStick(
    retroView: GLRetroView,
    placement: GroupPlacement,
    source: Int,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float
) {
    val density = LocalDensity.current
    val baseSize = 130.dp * placement.scale * sizeScale
    val edge = 8.dp
    val offsetX = (areaW * placement.anchorX - baseSize / 2)
        .coerceIn(edge, (areaW - baseSize - edge).coerceAtLeast(edge))
    val offsetY = (areaH * placement.anchorY - baseSize / 2)
        .coerceIn(edge, (areaH - baseSize - edge).coerceAtLeast(edge))
    var thumbPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .offset { IntOffset(density.run { offsetX.roundToPx() }, density.run { offsetY.roundToPx() }) }
            .size(baseSize)
            .drawBehind {
                drawCircle(Color(0x66000000), size.width / 2f, Offset(size.width / 2f, size.height / 2f))
                drawCircle(Color(0xCCCCCCCC), size.width / 2f - 4, Offset(size.width / 2f, size.height / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                val tx = size.width / 2f + thumbPos.x * (size.width / 2f - 12f)
                val ty = size.height / 2f + thumbPos.y * (size.height / 2f - 12f)
                drawCircle(Color(0xFFE6E6E6), size.width * 0.18f, Offset(tx, ty))
            }
            .pointerInput(source) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.firstOrNull { it.pressed }
                        if (pressed != null) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val rel = pressed.position - center
                            val radius = size.width / 2f
                            val dist = hypot(rel.x.toDouble(), rel.y.toDouble()).toFloat()
                            val nx = (rel.x / radius).coerceIn(-1f, 1f)
                            val ny = (rel.y / radius).coerceIn(-1f, 1f)
                            val clampedNx = if (dist > radius) cos(atan2(rel.y, rel.x)).toFloat() else nx
                            val clampedNy = if (dist > radius) sin(atan2(rel.y, rel.x)).toFloat() else ny
                            thumbPos = Offset(clampedNx, clampedNy)
                            retroView.sendMotionEvent(source, clampedNx, clampedNy)
                        } else {
                            if (thumbPos != Offset.Zero) {
                                thumbPos = Offset.Zero
                                retroView.sendMotionEvent(source, 0f, 0f)
                            }
                        }
                    }
                }
            }
    )
}

@Composable
private fun FaceCluster(
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    overrides: Map<String, GroupPlacement>,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit,
    onKey: (Int, Int) -> Unit
) {
    val slots = layoutFaceSlots(spec, placement, overrides, areaW, areaH, sizeScale)
    val buttons = spec.faceSlots.zip(slots).mapNotNull { (slot, lay) ->
        if (overrides[lay.key]?.disabled == true) return@mapNotNull null
        ClusterButton(
            key = lay.key,
            label = slot.label,
            tint = slot.tint,
            offsetX = lay.offsetX,
            offsetY = lay.offsetY,
            widthDp = lay.widthDp,
            heightDp = lay.heightDp,
            onPress = {
                fireHaptic()
                onKey(KeyEvent.ACTION_DOWN, slot.androidKeyCode)
            },
            onRelease = {
                onKey(KeyEvent.ACTION_UP, slot.androidKeyCode)
            }
        )
    }
    ClusterDispatcher(buttons)
}

@Composable
private fun ShoulderBar(
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    overrides: Map<String, GroupPlacement>,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit,
    onKey: (Int, Int) -> Unit
) {
    val slots = layoutShoulderSlots(spec, placement, overrides, areaW, areaH, sizeScale)
    val buttons = spec.shoulderSlots.zip(slots).mapNotNull { (slot, lay) ->
        if (overrides[lay.key]?.disabled == true) return@mapNotNull null
        val tint = if (spec.shoulders == ShoulderShape.TopPairPlusZ && lay.key == "shoulder_2") {
            Color(0xFF6F4FCD)
        } else null
        ClusterButton(
            key = lay.key,
            label = slot.label,
            tint = tint,
            offsetX = lay.offsetX,
            offsetY = lay.offsetY,
            widthDp = lay.widthDp,
            heightDp = lay.heightDp,
            onPress = { fireHaptic(); onKey(KeyEvent.ACTION_DOWN, slot.androidKeyCode) },
            onRelease = { onKey(KeyEvent.ACTION_UP, slot.androidKeyCode) }
        )
    }
    ClusterDispatcher(buttons)
}

@Composable
private fun SystemBar(
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    overrides: Map<String, GroupPlacement>,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit,
    onKey: (Int, Int) -> Unit
) {
    if (spec.system.isEmpty()) return
    val slots = layoutSystemSlots(spec, placement, overrides, areaW, areaH, sizeScale)
    val buttons = spec.system.zip(slots).mapNotNull { (slot, lay) ->
        if (overrides[lay.key]?.disabled == true) return@mapNotNull null
        ClusterButton(
            key = lay.key,
            label = slot.label,
            tint = null,
            offsetX = lay.offsetX,
            offsetY = lay.offsetY,
            widthDp = lay.widthDp,
            heightDp = lay.heightDp,
            onPress = { fireHaptic(); onKey(KeyEvent.ACTION_DOWN, slot.androidKeyCode) },
            onRelease = { onKey(KeyEvent.ACTION_UP, slot.androidKeyCode) }
        )
    }
    ClusterDispatcher(buttons)
}

private data class ClusterButton(
    val key: Any,
    val label: String,
    val tint: Color?,
    val offsetX: Dp,
    val offsetY: Dp,
    val widthDp: Dp,
    val heightDp: Dp,
    val onPress: () -> Unit,
    val onRelease: () -> Unit
)

@Composable
private fun ClusterDispatcher(
    buttons: List<ClusterButton>
) {
    if (buttons.isEmpty()) return
    val density = LocalDensity.current
    val pressedKeys = remember { mutableStateMapOf<Any, Unit>() }

    val minTap = 48.dp
    val padded = buttons.map { b ->
        val drawW = b.widthDp
        val drawH = b.heightDp
        val hitW = if (drawW < minTap) minTap else drawW
        val hitH = if (drawH < minTap) minTap else drawH
        val hitOffX = b.offsetX - (hitW - drawW) / 2
        val hitOffY = b.offsetY - (hitH - drawH) / 2
        Quad(b, hitOffX, hitOffY, hitW, hitH)
    }
    val minX = padded.minOf { it.x.value }.dp
    val minY = padded.minOf { it.y.value }.dp
    val maxX = padded.maxOf { (it.x + it.w).value }.dp
    val maxY = padded.maxOf { (it.y + it.h).value }.dp

    val zones = padded.map { q ->
        val l = with(density) { (q.x - minX).toPx() }
        val t = with(density) { (q.y - minY).toPx() }
        val r = l + with(density) { q.w.toPx() }
        val btm = t + with(density) { q.h.toPx() }
        q.button to Rect(l, t, r, btm)
    }

    buttons.forEach { b ->
        ButtonChipVisual(
            label = b.label,
            tint = b.tint,
            sizeDp = b.heightDp,
            widthDp = b.widthDp,
            offsetX = b.offsetX,
            offsetY = b.offsetY,
            pressed = pressedKeys.containsKey(b.key)
        )
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(density.run { minX.roundToPx() }, density.run { minY.roundToPx() }) }
            .size(maxX - minX, maxY - minY)
            .pointerInput(zones) {
                val ownership = mutableMapOf<PointerId, ClusterButton>()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { change ->
                            val pos = change.position
                            val zoneHit = zones.firstOrNull { (_, rect) -> rect.contains(pos) }?.first
                            val current = ownership[change.id]
                            if (change.pressed) {
                                if (zoneHit != null && zoneHit !== current) {
                                    current?.let {
                                        it.onRelease()
                                        pressedKeys.remove(it.key)
                                    }
                                    if (!pressedKeys.containsKey(zoneHit.key)) {
                                        zoneHit.onPress()
                                        pressedKeys[zoneHit.key] = Unit
                                    }
                                    ownership[change.id] = zoneHit
                                    change.consume()
                                } else if (zoneHit == null && current != null) {
                                    current.onRelease()
                                    pressedKeys.remove(current.key)
                                    ownership.remove(change.id)
                                }
                            } else {
                                current?.let {
                                    it.onRelease()
                                    pressedKeys.remove(it.key)
                                }
                                ownership.remove(change.id)
                            }
                        }
                    }
                }
            }
    )
}

private data class Quad(val button: ClusterButton, val x: Dp, val y: Dp, val w: Dp, val h: Dp)

@Composable
private fun ButtonChipVisual(
    label: String,
    tint: Color?,
    sizeDp: Dp,
    widthDp: Dp,
    offsetX: Dp,
    offsetY: Dp,
    pressed: Boolean
) {
    val density = LocalDensity.current
    val bg = tint ?: Color(0xCC1A1A1A)
    val pressedBg = (tint ?: Color(0xFF3A6FC8)).copy(alpha = 0.95f)
    val border = Color(0xFFE6E6E6)
    val measurer = rememberTextMeasurer()
    val drawWPx = with(density) { widthDp.toPx() }
    val drawHPx = with(density) { sizeDp.toPx() }
    val isPill = drawWPx > drawHPx * 1.3f
    Box(
        modifier = Modifier
            .offset { IntOffset(
                density.run { offsetX.roundToPx() },
                density.run { offsetY.roundToPx() }
            ) }
            .size(width = widthDp, height = sizeDp)
            .drawBehind {
                val center = Offset(drawWPx / 2f, drawHPx / 2f)
                val fillColor = if (pressed) pressedBg else bg
                if (isPill) {
                    val corner = androidx.compose.ui.geometry.CornerRadius(drawHPx / 2f, drawHPx / 2f)
                    drawRoundRect(color = fillColor, size = Size(drawWPx, drawHPx), cornerRadius = corner)
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(1.5f, 1.5f),
                        size = Size(drawWPx - 3f, drawHPx - 3f),
                        cornerRadius = corner,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                } else {
                    val r = kotlin.math.min(drawWPx, drawHPx) / 2f
                    drawCircle(color = fillColor, radius = r, center = center)
                    drawCircle(
                        color = border,
                        radius = r - 1.5f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
                val maxTextW = drawWPx - 12f
                val maxTextH = drawHPx - 8f
                var fontSp = if (isPill) (drawHPx / density.density) * 0.55f
                             else (drawHPx / density.density) * 0.42f
                var layout = measurer.measure(
                    text = label,
                    style = TextStyle(
                        color = border,
                        fontSize = fontSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                var guard = 0
                while ((layout.size.width > maxTextW || layout.size.height > maxTextH) && fontSp > 7f && guard < 10) {
                    fontSp *= 0.85f
                    layout = measurer.measure(
                        text = label,
                        style = TextStyle(
                            color = border,
                            fontSize = fontSp.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    guard++
                }
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        center.x - layout.size.width / 2f,
                        center.y - layout.size.height / 2f
                    )
                )
            }
    )
}


private fun computeFacePositions(shape: FaceShape, count: Int, btnSize: Dp, gap: Dp): List<DpOffset> {
    val s = btnSize.value
    val g = gap.value
    return when (shape) {
        FaceShape.Single -> listOf(DpOffset(0f, 0f))
        FaceShape.HorizontalPair -> List(count) { i -> DpOffset((s + g) * i, 0f) }
        FaceShape.HorizontalTrio -> List(count) { i -> DpOffset((s + g) * i, 0f) }
        FaceShape.Row4 -> List(count) { i -> DpOffset((s + g) * i, 0f) }
        FaceShape.Row6 -> List(count) { i -> DpOffset((s + g) * (i % 6), 0f) }
        FaceShape.Diamond4 -> listOf(
            DpOffset(0f, s + g),
            DpOffset(s + g, 0f),
            DpOffset(s + g, (s + g) * 2),
            DpOffset((s + g) * 2, s + g)
        ).take(count)
        FaceShape.Stack2x3 -> List(count) { i ->
            val row = i / 3
            val col = i % 3
            DpOffset((s + g) * col, (s + g) * row)
        }
        FaceShape.NbuttonCluster -> List(count) { i -> DpOffset((s + g) * i, 0f) }
    }
}

private data class DpOffset(val x: Float, val y: Float)

internal data class SlotLayout(
    val key: String,
    val offsetX: Dp,
    val offsetY: Dp,
    val widthDp: Dp,
    val heightDp: Dp
)

internal fun layoutFaceSlots(
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    overrides: Map<String, GroupPlacement>,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float
): List<SlotLayout> {
    val btnSize = 50.dp * sizeScale * placement.scale
    val gap = 6.dp * sizeScale * placement.scale
    val positions = computeFacePositions(spec.face, spec.faceSlots.size, btnSize, gap)
    val maxX: Float = positions.fold(0f) { acc, p -> kotlin.math.max(acc, p.x + btnSize.value) }
    val maxY: Float = positions.fold(0f) { acc, p -> kotlin.math.max(acc, p.y + btnSize.value) }
    val groupW = maxX.dp
    val groupH = maxY.dp
    val edge = 8.dp
    val originX = (areaW * placement.anchorX - groupW / 2)
        .coerceIn(edge, (areaW - groupW - edge).coerceAtLeast(edge))
    val originY = (areaH * placement.anchorY - groupH / 2)
        .coerceIn(edge, (areaH - groupH - edge).coerceAtLeast(edge))
    return spec.faceSlots.mapIndexed { i, _ ->
        val key = "face_$i"
        val override = overrides[key]
        if (override != null) {
            val w = btnSize * override.scale
            val h = btnSize * override.scale
            val ox = (areaW * override.anchorX - w / 2)
                .coerceIn(edge, (areaW - w - edge).coerceAtLeast(edge))
            val oy = (areaH * override.anchorY - h / 2)
                .coerceIn(edge, (areaH - h - edge).coerceAtLeast(edge))
            SlotLayout(key, ox, oy, w, h)
        } else {
            val pos = positions[i]
            SlotLayout(key, originX + pos.x.dp, originY + pos.y.dp, btnSize, btnSize)
        }
    }
}

internal fun layoutShoulderSlots(
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    overrides: Map<String, GroupPlacement>,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float
): List<SlotLayout> {
    val btnSize = 48.dp * sizeScale * placement.scale
    val gap = 8.dp * sizeScale * placement.scale
    val padding = 24.dp
    val centerY = areaH * placement.anchorY
    val edge = 8.dp
    val rawPositions: List<Pair<Dp, Dp>> = when (spec.shoulders) {
        ShoulderShape.None -> emptyList()
        ShoulderShape.TopPair -> listOf(
            padding to centerY - btnSize / 2,
            areaW - padding - btnSize to centerY - btnSize / 2
        )
        ShoulderShape.FourCorners -> listOf(
            padding + btnSize + gap to centerY - btnSize / 2,
            padding to centerY - btnSize / 2,
            areaW - padding - btnSize - btnSize - gap to centerY - btnSize / 2,
            areaW - padding - btnSize to centerY - btnSize / 2
        )
        ShoulderShape.TopPairPlusZ -> listOf(
            padding to centerY - btnSize / 2,
            areaW - padding - btnSize to centerY - btnSize / 2,
            areaW - padding - btnSize to centerY + btnSize / 2 + gap
        )
    }
    return spec.shoulderSlots.mapIndexedNotNull { i, _ ->
        val key = "shoulder_$i"
        val raw = rawPositions.getOrNull(i) ?: return@mapIndexedNotNull null
        val override = overrides[key]
        if (override != null) {
            val w = btnSize * override.scale
            val h = btnSize * override.scale
            val ox = (areaW * override.anchorX - w / 2)
                .coerceIn(edge, (areaW - w - edge).coerceAtLeast(edge))
            val oy = (areaH * override.anchorY - h / 2)
                .coerceIn(edge, (areaH - h - edge).coerceAtLeast(edge))
            SlotLayout(key, ox, oy, w, h)
        } else {
            SlotLayout(key, raw.first, raw.second, btnSize, btnSize)
        }
    }
}

internal fun layoutSystemSlots(
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    overrides: Map<String, GroupPlacement>,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float
): List<SlotLayout> {
    if (spec.system.isEmpty()) return emptyList()
    val btnW = 58.dp * sizeScale * placement.scale
    val btnH = 26.dp * sizeScale * placement.scale
    val gap = 10.dp * sizeScale * placement.scale
    val groupW = btnW * spec.system.size + gap * (spec.system.size - 1)
    val edge = 8.dp
    val originX = (areaW * placement.anchorX - groupW / 2)
        .coerceIn(edge, (areaW - groupW - edge).coerceAtLeast(edge))
    val originY = (areaH * placement.anchorY - btnH / 2)
        .coerceIn(edge, (areaH - btnH - edge).coerceAtLeast(edge))
    return spec.system.mapIndexed { i, _ ->
        val key = "system_$i"
        val override = overrides[key]
        if (override != null) {
            val w = btnW * override.scale
            val h = btnH * override.scale
            val ox = (areaW * override.anchorX - w / 2)
                .coerceIn(edge, (areaW - w - edge).coerceAtLeast(edge))
            val oy = (areaH * override.anchorY - h / 2)
                .coerceIn(edge, (areaH - h - edge).coerceAtLeast(edge))
            SlotLayout(key, ox, oy, w, h)
        } else {
            SlotLayout(key, originX + (btnW + gap) * i, originY, btnW, btnH)
        }
    }
}

private fun findVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

private fun vibrateClick(vibrator: Vibrator?) {
    val v = vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        v.vibrate(10)
    }
}
