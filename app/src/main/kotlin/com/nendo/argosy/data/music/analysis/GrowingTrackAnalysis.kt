package com.nendo.argosy.data.music.analysis

/**
 * The [TrackAnalysis] of audio still being decoded: interleaved float frames of [channels]
 * channels go in as they arrive, and [snapshot] returns the analysis of every frame so far in
 * the same blocks a finished decode holds, the last block possibly short.
 */
class GrowingTrackAnalysis(val sampleRate: Int, val channels: Int) {
    private val analyzer = BandAnalyzer(sampleRate)
    private val pending = FloatArray(TrackAnalysis.BLOCK_FRAMES)
    private var pendingCount = 0
    private var completeBlocks = 0
    private var frames = 0L
    private var peaks = ShortArray(INITIAL_BLOCKS * 2)
    private var bands = ByteArray(INITIAL_BLOCKS * TrackAnalysis.BAND_COUNT)

    init {
        require(sampleRate > 0 && channels > 0)
    }

    fun add(samples: FloatArray, frameCount: Int) {
        for (frame in 0 until frameCount) {
            var sum = 0f
            val base = frame * channels
            for (channel in 0 until channels) sum += samples[base + channel]
            pending[pendingCount++] = sum / channels
            frames++
            if (pendingCount == TrackAnalysis.BLOCK_FRAMES) {
                ensureCapacity(completeBlocks + 1)
                writeBlock(completeBlocks, peaks, bands)
                completeBlocks++
                pendingCount = 0
            }
        }
    }

    fun snapshot(): TrackAnalysis {
        val total = completeBlocks + if (pendingCount > 0) 1 else 0
        val peakCopy = peaks.copyOf(total * 2)
        val bandCopy = bands.copyOf(total * TrackAnalysis.BAND_COUNT)
        if (pendingCount > 0) writeBlock(completeBlocks, peakCopy, bandCopy)
        return TrackAnalysis(sampleRate, frames, peakCopy, bandCopy)
    }

    private fun writeBlock(block: Int, peakOut: ShortArray, bandOut: ByteArray) {
        var low = 1f
        var high = -1f
        for (i in 0 until pendingCount) {
            val sample = pending[i]
            if (sample < low) low = sample
            if (sample > high) high = sample
        }
        peakOut[block * 2] = TrackAnalysis.quantize(low)
        peakOut[block * 2 + 1] = TrackAnalysis.quantize(high)
        analyzer.analyze(pending, pendingCount, bandOut, block * TrackAnalysis.BAND_COUNT)
    }

    private fun ensureCapacity(blocks: Int) {
        if (blocks * 2 <= peaks.size) return
        val grown = maxOf(blocks, peaks.size)
        peaks = peaks.copyOf(grown * 2)
        bands = bands.copyOf(grown * TrackAnalysis.BAND_COUNT)
    }

    private companion object {
        const val INITIAL_BLOCKS = 1024
    }
}
