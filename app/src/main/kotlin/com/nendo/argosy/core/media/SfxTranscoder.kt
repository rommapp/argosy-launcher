package com.nendo.argosy.core.media

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

private const val TAG = "SfxTranscoder"
private const val CACHE_DIR = "sfx"
private const val MAX_SECONDS = 10
private const val BYTES_PER_SAMPLE = 2
private const val TIMEOUT_US = 10_000L
private const val MAX_DRY_SPINS = 200
private const val CACHE_VERSION = "v2"
private const val TARGET_PEAK = 13045

object SfxTranscoder {

    /** Decodes the first audio track of sourcePath to a cached peak-normalized PCM 16-bit WAV; null on failure. */
    fun transcodeToWavCache(context: Context, sourcePath: String): File? {
        val source = File(sourcePath)
        if (!source.exists()) {
            Log.w(TAG, "Source missing: $sourcePath")
            return null
        }
        val cacheRoot = File(context.cacheDir, CACHE_DIR)
        if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
            Log.w(TAG, "Cannot create cache dir: $cacheRoot")
            return null
        }
        val pathHash = sha1(sourcePath)
        val target = File(cacheRoot, "$pathHash-${sha1("${source.lastModified()}|${source.length()}|$CACHE_VERSION")}.wav")
        if (target.exists()) return target
        cacheRoot.listFiles { file -> file.name.startsWith("$pathHash-") }?.forEach { it.delete() }
        return try {
            decodeToWav(source, target)
        } catch (e: Exception) {
            Log.w(TAG, "Transcode failed for $sourcePath", e)
            target.delete()
            null
        }
    }

    private fun decodeToWav(source: File, target: File): File? {
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
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var drySpins = 0
            while (!outputDone && drySpins < MAX_DRY_SPINS) {
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
                        val maxBytes = sampleRate * channels * BYTES_PER_SAMPLE * MAX_SECONDS
                        val remaining = maxBytes - pcm.size()
                        if (info.size > 0 && remaining > 0) {
                            val outputBuffer = codec.getOutputBuffer(outIndex) ?: return null
                            val chunk = ByteArray(minOf(info.size, remaining))
                            outputBuffer.position(info.offset)
                            outputBuffer.get(chunk)
                            pcm.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || pcm.size() >= maxBytes) {
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
            if (pcm.size() == 0) {
                Log.w(TAG, "Decoded zero PCM bytes from ${source.path}")
                return null
            }
            writeWav(target, normalizePeak(pcm.toByteArray()), sampleRate, channels)
            return target
        } finally {
            codec?.let { active ->
                runCatching { active.stop() }
                runCatching { active.release() }
            }
            extractor.release()
        }
    }

    private fun normalizePeak(pcm: ByteArray): ByteArray {
        var peak = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
            i += 2
        }
        if (peak == 0 || peak == TARGET_PEAK) return pcm
        val scale = TARGET_PEAK.toFloat() / peak
        i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            val scaled = (sample * scale).toInt().coerceIn(-32768, 32767)
            pcm[i] = (scaled and 0xFF).toByte()
            pcm[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
        return pcm
    }

    private fun writeWav(target: File, pcm: ByteArray, sampleRate: Int, channels: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * BYTES_PER_SAMPLE)
        header.putShort((channels * BYTES_PER_SAMPLE).toShort())
        header.putShort((BYTES_PER_SAMPLE * 8).toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        target.outputStream().use { stream ->
            stream.write(header.array())
            stream.write(pcm)
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
