package com.nendo.argosy.data.storage

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Positional access to one file. A container reader needs a few kilobytes from scattered
 * offsets of a file that runs to gigabytes, which neither [FileAccessLayer.readBytes] nor
 * [FileAccessLayer.getInputStream] can express without reading everything up to the offset.
 */
interface SeekableFile : Closeable {
    val size: Long

    /**
     * Reads up to [length] bytes at [position] and returns how many landed, or -1 at the end
     * of the file. A short read is normal and callers that need exact bytes must loop.
     */
    fun read(position: Long, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int

    /**
     * Overwrites [length] bytes at [position]. Callers patch inside a file that already holds
     * the bytes they are replacing, so this never appends and never truncates.
     */
    fun write(position: Long, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size)
}

data class FileInfo(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val size: Long,
    val lastModified: Long
) {
    val extension: String get() = name.substringAfterLast('.', "")
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val absolutePath: String get() = path
    val parent: String? get() = path.substringBeforeLast('/').takeIf { it != path && it.isNotEmpty() }
}

interface FileAccessLayer {

    // Query
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun isFile(path: String): Boolean
    fun length(path: String): Long
    fun lastModified(path: String): Long
    fun canRead(path: String): Boolean
    fun canWrite(path: String): Boolean

    // List
    fun listFiles(path: String): List<FileInfo>?

    // Union of alt-access, managed-access, and direct listings -- for Android/data paths
    // where per-UID mount views can return incomplete subsets.
    fun listFilesUnion(path: String): List<FileInfo>

    // Create
    fun mkdirs(path: String): Boolean

    // Delete
    fun delete(path: String): Boolean
    fun deleteRecursively(path: String): Boolean

    // Read/Write
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, data: ByteArray): Boolean
    fun getInputStream(path: String): InputStream?
    fun getOutputStream(path: String): OutputStream?

    /**
     * Opens [path] for positional access, trying the same tiers as [readBytes]. Returns null
     * when no tier can reach it, which for a restricted path is the device refusing rather
     * than the file being absent.
     */
    fun openSeekable(path: String, writable: Boolean): SeekableFile?

    // Copy
    fun copyFile(source: String, dest: String): Boolean
    fun copyDirectory(source: String, dest: String): Boolean

    // Walk
    fun walk(path: String): Sequence<FileInfo>

    // Utilities
    fun isRestrictedPath(path: String): Boolean
    fun normalizeForDisplay(path: String): String
    fun externalStorageRoots(): List<String>

    // Escape hatch for third-party APIs requiring File
    fun getTransformedFile(path: String): File
}
