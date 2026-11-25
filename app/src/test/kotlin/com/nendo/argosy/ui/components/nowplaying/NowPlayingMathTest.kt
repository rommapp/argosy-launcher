package com.nendo.argosy.ui.components.nowplaying

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NowPlayingMathTest {

    private val epsilon = 1e-4f

    @Test
    fun `upright from 350 turns forward 10 instead of back 350`() {
        assertEquals(10f, uprightDelta(350f), epsilon)
    }

    @Test
    fun `upright from 10 turns back 10`() {
        assertEquals(-10f, uprightDelta(10f), epsilon)
    }

    @Test
    fun `upright at exactly half a turn goes back half a turn`() {
        assertEquals(-180f, uprightDelta(180f), epsilon)
    }

    @Test
    fun `upright just past half a turn goes forward`() {
        assertEquals(179f, uprightDelta(181f), epsilon)
    }

    @Test
    fun `upright normalizes accumulated and negative angles`() {
        assertEquals(10f, uprightDelta(720f + 350f), epsilon)
        assertEquals(10f, uprightDelta(-10f), epsilon)
        assertEquals(0f, uprightDelta(0f), epsilon)
        assertEquals(0f, uprightDelta(1080f), epsilon)
    }

    @Test
    fun `upright never turns more than half a revolution`() {
        var angle = -1000f
        while (angle < 1000f) {
            val delta = uprightDelta(angle)
            assertTrue("delta $delta for $angle", delta >= -180f && delta < 180f)
            assertEquals(0f, (angle + delta).mod(360f).let { if (it > 359.99f) 0f else it }, 1e-2f)
            angle += 7.3f
        }
    }

    @Test
    fun `upright duration maps zero to min and half turn to max`() {
        assertEquals(300, uprightDurationMs(0f, 300, 600))
        assertEquals(600, uprightDurationMs(180f, 300, 600))
        assertEquals(600, uprightDurationMs(-180f, 300, 600))
        assertEquals(450, uprightDurationMs(90f, 300, 600))
    }

    @Test
    fun `upright duration grows with angle and stays in range`() {
        var previous = uprightDurationMs(0f, 300, 600)
        for (degrees in 1..180) {
            val duration = uprightDurationMs(degrees.toFloat(), 300, 600)
            assertTrue(duration in 300..600)
            assertTrue(duration >= previous)
            previous = duration
        }
    }

    @Test
    fun `pinch envelope is zero at the thumb`() {
        assertEquals(0f, pinchEnvelope(0f, 40f), epsilon)
    }

    @Test
    fun `pinch envelope is one at and beyond the radius`() {
        assertEquals(1f, pinchEnvelope(40f, 40f), epsilon)
        assertEquals(1f, pinchEnvelope(400f, 40f), epsilon)
        assertEquals(1f, pinchEnvelope(-400f, 40f), epsilon)
    }

    @Test
    fun `pinch envelope is symmetric and monotonic with distance`() {
        var previous = pinchEnvelope(0f, 40f)
        var distance = 0.5f
        while (distance <= 60f) {
            val value = pinchEnvelope(distance, 40f)
            assertTrue("envelope dropped at $distance", value >= previous)
            assertEquals(value, pinchEnvelope(-distance, 40f), epsilon)
            previous = value
            distance += 0.5f
        }
    }

    @Test
    fun `wave is flat at the thumb whatever the phase`() {
        for (step in 0..20) {
            val offset = pinchedWaveOffset(
                x = 120f,
                thumbX = 120f,
                amplitude = 10f,
                pinchRadius = 50f,
                wavelength = 12f,
                phase = step * 0.3f
            )
            assertEquals(0f, offset, epsilon)
        }
    }

    @Test
    fun `envelope scales the loudest column to one and stays silent past the end`() {
        val peaks = shortArrayOf(
            TrackAnalysis.quantize(-0.2f), TrackAnalysis.quantize(0.1f),
            TrackAnalysis.quantize(-0.1f), TrackAnalysis.quantize(0.4f)
        )
        val analysis = TrackAnalysis(TrackAnalysis.BLOCK_FRAMES, 2L * TrackAnalysis.BLOCK_FRAMES, peaks, ByteArray(2 * TrackAnalysis.BAND_COUNT))
        val envelope = waveEnvelope(analysis, durationMs = 4000L, columns = 4)
        assertArrayEquals(floatArrayOf(0.5f, 1f, 0f, 0f), envelope, 1e-3f)
        assertNull(waveEnvelope(analysis, durationMs = 0L, columns = 4))
        assertNull(waveEnvelope(analysis, durationMs = 4000L, columns = 0))
    }

    @Test
    fun `loudness follows the slice under the playhead above the floor`() {
        val envelope = floatArrayOf(0f, 0.5f, 1f, 0.25f)
        assertEquals(0.4f, loudnessAt(envelope, 0f, floor = 0.4f), epsilon)
        assertEquals(0.7f, loudnessAt(envelope, 0.3f, floor = 0.4f), epsilon)
        assertEquals(1f, loudnessAt(envelope, 0.6f, floor = 0.4f), epsilon)
        assertEquals(0.55f, loudnessAt(envelope, 1f, floor = 0.4f), epsilon)
        assertEquals(1f, loudnessAt(null, 0.5f, floor = 0.4f), epsilon)
        assertEquals(1f, loudnessAt(FloatArray(0), 0.5f, floor = 0.4f), epsilon)
    }

    @Test
    fun `settled levels jump up and fall at the given rate`() {
        val shown = floatArrayOf(0.2f, 0.9f)
        settleLevels(shown, floatArrayOf(0.6f, 0f), fallPerSecond = 2f, elapsedSeconds = 0.25f)
        assertEquals(0.6f, shown[0], epsilon)
        assertEquals(0.4f, shown[1], epsilon)
        settleLevels(shown, floatArrayOf(0f, 0f), fallPerSecond = 2f, elapsedSeconds = 1f)
        assertEquals(0f, shown[0], epsilon)
        assertEquals(0f, shown[1], epsilon)
    }

    @Test
    fun `interpolated position advances with frame time and stops at the duration`() {
        assertEquals(1_250L, interpolatedPositionMs(1_000L, 250_000_000L, 10_000L))
        assertEquals(10_000L, interpolatedPositionMs(9_900L, 500_000_000L, 10_000L))
        assertEquals(1_000L, interpolatedPositionMs(1_000L, -5L, 10_000L))
    }

    @Test
    fun `wave never exceeds its amplitude`() {
        var x = 0f
        while (x < 800f) {
            val offset = pinchedWaveOffset(x, 400f, 10f, 50f, 12f, 1.1f)
            assertTrue(abs(offset) <= 10f + epsilon)
            x += 1f
        }
    }
}
