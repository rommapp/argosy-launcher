package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
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
    private val extractor: TexturePackExtractor = TexturePackExtractor()
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
        extractor: TexturePackExtractor
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
        extractor = extractor
    )

    suspend fun installPendingModFilesForGame(
        gameId: Long,
        extractedTitleId: TitleIdResult? = null,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext 0
        if (BuiltinTexturePathRegistry.resolveRuleForPlatform(game.platformSlug) == null) {
            return@withContext 0
        }
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            game.id,
            game.platformId,
            game.platformSlug
        )
        if (emulatorPackage != EmulatorRegistry.BUILTIN_PACKAGE) {
            return@withContext 0
        }
        val modFiles = gameFileDao.getModFilesForGame(gameId)
        if (modFiles.isEmpty()) return@withContext 0

        val gameFolder = gameFolderOf(game)
        val candidates = mutableListOf<Pair<GameFileEntity, File>>()
        for (modFile in modFiles) {
            val recordedPath = modFile.localPath
            if (recordedPath != null && isManifestPath(recordedPath) && File(recordedPath).isFile) {
                continue
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
            candidates.add(modFile to sourceFile)
        }
        if (candidates.isEmpty()) return@withContext 0

        onProgress?.invoke(0f)
        var installedCount = 0
        val totalCount = candidates.size

        for ((index, pair) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (isCancelled?.invoke() == true) {
                Logger.info(TAG, "Texture pack install cancelled for ${game.title}")
                throw CancellationException("Texture pack install cancelled for ${game.title}")
            }
            val (modFile, sourceFile) = pair
            val packProgress: (Float?) -> Unit = { packFraction ->
                if (packFraction != null) {
                    val overall = (index + packFraction) / totalCount
                    onProgress?.invoke(overall.coerceIn(0f, 1f))
                }
            }
            try {
                val outcome = installForModFileOutcome(
                    game = game,
                    gameFile = modFile,
                    sourceFile = sourceFile,
                    extractedTitleId = extractedTitleId,
                    preValidatedZip = true,
                    isCancelled = isCancelled,
                    onProgress = packProgress
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

    suspend fun hasInstalledTexturePack(gameId: Long): Boolean = withContext(Dispatchers.IO) {
        gameFileDao.getModFilesForGame(gameId).any { modFile ->
            val path = modFile.localPath
            path != null && isManifestPath(path) && File(path).isFile
        }
    }

    fun invalidateFailedInstall(gameFileId: Long) {
        failedInstalls.remove(gameFileId)
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
        val rule = BuiltinTexturePathRegistry.resolveRule(coreId, platformSlug)
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
        val platformDir = platformFolderResolver.downloadDirForPlatform(
            game.platformId,
            game.platformSlug
        )
        val folderCanonical = runCatching { folder.canonicalPath }.getOrNull() ?: return null
        val platformCanonical = runCatching { platformDir.canonicalPath }.getOrNull() ?: return null
        return folder.takeIf { folderCanonical != platformCanonical }
    }

    private fun resolveStagingModFile(modFile: GameFileEntity, gameFolder: File?): File? {
        modFile.localPath?.let { path ->
            val file = File(path)
            if (file.isFile && !isManifestFile(file)) return file
        }
        if (gameFolder == null) return null
        return ZipExtractor.findAddonFile(gameFolder, modFile.fileName, modFile.category)
    }

    private suspend fun resolveRomFile(game: GameEntity): File? {
        if (game.isMultiDisc) {
            val firstDisc = gameDiscDao.getDiscsForGame(game.id).minByOrNull { it.discNumber }
                ?: return null
            val discPath = firstDisc.localPath ?: return null
            val discFile = File(discPath).takeIf { it.isFile } ?: return null
            return if (discFile.extension.equals("m3u", ignoreCase = true)) {
                M3uManager.parseFirstDisc(discFile)?.takeIf { it.isFile }
            } else {
                discFile
            }
        }
        val recorded = File(game.localPath ?: return null)
        if (!recorded.exists()) return null
        val base = baseRomFileResolver.resolve(game, recorded).takeIf { it.isFile } ?: return null
        return if (base.extension.equals("m3u", ignoreCase = true)) {
            M3uManager.parseFirstDisc(base)?.takeIf { it.isFile }
        } else {
            base
        }
    }

    sealed interface ModInstallOutcome {
        data class Success(val manifest: File) : ModInstallOutcome
        data class Deferred(val reason: String) : ModInstallOutcome
        data class Failed(val reason: String) : ModInstallOutcome
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
        if (!sourceFile.isFile) return@withContext ModInstallOutcome.Failed("Source file does not exist")

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            game.id,
            game.platformId,
            game.platformSlug
        )
        if (emulatorPackage != EmulatorRegistry.BUILTIN_PACKAGE) {
            return@withContext ModInstallOutcome.Deferred("Emulator $emulatorPackage is not built-in")
        }

        val resolvedCoreId = builtinCoreResolver.resolveCoreId(
            gameId = game.id,
            platformId = game.platformId,
            platformSlug = game.platformSlug
        )
        val rule = BuiltinTexturePathRegistry.resolveRule(resolvedCoreId, game.platformSlug)
            ?: return@withContext ModInstallOutcome.Deferred("No texture path rule for core $resolvedCoreId")

        val romFile = resolveRomFile(game)
        if (romFile == null || !romFile.isFile) {
            Logger.debug(TAG, "Deferring texture pack install for ${game.title} until base ROM is on disk")
            return@withContext ModInstallOutcome.Deferred("Base ROM not yet on disk")
        }

        val baseRomPath = romFile.absolutePath
        val effectiveSaveDir = File(builtinSaveBase.forGame(game, baseRomPath))
        val cacheDir = BuiltinTexturePathRegistry.resolveHtcHtsCacheDir(
            rule,
            effectiveSaveDir,
            systemDir
        )

        if (BuiltinTexturePathRegistry.isHtcOrHtsFileName(sourceFile.name)) {
            val isHts = BuiltinTexturePathRegistry.isHtsFileName(sourceFile.name)
            val isHtc = BuiltinTexturePathRegistry.isHtcFileName(sourceFile.name)
            if ((isHts && !rule.supportsHts) || (isHtc && !rule.supportsHtc)) {
                Logger.warn(TAG, "Core for ${game.platformSlug} does not support ${sourceFile.extension} texture packs")
                return@withContext ModInstallOutcome.Failed("Unsupported HTC/HTS format")
            }
            val targetCacheDir = cacheDir ?: return@withContext ModInstallOutcome.Failed("Cache directory unresolvable")
            val manifest = extractor.installHtcOrHtsFile(sourceFile, targetCacheDir, gameFile.id, isCancelled, onProgress)
                ?: return@withContext ModInstallOutcome.Failed("Failed to install HTC/HTS file")
            try {
                gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
            } catch (e: Throwable) {
                TexturePackExtractor.uninstallFromManifestFile(manifest)
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

        val htcHtsZipSummary = if (rule.supportsHtc || rule.supportsHts) {
            extractor.inspectHtcHtsOnlyZip(sourceFile)
        } else {
            null
        }
        if (htcHtsZipSummary != null) {
            if (cacheDir == null ||
                (htcHtsZipSummary.hasHts && !rule.supportsHts) ||
                (htcHtsZipSummary.hasHtc && !rule.supportsHtc)
            ) {
                Logger.warn(TAG, "Core for ${game.platformSlug} does not support cache texture archive ${sourceFile.name}")
                return@withContext ModInstallOutcome.Failed("Unsupported cache archive")
            }
            val manifest = extractor.extractHtcOrHtsZip(
                zipFile = sourceFile,
                cacheDir = cacheDir,
                gameFileId = gameFile.id,
                isCancelled = isCancelled,
                onProgress = onProgress
            ) ?: return@withContext ModInstallOutcome.Failed("Failed to extract HTC/HTS archive")
            try {
                gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
            } catch (e: Throwable) {
                TexturePackExtractor.uninstallFromManifestFile(manifest)
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

        val folderKey = titleIdExtractor.resolveTextureFolderKey(
            strategy = rule.folderKeyStrategy,
            platformSlug = game.platformSlug,
            game = game,
            romFile = romFile,
            zipArchive = sourceFile,
            extractedResult = extractedTitleId
        ) ?: return@withContext ModInstallOutcome.Failed("Could not resolve folder key")

        val targetDir = BuiltinTexturePathRegistry.resolveTargetDir(
            rule = rule,
            saveDir = effectiveSaveDir,
            systemDir = systemDir,
            folderKey = folderKey
        )

        val manifest = extractor.extractTextureZip(
            zipFile = sourceFile,
            targetDir = targetDir,
            folderKey = folderKey,
            gameFileId = gameFile.id,
            isCancelled = isCancelled,
            onProgress = onProgress
        ) ?: return@withContext ModInstallOutcome.Failed("Failed to extract texture zip")
        try {
            gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
        } catch (e: Throwable) {
            TexturePackExtractor.uninstallFromManifestFile(manifest)
            throw e
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
        return (outcome as? ModInstallOutcome.Success)?.manifest
    }

    suspend fun uninstallModFile(gameFile: GameFileEntity): Boolean = withContext(Dispatchers.IO) {
        val localPath = gameFile.localPath
        var deleted = true
        if (localPath != null) {
            val file = File(localPath)
            deleted = when {
                isManifestFile(file) -> uninstallFromManifestFile(file)
                file.isDirectory -> file.deleteRecursively()
                file.isFile -> file.delete()
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
        isCancelled = isCancelled,
        onProgress = onProgress
    )

    companion object {
        private const val TAG = "TexturePackInstaller"

        private val failedInstalls = java.util.concurrent.ConcurrentHashMap<Long, Long>()

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
            seenCanonicalPaths: MutableSet<String>? = null
        ): Pair<Long, Int> = TexturePackExtractor.measureManifestUsage(manifestFile, seenCanonicalPaths)

        fun measureManifestBytes(
            manifestFile: File,
            seenCanonicalPaths: MutableSet<String>? = null
        ): Long = TexturePackExtractor.measureManifestBytes(manifestFile, seenCanonicalPaths)

        fun uninstallFromManifestFile(manifestFile: File): Boolean =
            TexturePackExtractor.uninstallFromManifestFile(manifestFile)

        internal fun computeRedundantPrefix(
            entryPaths: List<String>,
            folderKey: String
        ): String = TexturePackExtractor.computeRedundantPrefix(entryPaths, folderKey)
    }
}
