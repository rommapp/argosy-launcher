package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetroArchPathResolver @Inject constructor(
    private val parser: RetroArchConfigParser,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
) {
    sealed interface DisplayPath {
        data object ContentDirectory : DisplayPath
        data class Resolved(val path: String) : DisplayPath
        data object Unknown : DisplayPath
    }

    data class Request(
        val emulatorId: String,
        val coreName: String?,
        val romPath: String?,
    )

    /**
     * A resolved display path together with the configuration it was derived from, so a caller
     * can state which retroarch.cfg is in play instead of assuming one was read.
     */
    data class SavePathDisplay(
        val path: DisplayPath,
        val source: RetroArchConfigSource
    )

    suspend fun describeSavePath(req: Request): SavePathDisplay {
        val packageName = packageForEmulatorId(req.emulatorId)
        val source = parser.locateConfig(packageName)
        val raConfig = (source as? RetroArchConfigSource.Loaded)?.save
        if (raConfig?.savefilesInContentDir == true) {
            return SavePathDisplay(DisplayPath.ContentDirectory, source)
        }

        val override = userSaveOverride(req.emulatorId)
        val candidates = parser.resolveSavePathsWithConfig(
            config = raConfig,
            contentDirName = contentDirName(req.romPath),
            coreName = mappedCoreName(req.coreName),
            contentDirectory = contentDirectory(req.romPath),
            basePathOverride = override,
        )
        val path = candidates.firstOrNull()?.let { DisplayPath.Resolved(it) } ?: DisplayPath.Unknown
        return SavePathDisplay(path, source)
    }

    suspend fun displaySavePath(req: Request): DisplayPath = describeSavePath(req).path

    suspend fun displayStatePath(req: Request): DisplayPath {
        val packageName = packageForEmulatorId(req.emulatorId)
        val raConfig = parser.parseStateConfig(packageName)
        if (raConfig?.savestatesInContentDir == true) return DisplayPath.ContentDirectory

        val override = userStateOverride(req.emulatorId)
        val candidates = parser.resolveStatePathsWithConfig(
            config = raConfig,
            contentDirName = contentDirName(req.romPath),
            coreName = mappedCoreName(req.coreName),
            contentDirectory = contentDirectory(req.romPath),
            basePathOverride = override,
        )
        return candidates.firstOrNull()?.let { DisplayPath.Resolved(it) } ?: DisplayPath.Unknown
    }

    suspend fun resolveSaveDirectories(req: Request): List<String> {
        val override = userSaveOverride(req.emulatorId)
        return parser.resolveSavePaths(
            packageName = packageForEmulatorId(req.emulatorId),
            contentDirName = contentDirName(req.romPath),
            coreName = mappedCoreName(req.coreName),
            contentDirectory = contentDirectory(req.romPath),
            basePathOverride = override,
        )
    }

    suspend fun resolveStateDirectories(req: Request): List<String> {
        val override = userStateOverride(req.emulatorId)
        return parser.resolveStatePaths(
            packageName = packageForEmulatorId(req.emulatorId),
            contentDirName = contentDirName(req.romPath),
            coreName = mappedCoreName(req.coreName),
            contentDirectory = contentDirectory(req.romPath),
            basePathOverride = override,
        )
    }

    suspend fun resolveSaveDirectoriesForPlatform(req: Request, platformSlug: String): List<String> {
        if (req.coreName != null) return resolveSaveDirectories(req)

        val override = userSaveOverride(req.emulatorId)
        val packageName = packageForEmulatorId(req.emulatorId)
        val contentDirName = contentDirName(req.romPath)
        val contentDirectory = contentDirectory(req.romPath)
        val corePatterns = EmulatorRegistry.getRetroArchCorePatterns()[platformSlug].orEmpty()

        val fanOut = corePatterns.flatMap { core ->
            parser.resolveSavePaths(packageName, contentDirName, core, contentDirectory, override)
        }
        val noCore = parser.resolveSavePaths(packageName, contentDirName, null, contentDirectory, override)
        return fanOut + noCore
    }

    suspend fun buildSaveFilePath(req: Request, romBaseName: String, saveExtension: String): String? {
        val dirs = resolveSaveDirectories(req)
        val baseDir = dirs.firstOrNull { File(it).exists() } ?: dirs.firstOrNull() ?: return null
        val ext = saveExtension.trimStart('.').ifEmpty { "srm" }
        return "$baseDir/$romBaseName.$ext"
    }

    suspend fun buildStateFilePath(req: Request, romBaseName: String, slot: Int): String? {
        val dirs = resolveStateDirectories(req)
        val baseDir = dirs.firstOrNull { File(it).exists() } ?: dirs.firstOrNull() ?: return null
        val suffix = if (slot <= 0) "" else slot.toString()
        return "$baseDir/$romBaseName.state$suffix"
    }

    private suspend fun userSaveOverride(emulatorId: String): String? =
        emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserOverride }
            ?.savePathPattern
            ?.takeIf { it.isNotBlank() }

    private suspend fun userStateOverride(emulatorId: String): String? =
        emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserStateOverride }
            ?.statePathPattern
            ?.takeIf { it.isNotBlank() }

    private fun mappedCoreName(coreName: String?): String? =
        coreName?.let { EmulatorRegistry.getRetroArchSaveDirName(it) }

    private fun contentDirName(romPath: String?): String? =
        romPath?.let { File(it).parentFile?.name }

    private fun contentDirectory(romPath: String?): String? =
        romPath?.let { File(it).parent }

    companion object {
        fun packageForEmulatorId(emulatorId: String): String = when (emulatorId) {
            "retroarch_64" -> "com.retroarch.aarch64"
            "retroarch_32" -> "com.retroarch.ra32"
            else -> "com.retroarch"
        }

        fun isRetroArch(emulatorId: String): Boolean =
            emulatorId == "retroarch" || emulatorId == "retroarch_64" || emulatorId == "retroarch_32"
    }
}
