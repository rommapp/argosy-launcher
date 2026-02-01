package com.nendo.argosy.data.storage

import android.content.Context
import android.os.Environment
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileAccessLayerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val androidDataAccessor: AndroidDataAccessor,
    private val managedStorageAccessor: ManagedStorageAccessor
) : FileAccessLayer {

    companion object {
        private const val TAG = "FileAccessLayer"
    }

    override fun exists(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            val result = androidDataAccessor.exists(path)
            if (result) {
                Logger.verbose(TAG) { "[AltAccess] exists=true | path=$path" }
                return true
            }
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return File(path).exists()
            if (managedStorageAccessor.existsAtPath(volumeId, relativePath)) {
                Logger.verbose(TAG) { "[ManagedAccess] exists=true | path=$path" }
                return true
            }
        }

        return File(path).exists()
    }

    override fun isDirectory(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.isDirectory(path)
        }
        return File(path).isDirectory
    }

    override fun isFile(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.isFile(path)
        }
        return File(path).isFile
    }

    override fun length(path: String): Long {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.length(path)
        }
        return File(path).length()
    }

    override fun lastModified(path: String): Long {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.lastModified(path)
        }
        return File(path).lastModified()
    }

    override fun canRead(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.canRead(path)
        }
        return File(path).canRead()
    }

    override fun canWrite(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.canWrite(path)
        }
        return File(path).canWrite()
    }

    override fun listFiles(path: String): List<FileInfo>? {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            val files = androidDataAccessor.listFiles(path)
            if (files != null && files.isNotEmpty()) {
                Logger.verbose(TAG) { "[AltAccess] listFiles=${files.size} | path=$path" }
                return files.map { it.toFileInfo() }
            }
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return directListFiles(path)
            val docs = managedStorageAccessor.listFiles(volumeId, relativePath)
            if (docs != null && docs.isNotEmpty()) {
                Logger.verbose(TAG) { "[ManagedAccess] listFiles=${docs.size} | path=$path" }
                return docs.map { doc ->
                    FileInfo(
                        path = "$path/${doc.displayName}",
                        name = doc.displayName,
                        isDirectory = doc.isDirectory,
                        isFile = !doc.isDirectory,
                        size = doc.size,
                        lastModified = doc.lastModified
                    )
                }
            }
        }

        return directListFiles(path)
    }

    private fun directListFiles(path: String): List<FileInfo>? {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return null
        return dir.listFiles()?.map { it.toFileInfo() }
    }

    override fun mkdirs(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            val result = androidDataAccessor.mkdirs(path)
            if (result) {
                Logger.verbose(TAG) { "[AltAccess] mkdirs=true | path=$path" }
                return true
            }
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return File(path).mkdirs()
            if (managedStorageAccessor.createDirectoryAtPath(volumeId, relativePath)) {
                Logger.verbose(TAG) { "[ManagedAccess] mkdirs=true | path=$path" }
                return true
            }
        }

        val file = File(path)
        return file.mkdirs() || file.exists()
    }

    override fun delete(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.delete(path)
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return File(path).delete()
            if (managedStorageAccessor.deleteAtPath(volumeId, relativePath)) {
                return true
            }
        }

        return File(path).delete()
    }

    override fun deleteRecursively(path: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.deleteRecursively(path)
        }
        return File(path).deleteRecursively()
    }

    override fun readBytes(path: String): ByteArray? {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            val result = androidDataAccessor.readBytes(path)
            if (result != null) return result
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return null
            managedStorageAccessor.openInputStreamAtPath(volumeId, relativePath)?.use { stream ->
                return stream.readBytes()
            }
        }

        val file = File(path)
        return if (file.exists() && file.canRead()) file.readBytes() else null
    }

    override fun writeBytes(path: String, data: ByteArray): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            if (androidDataAccessor.writeBytes(path, data)) return true
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return false
            managedStorageAccessor.openOutputStreamAtPath(volumeId, relativePath)?.use { stream ->
                stream.write(data)
                return true
            }
        }

        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getInputStream(path: String): InputStream? {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            val stream = androidDataAccessor.getInputStream(path)
            if (stream != null) return stream
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return null
            val stream = managedStorageAccessor.openInputStreamAtPath(volumeId, relativePath)
            if (stream != null) return stream
        }

        val file = File(path)
        return if (file.exists() && file.canRead()) file.inputStream() else null
    }

    override fun getOutputStream(path: String): OutputStream? {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            val stream = androidDataAccessor.getOutputStream(path)
            if (stream != null) return stream
        }

        if (isRestrictedPath(path)) {
            val (volumeId, relativePath) = extractVolumeAndPath(path) ?: return null
            val stream = managedStorageAccessor.openOutputStreamAtPath(volumeId, relativePath)
            if (stream != null) return stream
        }

        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.outputStream()
        } catch (e: Exception) {
            null
        }
    }

    override fun copyFile(source: String, dest: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && (isRestrictedPath(source) || isRestrictedPath(dest))) {
            return androidDataAccessor.copyFile(source, dest)
        }
        return try {
            File(source).copyTo(File(dest), overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun copyDirectory(source: String, dest: String): Boolean {
        if (androidDataAccessor.isAltAccessSupported() && (isRestrictedPath(source) || isRestrictedPath(dest))) {
            return androidDataAccessor.copyDirectory(source, dest)
        }
        return try {
            File(source).copyRecursively(File(dest), overwrite = true)
        } catch (e: Exception) {
            false
        }
    }

    override fun walk(path: String): Sequence<FileInfo> {
        if (androidDataAccessor.isAltAccessSupported() && isRestrictedPath(path)) {
            return androidDataAccessor.walk(path).map { it.toFileInfo() }
        }
        return File(path).walkTopDown().map { it.toFileInfo() }
    }

    override fun isRestrictedPath(path: String): Boolean {
        return androidDataAccessor.isRestrictedAndroidPath(path)
    }

    override fun normalizeForDisplay(path: String): String {
        return androidDataAccessor.normalizePathForDisplay(path)
    }

    override fun getTransformedFile(path: String): File {
        return androidDataAccessor.getFile(path)
    }

    private fun extractVolumeAndPath(path: String): Pair<String, String>? {
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
        val sdcardPattern = Regex("^/storage/([A-F0-9-]+)")

        return when {
            path.startsWith(primaryRoot) -> {
                "primary" to path.removePrefix(primaryRoot).trimStart('/')
            }
            path.matches(Regex("^/storage/[A-F0-9-]+.*")) -> {
                val match = sdcardPattern.find(path)
                if (match != null) {
                    val volId = match.groupValues[1]
                    volId to path.removePrefix("/storage/$volId").trimStart('/')
                } else null
            }
            else -> null
        }
    }

    private fun File.toFileInfo(): FileInfo = FileInfo(
        path = absolutePath,
        name = name,
        isDirectory = isDirectory,
        isFile = isFile,
        size = length(),
        lastModified = lastModified()
    )
}
