package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TexturePackInstaller(
    private val builtinSaveBase: BuiltinSaveBase,
    private val builtinCoreResolver: BuiltinCoreResolver,
    private val emulatorResolver: EmulatorResolver,
    private val titleIdExtractor: TitleIdExtractor,
    private val baseRomFileResolver: BaseRomFileResolver,
    private val platformFolderResolver: PlatformFolderResolver,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val systemDir: File,
    private val extractor: TexturePackExtractor = TexturePackExtractor(),
    private val fileAccessLayer: FileAccessLayer? = null,
    private val cacheDir: File? = null
) {
    @Inject
    constructor(
        builtinSaveBase: BuiltinSaveBase,
        builtinCoreResolver: BuiltinCoreResolver,
        emulatorResolver: EmulatorResolver,
        titleIdExtractor: TitleIdExtractor,
        baseRomFileResolver: BaseRomFileResolver,
        platformFolderResolver: PlatformFolderResolver,
        gameDao: GameDao,
        gameDiscDao: GameDiscDao,
        gameFileDao: GameFileDao,
        @ApplicationContext context: Context,
        extractor: TexturePackExtractor,
        fileAccessLayer: FileAccessLayer
    ) : this(
        builtinSaveBase = builtinSaveBase,
        builtinCoreResolver = builtinCoreResolver,
        emulatorResolver = emulatorResolver,
        titleIdExtractor = titleIdExtractor,
        baseRomFileResolver = baseRomFileResolver,
        platformFolderResolver = platformFolderResolver,
        gameDao = gameDao,
        gameDiscDao = gameDiscDao,
        gameFileDao = gameFileDao,
        systemDir = AppPaths.libretroSystemDir(context.filesDir),
        extractor = extractor,
        fileAccessLayer = fileAccessLayer,
        cacheDir = context.cacheDir
    )

    sealed class ModInstallOutcome {
        data class Success(val manifestFile: File) : ModInstallOutcome()
        data class Deferred(val reason: String) : ModInstallOutcome()
        data class Failed(val reason: String) : ModInstallOutcome()
    }

    private data class TargetResolution(
        val targetDir: File,
        val folderKey: String,
        val cacheDir: File?,
        val isDolphin: Boolean,
        val builtinRule: BuiltinTextureRule? = null,
        val externalRule: ExternalTextureRule? = null
    ) {
        val packSubdir: String? get() = builtinRule?.packSubdir ?: externalRule?.packSubdir
    }

    suspend fun installPendingModFilesForGame(
        gameId: Long,
        extractedTitleId: TitleIdResult? = null,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext 0
        val romFile = resolveRomFile(game)
        if (romFile == null || !romFile.isFile) {
            Logger.debug(TAG, "Deferring texture pack install for ${game.title} until base ROM is on disk")
            return@withContext 0
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            game.id,
            game.platformId,
            game.platformSlug
        )
        val isBuiltIn = emulatorPackage == null || emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE
        val hasRule = if (isBuiltIn) {
            val coreId = builtinCoreResolver.resolveCoreId(game.id, game.platformId, game.platformSlug)
            BuiltinTexturePathRegistry.resolveRule(coreId, game.platformSlug) != null
        } else {
            ExternalTexturePathRegistry.resolveRule(emulatorPackage, game.platformSlug) != null
        }
        if (!hasRule) return@withContext 0

        val modFiles = gameFileDao.getModFilesForGame(gameId)
        if (modFiles.isEmpty()) return@withContext 0

        val activeTargetResolution = resolveTargetForGame(game, romFile, extractedTitleId, sourceFile = null)
        val activeTargetDir = activeTargetResolution?.targetDir
        val activeTargetPaths = listOfNotNull(activeTargetDir, activeTargetResolution?.cacheDir)
            .map { it.canonicalPath }
        val gameFolder = gameFolderOf(game)
        var installedCount = 0

        for (modFile in modFiles) {
            currentCoroutineContext().ensureActive()
            if (isCancelled?.invoke() == true) {
                Logger.info(TAG, "Texture pack install cancelled for ${game.title}")
                throw CancellationException("Texture pack install cancelled for ${game.title}")
            }

            val recordedPath = modFile.localPath
            if (recordedPath != null && isManifestPath(recordedPath) && activeTargetDir != null) {
                val recordedFile = File(recordedPath)
                if (manifestExists(recordedPath)) {
                    val recordedParent = recordedFile.parentFile
                    val isSameTarget = recordedParent != null &&
                        recordedParent.canonicalPath in activeTargetPaths

                    if (isSameTarget) {
                        if (activeTargetResolution.isDolphin) {
                            enableDolphinTextures(activeTargetDir, modFile)
                        }
                        continue
                    }

                    Logger.info(
                        TAG,
                        "Migrating textures for ${game.title} from ${recordedParent?.absolutePath} to ${activeTargetDir.absolutePath}"
                    )
                    val migratedManifest = migrateTextures(
                        oldManifestFile = recordedFile,
                        newTargetDir = activeTargetDir,
                        fileAccessLayer = fileAccessLayer,
                        isCancelled = isCancelled,
                        onProgress = onProgress
                    )
                    if (migratedManifest != null) {
                        gameFileDao.updateLocalPath(modFile.id, migratedManifest.absolutePath, Instant.now())
                        failedInstalls.remove(modFile.id)
                        if (activeTargetResolution.isDolphin) {
                            enableDolphinTextures(activeTargetDir, modFile)
                        }
                        installedCount++
                    } else {
                        failedInstalls[modFile.id] = System.currentTimeMillis()
                        Logger.warn(TAG, "Failed to migrate textures for ${game.title} from ${recordedParent?.absolutePath}")
                    }
                    continue
                }
            }

            if (failedInstalls.containsKey(modFile.id)) {
                continue
            }

            val sourceFile = resolveStagingModFile(modFile, gameFolder) ?: continue
            if (!isSupplementalTexturePack(
                    fileName = modFile.fileName,
                    category = modFile.category,
                    platformSlug = game.platformSlug,
                    localPath = sourceFile.absolutePath
                )
            ) {
                continue
            }

            try {
                val outcome = installForModFileOutcome(
                    game = game,
                    gameFile = modFile,
                    sourceFile = sourceFile,
                    extractedTitleId = extractedTitleId,
                    preValidatedZip = true,
                    isCancelled = isCancelled,
                    onProgress = onProgress
                )
                when (outcome) {
                    is ModInstallOutcome.Success -> {
                        installedCount++
                    }
                    is ModInstallOutcome.Deferred -> {
                        Logger.debug(TAG, "Texture mod ${modFile.fileName} deferred: ${outcome.reason}")
                    }
                    is ModInstallOutcome.Failed -> {
                        failedInstalls[modFile.id] = System.currentTimeMillis()
                        Logger.warn(TAG, "Failed to install texture pack ${modFile.fileName}: ${outcome.reason}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedInstalls[modFile.id] = System.currentTimeMillis()
                Logger.warn(TAG, "Failed to install texture pack ${modFile.fileName}: ${e.message}")
            }
        }

        if (installedCount > 0) {
            onProgress?.invoke(1f)
        }
        installedCount
    }

    private fun enableDolphinTextures(targetDir: File, modFile: GameFileEntity) {
        if (DolphinConfigModifier.enableCustomTextures(targetDir, fileAccessLayer)) {
            failedInstalls.remove(modFile.id)
        } else {
            failedInstalls[modFile.id] = System.currentTimeMillis()
            Logger.warn(TAG, "Installed ${modFile.fileName} but could not turn on custom textures in Dolphin's GFX.ini")
        }
    }

    private class MigratedFile(val source: File, val dest: File, val renamed: Boolean)

    private suspend fun migrateTextures(
        oldManifestFile: File,
        newTargetDir: File,
        fileAccessLayer: FileAccessLayer?,
        isCancelled: (() -> Boolean)?,
        onProgress: ((fraction: Float?) -> Unit)?
    ): File? = withContext(Dispatchers.IO) {
        val oldTargetDir = oldManifestFile.parentFile ?: return@withContext null
        val fal = fileAccessLayer
        val isSrcRestricted = fal != null && fal.isRestrictedPath(oldTargetDir.absolutePath)
        val isDestRestricted = fal != null && fal.isRestrictedPath(newTargetDir.absolutePath)

        val oldManifestExists = if (isSrcRestricted) {
            fal.exists(oldManifestFile.absolutePath)
        } else {
            oldManifestFile.isFile
        }
        if (!oldManifestExists) return@withContext null

        val lines = try {
            if (isSrcRestricted) {
                fal.readBytes(oldManifestFile.absolutePath)?.let { String(it, Charsets.UTF_8).lines() }
                    ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            } else {
                oldManifestFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to read old manifest ${oldManifestFile.absolutePath}: ${e.message}")
            return@withContext null
        }
        if (lines.isEmpty()) return@withContext null

        val newManifest = File(newTargetDir, oldManifestFile.name)
        val migrated = mutableListOf<MigratedFile>()
        val migratedRelativePaths = LinkedHashSet<String>()
        val ensuredDirs = mutableSetOf<String>()

        fun ensureDir(dir: File) {
            if (!ensuredDirs.add(dir.absolutePath)) return
            if (isDestRestricted) fal.mkdirs(dir.absolutePath) else dir.mkdirs()
        }

        fun rollBack() {
            for (file in migrated) {
                if (file.renamed) {
                    file.dest.renameTo(file.source)
                } else if (isDestRestricted) {
                    fal.delete(file.dest.absolutePath)
                } else {
                    file.dest.delete()
                }
            }
            if (isDestRestricted) fal.delete(newManifest.absolutePath) else newManifest.delete()
        }

        try {
            ensureDir(newTargetDir)
            for ((index, relPath) in lines.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (isCancelled?.invoke() == true) {
                    throw CancellationException("Texture migration cancelled")
                }
                val srcFile = File(oldTargetDir, relPath)
                val destFile = File(newTargetDir, relPath)
                val sourceExists = if (isSrcRestricted) fal.exists(srcFile.absolutePath) else srcFile.isFile
                if (sourceExists) {
                    destFile.parentFile?.let(::ensureDir)
                    val renamed = !isSrcRestricted && !isDestRestricted && run {
                        if (destFile.exists()) destFile.delete()
                        srcFile.renameTo(destFile)
                    }
                    if (!renamed) {
                        copyVerified(srcFile, destFile, fal, isSrcRestricted, isDestRestricted)
                    }
                    migrated.add(MigratedFile(srcFile, destFile, renamed))
                    migratedRelativePaths.add(relPath)
                }
                onProgress?.invoke(((index + 1).toFloat() / lines.size).coerceIn(0f, 1f))
            }

            if (migrated.isEmpty()) {
                throw IOException("No files from ${oldManifestFile.name} could be found at ${oldTargetDir.absolutePath}")
            }

            val manifestBytes = migratedRelativePaths.joinToString("\n").toByteArray(Charsets.UTF_8)
            if (isDestRestricted) {
                if (!fal.writeBytes(newManifest.absolutePath, manifestBytes)) {
                    throw IOException("Could not write ${newManifest.absolutePath}")
                }
            } else {
                newManifest.writeBytes(manifestBytes)
            }
        } catch (e: CancellationException) {
            rollBack()
            throw e
        } catch (e: Exception) {
            rollBack()
            Logger.warn(TAG, "Texture migration failed, source pack left in place: ${e.message}")
            return@withContext null
        }

        for (file in migrated) {
            if (file.renamed) continue
            if (isSrcRestricted) fal.delete(file.source.absolutePath) else file.source.delete()
        }
        if (isSrcRestricted) fal.delete(oldManifestFile.absolutePath) else oldManifestFile.delete()
        pruneEmptyDirs(migrated.map { it.source }, oldTargetDir, fal, isSrcRestricted)
        newManifest
    }

    private fun copyVerified(
        source: File,
        dest: File,
        fal: FileAccessLayer?,
        isSrcRestricted: Boolean,
        isDestRestricted: Boolean
    ) {
        val input = if (isSrcRestricted) fal?.getInputStream(source.absolutePath) else source.inputStream()
        input ?: throw IOException("Could not read ${source.absolutePath}")
        val output = if (isDestRestricted) fal?.getOutputStream(dest.absolutePath) else FileOutputStream(dest)
        output ?: run {
            input.close()
            throw IOException("Could not write ${dest.absolutePath}")
        }
        val copied = input.use { from -> output.use { to -> from.copyTo(to, 64 * 1024) } }
        val expected = if (isSrcRestricted) fal?.length(source.absolutePath) ?: 0L else source.length()
        if (expected > 0L && copied != expected) {
            throw IOException("Short copy of ${source.absolutePath}: $copied of $expected bytes")
        }
    }

    private fun pruneEmptyDirs(
        removedFiles: List<File>,
        rootDir: File,
        fal: FileAccessLayer?,
        isRestricted: Boolean
    ) {
        val touchedDirs = mutableSetOf<String>()
        for (file in removedFiles) {
            var parent = file.parentFile
            while (parent != null && parent.absolutePath != rootDir.absolutePath && FileNames.isWithin(parent, rootDir)) {
                touchedDirs.add(parent.absolutePath)
                parent = parent.parentFile
            }
        }
        (touchedDirs.sortedByDescending { it.length } + rootDir.absolutePath).forEach { dirPath ->
            if (isRestricted) {
                if (fal?.listFiles(dirPath)?.isEmpty() == true) fal.delete(dirPath)
            } else {
                val dir = File(dirPath)
                if (dir.isDirectory && dir.listFiles().isNullOrEmpty()) dir.delete()
            }
        }
    }

    suspend fun hasInstalledTexturePack(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        gameFileDao.getModFilesForGame(gameId).any { modFile ->
            val path = modFile.localPath
            path != null && isManifestPath(path) && manifestExists(path)
        }
    }

    fun manifestExists(path: String): Boolean =
        if (fileAccessLayer != null && fileAccessLayer.isRestrictedPath(path)) fileAccessLayer.exists(path)
        else File(path).isFile

    fun invalidateFailedInstall(gameFileId: Long) {
        failedInstalls.remove(gameFileId)
    }

    suspend fun failedInstallNames(gameId: Long): List<String> = withContext(Dispatchers.IO) {
        gameFileDao.getModFilesForGame(gameId)
            .filter { failedInstalls.containsKey(it.id) }
            .map { it.fileName }
    }

    suspend fun invalidateFailedInstallsForGame(gameId: Long) = withContext(Dispatchers.IO) {
        gameFileDao.getModFilesForGame(gameId).forEach { modFile ->
            failedInstalls.remove(modFile.id)
        }
    }

    suspend fun resolveAutoCoreOptionsForGame(
        gameId: Long,
        coreId: String?,
        platformSlug: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val rule = BuiltinTexturePathRegistry.resolveRule(coreId = coreId, platformSlug = platformSlug)
            ?: return@withContext emptyMap()
        val installedManifests = gameFileDao.getModFilesForGame(gameId).mapNotNull { modFile ->
            val path = modFile.localPath ?: return@mapNotNull null
            val file = File(path)
            if (isManifestFile(file) && file.isFile) modFile to file else null
        }
        if (installedManifests.isEmpty()) return@withContext emptyMap()

        var hasHtc = false
        var hasHts = false
        for ((modFile, manifestFile) in installedManifests) {
            if (BuiltinTexturePathRegistry.isHtcFileName(modFile.fileName)) hasHtc = true
            if (BuiltinTexturePathRegistry.isHtsFileName(modFile.fileName)) hasHts = true
            val lines = runCatching { manifestFile.readLines() }.getOrDefault(emptyList())
            for (line in lines) {
                val trimmed = line.trim()
                if (BuiltinTexturePathRegistry.isHtcFileName(trimmed)) hasHtc = true
                if (BuiltinTexturePathRegistry.isHtsFileName(trimmed)) hasHts = true
            }
        }
        rule.optionsForPack(hasHtc = hasHtc, hasHts = hasHts)
    }

    private suspend fun gameFolderOf(game: GameEntity): File? {
        val folder = game.localPath?.let { File(it).parentFile }?.takeIf { it.isDirectory }
            ?: return null
        val platformDir = platformFolderResolver.downloadDirForPlatform(game.platformId, game.platformSlug)
        return if (folder.canonicalPath == platformDir.canonicalPath) null else folder
    }

    private suspend fun resolveStagingModFile(modFile: GameFileEntity, gameFolder: File?): File? {
        val recorded = modFile.localPath?.let(::File)
        if (recorded != null && recorded.isFile && !isManifestPath(recorded.absolutePath)) {
            return recorded
        }
        if (gameFolder != null) {
            val candidate = File(File(gameFolder, "mods"), modFile.fileName)
            if (candidate.isFile) return candidate
        }
        val game = gameDao.getById(modFile.gameId) ?: return null
        val platformDir = platformFolderResolver.downloadDirForPlatform(
            game.platformId,
            game.platformSlug
        )
        val platformCandidate = File(File(platformDir, "mods"), modFile.fileName)
        if (platformCandidate.isFile) return platformCandidate
        return null
    }

    private suspend fun resolveRomFile(game: GameEntity): File? {
        val path = game.localPath ?: return null
        return baseRomFileResolver.resolve(game, File(path))
    }

    private suspend fun resolveTargetForGame(
        game: GameEntity,
        romFile: File,
        extractedTitleId: TitleIdResult?,
        sourceFile: File? = null
    ): TargetResolution? {
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            game.id,
            game.platformId,
            game.platformSlug
        )
        val isBuiltIn = emulatorPackage == null || emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE

        if (isBuiltIn) {
            val resolvedCoreId = builtinCoreResolver.resolveCoreId(
                gameId = game.id,
                platformId = game.platformId,
                platformSlug = game.platformSlug
            )
            val rule = BuiltinTexturePathRegistry.resolveRule(
                coreId = resolvedCoreId,
                platformSlug = game.platformSlug
            ) ?: return null

            val baseRomPath = romFile.absolutePath
            val effectiveSaveDir = File(builtinSaveBase.forGame(game, baseRomPath))
            val folderKey = titleIdExtractor.resolveTextureFolderKey(
                strategy = rule.folderKeyStrategy,
                platformSlug = game.platformSlug,
                game = game,
                romFile = romFile,
                zipArchive = sourceFile,
                extractedResult = extractedTitleId
            ) ?: return null

            val targetDir = BuiltinTexturePathRegistry.resolveTargetDir(
                rule = rule,
                saveDir = effectiveSaveDir,
                systemDir = systemDir,
                folderKey = folderKey
            )
            val cacheDir = BuiltinTexturePathRegistry.resolveHtcHtsCacheDir(rule, effectiveSaveDir, systemDir)
            return TargetResolution(
                targetDir = targetDir,
                folderKey = folderKey,
                cacheDir = cacheDir,
                isDolphin = false,
                builtinRule = rule
            )
        } else {
            val extRule = ExternalTexturePathRegistry.resolveRule(
                emulatorPackage = emulatorPackage,
                platformSlug = game.platformSlug
            ) ?: return null

            val folderKey = titleIdExtractor.resolveTextureFolderKey(
                strategy = extRule.folderKeyStrategy,
                platformSlug = game.platformSlug,
                game = game,
                romFile = romFile,
                zipArchive = sourceFile,
                extractedResult = extractedTitleId
            ) ?: return null

            val extRoots = fileAccessLayer?.externalStorageRoots()?.takeIf { it.isNotEmpty() }
                ?: listOf(StoragePathUtils.primaryExternalRoot)

            val targetDir = ExternalTexturePathRegistry.resolveTargetDir(
                rule = extRule,
                emulatorPackage = emulatorPackage,
                externalStorageRoots = extRoots,
                folderKey = folderKey,
                directoryExists = { fileAccessLayer?.exists(it.absolutePath) ?: it.exists() }
            ) ?: return null

            val isDolphin = emulatorPackage.contains("dolphin", ignoreCase = true)
            return TargetResolution(
                targetDir = targetDir,
                folderKey = folderKey,
                cacheDir = null,
                isDolphin = isDolphin,
                externalRule = extRule
            )
        }
    }

    suspend fun installForModFileOutcome(
        game: GameEntity,
        gameFile: GameFileEntity,
        sourceFile: File,
        extractedTitleId: TitleIdResult? = null,
        preValidatedZip: Boolean = false,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): ModInstallOutcome = withContext(Dispatchers.IO) {
        val romFile = resolveRomFile(game)
        if (romFile == null || !romFile.isFile) {
            Logger.debug(TAG, "Deferring texture pack install for ${game.title} until base ROM is on disk")
            return@withContext ModInstallOutcome.Deferred("Base ROM not yet on disk")
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            game.id,
            game.platformId,
            game.platformSlug
        )
        val isBuiltIn = emulatorPackage == null || emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE
        val ruleExists = if (isBuiltIn) {
            val resolvedCoreId = builtinCoreResolver.resolveCoreId(
                gameId = game.id,
                platformId = game.platformId,
                platformSlug = game.platformSlug
            )
            BuiltinTexturePathRegistry.resolveRule(
                coreId = resolvedCoreId,
                platformSlug = game.platformSlug
            ) != null
        } else {
            ExternalTexturePathRegistry.resolveRule(
                emulatorPackage = emulatorPackage,
                platformSlug = game.platformSlug
            ) != null
        }

        if (!ruleExists) {
            return@withContext ModInstallOutcome.Deferred("No texture path rule for game")
        }

        val targetResolution = resolveTargetForGame(game, romFile, extractedTitleId, sourceFile)
            ?: return@withContext ModInstallOutcome.Failed("Could not resolve folder key")

        val targetDir = targetResolution.targetDir
        val cacheDir = targetResolution.cacheDir
        val folderKey = targetResolution.folderKey

        if (BuiltinTexturePathRegistry.isHtcOrHtsFileName(sourceFile.name)) {
            val builtinRule = targetResolution.builtinRule
                ?: return@withContext ModInstallOutcome.Failed("HTC/HTS only supported by built-in cores")
            val isHts = BuiltinTexturePathRegistry.isHtsFileName(sourceFile.name)
            val isHtc = BuiltinTexturePathRegistry.isHtcFileName(sourceFile.name)
            if ((isHts && !builtinRule.supportsHts) || (isHtc && !builtinRule.supportsHtc)) {
                Logger.warn(TAG, "Core for ${game.platformSlug} does not support ${sourceFile.extension} texture packs")
                return@withContext ModInstallOutcome.Failed("Unsupported HTC/HTS format")
            }
            val targetCacheDir = cacheDir ?: return@withContext ModInstallOutcome.Failed("Cache directory unresolvable")
            val manifest = extractor.installHtcOrHtsFile(
                sourceFile = sourceFile,
                cacheDir = targetCacheDir,
                gameFileId = gameFile.id,
                fileAccessLayer = fileAccessLayer,
                isCancelled = isCancelled,
                onProgress = onProgress
            ) ?: return@withContext ModInstallOutcome.Failed("Failed to install HTC/HTS file")
            try {
                gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
            } catch (e: Throwable) {
                TexturePackExtractor.uninstallFromManifestFile(manifest, fileAccessLayer)
                throw e
            }
            if (sourceFile.exists() && sourceFile.canonicalPath != manifest.canonicalPath) {
                TexturePackExtractor.deleteSourceAndPruneEmptyModParent(sourceFile)
            }
            return@withContext ModInstallOutcome.Success(manifest)
        }

        if (!preValidatedZip && !looksLikeTextureZip(sourceFile)) {
            return@withContext ModInstallOutcome.Deferred("Not a texture zip")
        }

        val builtinRule = targetResolution.builtinRule
        val htcHtsZipSummary = if (builtinRule != null && (builtinRule.supportsHtc || builtinRule.supportsHts)) {
            extractor.inspectHtcHtsOnlyZip(sourceFile)
        } else {
            null
        }
        if (htcHtsZipSummary != null) {
            if (cacheDir == null ||
                (htcHtsZipSummary.hasHts && !builtinRule!!.supportsHts) ||
                (htcHtsZipSummary.hasHtc && !builtinRule!!.supportsHtc)
            ) {
                Logger.warn(TAG, "Core for ${game.platformSlug} does not support cache texture archive ${sourceFile.name}")
                return@withContext ModInstallOutcome.Failed("Unsupported cache archive")
            }
            val manifest = extractor.extractHtcOrHtsZip(
                zipFile = sourceFile,
                cacheDir = cacheDir,
                gameFileId = gameFile.id,
                stagingBaseDir = this@TexturePackInstaller.cacheDir,
                fileAccessLayer = fileAccessLayer,
                isCancelled = isCancelled,
                onProgress = onProgress
            ) ?: return@withContext ModInstallOutcome.Failed("Failed to extract HTC/HTS archive")
            try {
                gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
            } catch (e: Throwable) {
                TexturePackExtractor.uninstallFromManifestFile(manifest, fileAccessLayer)
                throw e
            }
            if (sourceFile.exists() && sourceFile.canonicalPath != manifest.canonicalPath) {
                TexturePackExtractor.deleteSourceAndPruneEmptyModParent(sourceFile)
            }
            Logger.info(
                TAG,
                "Installed N64 cache texture pack ${gameFile.fileName} for ${game.title} into ${cacheDir.absolutePath}"
            )
            return@withContext ModInstallOutcome.Success(manifest)
        }

        val manifest = extractor.extractTextureZip(
            zipFile = sourceFile,
            targetDir = targetDir,
            folderKey = folderKey,
            gameFileId = gameFile.id,
            stagingBaseDir = this@TexturePackInstaller.cacheDir,
            fileAccessLayer = fileAccessLayer,
            isCancelled = isCancelled,
            onProgress = onProgress,
            packSubdir = targetResolution.packSubdir
        ) ?: return@withContext ModInstallOutcome.Failed("Failed to extract texture zip")
        try {
            gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
        } catch (e: Throwable) {
            TexturePackExtractor.uninstallFromManifestFile(manifest, fileAccessLayer)
            throw e
        }

        if (targetResolution.isDolphin) {
            enableDolphinTextures(targetDir, gameFile)
        }

        if (sourceFile.exists() && sourceFile.canonicalPath != manifest.canonicalPath) {
            TexturePackExtractor.deleteSourceAndPruneEmptyModParent(sourceFile)
        }
        Logger.info(
            TAG,
            "Installed texture pack ${gameFile.fileName} for ${game.title} into ${targetDir.absolutePath}"
        )
        ModInstallOutcome.Success(manifest)
    }

    suspend fun installForModFile(
        game: GameEntity,
        gameFile: GameFileEntity,
        sourceFile: File,
        extractedTitleId: TitleIdResult? = null,
        preValidatedZip: Boolean = false,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): File? {
        val outcome = installForModFileOutcome(
            game = game,
            gameFile = gameFile,
            sourceFile = sourceFile,
            extractedTitleId = extractedTitleId,
            preValidatedZip = preValidatedZip,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
        return (outcome as? ModInstallOutcome.Success)?.manifestFile
    }

    suspend fun uninstallModFile(gameFile: GameFileEntity): Boolean = withContext(Dispatchers.IO) {
        val localPath = gameFile.localPath
        var deleted = true
        if (localPath != null) {
            val file = File(localPath)
            val fal = fileAccessLayer
            val isRestricted = fal != null && fal.isRestrictedPath(localPath)
            deleted = when {
                isManifestFile(file) -> uninstallFromManifestFile(file, fal)
                file.isDirectory -> if (isRestricted) fal.delete(localPath) else file.deleteRecursively()
                file.isFile -> if (isRestricted) fal.delete(localPath) else file.delete()
                else -> true
            }
        }
        if (deleted) {
            gameFileDao.clearLocalPath(gameFile.id)
            failedInstalls.remove(gameFile.id)
        }
        deleted
    }

    suspend fun uninstallForGame(game: GameEntity) = withContext(Dispatchers.IO) {
        val modFiles = gameFileDao.getModFilesForGame(game.id)
        for (modFile in modFiles) {
            if (modFile.localPath != null) {
                uninstallModFile(modFile)
            }
        }
    }

    suspend fun extractTextureZip(
        zipFile: File,
        targetDir: File,
        folderKey: String,
        gameFileId: Long,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): File? = extractor.extractTextureZip(
        zipFile = zipFile,
        targetDir = targetDir,
        folderKey = folderKey,
        gameFileId = gameFileId,
        stagingBaseDir = cacheDir,
        fileAccessLayer = fileAccessLayer,
        isCancelled = isCancelled,
        onProgress = onProgress
    )

    companion object {
        private const val TAG = "TexturePackInstaller"

        private val failedInstalls = ConcurrentHashMap<Long, Long>()

        fun clearFailedInstalls() {
            failedInstalls.clear()
        }

        fun hasFailedInstall(gameFileId: Long): Boolean = failedInstalls.containsKey(gameFileId)

        fun invalidateFailedInstall(gameFileId: Long) {
            failedInstalls.remove(gameFileId)
        }

        fun manifestFileName(gameFileId: Long): String =
            TexturePackExtractor.manifestFileName(gameFileId)

        fun manifestFileFor(targetDir: File, gameFileId: Long): File =
            TexturePackExtractor.manifestFileFor(targetDir, gameFileId)

        fun isManifestPath(path: String?): Boolean =
            TexturePackExtractor.isManifestPath(path)

        fun isManifestFile(file: File): Boolean =
            TexturePackExtractor.isManifestFile(file)

        fun isSupplementalTexturePack(
            fileName: String,
            category: String?,
            platformSlug: String,
            localPath: String? = null
        ): Boolean = TexturePackExtractor.isSupplementalTexturePack(
            fileName = fileName,
            category = category,
            platformSlug = platformSlug,
            localPath = localPath
        )

        fun looksLikeTextureZip(zipFile: File): Boolean =
            TexturePackExtractor.looksLikeTextureZip(zipFile)

        fun measureManifestUsage(
            manifestFile: File,
            seenCanonicalPaths: MutableSet<String>? = null,
            fileAccessLayer: FileAccessLayer? = null
        ): Pair<Long, Int> = TexturePackExtractor.measureManifestUsage(manifestFile, seenCanonicalPaths, fileAccessLayer)

        fun measureManifestBytes(
            manifestFile: File,
            seenCanonicalPaths: MutableSet<String>? = null,
            fileAccessLayer: FileAccessLayer? = null
        ): Long = TexturePackExtractor.measureManifestBytes(manifestFile, seenCanonicalPaths, fileAccessLayer)

        fun uninstallFromManifestFile(
            manifestFile: File,
            fileAccessLayer: FileAccessLayer? = null
        ): Boolean =
            TexturePackExtractor.uninstallFromManifestFile(manifestFile, fileAccessLayer)

        internal fun computeRedundantPrefix(
            entryPaths: List<String>,
            folderKey: String
        ): String = TexturePackExtractor.computeRedundantPrefix(entryPaths, folderKey)
    }
}
