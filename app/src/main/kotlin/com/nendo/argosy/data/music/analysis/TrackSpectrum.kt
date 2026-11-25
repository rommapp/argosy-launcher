package com.nendo.argosy.data.music.analysis

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object TrackSpectrum {
    const val FFT_SIZE = TrackAnalysis.BLOCK_FRAMES
    const val MIN_HZ = 40f
    const val MAX_HZ = 16_000f
    const val FLOOR_DB = -78f
    const val TOP_DB = -12f

    private const val NYQUIST_MARGIN = 0.95f
    private const val LEVEL_MAX = 255

    fun hann(n: Int): FloatArray = FloatArray(n) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / n)).toFloat() }

    fun topHz(sampleRate: Int): Float = minOf(MAX_HZ, sampleRate / 2f * NYQUIST_MARGIN)

    fun bandEdgeHz(edge: Int, sampleRate: Int): Float {
        val top = topHz(sampleRate)
        return MIN_HZ * (top / MIN_HZ).pow(edge.toFloat() / TrackAnalysis.BAND_COUNT)
    }

    fun bandOf(hz: Float, sampleRate: Int): Int {
        val top = topHz(sampleRate)
        val position = ln(hz / MIN_HZ) / ln(top / MIN_HZ) * TrackAnalysis.BAND_COUNT
        return floor(position).toInt().coerceIn(0, TrackAnalysis.BAND_COUNT - 1)
    }

    fun db(amplitude: Float): Float =
        if (amplitude <= 0f) FLOOR_DB else (20f * log10(amplitude)).coerceAtLeast(FLOOR_DB)

    fun level(amplitude: Float): Byte {
        val fraction = ((db(amplitude) - FLOOR_DB) / (TOP_DB - FLOOR_DB)).coerceIn(0f, 1f)
        return (fraction * LEVEL_MAX).roundToInt().toByte()
    }

    fun fft(re: FloatArray, im: FloatArray, cosTable: FloatArray, sinTable: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var length = 2
        while (length <= n) {
            val half = length / 2
            val stride = n / length
            for (start in 0 until n step length) {
                for (k in 0 until half) {
                    val wr = cosTable[k * stride]
                    val wi = sinTable[k * stride]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
            }
            length = length shl 1
        }
    }
}

internal class BandAnalyzer(sampleRate: Int) {
    private val size = TrackSpectrum.FFT_SIZE
    private val window = TrackSpectrum.hann(size)
    private val cosTable = FloatArray(size / 2) { k -> cos(-2.0 * PI * k / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { k -> sin(-2.0 * PI * k / size).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val amplitudes = FloatArray(size / 2)
    private val hzPerBin = sampleRate.toFloat() / size
    private val bandFrom = FloatArray(TrackAnalysis.BAND_COUNT) { TrackSpectrum.bandEdgeHz(it, sampleRate) / hzPerBin }
    private val bandTo = FloatArray(TrackAnalysis.BAND_COUNT) { TrackSpectrum.bandEdgeHz(it + 1, sampleRate) / hzPerBin }

    fun analyze(samples: FloatArray, count: Int, out: ByteArray, offset: Int) {
        for (i in 0 until size) {
            re[i] = if (i < count) samples[i] * window[i] else 0f
            im[i] = 0f
        }
        TrackSpectrum.fft(re, im, cosTable, sinTable)
        val scale = AMPLITUDE_SCALE / size
        for (k in amplitudes.indices) amplitudes[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
        val bins = amplitudes.size
        for (band in 0 until TrackAnalysis.BAND_COUNT) {
            val from = bandFrom[band]
            val to = bandTo[band]
            val first = ceil(from).toInt()
            val last = minOf(floor(to).toInt(), bins - 1)
            val amplitude = if (last >= first) {
                var loudest = 0f
                for (bin in first..last) if (amplitudes[bin] > loudest) loudest = amplitudes[bin]
                loudest
            } else {
                val at = (from + to) / 2f
                val lower = floor(at).toInt().coerceIn(0, bins - 1)
                val upper = (lower + 1).coerceAtMost(bins - 1)
                val t = (at - lower).coerceIn(0f, 1f)
                amplitudes[lower] + (amplitudes[upper] - amplitudes[lower]) * t
            }
            out[offset + band] = TrackSpectrum.level(amplitude)
        }
    }

    private companion object {
        const val AMPLITUDE_SCALE = 4f
    }
}
