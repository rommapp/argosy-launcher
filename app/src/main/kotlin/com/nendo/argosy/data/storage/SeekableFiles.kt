package com.nendo.argosy.data.storage

import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

internal class RandomAccessSeekableFile(
    private val handle: RandomAccessFile
) : SeekableFile {

    override val size: Long get() = handle.length()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        handle.seek(position)
        return handle.read(buffer, offset, length)
    }

    override fun write(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        handle.seek(position)
        handle.write(buffer, offset, length)
    }

    override fun close() = handle.close()

    companion object {
        fun open(file: File, writable: Boolean): SeekableFile? {
            if (!file.exists()) return null
            return runCatching {
                RandomAccessSeekableFile(RandomAccessFile(file, if (writable) "rw" else "r"))
            }.getOrNull()
        }
    }
}

/**
 * Positional access over a descriptor handed out by the storage provider, which is the only
 * route into another app's `/Android/data` on a device that refuses direct file access.
 * `pread` and `pwrite` are used rather than a stream's channel because a descriptor opened
 * "rw" has to serve both, while the stream wrappers each expose only one direction.
 */
internal class DescriptorSeekableFile(
    private val descriptor: ParcelFileDescriptor
) : SeekableFile {

    override val size: Long get() = descriptor.statSize

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val count = try {
            Os.pread(descriptor.fileDescriptor, buffer, offset, length, position)
        } catch (e: android.system.ErrnoException) {
            throw IOException("pread failed at $position", e)
        }
        return if (count == 0) -1 else count
    }

    override fun write(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            val count = try {
                Os.pwrite(
                    descriptor.fileDescriptor,
                    buffer,
                    offset + written,
                    length - written,
                    position + written
                )
            } catch (e: android.system.ErrnoException) {
                throw IOException("pwrite failed at ${position + written}", e)
            }
            if (count <= 0) throw IOException("pwrite stalled at ${position + written}")
            written += count
        }
    }

    override fun close() = descriptor.close()
}
