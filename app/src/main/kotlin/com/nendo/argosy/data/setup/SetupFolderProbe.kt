package com.nendo.argosy.data.setup

import android.os.Environment
import android.os.StatFs
import com.nendo.argosy.core.storage.StorageVolumeType
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StorageVolumeDetector
import com.nendo.argosy.domain.model.FolderSpace
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where setup offers to keep games and how much room each place has. Reads the file system and
 * the mounted volumes: call it off the main thread.
 */
@Singleton
class SetupFolderProbe @Inject constructor(
    private val volumes: StorageVolumeDetector,
    private val fileAccess: FileAccessLayer
) {
    fun internalRomPath(): String =
        File(Environment.getExternalStorageDirectory(), ROMS_FOLDER).absolutePath

    fun sdCardRomPath(): String? = volumes.detectStorageVolumes()
        .firstOrNull { it.type == StorageVolumeType.SD_CARD }
        ?.let { File(it.path, ROMS_FOLDER).absolutePath }

    /**
     * The free and total space of the volume [path] sits on, read from its nearest existing
     * folder. Null when no part of the path exists.
     */
    fun spaceOf(path: String): FolderSpace? {
        val existing = generateSequence(File(path)) { it.parentFile }.firstOrNull { it.exists() } ?: return null
        return runCatching {
            val stat = StatFs(existing.absolutePath)
            FolderSpace(
                freeBytes = stat.availableBytes,
                totalBytes = stat.totalBytes,
                exists = fileAccess.isDirectory(path)
            )
        }.getOrNull()
    }

    /**
     * Creates [path] when it is missing and answers whether Argosy can write to it.
     */
    fun prepareWritable(path: String): Boolean {
        if (!fileAccess.exists(path)) fileAccess.mkdirs(path)
        return fileAccess.isDirectory(path) && fileAccess.canWrite(path)
    }

    private companion object {
        const val ROMS_FOLDER = "ROMs"
    }
}
