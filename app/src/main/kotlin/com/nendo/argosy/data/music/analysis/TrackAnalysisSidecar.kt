package com.nendo.argosy.data.music.analysis

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object TrackAnalysisSidecar {
    const val MAGIC = 0x314E4154
    const val VERSION = 1
    const val HEADER_BYTES = 28
    const val PARTIAL_SUFFIX = ".partial"

    private const val PEAK_BYTES_PER_BLOCK = 4
    private const val MAX_BLOCKS = 4_000_000L

    fun write(analysis: TrackAnalysis, sourceKey: Long, sidecar: File): Boolean {
        val dir = sidecar.parentFile ?: return false
        dir.mkdirs()
        val partial = File(dir, "${sidecar.name}$PARTIAL_SUFFIX")
        return try {
            val buffer = ByteBuffer.allocate(HEADER_BYTES + analysis.peaks.size * 2 + analysis.bands.size)
                .order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(MAGIC)
                .putInt(VERSION)
                .putInt(analysis.sampleRate)
                .putLong(analysis.frames)
                .putLong(sourceKey)
            analysis.peaks.forEach { buffer.putShort(it) }
            buffer.put(analysis.bands)
            partial.writeBytes(buffer.array())
            if (partial.renameTo(sidecar)) {
                true
            } else {
                partial.delete()
                false
            }
        } catch (e: IOException) {
            partial.delete()
            false
        }
    }

    fun read(sidecar: File, sourceKey: Long): TrackAnalysis? {
        if (!sidecar.isFile) return null
        return try {
            val bytes = sidecar.readBytes()
            if (bytes.size < HEADER_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.getInt() != MAGIC || buffer.getInt() != VERSION) return null
            val sampleRate = buffer.getInt()
            val frames = buffer.getLong()
            val key = buffer.getLong()
            if (sampleRate <= 0 || frames < 0L || key != sourceKey) return null
            val blocks = (frames + TrackAnalysis.BLOCK_FRAMES - 1) / TrackAnalysis.BLOCK_FRAMES
            if (blocks > MAX_BLOCKS) return null
            val expected = HEADER_BYTES + blocks * (PEAK_BYTES_PER_BLOCK + TrackAnalysis.BAND_COUNT)
            if (bytes.size.toLong() != expected) return null
            val peaks = ShortArray(blocks.toInt() * 2)
            buffer.asShortBuffer().get(peaks)
            buffer.position(buffer.position() + peaks.size * 2)
            val bands = ByteArray(blocks.toInt() * TrackAnalysis.BAND_COUNT)
            buffer.get(bands)
            TrackAnalysis(sampleRate, frames, peaks, bands)
        } catch (e: IOException) {
            null
        }
    }
}
