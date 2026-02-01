package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VitaSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "VitaSaveHandler"
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val saveFolder = fal.getTransformedFile(localPath)
            if (!saveFolder.exists() || !saveFolder.isDirectory) {
                Logger.debug(TAG, "prepareForUpload: Save folder does not exist | path=$localPath")
                return@withContext null
            }

            val outputFile = File(this@VitaSaveHandler.context.cacheDir, "${saveFolder.name}.zip")
            if (!saveArchiver.zipFolder(saveFolder, outputFile)) {
                Logger.error(TAG, "prepareForUpload: Failed to zip folder | source=$localPath")
                return@withContext null
            }

            PreparedSave(outputFile, isTemporary = true, listOf(localPath))
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        withContext(Dispatchers.IO) {
            val basePath = resolveBasePath(context.config, null)
            if (basePath == null) {
                return@withContext ExtractResult(false, null, "No base path for Vita saves")
            }

            val titleId = context.titleId
            if (titleId == null) {
                return@withContext ExtractResult(false, null, "No title ID for Vita save")
            }

            val targetPath = constructSavePath(basePath, titleId)
            val targetFolder = File(targetPath)
            targetFolder.mkdirs()

            val success = saveArchiver.unzipSingleFolder(tempFile, targetFolder)
            if (!success) {
                Logger.error(TAG, "extractDownload: Unzip failed | target=$targetPath")
                return@withContext ExtractResult(false, null, "Failed to extract Vita save")
            }

            Logger.debug(TAG, "extractDownload: Complete | target=$targetPath")
            ExtractResult(true, targetPath)
        }

    fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val match = fal.listFiles(basePath)?.firstOrNull {
            it.isDirectory && it.name.equals(titleId, ignoreCase = true)
        }

        if (match != null) {
            Logger.debug(TAG, "Save found | path=${match.path}")
            return match.path
        }

        Logger.debug(TAG, "No save found | basePath=$basePath, titleId=$titleId")
        return null
    }

    fun constructSavePath(baseDir: String, titleId: String): String {
        return "$baseDir/$titleId"
    }

    fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) {
            return basePathOverride
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, "vita", null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
    }
}
