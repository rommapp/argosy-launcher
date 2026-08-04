package com.nendo.argosy.hardware

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.sqrt

data class AudioLevels(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
    val output: Float = 0f
)

class AudioReactiveLEDTest(
    private val context: Context,
    private val ledController: OdinLEDController
) {
    private var visualizer: Visualizer? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val minBrightness = 0.05f
    private val maxBrightness = 1.0f

    // Smoothed band values (squared energy)
    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedHigh = 0f

    // Asymmetric smoothing with separate decay rates
    private val alphaRise = 0.92f
    private val alphaDecaySmooth = 0.85f  // slow decay for bass/mid (smooth fadeout)
    private val alphaDecayFast = 0.5f     // fast decay for highs (quick drop after peaks)

    private fun smoothBassOrMid(current: Float, newValue: Float): Float {
        val alpha = if (newValue > current) alphaRise else alphaDecaySmooth
        return alpha * current + (1 - alpha) * newValue
    }

    private fun smoothHigh(current: Float, newValue: Float): Float {
        val alpha = if (newValue > current) alphaRise else alphaDecayFast
        return alpha * current + (1 - alpha) * newValue
    }

    fun start() {
        if (!ledController.isAvailable) {
            Log.e(TAG, "LED controller not available")
            return
        }

        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                enabled = true
            }
            val samplingRate = visualizer?.samplingRate ?: 0
            Log.i(TAG, "Visualizer started, capture size: ${visualizer?.captureSize}, sampling rate: $samplingRate Hz")

            smoothedBass = 0f
            smoothedMid = 0f
            smoothedHigh = 0f

            job = scope.launch {
                val captureSize = visualizer?.captureSize ?: 1024
                val fftData = ByteArray(captureSize)

                // Use actual sampling rate for accurate bin calculations
                // Note: getSamplingRate() returns milliHz, divide by 1000
                val actualSampleRate = (visualizer?.samplingRate ?: 48000000) / 1000f
                val binWidth = actualSampleRate / captureSize
                Log.i(TAG, "Bin width: $binWidth Hz (sample rate: $actualSampleRate Hz)")
                val bassStartBin = (60 / binWidth).toInt()    // Skip sub-bass (<60Hz)
                val bassEndBin = (250 / binWidth).toInt()     // Bass: 60-250 Hz
                val midEndBin = (1000 / binWidth).toInt()     // Mid: 250Hz-1kHz
                val highEndBin = (8000 / binWidth).toInt()    // High: 1-8kHz (expanded down)

                Log.i(TAG, "FFT bins: BASS $bassStartBin-$bassEndBin, MID $bassEndBin-$midEndBin, HIGH $midEndBin-$highEndBin")

                while (isActive) {
                    val status = visualizer?.getFft(fftData)
                    if (status == Visualizer.SUCCESS) {
                        // Get magnitudes and SQUARE for dynamic range expansion
                        val bassMag = computeBandMagnitude(fftData, bassStartBin, bassEndBin).pow(2)
                        val midMag = computeBandMagnitude(fftData, bassEndBin, midEndBin).pow(2)
                        val highMag = computeBandMagnitude(fftData, midEndBin, highEndBin).pow(2)

                        // Slow smoothing for bass and mid (smooth fadeout)
                        smoothedBass = smoothBassOrMid(smoothedBass, bassMag)
                        smoothedMid = smoothBassOrMid(smoothedMid, midMag)

                        // HIGH: no smoothing - use raw value directly for instant response
                        val rawNormHigh = (highMag / 15f).coerceIn(0f, 1f)
                        val isDramaticPeak = rawNormHigh > 0.8f
                        smoothedHigh = highMag  // no smoothing for highs

                        // Normalize
                        val normBass = (smoothedBass / 800f).coerceIn(0f, 1f)
                        val normMid = (smoothedMid / 2000f).coerceIn(0f, 1f)
                        val normHigh = (smoothedHigh / 15f).coerceIn(0f, 1f)

                        // BASS: sets floor (10-25%)
                        val floor = minBrightness + (normBass * 0.15f)

                        // MID: max impact 70%, can only peak above if raw volume >80%
                        val rawMidVolume = (midMag / 2000f).coerceIn(0f, 1f)
                        val midCeiling = if (rawMidVolume > 0.8f) 0.85f else 0.70f
                        val midContrib = (normMid * 0.60f).coerceAtMost(midCeiling - floor)

                        // HIGH: matches mid up to 50%, gradual above, dramatic peaks override
                        val highContrib = if (isDramaticPeak) {
                            (rawNormHigh - 0.5f) * 0.40f
                        } else if (normHigh > 0.5f) {
                            (normHigh - 0.5f) * 0.25f
                        } else {
                            0f
                        }

                        val brightness = (floor + midContrib + highContrib)
                            .coerceIn(minBrightness, maxBrightness)

                        ledController.setBrightness(brightness)
                        updateLevels(AudioLevels(normBass, normMid, normHigh, brightness))

                        if (System.currentTimeMillis() % 500 < 50) {
                            val peakMarker = if (isDramaticPeak) "!" else ""
                            // Log raw magnitudes before squaring too
                            val rawHighMag = computeBandMagnitude(fftData, midEndBin, highEndBin)
                            Log.d(TAG, "B:${"%.0f".format(normBass*100)} M:${"%.0f".format(normMid*100)} H:${"%.0f".format(normHigh*100)}$peakMarker -> ${"%.0f".format(brightness*100)}% [rawH:${"%.2f".format(rawHighMag)} sq:${"%.2f".format(highMag)}]")
                        }
                    }
                    delay(50) // 20 Hz update rate
                }
            }
            Log.i(TAG, "Audio reactive LED test started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio reactive test", e)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        ledController.setBrightness(1.0f)
        Log.i(TAG, "Audio reactive LED test stopped")
    }

    private fun computeBandMagnitude(fftData: ByteArray, startBin: Int, endBin: Int): Float {
        var sum = 0f
        var count = 0
        for (bin in startBin until endBin) {
            val realIdx = bin * 2
            val imagIdx = realIdx + 1
            if (imagIdx < fftData.size) {
                val real = fftData[realIdx].toFloat()
                val imag = fftData[imagIdx].toFloat()
                val magnitude = sqrt(real * real + imag * imag)
                sum += magnitude
                count++
            }
        }
        return if (count > 0) sum / count else 0f
    }

    companion object {
        private const val TAG = "AudioReactiveLED"

        @Volatile
        private var instance: AudioReactiveLEDTest? = null

        private val _levels = MutableStateFlow(AudioLevels())
        val levels: StateFlow<AudioLevels> = _levels.asStateFlow()

        fun toggle(context: Context): Boolean {
            return if (instance != null) {
                instance?.stop()
                instance = null
                _levels.value = AudioLevels()
                Log.i(TAG, "Stopped")
                false
            } else {
                instance = AudioReactiveLEDTest(context.applicationContext, OdinLEDController())
                    .apply { start() }
                Log.i(TAG, "Started")
                true
            }
        }

        fun isRunning() = instance != null

        internal fun updateLevels(levels: AudioLevels) {
            _levels.value = levels
        }
    }
}
