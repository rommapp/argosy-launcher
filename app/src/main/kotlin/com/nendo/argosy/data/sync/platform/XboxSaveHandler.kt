package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.xbox.XboxHddImage
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Xbox saves live inside the emulator's hard disk image rather than on the host filesystem,
 * so this handler stages them out to a directory the rest of the sync layer can treat as an
 * ordinary save folder, and writes the staged tree back into the image on restore.
 *
 * Staging is rebuilt from the image every time rather than trusted, because the emulator
 * writes the image behind our back and a stale copy would upload a save the user has moved on
 * from. Everything between here and the server sees files.
 */
class XboxSaveHandler(
    private val context: Context,
    private val fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, PLATFORM_SLUG, TAG) {

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        val staged = stagingDir(saveId)
        return if (materialize(basePath, saveId, staged)) staged.absolutePath else null
    }

    override fun constructSavePath(baseDir: String, saveId: String): String =
        stagingDir(saveId).absolutePath

    override fun isCanonicalFolderPath(savePath: String, saveId: String): Boolean =
        savePath == stagingDir(saveId).absolutePath

    /**
     * A staged path only describes a save while the image behind it still holds one, so a row
     * cached from an image the user has since replaced must not be trusted on existence alone.
     */
    override fun isValidCachedSavePath(path: String): Boolean =
        File(path).parentFile?.name == STAGING_ROOT

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveId = context.saveId ?: return@withContext null
        val basePath = resolveBasePath(context.config, context.basePathOverride)
            ?: return@withContext null

        if (!materialize(basePath, saveId, File(localPath))) {
            Logger.debug(TAG, "prepareForUpload: image holds no save | saveId=$saveId")
            return@withContext null
        }
        super.prepareForUpload(localPath, context)
    }

    override suspend fun extractDownload(
        tempFile: File,
        context: SaveContext
    ): ExtractResult = withContext(Dispatchers.IO) {
        val result = super.extractDownload(tempFile, context)
        if (!result.success) return@withContext result

        val saveId = context.saveId
            ?: return@withContext result.copy(success = false, error = "No save ID for Xbox save")
        val basePath = resolveBasePath(context.config, context.basePathOverride)
            ?: return@withContext result.copy(success = false, error = "No Xbox disk image configured")
        val staged = File(result.targetPath ?: return@withContext result)

        val failure = withImage(basePath, writable = true) { image ->
            when {
                image == null -> "Xbox disk image could not be opened for writing"
                image.isDirty -> "Xbox disk image was left mid-write by the emulator"
                else -> runCatching { image.writeSaveInPlace(saveId, staged) }
                    .exceptionOrNull()
                    ?.let { it.message ?: "Failed to write the Xbox save into the disk image" }
            }
        }

        if (failure != null) {
            Logger.error(TAG, "extractDownload: $failure | saveId=$saveId")
            return@withContext result.copy(success = false, error = failure)
        }
        result
    }

    private fun materialize(basePath: String, saveId: String, destination: File): Boolean =
        withImage(basePath, writable = false) { image ->
            if (image == null) {
                false
            } else {
                runCatching { image.extractSave(saveId, destination) }
                    .onFailure { Logger.error(TAG, "materialize failed | saveId=$saveId", it) }
                    .getOrDefault(false)
            }
        }

    private inline fun <T> withImage(
        basePath: String,
        writable: Boolean,
        body: (XboxHddImage?) -> T
    ): T {
        val imagePath = "$basePath/$IMAGE_FILE"
        val image = if (fal.exists(imagePath)) {
            XboxHddImage.open(fal, imagePath, writable)
        } else {
            Logger.debug(TAG, "No disk image at $imagePath")
            null
        }
        return try {
            body(image)
        } finally {
            image?.close()
        }
    }

    private fun stagingDir(saveId: String): File =
        File(File(context.cacheDir, STAGING_ROOT), saveId)

    private companion object {
        const val PLATFORM_SLUG = "xbox"
        const val TAG = "XboxSaveHandler"
        const val STAGING_ROOT = "xbox-saves"
        const val IMAGE_FILE = "hdd.img"
    }
}
