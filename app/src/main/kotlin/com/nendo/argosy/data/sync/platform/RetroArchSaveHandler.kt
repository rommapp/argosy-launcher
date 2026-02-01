package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetroArchSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val retroArchConfigParser: RetroArchConfigParser
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "RetroArchSaveHandler"
        private val SAVE_EXTENSIONS = listOf("srm", "sav")
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val file = fal.getTransformedFile(localPath)
            if (!file.exists() || file.isDirectory) {
                Logger.debug(TAG, "prepareForUpload: Save file does not exist | path=$localPath")
                return@withContext null
            }

            // File-based: return as-is, no zipping needed
            Logger.debug(TAG, "prepareForUpload: Using file directly | path=$localPath, size=${file.length()}")
            PreparedSave(file, isTemporary = false, listOf(localPath))
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        withContext(Dispatchers.IO) {
            // Use cached path if available, otherwise discover/construct
            val targetPath = context.localSavePath
                ?: discoverSavePath(context)
                ?: constructSavePath(context)

            if (targetPath == null) {
                return@withContext ExtractResult(false, null, "Cannot resolve RetroArch save path")
            }

            val parentDir = File(targetPath).parent
            if (parentDir != null) fal.mkdirs(parentDir)

            val success = fal.copyFile(tempFile.absolutePath, targetPath)
            if (!success) {
                Logger.error(TAG, "extractDownload: Failed to copy file | target=$targetPath")
                return@withContext ExtractResult(false, null, "Failed to copy RetroArch save")
            }

            Logger.debug(TAG, "extractDownload: Complete | target=$targetPath")
            ExtractResult(true, targetPath)
        }

    fun discoverSavePath(context: SaveContext): String? {
        val packageName = getPackageName(context.emulatorId)
        val coreName = SavePathRegistry.getRetroArchCore(context.platformSlug)
        if (coreName == null) {
            Logger.debug(TAG, "discoverSavePath: No core mapping for platform | platform=${context.platformSlug}")
            return null
        }

        val contentDir = context.romPath?.let { File(it).parent }

        // RetroArchConfigParser.resolveSavePaths handles:
        // - savefile_directory from config
        // - savefiles_in_content_dir setting
        // - Core-specific subdirectories
        // - Fallback paths based on install type
        val paths = retroArchConfigParser.resolveSavePaths(
            packageName, context.platformSlug, coreName, contentDir, null
        )

        val baseName = buildSaveFileName(context)

        for (basePath in paths) {
            for (ext in SAVE_EXTENSIONS) {
                val candidate = "$basePath/$baseName.$ext"
                if (fal.exists(candidate)) {
                    Logger.debug(TAG, "discoverSavePath: Found save | path=$candidate")
                    return candidate
                }
            }
        }

        Logger.debug(TAG, "discoverSavePath: No save found | baseName=$baseName, pathsChecked=${paths.size}")
        return null
    }

    fun constructSavePath(context: SaveContext): String? {
        val packageName = getPackageName(context.emulatorId)
        val coreName = SavePathRegistry.getRetroArchCore(context.platformSlug)
        if (coreName == null) {
            Logger.debug(TAG, "constructSavePath: No core mapping for platform | platform=${context.platformSlug}")
            return null
        }

        // Parse config to get actual save directory (respects user settings)
        val raConfig = retroArchConfigParser.parse(packageName)
        val saveDir = raConfig?.savefileDirectory
            ?: "/storage/emulated/0/RetroArch/saves"

        val baseName = buildSaveFileName(context)
        val savePath = "$saveDir/$coreName/$baseName.srm"
        Logger.debug(TAG, "constructSavePath: Constructed | path=$savePath")
        return savePath
    }

    private fun getPackageName(emulatorId: String): String = when (emulatorId) {
        "retroarch_64" -> "com.retroarch.aarch64"
        else -> "com.retroarch"
    }

    private fun buildSaveFileName(context: SaveContext): String {
        // RetroArch uses ROM filename (without extension) as save filename
        if (context.romPath != null) {
            return File(context.romPath).nameWithoutExtension
        }
        // Fallback: sanitize game title
        return context.gameTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
