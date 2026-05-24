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

        resolved.get(GroupId.SHOULDERS)?.let { placement ->
            ShoulderBar(
                retroView = retroView,
                spec = spec,
                placement = placement,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic
            )
        }

        resolved.get(GroupId.LEFT_ANALOG)?.let { placement ->
            AnalogStick(
                retroView = retroView,
                placement = placement,
                source = GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale
            )
        }

        resolved.get(GroupId.DPAD)?.let { placement ->
            DpadCross(
                retroView = retroView,
                placement = placement,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic
            )
        }

        resolved.get(GroupId.RIGHT_ANALOG)?.let { placement ->
            AnalogStick(
                retroView = retroView,
                placement = placement,
                source = GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale
            )
        }

        resolved.get(GroupId.FACE)?.let { placement ->
            FaceCluster(
                retroView = retroView,
                spec = spec,
                placement = placement,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic
            )
        }

        resolved.get(GroupId.SYSTEM)?.let { placement ->
            SystemBar(
                retroView = retroView,
                spec = spec,
                placement = placement,
                areaW = areaW,
                areaH = areaH,
                sizeScale = sizeScale,
                fireHaptic = fireHaptic
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
    fireHaptic: () -> Unit
) {
    val density = LocalDensity.current
    val baseSize = 140.dp * placement.scale * sizeScale
    val offsetX = areaW * placement.anchorX - baseSize / 2
    val offsetY = areaH * placement.anchorY - baseSize / 2

    Box(
        modifier = Modifier
            .offset { IntOffset(density.run { offsetX.roundToPx() }, density.run { offsetY.roundToPx() }) }
            .size(baseSize)
            .drawBehind { drawDpad(size) }
            .pointerInput(Unit) {
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
                                retroView.sendMotionEvent(
                                    GLRetroView.MOTION_SOURCE_DPAD,
                                    dx.toFloat(), dy.toFloat()
                                )
                                lastX = dx
                                lastY = dy
                            }
                        } else if (lastX != 0 || lastY != 0) {
                            retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f)
                            lastX = 0
                            lastY = 0
                        }
                    }
                }
            }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDpad(size: Size) {
    val arm = size.width * 0.32f
    val center = Offset(size.width / 2f, size.height / 2f)
    val color = Color(0xFFE6E6E6)
    val ringColor = Color(0xCC000000)
    drawRect(
        color = ringColor,
        topLeft = Offset(center.x - arm, center.y - arm / 2),
        size = Size(arm * 2, arm),
    )
    drawRect(
        color = ringColor,
        topLeft = Offset(center.x - arm / 2, center.y - arm),
        size = Size(arm, arm * 2),
    )
    drawRect(
        color = color,
        topLeft = Offset(center.x - arm + 6, center.y - arm / 2 + 6),
        size = Size(arm * 2 - 12, arm - 12),
    )
    drawRect(
        color = color,
        topLeft = Offset(center.x - arm / 2 + 6, center.y - arm + 6),
        size = Size(arm - 12, arm * 2 - 12),
    )
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
    val offsetX = areaW * placement.anchorX - baseSize / 2
    val offsetY = areaH * placement.anchorY - baseSize / 2
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
    retroView: GLRetroView,
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit
) {
    val density = LocalDensity.current
    val btnSize = 60.dp * sizeScale * placement.scale
    val gap = 14.dp * sizeScale * placement.scale

    val positions = computeFacePositions(spec.face, spec.faceSlots.size, btnSize, gap)
    val maxX: Float = positions.fold(0f) { acc, p -> kotlin.math.max(acc, p.x + btnSize.value) }
    val maxY: Float = positions.fold(0f) { acc, p -> kotlin.math.max(acc, p.y + btnSize.value) }
    val groupW = maxX.dp
    val groupH = maxY.dp
    val originX = areaW * placement.anchorX - groupW / 2
    val originY = areaH * placement.anchorY - groupH / 2

    spec.faceSlots.zip(positions).forEach { (slot, pos) ->
        ButtonChip(
            label = slot.label,
            tint = slot.tint,
            sizeDp = btnSize,
            offsetX = originX + pos.x.dp,
            offsetY = originY + pos.y.dp,
            onPress = {
                fireHaptic()
                retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, slot.androidKeyCode)
            },
            onRelease = {
                retroView.sendKeyEvent(KeyEvent.ACTION_UP, slot.androidKeyCode)
            }
        )
    }
}

@Composable
private fun ShoulderBar(
    retroView: GLRetroView,
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit
) {
    val btnSize = 56.dp * sizeScale * placement.scale
    val gap = 12.dp * sizeScale * placement.scale
    when (spec.shoulders) {
        ShoulderShape.None -> Unit
        ShoulderShape.TopPair -> {
            val padding = 24.dp
            val leftSlot = spec.shoulderSlots.getOrNull(0)
            val rightSlot = spec.shoulderSlots.getOrNull(1)
            if (leftSlot != null) {
                ButtonChip(
                    label = leftSlot.label,
                    tint = null,
                    sizeDp = btnSize,
                    offsetX = padding,
                    offsetY = areaH * placement.anchorY - btnSize / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, leftSlot.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, leftSlot.androidKeyCode) }
                )
            }
            if (rightSlot != null) {
                ButtonChip(
                    label = rightSlot.label,
                    tint = null,
                    sizeDp = btnSize,
                    offsetX = areaW - padding - btnSize,
                    offsetY = areaH * placement.anchorY - btnSize / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, rightSlot.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, rightSlot.androidKeyCode) }
                )
            }
        }
        ShoulderShape.FourCorners -> {
            val padding = 24.dp
            val slots = spec.shoulderSlots
            slots.getOrNull(0)?.let {
                ButtonChip(it.label, null, btnSize, padding, areaH * placement.anchorY - btnSize - gap / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
            slots.getOrNull(1)?.let {
                ButtonChip(it.label, null, btnSize, padding, areaH * placement.anchorY + gap / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
            slots.getOrNull(2)?.let {
                ButtonChip(it.label, null, btnSize, areaW - padding - btnSize, areaH * placement.anchorY - btnSize - gap / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
            slots.getOrNull(3)?.let {
                ButtonChip(it.label, null, btnSize, areaW - padding - btnSize, areaH * placement.anchorY + gap / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
        }
        ShoulderShape.TopPairPlusZ -> {
            val padding = 24.dp
            spec.shoulderSlots.getOrNull(0)?.let {
                ButtonChip(it.label, null, btnSize, padding, areaH * placement.anchorY - btnSize / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
            spec.shoulderSlots.getOrNull(1)?.let {
                ButtonChip(it.label, null, btnSize, areaW - padding - btnSize, areaH * placement.anchorY - btnSize / 2,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
            spec.shoulderSlots.getOrNull(2)?.let {
                ButtonChip(it.label, Color(0xFF6F4FCD), btnSize, areaW - padding - btnSize, areaH * placement.anchorY + btnSize / 2 + gap,
                    onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, it.androidKeyCode) },
                    onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it.androidKeyCode) }
                )
            }
        }
    }
}

@Composable
private fun SystemBar(
    retroView: GLRetroView,
    spec: TouchLayoutSpec,
    placement: GroupPlacement,
    areaW: Dp,
    areaH: Dp,
    sizeScale: Float,
    fireHaptic: () -> Unit
) {
    if (spec.system.isEmpty()) return
    val btnW = 64.dp * sizeScale * placement.scale
    val btnH = 28.dp * sizeScale * placement.scale
    val gap = 12.dp * sizeScale * placement.scale
    val groupW = btnW * spec.system.size + gap * (spec.system.size - 1)
    val originX = areaW * placement.anchorX - groupW / 2
    val originY = areaH * placement.anchorY - btnH / 2

    spec.system.forEachIndexed { i, slot ->
        ButtonChip(
            label = slot.label,
            tint = null,
            sizeDp = btnH,
            widthDp = btnW,
            offsetX = originX + (btnW + gap) * i,
            offsetY = originY,
            onPress = { fireHaptic(); retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, slot.androidKeyCode) },
            onRelease = { retroView.sendKeyEvent(KeyEvent.ACTION_UP, slot.androidKeyCode) }
        )
    }
}

@Composable
private fun ButtonChip(
    label: String,
    tint: Color?,
    sizeDp: Dp,
    offsetX: Dp,
    offsetY: Dp,
    widthDp: Dp = sizeDp,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    val bg = tint ?: Color(0xCC1A1A1A)
    val border = Color(0xFFE6E6E6)
    val measurer = rememberTextMeasurer()
    val minTap = 48.dp
    val drawW = widthDp
    val drawH = sizeDp
    val hitW = if (drawW < minTap) minTap else drawW
    val hitH = if (drawH < minTap) minTap else drawH
    val hitOffX = offsetX - (hitW - drawW) / 2
    val hitOffY = offsetY - (hitH - drawH) / 2
    val drawInsetX = with(density) { ((hitW - drawW) / 2).toPx() }
    val drawInsetY = with(density) { ((hitH - drawH) / 2).toPx() }
    val drawWPx = with(density) { drawW.toPx() }
    val drawHPx = with(density) { drawH.toPx() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    density.run { hitOffX.roundToPx() },
                    density.run { hitOffY.roundToPx() }
                )
            }
            .size(width = hitW, height = hitH)
            .drawBehind {
                val r = kotlin.math.min(drawWPx, drawHPx) / 2f
                val center = Offset(drawInsetX + drawWPx / 2f, drawInsetY + drawHPx / 2f)
                drawCircle(
                    color = if (pressed) bg.copy(alpha = 1f) else bg,
                    radius = r,
                    center = center
                )
                drawCircle(
                    color = border,
                    radius = r - 3f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
                val layout = measurer.measure(
                    text = label,
                    style = TextStyle(
                        color = border,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        center.x - layout.size.width / 2f,
                        center.y - layout.size.height / 2f
                    )
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed && !pressed) {
                            pressed = true
                            onPress()
                        } else if (!anyPressed && pressed) {
                            pressed = false
                            onRelease()
                        }
                    }
                }
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
