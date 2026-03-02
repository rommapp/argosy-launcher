package com.nendo.argosy.data.storage

import android.os.Environment
import java.io.File

object StoragePathUtils {

    private val sdcardPattern = Regex("^/storage/([A-F0-9-]+)")

    /**
     * Resolves an absolute file path into a (volumeId, relativePath) pair suitable for
     * DocumentsContract URIs. Symlinks (e.g. /sdcard) are resolved to their canonical
     * path before matching. Returns null for paths that cannot be mapped to a
     * documentable storage volume (e.g. app-private directories).
     */
    fun extractVolumeAndPath(path: String): Pair<String, String>? {
        val canonicalPath = try {
            File(path).canonicalPath
        } catch (_: Exception) {
            path
        }

        @Suppress("DEPRECATION")
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath

        return when {
            canonicalPath.startsWith(primaryRoot) -> {
                "primary" to canonicalPath.removePrefix(primaryRoot).trimStart('/')
            }
            canonicalPath.matches(Regex("^/storage/[A-F0-9-]+.*")) -> {
                val match = sdcardPattern.find(canonicalPath)
                if (match != null) {
                    val volId = match.groupValues[1]
                    volId to canonicalPath.removePrefix("/storage/$volId").trimStart('/')
                } else null
            }
            else -> null
        }
    }
}
