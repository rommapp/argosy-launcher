package com.nendo.argosy.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.Os
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDataAccessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AndroidDataAccessor"
        private const val ANDROID_PATH = "/Android/"
        private val ALT_PATH = BuildConfig.UCDATA_PATH.takeIf { it.isNotEmpty() }
    }

    @Volatile
    private var altPathSupported: Boolean? = null

    fun resetAltAccessCache() {
        altPathSupported = null
    }

    fun isAltAccessSupported(): Boolean {
        if (ALT_PATH == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val hasStoragePermission = Environment.isExternalStorageManager()
        if (hasStoragePermission) {
            altPathSupported?.let { return it }
        }

        synchronized(this) {
            if (hasStoragePermission) {
                altPathSupported?.let { return it }
            }

            val extStorage = Environment.getExternalStorageDirectory().absolutePath
            val altAndroidPath = extStorage + ALT_PATH.dropLast(1)

            val supported = try {
                val dir = File(altAndroidPath)
                val dataLink = File(dir, "data")

                if (hasStoragePermission && !dataLink.exists()) {
                    setupAltAccess(extStorage, dir, dataLink)
                }

                dir.exists() && dataLink.canRead()
            } catch (e: Exception) {
                Logger.warn(TAG, "[AltAccess] Path check failed: ${e.message}")
                false
            }

            if (hasStoragePermission) {
                altPathSupported = supported
            }
            return supported
        }
    }

    private fun setupAltAccess(extStorage: String, altDir: File, dataLink: File) {
        try {
            if (!altDir.exists()) altDir.mkdirs()
            if (!dataLink.exists()) Os.symlink("$extStorage/Android/data", dataLink.absolutePath)

            val obbLink = File(altDir, "obb")
            if (!obbLink.exists()) Os.symlink("$extStorage/Android/obb", obbLink.absolutePath)
        } catch (e: Exception) {
            Logger.warn(TAG, "[AltAccess] Setup failed: ${e.message}")
        }
    }

    fun transformPath(path: String): String {
        val altPath = ALT_PATH ?: return path
        if (!isAltAccessSupported()) return path
        if (!isRestrictedAndroidPath(path)) return path
        if (path.contains(altPath)) return path

        return path.replaceFirst(ANDROID_PATH, altPath)
    }

    fun normalizePathForDisplay(path: String): String {
        val altPath = ALT_PATH ?: return path
        return path.replace(altPath, ANDROID_PATH)
    }

    fun isRestrictedAndroidPath(path: String): Boolean {
        val altPath = ALT_PATH
        return path.contains("/Android/data/") ||
            path.contains("/Android/obb/") ||
            path.endsWith("/Android/data") ||
            path.endsWith("/Android/obb") ||
            (altPath != null && path.contains(altPath))
    }

    fun listFiles(path: String): Array<File>? {
        val transformedPath = transformPath(path)
        val dir = File(transformedPath)
        return if (dir.exists() && dir.isDirectory) dir.listFiles() else null
    }

    fun exists(path: String): Boolean {
        return File(transformPath(path)).exists()
    }

    fun canRead(path: String): Boolean {
        return File(transformPath(path)).canRead()
    }

    fun canWrite(path: String): Boolean {
        return File(transformPath(path)).canWrite()
    }

    fun readBytes(path: String): ByteArray? {
        val file = File(transformPath(path))
        return if (file.exists() && file.canRead()) {
            try { file.readBytes() } catch (e: Exception) { null }
        } else null
    }

    fun writeBytes(path: String, data: ByteArray): Boolean {
        return try {
            val file = File(transformPath(path))
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: Exception) { false }
    }

    fun delete(path: String): Boolean {
        return try { File(transformPath(path)).delete() } catch (e: Exception) { false }
    }

    fun deleteRecursively(path: String): Boolean {
        return try { File(transformPath(path)).deleteRecursively() } catch (e: Exception) { false }
    }

    /**
     * Get a File object with the transformed path.
     * Use this when you need direct File access.
     */
    fun getFile(path: String): File {
        return File(transformPath(path))
    }

    fun getInputStream(path: String): InputStream? {
        val file = File(transformPath(path))
        return if (file.exists() && file.canRead()) {
            try { file.inputStream() } catch (e: Exception) { null }
        } else null
    }

    fun getOutputStream(path: String): OutputStream? {
        val transformedPath = transformPath(path)
        return try {
            val file = File(transformedPath)
            file.parentFile?.mkdirs()
            file.outputStream()
        } catch (e: Exception) {
            Logger.error(TAG, "[AltAccess] getOutputStream failed | path=$transformedPath, error=${e.message}")
            null
        }
    }

    fun lastModified(path: String): Long {
        return File(transformPath(path)).lastModified()
    }

    fun length(path: String): Long {
        return File(transformPath(path)).length()
    }

    fun isDirectory(path: String): Boolean {
        return File(transformPath(path)).isDirectory
    }

    fun isFile(path: String): Boolean {
        return File(transformPath(path)).isFile
    }

    fun mkdirs(path: String): Boolean {
        val file = File(transformPath(path))
        return try { file.mkdirs() || file.exists() } catch (e: Exception) { false }
    }

    /**
     * Walk directory tree, yielding all files and directories.
     */
    fun walk(path: String): Sequence<File> {
        return File(transformPath(path)).walkTopDown()
    }

    fun copyFile(sourcePath: String, destPath: String): Boolean {
        return try {
            val sourceFile = File(transformPath(sourcePath))
            val destFile = File(transformPath(destPath))
            destFile.parentFile?.mkdirs()
            sourceFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) { false }
    }

    fun copyDirectory(sourcePath: String, destPath: String): Boolean {
        return try {
            File(transformPath(sourcePath)).copyRecursively(File(transformPath(destPath)), overwrite = true)
        } catch (e: Exception) { false }
    }

    fun moveDirectory(sourcePath: String, destPath: String): Boolean {
        val sourceDir = File(sourcePath) // Source is always unrestricted (cache)
        val destDir = File(transformPath(destPath))

        return try {
            destDir.parentFile?.mkdirs()

            // Try atomic rename first
            if (sourceDir.renameTo(destDir)) return true

            // Fallback: copy then delete
            if (sourceDir.copyRecursively(destDir, overwrite = true)) {
                sourceDir.deleteRecursively()
                return true
            }
            false
        } catch (e: Exception) {
            Logger.error(TAG, "[AltAccess] moveDirectory failed | dest=$destPath, error=${e.message}")
            false
        }
    }
}
