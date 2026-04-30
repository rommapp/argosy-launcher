package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdExtractor
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SavePathResolver"

@Singleton
class SavePathResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val gameDao: GameDao,
    private val retroArchConfigParser: RetroArchConfigParser,
    private val retroArchPathResolver: com.nendo.argosy.data.emulator.RetroArchPathResolver,
    private val titleIdExtractor: TitleIdExtractor,
    private val titleDbRepository: TitleDbRepository,
    private val saveArchiver: SaveArchiver,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry
) {
    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedTitleId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null,
        isFolderSaveSyncEnabled: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (emulatorId == "default" || emulatorId.isBlank()) {
            Logger.warn(TAG, "[SaveSync] DISCOVER | Invalid emulatorId='$emulatorId' | game=$gameTitle, platform=$platformSlug")
            return@withContext null
        }

        val config = emulatorPackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, platformSlug) }
            ?: SavePathRegistry.getConfigForPlatform(emulatorId, platformSlug)
        if (config == null) {
            Logger.warn(TAG, "[SaveSync] DISCOVER | No save path config | emulatorId=$emulatorId, emulatorPackage=$emulatorPackage")
            return@withContext null
        }

        val effectiveEmulatorId = config.emulatorId
        val userConfig = emulatorSaveConfigDao.getByEmulator(effectiveEmulatorId)
        val isRetroArch = effectiveEmulatorId == "retroarch" || effectiveEmulatorId == "retroarch_64"

        if (userConfig?.isUserOverride == true && !isRetroArch) {
            if (config.usesFolderBasedSaves && romPath != null) {
                if (!isFolderSaveSyncEnabled) {
                    return@withContext null
                }
                return@withContext discoverFolderSavePath(
                    config = config,
                    platformSlug = platformSlug,
                    romPath = romPath,
                    cachedTitleId = cachedTitleId,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    gameTitle = gameTitle,
                    basePathOverride = userConfig.savePathPattern,
                    isFolderSaveSyncEnabled = isFolderSaveSyncEnabled
                )
            }
            if (config.usesGciFormat && romPath != null) {
                val gciSave = discoverGciSavePath(config, romPath, userConfig.savePathPattern)
                if (gciSave != null) {
                    Logger.debug(TAG, "discoverSavePath: GCI save found (user override) at $gciSave")
                    return@withContext gciSave
                }
            }
            if (romPath != null) {
                val savePath = findSaveByRomName(userConfig.savePathPattern, romPath, config.saveExtensions)
                if (savePath != null) return@withContext savePath
            }
            return@withContext findSaveInPath(userConfig.savePathPattern, gameTitle, config.saveExtensions)
        }

        if (config.usesFolderBasedSaves && romPath != null) {
            if (!isFolderSaveSyncEnabled) {
                return@withContext null
            }
            return@withContext discoverFolderSavePath(
                config = config,
                platformSlug = platformSlug,
                romPath = romPath,
                cachedTitleId = cachedTitleId,
                emulatorPackage = emulatorPackage,
                gameId = gameId,
                gameTitle = gameTitle,
                isFolderSaveSyncEnabled = isFolderSaveSyncEnabled
            )
        }

        if (config.usesGciFormat && romPath != null) {
            val gciSave = discoverGciSavePath(config, romPath)
            if (gciSave != null) {
                Logger.debug(TAG, "discoverSavePath: GCI save found at $gciSave")
                return@withContext gciSave
            }
        }

        if (platformSlug == "psx" && romPath != null) {
            val psxSave = discoverPSXSavePath(config, romPath, emulatorPackage)
            if (psxSave != null) {
                Logger.debug(TAG, "discoverSavePath: PSX save found at $psxSave")
                emulatorSaveConfigDao.upsert(
                    EmulatorSaveConfigEntity(
                        emulatorId = effectiveEmulatorId,
                        savePathPattern = File(psxSave).parent ?: "",
                        isAutoDetected = true,
                        lastVerifiedAt = Instant.now()
                    )
                )
                return@withContext psxSave
            }
        }

        val paths = if (isRetroArch) {
            val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
                emulatorId = effectiveEmulatorId,
                coreName = coreName,
                romPath = romPath,
            )
            if (coreName != null) {
                retroArchPathResolver.resolveSaveDirectories(req)
            } else {
                retroArchPathResolver.resolveSaveDirectoriesForPlatform(req, platformSlug)
            }
        } else {
            resolveSavePaths(config, platformSlug)
        }

        Logger.debug(TAG, "discoverSavePath: searching ${paths.size} paths for '$gameTitle' (romPath=$romPath)")

        if (romPath != null) {
            for (basePath in paths) {
                val savePath = findSaveByRomName(basePath, romPath, config.saveExtensions)
                if (savePath != null) {
                    Logger.debug(TAG, "discoverSavePath: ROM-based match found at $savePath")
                    if (!isRetroArch) {
                        emulatorSaveConfigDao.upsert(
                            EmulatorSaveConfigEntity(
                                emulatorId = effectiveEmulatorId,
                                savePathPattern = File(savePath).parent ?: basePath,
                                isAutoDetected = true,
                                lastVerifiedAt = Instant.now()
                            )
                        )
                    }
                    return@withContext savePath
                }
            }
            Logger.debug(TAG, "discoverSavePath: ROM-based lookup found nothing, trying title match")
        }

        for (basePath in paths) {
            val saveFile = findSaveInPath(basePath, gameTitle, config.saveExtensions)
            if (saveFile != null) {
                Logger.debug(TAG, "discoverSavePath: found save at $saveFile")
                if (!isRetroArch) {
                    emulatorSaveConfigDao.upsert(
                        EmulatorSaveConfigEntity(
                            emulatorId = effectiveEmulatorId,
                            savePathPattern = basePath,
                            isAutoDetected = true,
                            lastVerifiedAt = Instant.now()
                        )
                    )
                }
                return@withContext saveFile
            }
        }

        Logger.debug(TAG, "discoverSavePath: FAILED - no save found for '$gameTitle' | emulatorId=$effectiveEmulatorId, package=$emulatorPackage, core=$coreName, platformSlug=$platformSlug, romPath=$romPath, candidatePaths=${paths.size}")
        null
    }

    private suspend fun discoverFolderSavePath(
        config: SavePathConfig,
        platformSlug: String,
        romPath: String,
        cachedTitleId: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null,
        gameTitle: String? = null,
        basePathOverride: String? = null,
        allowCacheRefresh: Boolean = true,
        isFolderSaveSyncEnabled: Boolean = false
    ): String? {
        val romFile = File(romPath)
        val resolvedPaths = if (basePathOverride != null) {
            val effectivePath = if (platformSlug == "switch") {
                switchSaveHandler.resolveBasePath(
                    SavePathRegistry.getConfig(config.emulatorId)!!,
                    basePathOverride,
                    emulatorPackage
                ) ?: basePathOverride
            } else {
                saveHandlerRegistry.getFolderHandler(platformSlug)
                    ?.resolveBasePath(config, basePathOverride)
                    ?: basePathOverride
            }
            listOf(effectivePath)
        } else {
            SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)
        }
        val triedTitleIds = mutableSetOf<String>()
        val isSwitchPlatform = platformSlug == "switch"

        val validatedCachedTitleId = if (cachedTitleId != null && isSwitchPlatform) {
            if (switchSaveHandler.isValidTitleId(cachedTitleId)) {
                cachedTitleId
            } else {
                Logger.warn(TAG, "[SaveSync] DISCOVER | Invalid cached titleId=$cachedTitleId (doesn't start with 01), clearing")
                if (gameId != null) {
                    gameDao.updateTitleId(gameId, null)
                }
                null
            }
        } else {
            cachedTitleId
        }

        if (validatedCachedTitleId != null) {
            triedTitleIds.add(validatedCachedTitleId.uppercase())
            Logger.debug(TAG, "[SaveSync] DISCOVER | Trying cached titleId=$validatedCachedTitleId")
            for (basePath in resolvedPaths) {
                val saveFolder = findSaveFolderByTitleId(basePath, validatedCachedTitleId, platformSlug)
                if (saveFolder != null) return saveFolder
            }
            Logger.debug(TAG, "[SaveSync] DISCOVER | Cached titleId=$validatedCachedTitleId found no save")
        }

        val extractionResult = titleIdExtractor.extractTitleIdWithSource(romFile, platformSlug, emulatorPackage)
        if (extractionResult != null && extractionResult.titleId.uppercase() !in triedTitleIds) {
            val extractedTitleId = extractionResult.titleId
            triedTitleIds.add(extractedTitleId.uppercase())
            Logger.debug(TAG, "[SaveSync] DISCOVER | Trying extracted titleId=$extractedTitleId (fromBinary=${extractionResult.fromBinary})")

            val shouldCacheExtracted = gameId != null && validatedCachedTitleId == null &&
                (!isSwitchPlatform || switchSaveHandler.isValidTitleId(extractedTitleId))
            if (shouldCacheExtracted) {
                Logger.debug(TAG, "[SaveSync] DISCOVER | Caching extracted titleId=$extractedTitleId for gameId=$gameId, locked=${extractionResult.fromBinary}")
                gameDao.setTitleIdWithLock(gameId, extractedTitleId, extractionResult.fromBinary)
            } else if (gameId != null && isSwitchPlatform && !switchSaveHandler.isValidTitleId(extractedTitleId)) {
                Logger.warn(TAG, "[SaveSync] DISCOVER | Skipping cache of invalid extracted titleId=$extractedTitleId (doesn't start with 01)")
            }

            for (basePath in resolvedPaths) {
                val saveFolder = findSaveFolderByTitleId(basePath, extractedTitleId, platformSlug)
                if (saveFolder != null) {
                    return saveFolder
                }
            }
        }

        val allCandidates = mutableListOf<String>()
        if (gameId != null) {
            allCandidates.addAll(titleDbRepository.getCachedCandidates(gameId))
        }
        if (gameId != null && gameTitle != null) {
            val remoteCandidates = titleDbRepository.resolveTitleIdCandidates(gameId, gameTitle, platformSlug)
            allCandidates.addAll(remoteCandidates.filter { it !in allCandidates })
        }

        data class SaveMatch(val path: String, val titleId: String, val modTime: Long)
        val matches = mutableListOf<SaveMatch>()

        for (candidate in allCandidates) {
            if (candidate.uppercase() in triedTitleIds) continue
            triedTitleIds.add(candidate.uppercase())
            Logger.debug(TAG, "[SaveSync] DISCOVER | Trying candidate titleId=$candidate")
            for (basePath in resolvedPaths) {
                val saveFolder = findSaveFolderByTitleId(basePath, candidate, platformSlug)
                if (saveFolder != null) {
                    val modTime = findNewestFileTime(saveFolder)
                    Logger.debug(TAG, "[SaveSync] DISCOVER | Found match | titleId=$candidate, path=$saveFolder, modTime=$modTime")
                    matches.add(SaveMatch(saveFolder, candidate, modTime))
                }
            }
        }

        if (matches.isNotEmpty()) {
            val best = matches.maxByOrNull { it.modTime }!!
            Logger.debug(TAG, "[SaveSync] DISCOVER | Selected best match | titleId=${best.titleId}, path=${best.path}, modTime=${best.modTime}")
            if (gameId != null && (!isSwitchPlatform || switchSaveHandler.isValidTitleId(best.titleId))) {
                gameDao.updateTitleId(gameId, best.titleId)
            } else if (gameId != null && isSwitchPlatform && !switchSaveHandler.isValidTitleId(best.titleId)) {
                Logger.warn(TAG, "[SaveSync] DISCOVER | Skipping cache of invalid best match titleId=${best.titleId} (doesn't start with 01)")
            }
            return best.path
        }

        // Don't clear title ID cache here - the title ID may be correct even if no save exists yet
        // (e.g., downloading a save for the first time). Let the caller handle construction.

        Logger.debug(TAG, "[SaveSync] DISCOVER | No save found | title=$gameTitle, tried=${triedTitleIds.size} titleIds")
        return null
    }

    private fun discoverPSXSavePath(
        config: SavePathConfig,
        romPath: String,
        emulatorPackage: String?
    ): String? {
        val romNameNoExt = File(romPath).nameWithoutExtension
        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)

        // DuckStation PerGameFileTitle mode: {romFilenameNoExt}_1.mcd
        for (basePath in resolvedPaths) {
            if (!fal.exists(basePath) || !fal.isDirectory(basePath)) continue
            val mcdPath = "$basePath/${romNameNoExt}_1.mcd"
            if (fal.exists(mcdPath) && fal.isFile(mcdPath)) {
                Logger.debug(TAG, "[SaveSync] DISCOVER | PSX memcard found | path=$mcdPath")
                return mcdPath
            }
        }

        Logger.debug(TAG, "[SaveSync] DISCOVER | PSX memcard not found | rom=$romNameNoExt")
        return null
    }

    private fun discoverGciSavePath(
        config: SavePathConfig,
        romPath: String,
        basePathOverride: String? = null
    ): String? {
        val gciPaths = gciSaveHandler.discoverAllSavePaths(config, romPath, basePathOverride)
        return gciPaths.firstOrNull()
    }

    private fun findSaveFolderByTitleId(basePath: String, titleId: String, platformSlug: String): String? {
        if (!directoryExists(basePath)) {
            Logger.debug(TAG, "[SaveSync] DISCOVER | Base path does not exist | path=$basePath")
            return null
        }
        Logger.debug(TAG, "[SaveSync] DISCOVER | Scanning base path | path=$basePath, titleId=$titleId, platform=$platformSlug")

        if (platformSlug == "switch") {
            return switchSaveHandler.findSaveFolderByTitleId(basePath, titleId)
        }
        return saveHandlerRegistry.getFolderHandler(platformSlug)
            ?.findSaveFolderByTitleId(basePath, titleId)
    }

    fun findNewestFileTime(folderPath: String): Long {
        var newest = 0L
        fal.listFiles(folderPath)?.forEach { child ->
            if (child.isFile) {
                if (child.lastModified > newest) {
                    newest = child.lastModified
                }
            } else if (child.isDirectory) {
                val childNewest = findNewestFileTime(child.path)
                if (childNewest > newest) {
                    newest = childNewest
                }
            }
        }
        return newest
    }

    private fun findSaveByRomName(basePath: String, romPath: String, extensions: List<String>): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.verbose(TAG) { "findSaveByRomName: dir does not exist: $basePath" }
            return null
        }

        val romFile = File(romPath)
        val romName = romFile.nameWithoutExtension
        val isZipContainer = romFile.extension.equals("zip", ignoreCase = true)

        Logger.verbose(TAG) {
            "findSaveByRomName: romPath=${romFile.name}, romName=$romName, isZip=$isZipContainer, extensions=$extensions, searchDir=$basePath"
        }

        if (isZipContainer) {
            Logger.verbose(TAG) {
                "findSaveByRomName: WARNING - ROM is in ZIP container, save filename may differ from container name '$romName'"
            }
        }

        for (ext in extensions) {
            if (ext == "*") continue
            val savePath = "$basePath/$romName.$ext"
            Logger.verbose(TAG) { "findSaveByRomName: checking $romName.$ext -> exists=${fal.exists(savePath)}" }
            if (fal.exists(savePath) && fal.isFile(savePath)) {
                Logger.debug(TAG, "findSaveByRomName: found $savePath")
                return savePath
            }
        }

        if (Logger.isVerbose) {
            val existingFiles = fal.listFiles(basePath)?.filter { it.isFile }?.map { it.name } ?: emptyList()
            Logger.verbose(TAG) {
                "findSaveByRomName: no match for '$romName' in $basePath, existing files (${existingFiles.size}): ${existingFiles.take(10).joinToString()}" +
                    if (existingFiles.size > 10) "..." else ""
            }
        }

        return null
    }

    private fun findSaveInPath(basePath: String, gameTitle: String, extensions: List<String>): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.verbose(TAG) { "findSaveInPath: dir does not exist: $basePath" }
            return null
        }

        val sanitizedGameTitle = sanitizeFileName(gameTitle)
        val normalizedGameTitle = gameTitle.lowercase()

        for (ext in extensions) {
            if (ext == "*") continue

            val directPath = "$basePath/$sanitizedGameTitle.$ext"
            Logger.verbose(TAG) { "findSaveInPath: checking exact match $directPath -> exists=${fal.exists(directPath)}" }
            if (fal.exists(directPath) && fal.isFile(directPath)) {
                Logger.debug(TAG, "findSaveInPath: exact match $directPath")
                return directPath
            }
        }

        val files = fal.listFiles(basePath) ?: return null
        for (file in files) {
            if (!file.isFile) continue
            val fileName = file.name
            val fileExt = fileName.substringAfterLast('.', "").lowercase()
            if (fileExt !in extensions && "*" !in extensions) continue

            val fileBaseName = fileName.substringBeforeLast('.').lowercase()
            if (fileBaseName == normalizedGameTitle) {
                Logger.debug(TAG, "findSaveInPath: case-insensitive match ${file.path}")
                return file.path
            }

            if (normalizedGameTitle.contains(fileBaseName) || fileBaseName.contains(normalizedGameTitle)) {
                Logger.debug(TAG, "findSaveInPath: fuzzy match ${file.path}")
                return file.path
            }
        }

        Logger.verbose(TAG) { "findSaveInPath: no match for '$gameTitle' in $basePath" }
        return null
    }

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?,
        coreName: String? = null
    ): String? {
        val config = SavePathRegistry.getConfig(emulatorId) ?: run {
            Logger.debug(TAG, "constructSavePath: FAILED - no SavePathConfig | emulatorId=$emulatorId, platformSlug=$platformSlug")
            return null
        }

        if (emulatorId == "retroarch" || emulatorId == "retroarch_64") {
            return constructRetroArchSavePath(emulatorId, gameTitle, platformSlug, romPath, coreName)
        }

        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val baseDir = if (userConfig?.isUserOverride == true) {
            val userPath = userConfig.savePathPattern
            if (directoryExists(userPath) || saveArchiver.getFileForPath(userPath).mkdirs()) userPath else null
        } else {
            null
        } ?: run {
            val resolvedPaths = resolveSavePaths(config, platformSlug)
            if (resolvedPaths.isEmpty()) {
                Logger.debug(TAG, "constructSavePath: FAILED - no candidate paths | emulatorId=$emulatorId, platformSlug=$platformSlug, configEmulatorId=${config.emulatorId}")
            }
            resolvedPaths.firstOrNull { directoryExists(it) }
                ?: resolvedPaths.firstOrNull()
        } ?: return null

        if (platformSlug == "psx" && romPath != null) {
            val romNameNoExt = File(romPath).nameWithoutExtension
            return "$baseDir/${romNameNoExt}_1.mcd"
        }

        val extension = config.saveExtensions.firstOrNull { it != "*" } ?: "sav"
        val sanitizedName = sanitizeFileName(gameTitle)
        val fileName = "$sanitizedName.$extension"

        return "$baseDir/$fileName"
    }

    private suspend fun constructRetroArchSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?,
        preferredCore: String? = null
    ): String? {
        val raConfig = retroArchConfigParser.parse(
            com.nendo.argosy.data.emulator.RetroArchPathResolver.packageForEmulatorId(emulatorId)
        )
        val resolvedCore = preferredCore
            ?: SavePathRegistry.getRetroArchCore(platformSlug, raConfig?.lastLoadedCore)
            ?: run {
                Logger.debug(TAG, "constructRetroArchSavePath: FAILED - no core for platform | emulatorId=$emulatorId, platformSlug=$platformSlug, raLastLoaded=${raConfig?.lastLoadedCore}")
                return null
            }
        val saveConfig = SavePathRegistry.getConfig(emulatorId) ?: run {
            Logger.debug(TAG, "constructRetroArchSavePath: FAILED - no SavePathConfig for emulatorId=$emulatorId")
            return null
        }
        val extension = saveConfig.saveExtensions.firstOrNull() ?: "srm"

        val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
            emulatorId = emulatorId,
            coreName = resolvedCore,
            romPath = romPath,
        )
        val dirs = retroArchPathResolver.resolveSaveDirectories(req)
        val baseDir = dirs.firstOrNull { directoryExists(it) } ?: dirs.firstOrNull() ?: run {
            Logger.debug(TAG, "constructRetroArchSavePath: FAILED - no save directories | emulatorId=$emulatorId, core=$resolvedCore, platformSlug=$platformSlug, romPath=$romPath")
            return null
        }

        val fileName = buildRetroArchFileName(gameTitle, romPath, extension)
        return "$baseDir/$fileName"
    }

    private fun buildRetroArchFileName(gameTitle: String, romPath: String?, extension: String): String {
        if (romPath != null) {
            val romFile = File(romPath)
            val romName = romFile.nameWithoutExtension
            val isZipContainer = romFile.extension.equals("zip", ignoreCase = true)
            val result = "$romName.$extension"

            Logger.verbose(TAG) {
                "buildRetroArchFileName: romPath=${romFile.name}, derived=$result, isZip=$isZipContainer"
            }

            if (isZipContainer) {
                Logger.verbose(TAG) {
                    "buildRetroArchFileName: WARNING - using ZIP container name '$romName', but RetroArch may use inner ROM filename for saves"
                }
            }

            return result
        }

        val sanitized = gameTitle
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val result = "$sanitized.$extension"
        Logger.verbose(TAG) { "buildRetroArchFileName: no romPath, using sanitized title -> $result" }
        return result
    }

    suspend fun constructFolderSavePathWithOverride(
        emulatorId: String,
        platformSlug: String,
        romPath: String?,
        gameId: Long,
        gameTitle: String,
        cachedTitleId: String? = null,
        emulatorPackage: String? = null
    ): String? {
        val config = SavePathRegistry.getConfigForPlatform(emulatorId, platformSlug) ?: return null
        if (!config.usesFolderBasedSaves) return null

        val userConfig = emulatorSaveConfigDao.getByEmulator(config.emulatorId)
        val basePathOverride = userConfig?.takeIf { it.isUserOverride }?.savePathPattern

        val baseDir = if (platformSlug == "switch") {
            switchSaveHandler.resolveBasePath(config, basePathOverride, emulatorPackage)
        } else {
            saveHandlerRegistry.getFolderHandler(platformSlug)
                ?.resolveBasePath(config, basePathOverride)
                ?: basePathOverride
                ?: config.defaultPaths.firstOrNull { directoryExists(it) }
                ?: config.defaultPaths.firstOrNull()
        } ?: return null

        if (!directoryExists(baseDir)) {
            Logger.debug(TAG, "[SaveSync] CONSTRUCT | Base dir does not exist | path=$baseDir")
            return null
        }

        val titleId = cachedTitleId
            ?: (romPath?.let { titleIdExtractor.extractTitleId(File(it), platformSlug, emulatorPackage) })
            ?: titleDbRepository.resolveTitleId(gameId, gameTitle, platformSlug)

        if (titleId == null) {
            Logger.debug(TAG, "[SaveSync] CONSTRUCT | Cannot determine titleId | gameId=$gameId")
            return null
        }

        Logger.debug(TAG, "[SaveSync] CONSTRUCT | Building path | baseDir=$baseDir, titleId=$titleId, platform=$platformSlug")

        if (platformSlug == "switch") {
            return switchSaveHandler.constructSavePath(baseDir, titleId, emulatorPackage)
        }
        return saveHandlerRegistry.getFolderHandler(platformSlug)
            ?.constructSavePath(baseDir, titleId)
    }

    suspend fun resolveSwitchSaveTargetPath(
        zipFile: File,
        config: SavePathConfig,
        emulatorPackage: String?
    ): String? {
        val userConfig = emulatorSaveConfigDao.getByEmulator(config.emulatorId)
        val basePathOverride = userConfig?.takeIf { it.isUserOverride }?.savePathPattern
        return switchSaveHandler.resolveSaveTargetPath(zipFile, config, emulatorPackage, basePathOverride)
    }

    private fun resolveSavePaths(config: SavePathConfig, platformSlug: String): List<String> {
        val filesDir = if (config.usesInternalStorage) context.filesDir.absolutePath else null
        return SavePathRegistry.resolvePath(config, platformSlug, filesDir)
    }

    private fun directoryExists(path: String): Boolean = fal.exists(path) && fal.isDirectory(path)

    private fun sanitizeFileName(name: String): String = name
        .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
}
