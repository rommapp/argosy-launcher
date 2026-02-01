package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "DefaultSaveHandler"
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val file = fal.getTransformedFile(localPath)
            if (!file.exists()) {
                Logger.debug(TAG, "prepareForUpload: Save file does not exist | path=$localPath")
                return@withContext null
            }

            // File-based: return as-is, no zipping needed
            Logger.debug(TAG, "prepareForUpload: Using file directly | path=$localPath, size=${file.length()}")
            PreparedSave(file, isTemporary = false, listOf(localPath))
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        withContext(Dispatchers.IO) {
            val targetPath = context.localSavePath
            if (targetPath == null) {
                return@withContext ExtractResult(false, null, "No target path for default save handler")
            }

            val parentDir = File(targetPath).parent
            if (parentDir != null) fal.mkdirs(parentDir)

            val success = fal.copyFile(tempFile.absolutePath, targetPath)
            if (!success) {
                Logger.error(TAG, "extractDownload: Failed to copy file | target=$targetPath")
                return@withContext ExtractResult(false, null, "Failed to copy save file")
            }

            Logger.debug(TAG, "extractDownload: Complete | target=$targetPath")
            ExtractResult(true, targetPath)
        }
}
