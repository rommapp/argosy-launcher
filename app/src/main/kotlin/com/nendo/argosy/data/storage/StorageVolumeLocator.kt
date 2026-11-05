package com.nendo.argosy.data.storage

import android.os.StatFs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Volume key recorded for a game file whose path sits on no detected volume and outside the
 * app-private data partition. Never a real mount point.
 */
const val UNKNOWN_LOCATION_KEY = "unknown"

private const val APP_DATA_PREFIX = "/data/"

/**
 * The storage volumes on this device, and which one a path is on. Volumes are keyed by their
 * canonical mount path; two mounts of the same filesystem count once.
 */
@Singleton
class StorageVolumeLocator @Inject constructor(
    private val volumeDetector: StorageVolumeDetector
) {
    fun detectVolumes(): List<StorageVolumeInfo> {
        val seenPaths = HashSet<String>()
        val seenFingerprints = HashSet<String>()
        val result = mutableListOf<StorageVolumeInfo>()
        for (volume in volumeDetector.detectStorageVolumes()) {
            if (volume.totalBytes <= 0L) continue
            val canonical = StoragePathUtils.canonicalize(volume.path).trimEnd('/')
            if (canonical.isEmpty()) continue
            if (!seenPaths.add(canonical)) continue
            val fingerprint = statFingerprint(canonical) ?: canonical
            if (!seenFingerprints.add(fingerprint)) continue
            result.add(
                StorageVolumeInfo(
                    key = canonical,
                    displayName = volume.displayName,
                    type = volume.type,
                    totalBytes = volume.totalBytes,
                    availableBytes = volume.availableBytes
                )
            )
        }
        return result
    }

    private fun statFingerprint(path: String): String? = try {
        val stat = StatFs(path)
        "${stat.blockSizeLong}:${stat.blockCountLong}:${stat.availableBlocksLong}"
    } catch (_: Exception) {
        null
    }
}

/**
 * The key of the volume [canonicalPath] sits on, the most specific match when mounts nest, or
 * null when it is on none of [volumes].
 */
fun matchVolumeKey(canonicalPath: String, volumes: List<StorageVolumeInfo>): String? =
    volumes
        .filter { canonicalPath == it.key || canonicalPath.startsWith("${it.key}/") }
        .maxByOrNull { it.key.length }
        ?.key

/**
 * The volume key a game file counts toward: its volume, internal storage for app-private data,
 * and [UNKNOWN_LOCATION_KEY] for any other path no volume holds.
 */
fun gameFileVolumeKey(canonicalPath: String, volumes: List<StorageVolumeInfo>, internalKey: String?): String? =
    matchVolumeKey(canonicalPath, volumes)
        ?: if (canonicalPath.startsWith(APP_DATA_PREFIX)) internalKey else UNKNOWN_LOCATION_KEY
