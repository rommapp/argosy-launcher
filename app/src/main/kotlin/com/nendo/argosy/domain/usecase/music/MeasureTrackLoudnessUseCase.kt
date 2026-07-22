package com.nendo.argosy.domain.usecase.music

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.nendo.argosy.data.music.AudioLoudnessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.log10

private const val TAG = "MeasureTrackLoudness"
private const val MAX_MEASURE_SECONDS = 90
private const val BYTES_PER_SAMPLE = 2
private const val TIMEOUT_US = 10_000L
private const val MAX_DRY_SPINS = 200
private const val FULL_SCALE_SQUARE = 32768.0 * 32768.0
private const val SILENCE_FLOOR_DB = -60.0

/**
 * Measures a local track's RMS loudness in dBFS over the first 90 seconds of decoded audio
 * (a representative window for looping BGM rips at a fraction of the decode cost), caches the
 * result keyed by file identity, and returns the cached value without decoding when the file
 * is unchanged. Returns null for silence-only content or decode failure.
 */
class MeasureTrackLoudnessUseCase @Inject constructor(
    private val loudnessRepository: AudioLoudnessRepository
) {
    suspend operator fun invoke(filePath: String): Double? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.isFile) return@withContext null
        loudnessRepository.validatedMeanDb(filePath)?.let { return@withContext it }
        val fileKey = loudnessRepository.fileKeyFor(file)
        val meanDb = try {
            decodeMeanDb(file)
        } catch (e: Exception) {
            Log.w(TAG, "Loudness measurement failed for $filePath", e)
            null
        }
        if (meanDb == null || meanDb < SILENCE_FLOOR_DB) {
            if (meanDb != null) Log.d(TAG, "Skipping silence-only track: $filePath")
            return@withContext null
        }
        loudnessRepository.store(filePath, fileKey, meanDb)
        meanDb
    }

    private suspend fun decodeMeanDb(source: File): Double? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            if (trackIndex == null) {
                Log.w(TAG, "No audio track in ${source.path}")
                return null
            }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sumSquares = 0.0
            var sampleCount = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var drySpins = 0
            while (!outputDone && drySpins < MAX_DRY_SPINS && coroutineContext.isActive) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex) ?: return null
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        drySpins = 0
                    }
                    outIndex >= 0 -> {
                        val maxSamples = sampleRate.toLong() * channels * MAX_MEASURE_SECONDS
                        val remaining = maxSamples - sampleCount
                        if (info.size >= BYTES_PER_SAMPLE && remaining > 0) {
                            val outputBuffer = codec.getOutputBuffer(outIndex) ?: return null
                            val shorts = outputBuffer.duplicate()
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .also {
                                    it.position(info.offset)
                                    it.limit(info.offset + info.size)
                                }
                                .asShortBuffer()
                            var take = minOf(shorts.remaining().toLong(), remaining).toInt()
                            while (take > 0) {
                                val sample = shorts.get().toDouble()
                                sumSquares += sample * sample
                                sampleCount++
                                take--
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || sampleCount >= maxSamples) {
                            outputDone = true
                        }
                        drySpins = 0
                    }
                    else -> drySpins++
                }
            }
            if (!outputDone) {
                Log.w(TAG, "Decoder stalled on ${source.path}")
                return null
            }
            if (sampleCount == 0L || sumSquares <= 0.0) return null
            return 10.0 * log10(sumSquares / sampleCount / FULL_SCALE_SQUARE)
        } finally {
            codec?.let { active ->
                runCatching { active.stop() }
                runCatching { active.release() }
            }
            extractor.release()
        }
    }
}
