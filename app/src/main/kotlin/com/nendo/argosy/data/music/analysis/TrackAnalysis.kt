package com.nendo.argosy.data.music.analysis

import kotlin.math.roundToInt

/**
 * The decoded shape of one track, mixed to mono: the lowest and highest sample of every
 * [BLOCK_FRAMES] frames, and the level of [BAND_COUNT] log-spaced frequency bands over the same
 * blocks. [frames] counts the frames analysed so far, so a progressive snapshot is shorter than
 * the finished track. Instances never change after construction.
 */
class TrackAnalysis internal constructor(
    val sampleRate: Int,
    val frames: Long,
    internal val peaks: ShortArray,
    internal val bands: ByteArray
) {
    val blocks: Int get() = peaks.size / 2

    val durationMs: Long get() = if (sampleRate > 0) frames * MS_PER_SECOND / sampleRate else 0L

    /**
     * Splits the span [fromMs] until [toMs] into [mins].size equal columns and writes the lowest
     * and highest sample in each, from -1 to 1. A column narrower than a block shows the whole
     * block it lies in; a column past the analysed end shows silence.
     */
    fun columns(fromMs: Long, toMs: Long, mins: FloatArray, maxs: FloatArray) {
        require(mins.size == maxs.size)
        val count = mins.size
        if (count == 0) return
        val from = frameAt(fromMs)
        val span = (frameAt(toMs) - from).coerceAtLeast(0L)
        for (column in 0 until count) {
            val start = from + span * column / count
            val end = maxOf(start + 1, from + span * (column + 1) / count)
            val first = (start / BLOCK_FRAMES).coerceAtLeast(0L)
            val last = ((end - 1) / BLOCK_FRAMES).coerceAtMost(blocks - 1L)
            if (first > last) {
                mins[column] = 0f
                maxs[column] = 0f
                continue
            }
            var low = Short.MAX_VALUE.toInt()
            var high = Short.MIN_VALUE.toInt()
            for (block in first..last) {
                val at = (block * 2).toInt()
                low = minOf(low, peaks[at].toInt())
                high = maxOf(high, peaks[at + 1].toInt())
            }
            mins[column] = low / PCM_16_MAX
            maxs[column] = high / PCM_16_MAX
        }
    }

    fun bandsAt(positionMs: Long, out: FloatArray) {
        require(out.size == BAND_COUNT)
        val block = if (positionMs < 0L) -1L else frameAt(positionMs) / BLOCK_FRAMES
        if (block < 0L || block >= blocks) {
            out.fill(0f)
            return
        }
        val base = block.toInt() * BAND_COUNT
        for (band in 0 until BAND_COUNT) {
            out[band] = (bands[base + band].toInt() and BYTE_MASK) / LEVEL_MAX
        }
    }

    private fun frameAt(ms: Long): Long = ms * sampleRate / MS_PER_SECOND

    companion object {
        const val BLOCK_FRAMES = 2048
        const val BAND_COUNT = 16

        private const val MS_PER_SECOND = 1000L
        private const val PCM_16_MAX = 32767f
        private const val LEVEL_MAX = 255f
        private const val BYTE_MASK = 0xFF

        internal fun quantize(sample: Float): Short =
            (sample.coerceIn(-1f, 1f) * PCM_16_MAX).roundToInt().toShort()
    }
}
