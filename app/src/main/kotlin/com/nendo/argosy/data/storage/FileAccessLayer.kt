package com.nendo.argosy.data.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream

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

    // Copy
    fun copyFile(source: String, dest: String): Boolean
    fun copyDirectory(source: String, dest: String): Boolean

    // Walk
    fun walk(path: String): Sequence<FileInfo>

    // Utilities
    fun isRestrictedPath(path: String): Boolean
    fun normalizeForDisplay(path: String): String

    // Escape hatch for third-party APIs requiring File
    fun getTransformedFile(path: String): File
}
