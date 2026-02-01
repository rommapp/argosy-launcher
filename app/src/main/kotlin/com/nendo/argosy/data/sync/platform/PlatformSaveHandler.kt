package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.emulator.SavePathConfig
import java.io.File

interface PlatformSaveHandler {
    suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave?
    suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult
}

data class SaveContext(
    val config: SavePathConfig,
    val romPath: String?,
    val titleId: String?,
    val emulatorPackage: String?,
    val gameId: Long,
    val gameTitle: String,
    val platformSlug: String,
    val emulatorId: String,
    val localSavePath: String? = null
)

data class PreparedSave(
    val file: File,
    val isTemporary: Boolean,
    val originalPaths: List<String> = emptyList()
)

data class ExtractResult(
    val success: Boolean,
    val targetPath: String?,
    val error: String? = null
)
