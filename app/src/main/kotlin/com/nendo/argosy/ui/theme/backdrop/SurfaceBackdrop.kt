package com.nendo.argosy.ui.theme.backdrop

import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.BackdropMotion
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.isActive
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect

/** Alpha-cap role of the surface the backdrop paints: CONTENT sits under menus, WALLPAPER under the companion screen. */
enum class BackdropRole { CONTENT, WALLPAPER }

val LocalSurfaceBackdrop = staticCompositionLocalOf { BackdropConfig() }

private const val TWO_PI = 2f * PI.toFloat()
private const val SWAY_X_CYCLES_PER_LOOP = 8f
private const val SWAY_Y_CYCLES_PER_LOOP = 13f
private const val SWAY_Y_PHASE = PI.toFloat() / 3f
private const val MILLIS_PER_SECOND = 1000f
private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f
private const val MOTION_SPEED_BASE = 100f
private const val NANOS_TO_SECONDS = 1e-9f
private const val DRIFT_AXIS_EPSILON = 0.001f

private class BackdropLayer(val shader: Shader, val brush: ShaderBrush, val tileSizePx: Float)

/** Paints colorScheme.background then the tiled lattice above it, tinted with focusAccent so the pattern tracks the accent hue family; disabled is byte-identical to a plain background. */
@Composable
fun Modifier.surfaceBackdrop(role: BackdropRole): Modifier {
    val base = MaterialTheme.colorScheme.background
    val config = LocalSurfaceBackdrop.current
    if (!config.enabled) return background(base)
    val tint = LocalArgosyTheme.current.focusAccent
    val cellSizePx = with(LocalDensity.current) {
        (config.cellSize * LocalUiScale.current.scale).dp.toPx()
    }
    val cap = when (role) {
        BackdropRole.CONTENT -> ComponentDefaults.SurfaceBackdrop.contentMaxAlpha
        BackdropRole.WALLPAPER -> ComponentDefaults.SurfaceBackdrop.wallpaperMaxAlpha
    }
    val alpha = ((config.strength / 100f) * cap).coerceIn(0f, cap)
    val stamps = rememberBackdropStamps(config, tint, alpha, cellSizePx) ?: return background(base)
    val motionActive = config.motion != BackdropMotion.OFF && !reducedMotion()
    val layer = remember(config, tint, cellSizePx, alpha, motionActive, stamps) {
        val tile = renderBackdropTile(config, tint, alpha, cellSizePx, stamps)
        val shader = ImageShader(tile, TileMode.Repeated, TileMode.Repeated)
        BackdropLayer(shader, ShaderBrush(shader), tile.width.toFloat())
    }
    if (!motionActive) return background(base).drawBehind { drawRect(layer.brush) }
    val matrix = remember(layer) { Matrix() }
    val speedMultiplier = config.motionSpeed / MOTION_SPEED_BASE
    return when (config.motion) {
        BackdropMotion.DRIFT -> {
            val angleRad = config.driftAngle * DEGREES_TO_RADIANS
            val speed = rememberUpdatedState(
                Offset(
                    sin(angleRad) * ComponentDefaults.SurfaceBackdrop.driftCellsPerSecondRatio * cellSizePx * speedMultiplier,
                    -cos(angleRad) * ComponentDefaults.SurfaceBackdrop.driftCellsPerSecondRatio * cellSizePx * speedMultiplier
                )
            )
            val offset = integrateDriftOffset(speed, layer.tileSizePx)
            background(base).drawBehind {
                matrix.setTranslate(offset.value.x, offset.value.y)
                layer.shader.setLocalMatrix(matrix)
                drawRect(layer.brush)
            }
        }
        BackdropMotion.SWAY -> {
            val swayAmplitudePx = ComponentDefaults.SurfaceBackdrop.swayAmplitudeCellRatio * cellSizePx
            val phaseVelocity = rememberUpdatedState(
                speedMultiplier / (ComponentDefaults.SurfaceBackdrop.swayPeriodSeconds * SWAY_X_CYCLES_PER_LOOP)
            )
            val phase = integratePhase(phaseVelocity)
            background(base).drawBehind {
                val t = phase.value
                matrix.setTranslate(
                    swayAmplitudePx * sin(TWO_PI * t * SWAY_X_CYCLES_PER_LOOP),
                    swayAmplitudePx * sin(TWO_PI * t * SWAY_Y_CYCLES_PER_LOOP + SWAY_Y_PHASE)
                )
                layer.shader.setLocalMatrix(matrix)
                drawRect(layer.brush)
            }
        }
        BackdropMotion.OFF -> background(base).drawBehind { drawRect(layer.brush) }
    }
}

@Composable
private fun rememberBackdropStamps(
    config: BackdropConfig,
    tint: Color,
    alpha: Float,
    cellSizePx: Float
): List<ImageBitmap>? {
    if (config.preset.cellShape != BackdropCellShape.PLATFORM_STAMPS) return emptyList()
    val provider = LocalBackdropStamps.current ?: return null
    val uris = provider.stampUris.collectAsState().value
    val stampSizePx = (cellSizePx * ComponentDefaults.SurfaceBackdrop.stampCellRatio)
        .roundToInt()
        .coerceAtLeast(1)
    return produceState<List<ImageBitmap>?>(null, provider, uris, stampSizePx, tint, alpha) {
        value = provider.loadStamps(uris, stampSizePx, tint, alpha)
    }.value
}

@Composable
private fun integrateDriftOffset(speed: State<Offset>, tileSizePx: Float): State<Offset> {
    val offset = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(tileSizePx) {
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (last > 0) {
                    val dt = (now - last) * NANOS_TO_SECONDS
                    offset.value = Offset(
                        (offset.value.x + speed.value.x * dt).mod(tileSizePx),
                        (offset.value.y + speed.value.y * dt).mod(tileSizePx),
                    )
                }
                last = now
            }
        }
    }
    return offset
}

@Composable
private fun integratePhase(velocity: State<Float>): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (last > 0) phase.floatValue += velocity.value * ((now - last) * NANOS_TO_SECONDS)
                last = now
            }
        }
    }
    return phase
}

