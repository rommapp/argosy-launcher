package com.nendo.argosy.ui.components.nowplaying

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private const val NANOS_PER_MS = 1_000_000L
private const val FULL_TURN_DEGREES = 360f
private const val HALF_TURN_DEGREES = 180f
private const val WAVE_BASE_WEIGHT = 0.75f
private const val WAVE_MODULATION_WEIGHT = 0.25f
private const val WAVE_MODULATION_PERIOD_RATIO = 2.85f

fun smoothstep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

/**
 * Amplitude multiplier for a wave point [distance] pixels from the thumb: 0 at the thumb, rising
 * smoothly to 1 at [pinchRadius] and staying 1 beyond it.
 */
fun pinchEnvelope(distance: Float, pinchRadius: Float): Float {
    if (pinchRadius <= 0f) return 1f
    return smoothstep(minOf(1f, abs(distance) / pinchRadius))
}

fun pinchedWaveOffset(
    x: Float,
    thumbX: Float,
    amplitude: Float,
    pinchRadius: Float,
    wavelength: Float,
    phase: Float
): Float {
    if (wavelength <= 0f) return 0f
    val envelope = amplitude * pinchEnvelope(x - thumbX, pinchRadius)
    val modulation = WAVE_BASE_WEIGHT +
        WAVE_MODULATION_WEIGHT * sin(x / (wavelength * WAVE_MODULATION_PERIOD_RATIO))
    return envelope * sin(x / wavelength + phase) * modulation
}

/**
 * The signed rotation, in degrees, that brings [angle] back to upright by the shorter way round.
 * The result lies in [-180, 180), so a disc never turns more than half a revolution.
 */
fun uprightDelta(angle: Float): Float {
    val normalized = angle.mod(FULL_TURN_DEGREES)
    return if (normalized <= HALF_TURN_DEGREES) -normalized else FULL_TURN_DEGREES - normalized
}

fun uprightDurationMs(delta: Float, minMs: Int, maxMs: Int): Int {
    val fraction = (abs(delta) / HALF_TURN_DEGREES).coerceIn(0f, 1f)
    return (minMs + (maxMs - minMs) * fraction).roundToInt()
}

fun degreesPerMs(rpm: Float): Float = rpm * FULL_TURN_DEGREES / 60_000f

/**
 * The loudness of [analysis] in [columns] equal slices of 0 until [durationMs], each the larger
 * of its lowest and highest sample's magnitude, scaled so the loudest slice is 1. Null without a
 * duration or columns.
 */
fun waveEnvelope(analysis: TrackAnalysis, durationMs: Long, columns: Int): FloatArray? {
    if (durationMs <= 0L || columns <= 0) return null
    val mins = FloatArray(columns)
    val maxs = FloatArray(columns)
    analysis.columns(0L, durationMs, mins, maxs)
    val envelope = FloatArray(columns) { maxOf(abs(mins[it]), abs(maxs[it])) }
    val peak = envelope.max()
    if (peak > 0f) {
        for (i in envelope.indices) envelope[i] /= peak
    }
    return envelope
}

/**
 * The wave height multiplier at [progress] through [envelope]: the slice's loudness lifted so the
 * quietest slice still gives [floor]. 1 without an envelope.
 */
fun loudnessAt(envelope: FloatArray?, progress: Float, floor: Float): Float {
    if (envelope == null || envelope.isEmpty()) return 1f
    val index = (progress.coerceIn(0f, 1f) * envelope.size).toInt().coerceAtMost(envelope.lastIndex)
    return floor + (1f - floor) * envelope[index].coerceIn(0f, 1f)
}

/**
 * Moves each [shown] level toward [fresh]: a higher level jumps there, a lower one falls at
 * [fallPerSecond].
 */
fun settleLevels(shown: FloatArray, fresh: FloatArray, fallPerSecond: Float, elapsedSeconds: Float) {
    val fall = fallPerSecond * elapsedSeconds.coerceAtLeast(0f)
    for (i in shown.indices) shown[i] = maxOf(fresh[i], shown[i] - fall).coerceIn(0f, 1f)
}

/**
 * The height of equaliser bar [index] of [count] as a fraction of the glyph, between [minFraction]
 * and 1. Bars share one sine at [phase] in 0..1, offset evenly so no two rise together.
 */
fun equaliserBarFraction(index: Int, count: Int, phase: Float, minFraction: Float): Float {
    if (count <= 0) return minFraction
    val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * (phase + index.toFloat() / count))
    return minFraction + (1f - minFraction) * wave
}

fun marqueeDurationMs(distancePx: Float, pxPerSecond: Float): Int =
    if (distancePx <= 0f || pxPerSecond <= 0f) 0 else (distancePx / pxPerSecond * 1000f).roundToInt()

fun interpolatedPositionMs(sampledMs: Long, elapsedNanos: Long, durationMs: Long): Long {
    val advanced = sampledMs + elapsedNanos.coerceAtLeast(0L) / NANOS_PER_MS
    return if (durationMs > 0L) advanced.coerceAtMost(durationMs) else advanced
}
