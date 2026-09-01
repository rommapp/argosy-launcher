package com.nendo.argosy.data.sync.xbox

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the blank hard disk hakuX needs when the user has the flash ROM and MCPX but no
 * drive image. It only ever creates one that is absent: an existing image is the volume the
 * user's saves live inside, and replacing it would take every one of them.
 */
@Singleton
class XboxDiskImageProvisioner @Inject constructor(
    private val fal: FileAccessLayer
) {
    suspend fun ensureImageExists(directory: String): Boolean = withContext(Dispatchers.IO) {
        val imagePath = "$directory/$IMAGE_FILE"
        if (fal.exists(imagePath)) return@withContext false
        if (!fal.exists(directory)) return@withContext false

        val stream = fal.getOutputStream(imagePath)
        if (stream == null) {
            Logger.warn(TAG, "Cannot write a disk image to $imagePath")
            return@withContext false
        }

        val created = runCatching { stream.use { XboxDiskImageFactory.write(it) } }
            .onFailure {
                Logger.error(TAG, "Failed to generate a disk image at $imagePath", it)
                fal.delete(imagePath)
            }
            .isSuccess

        if (created) Logger.info(TAG, "Generated a blank Xbox disk image at $imagePath")
        created
    }

    private companion object {
        const val TAG = "XboxDiskImageProvisioner"
        const val IMAGE_FILE = "hdd.img"
    }
}
