package com.nendo.argosy.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.os.StatFs
import com.nendo.argosy.ui.filebrowser.StorageVolumeType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.nendo.argosy.ui.filebrowser.StorageVolume as AppStorageVolume

@Singleton
class StorageVolumeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val probePaths = listOf(
        "/storage/sdcard1",
        "/storage/extSdCard",
        "/mnt/external_sd",
        "/mnt/extSdCard",
        "/mnt/usb_storage",
        "/mnt/usb",
        "/storage/usb0",
        "/storage/usb1",
        "/storage/usb2",
        "/storage/external_SD",
        "/mnt/media_rw"
    )

    fun detectStorageVolumes(): List<AppStorageVolume> {
        val volumes = mutableListOf<AppStorageVolume>()
        val addedPaths = mutableSetOf<String>()

        val internalPath = Environment.getExternalStorageDirectory().absolutePath
        addedPaths.add(internalPath)
        volumes.add(createInternalStorageVolume(internalPath))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            storageManager.storageVolumes.forEach { volume ->
                val path = getVolumePath(volume)
                if (path != null && path !in addedPaths && File(path).canRead()) {
                    addedPaths.add(path)
                    volumes.add(createVolumeFromSystem(volume, path))
                }
            }
        }

        val additionalVolumes = probeAdditionalPaths(addedPaths)
        volumes.addAll(additionalVolumes)

        return volumes
    }

    private fun createInternalStorageVolume(path: String): AppStorageVolume {
        val stats = getStorageStats(path)
        return AppStorageVolume(
            id = "internal",
            displayName = "Internal",
            path = path,
            type = StorageVolumeType.INTERNAL,
            availableBytes = stats.first,
            totalBytes = stats.second
        )
    }

    private fun createVolumeFromSystem(volume: StorageVolume, path: String): AppStorageVolume {
        val type = when {
            !volume.isRemovable -> StorageVolumeType.INTERNAL
            path.contains("usb", ignoreCase = true) -> StorageVolumeType.USB
            else -> StorageVolumeType.SD_CARD
        }

        val displayName = volume.getDescription(context) ?: when (type) {
            StorageVolumeType.SD_CARD -> "SD Card"
            StorageVolumeType.USB -> "USB"
            else -> File(path).name
        }

        val stats = getStorageStats(path)

        return AppStorageVolume(
            id = volume.uuid ?: path,
            displayName = displayName,
            path = path,
            type = type,
            availableBytes = stats.first,
            totalBytes = stats.second
        )
    }

    private fun getVolumePath(volume: StorageVolume): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
        } else {
            try {
                val getPathMethod = StorageVolume::class.java.getMethod("getPath")
                getPathMethod.invoke(volume) as? String
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun probeAdditionalPaths(existingPaths: Set<String>): List<AppStorageVolume> {
        val additional = mutableListOf<AppStorageVolume>()

        probePaths.forEach { probePath ->
            val dir = File(probePath)
            if (dir.exists() && dir.canRead() && dir.isDirectory && probePath !in existingPaths) {
                if (probePath == "/mnt/media_rw") {
                    dir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.canRead() && subDir.absolutePath !in existingPaths) {
                            additional.add(createVolumeFromProbe(subDir.absolutePath))
                        }
                    }
                } else {
                    additional.add(createVolumeFromProbe(probePath))
                }
            }
        }

        val mountVolumes = parseProcMounts(existingPaths + additional.map { it.path }.toSet())
        additional.addAll(mountVolumes)

        return additional
    }

    private fun createVolumeFromProbe(path: String): AppStorageVolume {
        val type = when {
            path.contains("usb", ignoreCase = true) -> StorageVolumeType.USB
            path.contains("sd", ignoreCase = true) -> StorageVolumeType.SD_CARD
            else -> StorageVolumeType.UNKNOWN
        }

        val displayName = when (type) {
            StorageVolumeType.USB -> "USB"
            StorageVolumeType.SD_CARD -> "SD Card"
            else -> File(path).name
        }

        val stats = getStorageStats(path)

        return AppStorageVolume(
            id = path,
            displayName = displayName,
            path = path,
            type = type,
            availableBytes = stats.first,
            totalBytes = stats.second
        )
    }

    private fun parseProcMounts(existingPaths: Set<String>): List<AppStorageVolume> {
        return try {
            File("/proc/mounts").readLines()
                .filter { line ->
                    (line.contains("/storage/") || line.contains("/mnt/media_rw/")) &&
                        !line.contains("emulated") &&
                        !line.contains("self")
                }
                .mapNotNull { line ->
                    val parts = line.split(" ")
                    if (parts.size >= 2) parts[1] else null
                }
                .filter { path ->
                    val file = File(path)
                    path !in existingPaths && file.exists() && file.canRead()
                }
                .map { path -> createVolumeFromProbe(path) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getStorageStats(path: String): Pair<Long, Long> {
        return try {
            val stat = StatFs(path)
            stat.availableBytes to stat.totalBytes
        } catch (e: Exception) {
            0L to 0L
        }
    }
}
