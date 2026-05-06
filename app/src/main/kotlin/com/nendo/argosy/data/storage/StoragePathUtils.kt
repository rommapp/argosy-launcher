package com.nendo.argosy.data.storage

import android.os.Environment
import java.io.File

object StoragePathUtils {

    private val sdcardPattern = Regex("^/storage/([A-F0-9-]+)")

    /**
     * Absolute path to the current user's primary external storage root, e.g.
     * `/storage/emulated/0` for the primary user, `/storage/emulated/10` for a secondary user
     * or work profile. The trailing slash is omitted; callers that need it should append.
     * Avoid hardcoding `/storage/emulated/0/` — this resolves dynamically.
     */
    @Suppress("DEPRECATION")
    val primaryExternalRoot: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
    }

    /**
     * Resolves any input path through the symlink layer (e.g. `/storage/self/primary/...`,
     * `/sdcard/...`) and returns the canonical absolute form, falling back to the input on
     * IO failure.
     */
    fun canonicalize(path: String): String = try {
        File(path).canonicalPath
    } catch (_: Exception) {
        path
    }

    /**
     * Resolves an absolute file path into a (volumeId, relativePath) pair suitable for
     * DocumentsContract URIs. Symlinks (e.g. /sdcard) are resolved to their canonical
     * path before matching. Returns null for paths that cannot be mapped to a
     * documentable storage volume (e.g. app-private directories).
     */
    fun extractVolumeAndPath(path: String): Pair<String, String>? {
        val canonicalPath = canonicalize(path)

        return when {
            canonicalPath.startsWith(primaryExternalRoot) -> {
                "primary" to canonicalPath.removePrefix(primaryExternalRoot).trimStart('/')
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
