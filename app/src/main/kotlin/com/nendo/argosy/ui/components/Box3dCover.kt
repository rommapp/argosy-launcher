package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private const val REST_YAW_DEG = 24f
private const val SPIN_PERIOD_MS = 16000
private const val SAFE_TILT_DEG = 30f
private const val MAX_PITCH_DEG = 75f
private const val DRAG_DEGREES_PER_PX = 0.35f
private const val DEFAULT_SPINE_RATIO = 0.12f
private const val MAX_SPINE_RATIO = 0.3f
private const val CAMERA_DISTANCE = 3.2f
private const val EDGE_TINT = 0xFF241F1C.toInt()
private const val BACK_FALLBACK_SHADE = 150

private data class BoxFaces(
    val front: Bitmap,
    val spine: Bitmap,
    val back: Bitmap?
) {
    val frontRatio: Float get() = front.width.toFloat() / front.height
    val spineRatio: Float get() = (spine.width.toFloat() / spine.height).coerceAtMost(MAX_SPINE_RATIO)
}

/** Interactive 3D box from flat scans: idle yaw sway, drag to rotate, settles to a safe angle. */
@Composable
fun Box3dCover(
    frontPath: String,
    spinePath: String,
    backPath: String? = null,
    modifier: Modifier = Modifier
) {
    var faces by remember(frontPath, spinePath, backPath) { mutableStateOf<BoxFaces?>(null) }
    androidx.compose.runtime.LaunchedEffect(frontPath, spinePath, backPath) {
        faces = withContext(Dispatchers.IO) {
            val front = decode(frontPath)
            val spine = decode(spinePath)?.let(::uprightSpine)
            if (front == null || spine == null) {
                Logger.warn("Box3dCover", "decode failed front=${front != null} spine=${spine != null} frontPath=$frontPath spinePath=$spinePath")
                null
            } else {
                BoxFaces(front, spine, backPath?.let { decode(it) })
            }
        }
    }

    val yaw = remember { Animatable(REST_YAW_DEG) }
    val pitch = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(isDragging, faces) {
        if (isDragging || faces == null) return@LaunchedEffect
        while (true) {
            yaw.animateTo(yaw.value + 360f, tween(SPIN_PERIOD_MS, easing = LinearEasing))
        }
    }

    val loaded = faces ?: return
    val compositeRatio = loaded.frontRatio + loaded.spineRatio

    Canvas(
        modifier = modifier
            .aspectRatio(compositeRatio)
            .pointerInput(loaded) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            yaw.snapTo(yaw.value + dragAmount.x * DRAG_DEGREES_PER_PX)
                            pitch.snapTo(
                                (pitch.value - dragAmount.y * DRAG_DEGREES_PER_PX)
                                    .coerceIn(-MAX_PITCH_DEG, MAX_PITCH_DEG)
                            )
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            yaw.snapTo(normalizeDegrees(yaw.value))
                            pitch.animateTo(
                                pitch.value.coerceIn(-SAFE_TILT_DEG, SAFE_TILT_DEG),
                                spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.8f)
                            )
                            isDragging = false
                        }
                    },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        drawBox(loaded, yaw.value, pitch.value)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBox(
    faces: BoxFaces,
    yawDeg: Float,
    pitchDeg: Float
) {
    val canvas = drawContext.canvas.nativeCanvas
    val h = size.height * 0.94f
    val w = h * faces.frontRatio
    val d = h * faces.spineRatio
    val cx = size.width / 2f
    val cy = size.height / 2f
    val camera = h * CAMERA_DISTANCE

    val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
    val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
    val sy = sin(yawRad); val cyw = cos(yawRad)
    val sp = sin(pitchRad); val cp = cos(pitchRad)

    fun project(x: Float, y: Float, z: Float): FloatArray {
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y2 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val scale = camera / (camera - z2)
        return floatArrayOf(cx + x1 * scale, cy + y2 * scale, z2)
    }

    val hw = w / 2f; val hh = h / 2f; val hd = d / 2f
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(80, 0, 0, 0)
        maskFilter = BlurMaskFilter(h * 0.035f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawRect(cx - hw * 0.9f, cy + hh * 0.94f, cx + hw * 0.9f, cy + hh * 1.0f, shadowPaint)

    data class Face(
        val corners: Array<FloatArray>,
        val bitmap: Bitmap?,
        val mirrored: Boolean,
        val shade: Int,
        val rotated: Boolean = false
    )

    val facesToDraw = listOf(
        Face(
            arrayOf(project(-hw, -hh, hd), project(hw, -hh, hd), project(hw, hh, hd), project(-hw, hh, hd)),
            faces.front, mirrored = false, shade = 0
        ),
        Face(
            arrayOf(project(hw, -hh, -hd), project(-hw, -hh, -hd), project(-hw, hh, -hd), project(hw, hh, -hd)),
            faces.back ?: faces.front, mirrored = faces.back == null,
            shade = if (faces.back == null) BACK_FALLBACK_SHADE else 0
        ),
        Face(
            arrayOf(project(-hw, -hh, -hd), project(-hw, -hh, hd), project(-hw, hh, hd), project(-hw, hh, -hd)),
            faces.spine, mirrored = false, shade = 60
        ),
        Face(
            arrayOf(project(hw, -hh, hd), project(hw, -hh, -hd), project(hw, hh, -hd), project(hw, hh, hd)),
            faces.spine, mirrored = true, shade = 60
        ),
        Face(
            arrayOf(project(-hw, -hh, -hd), project(hw, -hh, -hd), project(hw, -hh, hd), project(-hw, -hh, hd)),
            faces.spine, mirrored = false, shade = 40, rotated = true
        ),
        Face(
            arrayOf(project(-hw, hh, hd), project(hw, hh, hd), project(hw, hh, -hd), project(-hw, hh, -hd)),
            faces.spine, mirrored = false, shade = 90, rotated = true
        )
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    facesToDraw
        .filter { isFrontFacing(it.corners) }
        .sortedBy { it.corners.minOf { c -> c[2] } }
        .forEach { face ->
            val dst = floatArrayOf(
                face.corners[0][0], face.corners[0][1],
                face.corners[1][0], face.corners[1][1],
                face.corners[2][0], face.corners[2][1],
                face.corners[3][0], face.corners[3][1]
            )
            if (face.bitmap != null) {
                drawBitmapFace(canvas, paint, face.bitmap, dst, face.mirrored, face.rotated)
                if (face.shade > 0) fillQuad(canvas, dst, android.graphics.Color.argb(face.shade, 0, 0, 0))
            } else {
                fillQuad(canvas, dst, EDGE_TINT)
            }
        }
}

private fun isFrontFacing(corners: Array<FloatArray>): Boolean {
    val ax = corners[1][0] - corners[0][0]
    val ay = corners[1][1] - corners[0][1]
    val bx = corners[3][0] - corners[0][0]
    val by = corners[3][1] - corners[0][1]
    return ax * by - ay * bx > 0
}

private fun drawBitmapFace(
    canvas: android.graphics.Canvas,
    paint: Paint,
    bitmap: Bitmap,
    dst: FloatArray,
    mirrored: Boolean,
    rotated: Boolean = false
) {
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val src = when {
        rotated -> floatArrayOf(0f, bh, 0f, 0f, bw, 0f, bw, bh)
        mirrored -> floatArrayOf(bw, 0f, 0f, 0f, 0f, bh, bw, bh)
        else -> floatArrayOf(0f, 0f, bw, 0f, bw, bh, 0f, bh)
    }
    val matrix = Matrix()
    if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
    canvas.save()
    canvas.concat(matrix)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    canvas.restore()
}

private fun fillQuad(canvas: android.graphics.Canvas, dst: FloatArray, color: Int) {
    val path = Path().apply {
        moveTo(dst[0], dst[1])
        lineTo(dst[2], dst[3])
        lineTo(dst[4], dst[5])
        lineTo(dst[6], dst[7])
        close()
    }
    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

private fun normalizeDegrees(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun decode(path: String): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return BitmapFactory.decodeFile(path)
}

/** ScreenScraper side scans arrive portrait for some systems and landscape for others; the box geometry assumes portrait. */
private fun uprightSpine(spine: Bitmap): Bitmap {
    if (spine.height >= spine.width) return spine
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(spine, 0, 0, spine.width, spine.height, matrix, true)
}
