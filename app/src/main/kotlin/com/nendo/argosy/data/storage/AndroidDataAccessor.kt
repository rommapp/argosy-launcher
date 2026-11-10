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
    @ApplicationContext private val context: Context,
    private val mirror: AndroidDataMirror,
    private val volumeDetector: StorageVolumeDetector
) {
    companion object {
        private const val TAG = "AndroidDataAccessor"
        private const val ANDROID_PATH = "/Android/"
        private val ALT_PATH = BuildConfig.UCDATA_PATH.takeIf { it.isNotEmpty() }

        /**
         * Whether [path] is on internal shared storage, the only volume whose Android/data needs
         * alt access; a removable volume's Android/data is reachable through the plain path.
         */
        fun isOnInternalVolume(path: String): Boolean =
            !path.startsWith("/storage/") ||
                path.startsWith("/storage/emulated/") ||
                path.startsWith("/storage/self/")
    }

    @Volatile
    private var altPathSupported: Boolean? = null

    fun resetAltAccessCache() {
        altPathSupported = null
    }

    fun isAltAccessSupported(): Boolean {
        if (mirror.isActive) return true
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

            val supported = setupAndVerifyAltAccess(
                Environment.getExternalStorageDirectory().absolutePath,
                hasStoragePermission
            )
            if (hasStoragePermission) {
                altPathSupported = supported
            }
            return supported
        }
    }

    fun getAllStorageRoots(): List<String> {
        val roots = mutableListOf(Environment.getExternalStorageDirectory().absolutePath)
        volumeDetector.volumeRootPaths().forEach { path ->
            if (path !in roots) roots.add(path)
        }
        try {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name != "emulated" && vol.name != "self") {
                    val path = vol.absolutePath
                    if (path !in roots && vol.canRead()) roots.add(path)
                }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[AltAccess] Failed to enumerate storage volumes: ${e.message}")
        }
        return roots
    }

    private fun setupAndVerifyAltAccess(storageRoot: String, hasStoragePermission: Boolean): Boolean {
        val altPath = ALT_PATH ?: return false
        val altAndroidPath = storageRoot + altPath.dropLast(1)
        return try {
            val dir = File(altAndroidPath)
            val dataLink = File(dir, "data")

            if (hasStoragePermission && !dataLink.exists()) {
                setupAltAccess(storageRoot, dir, dataLink)
            }

            dir.exists() && dataLink.canRead()
        } catch (e: Exception) {
            Logger.warn(TAG, "[AltAccess] Path check failed for $storageRoot: ${e.message}")
            false
        }
    }

    private fun setupAltAccess(storageRoot: String, altDir: File, dataLink: File) {
        try {
            if (!altDir.exists()) altDir.mkdirs()
            if (!dataLink.exists()) Os.symlink("$storageRoot/Android/data", dataLink.absolutePath)

            val obbLink = File(altDir, "obb")
            if (!obbLink.exists()) Os.symlink("$storageRoot/Android/obb", obbLink.absolutePath)
            Logger.info(TAG, "[AltAccess] Symlinks created for $storageRoot")
        } catch (e: Exception) {
            Logger.warn(TAG, "[AltAccess] Setup failed for $storageRoot: ${e.message}")
        }
    }

    /**
     * Reconciles the mirrored copies of [paths] with the real folders now and reports whether every
     * copy succeeded. Null when Android/data is not mirrored on this device.
     */
    fun syncMirror(paths: Collection<String>): Boolean? {
        if (!mirror.isActive) return null
        return mirror.sync(paths.map { mirror.realPathOf(it) ?: it })
    }

    /**
     * Names and sizes of the files directly inside the real folder at [path], read without
     * copying them into the mirror. Null when Android/data is not mirrored on this device or
     * [path] is not mirrored.
     */
    fun mirroredFileSizes(path: String): Map<String, Long>? {
        if (!mirror.isActive || !isRestrictedAndroidPath(path)) return null
        return mirror.realFileSizes(mirror.realPathOf(path) ?: path)
    }

    fun transformPath(path: String): String {
        if (mirror.isActive) {
            if (mirror.isMirrorPath(path)) {
                mirror.realPathOf(path)?.let { mirror.mirrorPathFor(it) }
                return path
            }
            if (!isRestrictedAndroidPath(path)) return path
            return mirror.mirrorPathFor(path) ?: path
        }
        val altPath = ALT_PATH ?: return path
        if (!isOnInternalVolume(path)) return path
        if (!isAltAccessSupported()) return path
        if (!isRestrictedAndroidPath(path)) return path
        if (path.contains(altPath)) return path

        return path.replaceFirst(ANDROID_PATH, altPath)
    }

    fun normalizePathForDisplay(path: String): String {
        mirror.realPathOf(path)?.let { return it }
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

    fun lastModified(path: String): Long =
        File(path).takeIf { it.exists() }?.lastModified() ?: File(transformPath(path)).lastModified()

    fun length(path: String): Long =
        File(path).takeIf { it.exists() }?.length() ?: File(transformPath(path)).length()

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
        val sourceDir = File(sourcePath)
        val destDir = File(transformPath(destPath))

        return try {
            destDir.parentFile?.mkdirs()

            if (sourceDir.renameTo(destDir)) return true

            if (destDir.exists()) destDir.deleteRecursively()
            val copied = sourceDir.copyRecursively(destDir, overwrite = true)
            if (copied) {
                sourceDir.deleteRecursively()
                return true
            }
            destDir.deleteRecursively()
            sourceDir.deleteRecursively()
            false
        } catch (e: Exception) {
            Logger.error(TAG, "[AltAccess] moveDirectory failed | dest=$destPath, error=${e.message}")
            runCatching { sourceDir.deleteRecursively() }
            false
        }
    }
}
