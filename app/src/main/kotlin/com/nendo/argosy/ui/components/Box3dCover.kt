package com.nendo.argosy.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
private const val SOURCE_RECHECK_MS = 5000L

private data class BoxFaces(
    val front: Bitmap,
    val spine: Bitmap,
    val back: Bitmap?
) {
    val frontRatio: Float get() = front.width.toFloat() / front.height
    val spineRatio: Float get() = (spine.width.toFloat() / spine.height).coerceAtMost(MAX_SPINE_RATIO)
}

private val loggedDecodeFailures = java.util.Collections.newSetFromMap(
    java.util.concurrent.ConcurrentHashMap<String, Boolean>()
)

/**
 * Interactive 3D box from flat scans: idle yaw sway, drag to rotate, settles to a safe angle.
 * Scans decode at the drawn height through Coil's memory cache. When the front or spine scan
 * cannot be decoded, calls [onUnavailable], draws [fallback], and decodes again once either
 * file changes on disk. The idle spin runs only while [isInteractive] and the host is started.
 */
@Composable
fun Box3dCover(
    frontPath: String,
    spinePath: String,
    backPath: String? = null,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    onUnavailable: () -> Unit = {},
    fallback: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val screenHeightPx = context.resources.displayMetrics.heightPixels
    var faces by remember(frontPath, spinePath, backPath) { mutableStateOf<BoxFaces?>(null) }
    var decodeFailed by remember(frontPath, spinePath, backPath) { mutableStateOf(false) }
    val reportUnavailable by rememberUpdatedState(onUnavailable)
    val isStarted = rememberIsStarted()

    val yaw = remember(frontPath, spinePath, backPath) { Animatable(REST_YAW_DEG) }
    val pitch = remember(frontPath, spinePath, backPath) { Animatable(0f) }
    var isDragging by remember(frontPath, spinePath, backPath) { mutableStateOf(false) }

    val reduced = reducedMotion()
    LaunchedEffect(isDragging, faces, isInteractive, isStarted, reduced) {
        if (reduced || !isInteractive || !isStarted || isDragging || faces == null) return@LaunchedEffect
        while (true) {
            yaw.animateTo(yaw.value + 360f, tween(SPIN_PERIOD_MS, easing = LinearEasing))
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val targetPx = (if (constraints.hasBoundedHeight) constraints.maxHeight else screenHeightPx)
            .coerceAtLeast(1)

        LaunchedEffect(frontPath, spinePath, backPath, targetPx, isStarted) {
            if (!isStarted && faces != null) return@LaunchedEffect
            var attemptedStamp: String? = null
            while (true) {
                val stamp = withContext(Dispatchers.IO) { sourceStamp(frontPath, spinePath) }
                if (stamp != attemptedStamp) {
                    attemptedStamp = stamp
                    val decoded = loadFaces(context, frontPath, spinePath, backPath, targetPx)
                    if (decoded != null) {
                        faces = decoded
                        decodeFailed = false
                        return@LaunchedEffect
                    }
                    faces = null
                    decodeFailed = true
                    reportUnavailable()
                }
                if (!isStarted) return@LaunchedEffect
                delay(SOURCE_RECHECK_MS)
            }
        }

        val loaded = faces
        when {
            loaded != null -> BoxCanvas(loaded, isInteractive, yaw, pitch) { isDragging = it }
            decodeFailed -> fallback()
        }
    }
}

@Composable
private fun rememberIsStarted(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isStarted
}

@Composable
private fun BoxCanvas(
    loaded: BoxFaces,
    isInteractive: Boolean,
    yaw: Animatable<Float, AnimationVector1D>,
    pitch: Animatable<Float, AnimationVector1D>,
    onDraggingChange: (Boolean) -> Unit
) {
    val compositeRatio = loaded.frontRatio + loaded.spineRatio

    if (!isInteractive) {
        Canvas(modifier = Modifier.aspectRatio(compositeRatio)) {
            drawBox(loaded, REST_YAW_DEG, 0f)
        }
        return
    }

    val scope = rememberCoroutineScope()
    Canvas(
        modifier = Modifier
            .aspectRatio(compositeRatio)
            .pointerInput(loaded) {
                detectDragGestures(
                    onDragStart = { onDraggingChange(true) },
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
                            onDraggingChange(false)
                        }
                    },
                    onDragCancel = { onDraggingChange(false) }
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

private fun sourceStamp(frontPath: String, spinePath: String): String =
    listOf(File(frontPath), File(spinePath)).joinToString("|") { "${it.lastModified()}:${it.length()}" }

private suspend fun loadFaces(
    context: Context,
    frontPath: String,
    spinePath: String,
    backPath: String?,
    targetPx: Int
): BoxFaces? = withContext(Dispatchers.IO) {
    val faceSize = Size(Dimension.Undefined, Dimension(targetPx))
    val front = loadBitmap(context, frontPath, faceSize)
    val spine = loadBitmap(context, spinePath, Size(targetPx, targetPx))?.let(::uprightSpine)
    if (front == null || spine == null) {
        if (loggedDecodeFailures.add("$frontPath|$spinePath")) {
            Logger.warn("Box3dCover", "decode failed front=${front != null} spine=${spine != null} frontPath=$frontPath spinePath=$spinePath")
        }
        return@withContext null
    }
    BoxFaces(front, spine, backPath?.let { loadBitmap(context, it, faceSize) })
}

private suspend fun loadBitmap(context: Context, path: String, size: Size): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(File(path))
        .size(size)
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return (result.drawable as? BitmapDrawable)?.bitmap
}

/** ScreenScraper side scans arrive portrait for some systems and landscape for others; the box geometry assumes portrait. */
private fun uprightSpine(spine: Bitmap): Bitmap {
    if (spine.height >= spine.width) return spine
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(spine, 0, 0, spine.width, spine.height, matrix, true)
}
