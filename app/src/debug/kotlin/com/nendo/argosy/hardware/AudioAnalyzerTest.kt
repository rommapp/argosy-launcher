package com.nendo.argosy.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Test class for audio analysis via Visualizer API.
 *
 * Requirements:
 * - RECORD_AUDIO permission
 * - Audio session ID 0 captures mix output (requires MODIFY_AUDIO_SETTINGS on some devices)
 */
object AudioAnalyzerTest {
    private const val TAG = "AudioAnalyzerTest"

    fun runDiagnostics(context: Context): String {
        val results = StringBuilder()

        // Check permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        results.appendLine("RECORD_AUDIO permission: $hasPermission")

        if (!hasPermission) {
            results.appendLine("Cannot proceed without RECORD_AUDIO permission")
            return results.toString()
        }

        // Try to create Visualizer on session 0 (mix output)
        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(0) // 0 = mix output
            results.appendLine("Visualizer created successfully on session 0")

            // Get capabilities
            val captureRange = Visualizer.getCaptureSizeRange()
            results.appendLine("Capture size range: ${captureRange[0]} - ${captureRange[1]}")

            val maxRate = Visualizer.getMaxCaptureRate()
            results.appendLine("Max capture rate: $maxRate milliHz (${maxRate / 1000} Hz)")

            // Set capture size to max for better frequency resolution
            val captureSize = captureRange[1].coerceAtMost(1024)
            visualizer.captureSize = captureSize
            results.appendLine("Set capture size: $captureSize")

            // Get sampling rate
            val samplingRate = visualizer.samplingRate
            results.appendLine("Sampling rate: $samplingRate Hz")

            // Calculate frequency resolution
            val freqResolution = samplingRate.toFloat() / captureSize
            results.appendLine("Frequency resolution: $freqResolution Hz per bin")

            // Calculate bin for 800Hz threshold
            val bin800Hz = (800 / freqResolution).toInt()
            results.appendLine("800Hz is approximately bin #$bin800Hz")

            // Enable and try to capture
            visualizer.enabled = true
            results.appendLine("Visualizer enabled: ${visualizer.enabled}")

            // Try multiple captures to catch audio
            var maxRms = 0f
            var captureCount = 0
            val waveform = ByteArray(captureSize)

            repeat(10) {
                Thread.sleep(50)
                val waveStatus = visualizer.getWaveForm(waveform)
                if (waveStatus == Visualizer.SUCCESS) {
                    val rms = computeRMS(waveform)
                    if (rms > maxRms) maxRms = rms
                    captureCount++
                }
            }
            results.appendLine("Captured $captureCount waveforms over 500ms")
            results.appendLine("Max waveform RMS: ${"%.2f".format(maxRms)}")

            // Try FFT on last capture
            val fftData = ByteArray(captureSize)
            val fftStatus = visualizer.getFft(fftData)
            results.appendLine("FFT capture status: $fftStatus (0 = SUCCESS)")

            if (fftStatus == Visualizer.SUCCESS) {
                val magnitudes = computeMagnitudes(fftData)
                val nonZero = magnitudes.count { it > 0 }
                val maxMag = magnitudes.maxOrNull() ?: 0f
                results.appendLine("Non-zero FFT bins: $nonZero / ${magnitudes.size}")
                results.appendLine("Max FFT magnitude: ${"%.2f".format(maxMag)}")
            }

        } catch (e: Exception) {
            results.appendLine("Error: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "Visualizer test failed", e)
        } finally {
            visualizer?.release()
        }

        return results.toString()
    }

    private fun computeMagnitudes(fftData: ByteArray): List<Float> {
        // FFT data format: [DC.real, DC.imag, f1.real, f1.imag, f2.real, f2.imag, ...]
        // But for Visualizer, it's: [real[0], imag[0], real[1], imag[1], ...]
        val magnitudes = mutableListOf<Float>()
        for (i in 0 until fftData.size step 2) {
            val real = fftData[i].toFloat()
            val imag = if (i + 1 < fftData.size) fftData[i + 1].toFloat() else 0f
            val magnitude = sqrt(real * real + imag * imag)
            magnitudes.add(magnitude)
        }
        return magnitudes
    }

    private fun computeRMS(waveform: ByteArray): Float {
        var sum = 0.0
        for (sample in waveform) {
            val normalized = (sample.toInt() and 0xFF) - 128
            sum += normalized * normalized
        }
        return sqrt(sum / waveform.size).toFloat()
    }
}
