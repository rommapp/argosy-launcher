package com.nendo.argosy.data.music.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class TrackAnalysisTest {

    private val epsilon = 1e-4f
    private val blockRate = TrackAnalysis.BLOCK_FRAMES

    private fun threeBlockAnalysis(): TrackAnalysis {
        val peaks = shortArrayOf(
            TrackAnalysis.quantize(-0.5f), TrackAnalysis.quantize(0.5f),
            TrackAnalysis.quantize(-0.25f), TrackAnalysis.quantize(1f),
            TrackAnalysis.quantize(-1f), TrackAnalysis.quantize(0.1f)
        )
        val bands = ByteArray(3 * TrackAnalysis.BAND_COUNT) { index ->
            val block = index / TrackAnalysis.BAND_COUNT
            val band = index % TrackAnalysis.BAND_COUNT
            (block * 60 + band * 10).toByte()
        }
        return TrackAnalysis(blockRate, 3L * TrackAnalysis.BLOCK_FRAMES, peaks, bands)
    }

    @Test
    fun `one column per block reads each block`() {
        val analysis = threeBlockAnalysis()
        val mins = FloatArray(3)
        val maxs = FloatArray(3)
        analysis.columns(0L, 3000L, mins, maxs)
        assertArrayEquals(floatArrayOf(-0.5f, -0.25f, -1f), mins, epsilon)
        assertArrayEquals(floatArrayOf(0.5f, 1f, 0.1f), maxs, epsilon)
    }

    @Test
    fun `a wide column aggregates every block inside it`() {
        val analysis = threeBlockAnalysis()
        val mins = FloatArray(1)
        val maxs = FloatArray(1)
        analysis.columns(0L, 3000L, mins, maxs)
        assertEquals(-1f, mins[0], epsilon)
        assertEquals(1f, maxs[0], epsilon)
    }

    @Test
    fun `columns past the analysed end are silent`() {
        val analysis = threeBlockAnalysis()
        val mins = FloatArray(2)
        val maxs = FloatArray(2)
        analysis.columns(0L, 6000L, mins, maxs)
        assertEquals(-1f, mins[0], epsilon)
        assertEquals(1f, maxs[0], epsilon)
        assertEquals(0f, mins[1], epsilon)
        assertEquals(0f, maxs[1], epsilon)
    }

    @Test
    fun `duration follows frames and sample rate`() {
        assertEquals(3000L, threeBlockAnalysis().durationMs)
    }

    @Test
    fun `bands at a position read the block it falls in`() {
        val analysis = threeBlockAnalysis()
        val out = FloatArray(TrackAnalysis.BAND_COUNT)
        analysis.bandsAt(1500L, out)
        for (band in 0 until TrackAnalysis.BAND_COUNT) {
            assertEquals((60 + band * 10) / 255f, out[band], epsilon)
        }
        analysis.bandsAt(0L, out)
        assertEquals(0f, out[0], epsilon)
        assertEquals(150 / 255f, out[15], epsilon)
    }

    @Test
    fun `bands outside the analysed span are zero`() {
        val analysis = threeBlockAnalysis()
        val out = FloatArray(TrackAnalysis.BAND_COUNT) { 1f }
        analysis.bandsAt(3000L, out)
        out.forEach { assertEquals(0f, it, epsilon) }
        out.fill(1f)
        analysis.bandsAt(-10L, out)
        out.forEach { assertEquals(0f, it, epsilon) }
    }

    @Test
    fun `growing snapshot equals a whole build of the same audio`() {
        val sampleRate = 44_100
        val channels = 2
        val frames = TrackAnalysis.BLOCK_FRAMES * 3 + 500
        val samples = FloatArray(frames * channels) { i ->
            val frame = i / channels
            val hz = if (i % channels == 0) 220.0 else 3_300.0
            (0.8 * sin(2.0 * PI * hz * frame / sampleRate)).toFloat()
        }
        val growing = GrowingTrackAnalysis(sampleRate, channels)
        val chunk = 777
        var at = 0
        while (at < frames) {
            val count = minOf(chunk, frames - at)
            growing.add(samples.copyOfRange(at * channels, (at + count) * channels), count)
            at += count
        }
        val snapshot = growing.snapshot()
        val reference = wholeBuild(sampleRate, channels, samples, frames)
        assertEquals(reference.frames, snapshot.frames)
        assertEquals(reference.sampleRate, snapshot.sampleRate)
        assertArrayEquals(reference.peaks, snapshot.peaks)
        assertArrayEquals(reference.bands, snapshot.bands)
    }

    @Test
    fun `a pure sine is loudest in the band holding its frequency`() {
        val sampleRate = 44_100
        val hz = 1_000f
        val block = FloatArray(TrackAnalysis.BLOCK_FRAMES) { i ->
            sin(2.0 * PI * hz * i / sampleRate).toFloat()
        }
        val out = ByteArray(TrackAnalysis.BAND_COUNT)
        BandAnalyzer(sampleRate).analyze(block, block.size, out, 0)
        val levels = out.map { it.toInt() and 0xFF }
        val loudest = levels.indices.maxByOrNull { levels[it] }
        assertEquals(TrackSpectrum.bandOf(hz, sampleRate), loudest)
        assertEquals(255, levels[loudest!!])
        assertEquals(8, loudest)
    }

    private fun wholeBuild(sampleRate: Int, channels: Int, samples: FloatArray, frames: Int): TrackAnalysis {
        val mono = FloatArray(frames) { frame ->
            var sum = 0f
            for (channel in 0 until channels) sum += samples[frame * channels + channel]
            sum / channels
        }
        val blocks = (frames + TrackAnalysis.BLOCK_FRAMES - 1) / TrackAnalysis.BLOCK_FRAMES
        val peaks = ShortArray(blocks * 2)
        val bands = ByteArray(blocks * TrackAnalysis.BAND_COUNT)
        val analyzer = BandAnalyzer(sampleRate)
        for (block in 0 until blocks) {
            val start = block * TrackAnalysis.BLOCK_FRAMES
            val slice = mono.copyOfRange(start, minOf(frames, start + TrackAnalysis.BLOCK_FRAMES))
            peaks[block * 2] = TrackAnalysis.quantize(slice.min())
            peaks[block * 2 + 1] = TrackAnalysis.quantize(slice.max())
            analyzer.analyze(slice, slice.size, bands, block * TrackAnalysis.BAND_COUNT)
        }
        return TrackAnalysis(sampleRate, frames.toLong(), peaks, bands)
    }
}
