package com.nendo.argosy.data.music.analysis

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import com.nendo.argosy.domain.model.MusicTrackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

interface TrackPcmSink {
    fun onFormat(sampleRate: Int, channels: Int): Boolean

    fun onFrames(samples: FloatArray, frameCount: Int)
}

interface TrackPcmDecoder {
    /**
     * Decodes [source] into [sink] until the audio ends. False when no codec on this device reads
     * it or the source cannot be opened. Throws CancellationException when the caller is cancelled.
     */
    suspend fun decode(source: MusicTrackSource, sink: TrackPcmSink): Boolean
}

@Singleton
class TrackDecoder @Inject constructor() : TrackPcmDecoder {

    override suspend fun decode(source: MusicTrackSource, sink: TrackPcmSink): Boolean =
        withContext(Dispatchers.IO) {
            val tid = Process.myTid()
            val previousPriority = Process.getThreadPriority(tid)
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                stream(source, sink)
            } finally {
                Process.setThreadPriority(previousPriority)
            }
        }

    private suspend fun stream(source: MusicTrackSource, sink: TrackPcmSink): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            when (source) {
                is MusicTrackSource.Local -> extractor.setDataSource(source.path)
                is MusicTrackSource.Remote -> extractor.setDataSource(source.url, source.headers)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                mimeOf(extractor.getTrackFormat(it))?.startsWith(AUDIO_MIME_PREFIX) == true
            } ?: return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = mimeOf(format) ?: return false
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            val started = MediaCodec.createDecoderByType(mime)
            codec = started
            started.configure(format, null, null, 0)
            started.start()
            drain(extractor, started, sink)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: IllegalStateException) {
            false
        } finally {
            codec?.let { active ->
                runCatching { active.stop() }
                runCatching { active.release() }
            }
            extractor.release()
        }
    }

    private suspend fun drain(extractor: MediaExtractor, codec: MediaCodec, sink: TrackPcmSink): Boolean {
        val info = MediaCodec.BufferInfo()
        val converter = PcmConverter()
        var inputDone = false
        var idle = 0
        while (idle < MAX_IDLE_POLLS) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) inputDone = feed(extractor, codec)
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!converter.configure(codec.outputFormat, sink)) return false
                }
                index >= 0 -> {
                    idle = 0
                    if (info.size > 0) {
                        if (!converter.configured && !converter.configure(codec.outputFormat, sink)) return false
                        val block = codec.getOutputBuffer(index) ?: return false
                        block.position(info.offset)
                        block.limit(info.offset + info.size)
                        converter.emit(block, sink)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return converter.configured
                }
                inputDone -> idle++
            }
        }
        return false
    }

    private fun feed(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return false
        val buffer = codec.getInputBuffer(index) ?: return false
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    private fun mimeOf(format: MediaFormat): String? = format.getString(MediaFormat.KEY_MIME)

    private class PcmConverter {
        var configured = false
            private set
        private var channels = 0
        private var encoding = AudioFormat.ENCODING_PCM_16BIT
        private var frames = FloatArray(0)

        fun configure(format: MediaFormat, sink: TrackPcmSink): Boolean {
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val count = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            if (rate <= 0 || count <= 0 || bytesPer(pcm) == 0) return false
            if (configured && count == channels && pcm == encoding) return true
            if (configured || !sink.onFormat(rate, count)) return false
            channels = count
            encoding = pcm
            configured = true
            return true
        }

        fun emit(block: ByteBuffer, sink: TrackPcmSink) {
            val bytes = bytesPer(encoding)
            val data = block.slice().order(ByteOrder.LITTLE_ENDIAN)
            val samples = data.remaining() / bytes
            val frameCount = samples / channels
            if (frameCount == 0) return
            if (frames.size < frameCount * channels) frames = FloatArray(frameCount * channels)
            for (i in 0 until frameCount * channels) {
                val at = i * bytes
                frames[i] = when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> data.getFloat(at)
                    ENCODING_PCM_24BIT_PACKED -> {
                        val value = (data.get(at).toInt() and BYTE_MASK) or
                            ((data.get(at + 1).toInt() and BYTE_MASK) shl 8) or
                            (data.get(at + 2).toInt() shl 16)
                        value / PCM_24_SCALE
                    }
                    else -> data.getShort(at) / PCM_16_SCALE
                }
            }
            sink.onFrames(frames, frameCount)
        }

        private fun bytesPer(pcm: Int): Int = when (pcm) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }
    }

    private companion object {
        const val AUDIO_MIME_PREFIX = "audio/"
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_POLLS = 200
        const val ENCODING_PCM_24BIT_PACKED = 21
        const val BYTE_MASK = 0xFF
        const val PCM_16_SCALE = 32768f
        const val PCM_24_SCALE = 8388608f
    }
}
