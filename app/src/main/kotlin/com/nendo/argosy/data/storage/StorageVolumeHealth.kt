package com.nendo.argosy.data.storage

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StorageVolumeHealth"
private const val INTERNAL_ROOT = "/data"

private val STORAGE_ROOT_PATTERN = Regex("^(/storage/[^/]+|/mnt/media_rw/[^/]+)")

/**
 * Distinguishes "this file is gone" from "the volume holding it cannot be read right now".
 *
 * A missing file is evidence of deletion only when the volume it names answers as mounted and
 * its root still lists. Every other answer - unmounted, removed after a bad eject, a path that
 * maps to no known volume, a listing that fails - resolves to "cannot tell", because an SD card
 * that drops out while internal storage stays healthy is indistinguishable from a deleted
 * library to anything that only asks `File.exists()`.
 */
@Singleton
class StorageVolumeHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileAccessLayer: FileAccessLayer
) {
    /**
     * Opens one pass worth of verdicts. Volume state is sampled once per root and reused for the
     * rest of the pass, so a card that drops out midway cannot make the first half of a sweep
     * mean something different from the second. Create one per operation and pass it down; never
     * cache one across operations.
     */
    fun newProbe(): VolumeProbe = VolumeProbe(context, fileAccessLayer)
}

/**
 * A single sweep's view of which storage volumes can be trusted to answer for absence.
 * Not thread-safe; confine one probe to the pass that created it.
 */
class VolumeProbe internal constructor(
    private val context: Context,
    private val fileAccessLayer: FileAccessLayer
) {
    private val rootVerdicts = HashMap<String, Boolean>()

    /**
     * Whether the volume backing [path] is currently mounted with a listable root. False for any
     * path that maps to no recognisable volume, which keeps unrecognised mounts on the
     * protective side.
     */
    fun isVolumeReadable(path: String): Boolean {
        if (path.isBlank()) return false
        val root = volumeRootFor(path) ?: return false
        return rootVerdicts.getOrPut(root) { computeRootReadable(root) }
    }

    /**
     * Whether [path] is missing for a reason worth acting on: the file does not resolve, the
     * volume naming it is readable, and the nearest surviving ancestor directory lists. Any
     * caller that clears a pointer, drops a row, or deletes content because a file was not
     * found must gate on this rather than on `exists()`.
     */
    fun isGenuinelyAbsent(path: String): Boolean {
        if (path.isBlank()) return false
        if (fileAccessLayer.exists(path)) return false
        if (!isVolumeReadable(path)) {
            Logger.warn(TAG, "withholding absence: volume not readable | path=$path")
            return false
        }
        if (!nearestExistingAncestorLists(path)) {
            Logger.warn(TAG, "withholding absence: no listable ancestor | path=$path")
            return false
        }
        return true
    }

    /**
     * Whether every one of [paths] sits on a readable volume. Callers that sweep a whole library
     * use this to decide once, before touching anything, whether the sweep may run at all.
     */
    fun allVolumesReadable(paths: Collection<String>): Boolean =
        paths.all { isVolumeReadable(it) }

    private fun volumeRootFor(path: String): String? {
        val canonical = StoragePathUtils.canonicalize(path)
        if (canonical.startsWith("$INTERNAL_ROOT/")) return INTERNAL_ROOT
        val primary = StoragePathUtils.primaryExternalRoot
        if (canonical == primary || canonical.startsWith("$primary/")) return primary
        val root = STORAGE_ROOT_PATTERN.find(canonical)?.value
            ?: legacyMountRootFor(canonical)
            ?: return null
        if (root == "/storage/self" || root == "/storage/emulated") return null
        return root
    }

    /**
     * Recognises the mount points removable media appears at on devices that keep it outside
     * `/storage`. Without these a card at `/mnt/external_sd` maps to no volume, and every path on
     * it answers "cannot tell" forever, so a pointer that really is stale never clears.
     */
    private fun legacyMountRootFor(canonical: String): String? =
        REMOVABLE_MOUNT_PROBE_PATHS
            .filter { canonical == it || canonical.startsWith("$it/") }
            .maxByOrNull { it.length }

    private fun computeRootReadable(root: String): Boolean {
        if (root == INTERNAL_ROOT) return true
        val dir = File(root)
        val state = mediaStateOf(dir)
        if (state != null &&
            state != Environment.MEDIA_MOUNTED &&
            state != Environment.MEDIA_MOUNTED_READ_ONLY
        ) {
            Logger.warn(TAG, "volume unusable | root=$root, state=$state")
            return false
        }
        if (!dir.isDirectory || !dir.canRead()) {
            Logger.warn(TAG, "volume root is not a readable directory | root=$root")
            return false
        }
        val entries = dir.listFiles()
        if (entries.isNullOrEmpty()) {
            Logger.warn(TAG, "volume root listed nothing | root=$root")
            return false
        }
        return true
    }

    private fun mediaStateOf(root: File): String? {
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volumeState = runCatching { manager?.getStorageVolume(root)?.state }.getOrNull()
        if (volumeState != null && volumeState != Environment.MEDIA_UNKNOWN) return volumeState
        return runCatching { Environment.getExternalStorageState(root) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != Environment.MEDIA_UNKNOWN }
    }

    private fun nearestExistingAncestorLists(path: String): Boolean {
        var cursor: File? = File(path).parentFile
        while (cursor != null) {
            if (cursor.exists()) {
                return cursor.isDirectory && cursor.canRead() && cursor.listFiles() != null
            }
            cursor = cursor.parentFile
        }
        return false
    }
}
