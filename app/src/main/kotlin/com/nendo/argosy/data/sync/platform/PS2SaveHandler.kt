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
class PS2SaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "PS2SaveHandler"
        private const val BA_PREFIX = "BA"
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val saveFolder = fal.getTransformedFile(localPath)
            if (!saveFolder.exists() || !saveFolder.isDirectory) {
                Logger.debug(TAG, "prepareForUpload: Save folder does not exist | path=$localPath")
                return@withContext null
            }

            val outputFile = File(this@PS2SaveHandler.context.cacheDir, "${saveFolder.name}.zip")
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
                return@withContext ExtractResult(false, null, "No base path for PS2 saves")
            }

            val serial = context.titleId
            if (serial == null) {
                return@withContext ExtractResult(false, null, "No serial for PS2 save")
            }

            val targetPath = constructSavePath(basePath, serial)
            val targetFolder = File(targetPath)
            targetFolder.mkdirs()

            val success = saveArchiver.unzipSingleFolder(tempFile, targetFolder)
            if (!success) {
                Logger.error(TAG, "extractDownload: Unzip failed | target=$targetPath")
                return@withContext ExtractResult(false, null, "Failed to extract PS2 save")
            }

            Logger.debug(TAG, "extractDownload: Complete | target=$targetPath")
            ExtractResult(true, targetPath)
        }

    fun findSaveFolderByTitleId(basePath: String, serial: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val folderCards = fal.listFiles(basePath)?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        } ?: emptyList()

        for (card in folderCards) {
            val match = fal.listFiles(card.path)?.firstOrNull {
                it.isDirectory && matchesFolderName(it.name, serial)
            }
            if (match != null) {
                Logger.debug(TAG, "Save found | card=${card.name}, path=${match.path}")
                return match.path
            }
        }

        Logger.debug(TAG, "No save found | basePath=$basePath, serial=$serial")
        return null
    }

    fun constructSavePath(baseDir: String, serial: String): String {
        val folderCards = fal.listFiles(baseDir)?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        } ?: emptyList()

        val cardDir = folderCards.firstOrNull()?.path ?: "$baseDir/Shared.ps2"
        return "$cardDir/${toFolderName(serial)}"
    }

    fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) return basePathOverride

        val resolvedPaths = SavePathRegistry.resolvePath(config, "ps2", null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
    }

    private fun toFolderName(serial: String): String {
        val stripped = serial.replace("-", "")
        return if (stripped.startsWith(BA_PREFIX, ignoreCase = true)) {
            stripped
        } else {
            "$BA_PREFIX$stripped"
        }
    }

    private fun matchesFolderName(folderName: String, serial: String): Boolean {
        val stripped = serial.replace("-", "")
        val baSerial = if (stripped.startsWith(BA_PREFIX, ignoreCase = true)) stripped else "$BA_PREFIX$stripped"
        val folderStripped = folderName.replace("-", "")
        return folderStripped.equals(baSerial, ignoreCase = true)
    }
}
