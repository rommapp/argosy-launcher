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
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.model.VariantCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
    private val systemDir: File
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
        @ApplicationContext context: Context
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
        systemDir = AppPaths.libretroSystemDir(context.filesDir)
    )

    suspend fun installPendingModFilesForGame(
        gameId: Long,
        extractedTitleId: TitleIdResult? = null
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

        val romFile = resolveRomFile(game)
        if (romFile == null || !romFile.isFile) {
            Logger.debug(TAG, "Deferring texture pack install for ${game.title} until base ROM is on disk")
            return@withContext 0
        }
        val modFiles = gameFileDao.getModFilesForGame(gameId)
        if (modFiles.isEmpty()) return@withContext 0

        val gameFolder = gameFolderOf(game)
        var installedCount = 0

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
            val manifest = runCatching {
                installForModFile(
                    game = game,
                    gameFile = modFile,
                    sourceFile = sourceFile,
                    extractedTitleId = extractedTitleId,
                    preValidatedZip = true
                )
            }.onFailure { e ->
                failedInstalls[modFile.id] = System.currentTimeMillis()
                Logger.warn(TAG, "Failed to install texture pack ${modFile.fileName}: ${e.message}")
            }.getOrNull()
            if (manifest != null) {
                installedCount++
            } else {
                failedInstalls[modFile.id] = System.currentTimeMillis()
            }
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

    suspend fun installForModFile(
        game: GameEntity,
        gameFile: GameFileEntity,
        sourceFile: File,
        extractedTitleId: TitleIdResult? = null,
        preValidatedZip: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.isFile) return@withContext null

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            game.id,
            game.platformId,
            game.platformSlug
        )
        if (emulatorPackage != EmulatorRegistry.BUILTIN_PACKAGE) {
            return@withContext null
        }

        val resolvedCoreId = builtinCoreResolver.resolveCoreId(
            gameId = game.id,
            platformId = game.platformId,
            platformSlug = game.platformSlug
        )
        val rule = BuiltinTexturePathRegistry.resolveRule(resolvedCoreId, game.platformSlug)
            ?: return@withContext null


        val romFile = resolveRomFile(game)
        if (romFile == null || !romFile.isFile) {
            Logger.debug(TAG, "Deferring texture pack install for ${game.title} until base ROM is on disk")
            return@withContext null
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
                return@withContext null
            }
            val targetCacheDir = cacheDir ?: return@withContext null
            val manifest = installHtcOrHtsFile(sourceFile, targetCacheDir, gameFile.id)
            gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
            return@withContext manifest
        }

        if (!preValidatedZip && !looksLikeTextureZip(sourceFile)) {
            return@withContext null
        }

        val htcHtsZipSummary = if (rule.supportsHtc || rule.supportsHts) {
            inspectHtcHtsOnlyZip(sourceFile)
        } else {
            null
        }
        if (htcHtsZipSummary != null) {
            if (cacheDir == null ||
                (htcHtsZipSummary.hasHts && !rule.supportsHts) ||
                (htcHtsZipSummary.hasHtc && !rule.supportsHtc)
            ) {
                Logger.warn(TAG, "Core for ${game.platformSlug} does not support cache texture archive ${sourceFile.name}")
                return@withContext null
            }
            val manifest = extractHtcOrHtsZip(
                zipFile = sourceFile,
                cacheDir = cacheDir,
                gameFileId = gameFile.id,
                deleteSourceZip = true
            )
            gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
            Logger.info(
                TAG,
                "Installed N64 cache texture pack ${gameFile.fileName} for ${game.title} into ${cacheDir.absolutePath}"
            )
            return@withContext manifest
        }


        val folderKey = titleIdExtractor.resolveTextureFolderKey(
            strategy = rule.folderKeyStrategy,
            platformSlug = game.platformSlug,
            game = game,
            romFile = romFile,
            zipArchive = sourceFile,
            extractedResult = extractedTitleId
        ) ?: return@withContext null

        val targetDir = BuiltinTexturePathRegistry.resolveTargetDir(
            rule = rule,
            saveDir = effectiveSaveDir,
            systemDir = systemDir,
            folderKey = folderKey
        )

        val manifest = extractTextureZip(
            zipFile = sourceFile,
            targetDir = targetDir,
            folderKey = folderKey,
            gameFileId = gameFile.id,
            deleteSourceZip = true
        )
        gameFileDao.updateLocalPath(gameFile.id, manifest.absolutePath, Instant.now())
        Logger.info(
            TAG,
            "Installed texture pack ${gameFile.fileName} for ${game.title} into ${targetDir.absolutePath}"
        )
        manifest
    }

    private fun installHtcOrHtsFile(sourceFile: File, cacheDir: File, gameFileId: Long): File {
        cacheDir.mkdirs()
        val destFile = File(cacheDir, sourceFile.name)
        if (!FileNames.isWithin(destFile, cacheDir)) {
            throw SecurityException("Target file outside cache dir: ${sourceFile.name}")
        }
        if (sourceFile.canonicalPath != destFile.canonicalPath) {
            sourceFile.copyTo(destFile, overwrite = true)
            deleteSourceAndPruneEmptyModParent(sourceFile)
        }
        val manifestFile = manifestFileFor(cacheDir, gameFileId)
        manifestFile.writeText(destFile.name)
        return manifestFile
    }

    private data class HtcHtsZipSummary(
        val hasHtc: Boolean,
        val hasHts: Boolean
    )

    private fun inspectHtcHtsOnlyZip(zipFile: File): HtcHtsZipSummary? {
        if (!zipFile.isFile || !BuiltinTexturePathRegistry.isTexturePackFileName(zipFile.name)) return null
        return try {
            ZipFile(zipFile).use { zf ->
                var hasHtc = false
                var hasHts = false
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val cleanPath = entry.name.replace('\\', '/').trim('/')
                    if (cleanPath.isEmpty() || cleanPath.startsWith("__MACOSX/") || cleanPath == "__MACOSX") {
                        continue
                    }
                    if (!entry.isDirectory) {
                        val name = cleanPath.substringAfterLast('/')
                        when {
                            BuiltinTexturePathRegistry.isHtcFileName(name) -> hasHtc = true
                            BuiltinTexturePathRegistry.isHtsFileName(name) -> hasHts = true
                            else -> return@use null
                        }
                    }
                }
                if (hasHtc || hasHts) HtcHtsZipSummary(hasHtc = hasHtc, hasHts = hasHts) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractHtcOrHtsZip(
        zipFile: File,
        cacheDir: File,
        gameFileId: Long,
        deleteSourceZip: Boolean
    ): File {
        cacheDir.mkdirs()
        val extractedNames = mutableListOf<String>()

        ZipFile(zipFile).use { zf ->
            val fileEntries = collectValidatedZipEntries(zf, cacheDir)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            for ((entry, cleanPath) in fileEntries) {
                val fileName = cleanPath.substringAfterLast('/')
                if (fileName.isEmpty()) continue
                val destFile = File(cacheDir, fileName)
                if (!FileNames.isWithin(destFile, cacheDir)) {
                    throw SecurityException("Zip entry outside cache dir: ${entry.name}")
                }
                zf.getInputStream(entry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                extractedNames.add(fileName)
            }
        }

        val manifestFile = manifestFileFor(cacheDir, gameFileId)
        manifestFile.writeText(extractedNames.joinToString("\n"))

        if (deleteSourceZip && zipFile.exists() && zipFile.canonicalPath != manifestFile.canonicalPath) {
            deleteSourceAndPruneEmptyModParent(zipFile)
        }
        return manifestFile
    }

    fun extractTextureZip(
        zipFile: File,
        targetDir: File,
        folderKey: String,
        gameFileId: Long,
        deleteSourceZip: Boolean = true
    ): File {
        targetDir.mkdirs()
        val extractedRelativePaths = mutableListOf<String>()

        ZipFile(zipFile).use { zf ->
            val fileEntries = collectValidatedZipEntries(zf, targetDir)

            val stripPrefix = computeRedundantPrefix(
                entryPaths = fileEntries.map { it.second },
                folderKey = folderKey
            )

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            for ((entry, cleanPath) in fileEntries) {
                val relativePath = if (stripPrefix.isNotEmpty() && cleanPath.startsWith(stripPrefix)) {
                    cleanPath.removePrefix(stripPrefix).trimStart('/')
                } else {
                    cleanPath
                }
                if (relativePath.isEmpty()) continue

                val destFile = File(targetDir, relativePath)
                if (!FileNames.isWithin(destFile, targetDir)) {
                    throw SecurityException("Zip entry outside target dir: ${entry.name}")
                }
                destFile.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    FileOutputStream(destFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                extractedRelativePaths.add(relativePath)
            }
        }

        val manifestFile = manifestFileFor(targetDir, gameFileId)
        manifestFile.writeText(extractedRelativePaths.joinToString("\n"))

        if (deleteSourceZip && zipFile.exists() && zipFile.canonicalPath != manifestFile.canonicalPath) {
            deleteSourceAndPruneEmptyModParent(zipFile)
        }
        return manifestFile
    }

    private fun collectValidatedZipEntries(
        zf: ZipFile,
        baseDir: File
    ): List<Pair<ZipEntry, String>> {
        val fileEntries = mutableListOf<Pair<ZipEntry, String>>()
        val entriesEnum = zf.entries()
        while (entriesEnum.hasMoreElements()) {
            val entry = entriesEnum.nextElement()
            val rawNormalized = entry.name.replace('\\', '/')
            val rawSegments = rawNormalized.split('/')
            if (rawSegments.any { it == ".." }) {
                throw SecurityException("Zip-Slip traversal entry rejected: ${entry.name}")
            }
            val cleanPath = rawNormalized.trim('/')
            if (cleanPath.isEmpty() || cleanPath.startsWith("__MACOSX/") || cleanPath == "__MACOSX") {
                continue
            }
            val probeTarget = File(baseDir, cleanPath)
            if (!FileNames.isWithin(probeTarget, baseDir)) {
                throw SecurityException("Zip entry outside target dir: ${entry.name}")
            }
            if (!entry.isDirectory) {
                fileEntries.add(entry to cleanPath)
            }
        }
        return fileEntries
    }

    private fun deleteSourceAndPruneEmptyModParent(sourceFile: File) {
        val parent = sourceFile.parentFile
        sourceFile.delete()
        if (parent != null &&
            parent.name.lowercase(Locale.ROOT) in setOf("mods", "mod", ZipExtractor.EXTCONTENT_FOLDER) &&
            parent.listFiles().isNullOrEmpty()
        ) {
            parent.delete()
        }
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

    companion object {
        private const val TAG = "TexturePackInstaller"
        private const val MANIFEST_PREFIX = ".argosy_mod_"
        private const val MANIFEST_SUFFIX = ".manifest"

        private val failedInstalls = java.util.concurrent.ConcurrentHashMap<Long, Long>()

        fun clearFailedInstalls() {
            failedInstalls.clear()
        }

        fun hasFailedInstall(gameFileId: Long): Boolean = failedInstalls.containsKey(gameFileId)

        fun invalidateFailedInstall(gameFileId: Long) {
            failedInstalls.remove(gameFileId)
        }

        private val REDUNDANT_CONTAINER_NAMES = setOf("textures", "hires_texture", "load", "user")

        private val TEXTURE_ASSET_EXTENSIONS = setOf(
            "png", "dds", "bmp", "tga", "ktx", "htc", "hts"
        )
        private val TEXTURE_METADATA_FILENAMES = setOf(
            "textures.ini", "hires.txt"
        )
        private val ROM_OR_PATCH_EXTENSIONS = setOf(
            "z64", "n64", "v64", "ndd",
            "iso", "gcm", "rvz", "wbfs", "ciso", "gcz", "wia", "dol", "elf", "wad",
            "cso", "pbp", "prx",
            "chd", "cdi", "gdi", "cue",
            "3ds", "cci", "cia", "3dsx", "app",
            "bps", "ips", "ups", "xdelta", "ppf"
        )

        fun manifestFileName(gameFileId: Long): String =
            "$MANIFEST_PREFIX$gameFileId$MANIFEST_SUFFIX"

        fun manifestFileFor(targetDir: File, gameFileId: Long): File =
            File(targetDir, manifestFileName(gameFileId))

        fun isManifestPath(path: String?): Boolean {
            if (path == null) return false
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            return name.startsWith(MANIFEST_PREFIX) && name.endsWith(MANIFEST_SUFFIX)
        }

        fun isManifestFile(file: File): Boolean = isManifestPath(file.name)

        fun isSupplementalTexturePack(
            fileName: String,
            category: String?,
            platformSlug: String,
            localPath: String? = null
        ): Boolean {
            if (VariantCategory.fromKey(category) != VariantCategory.MOD) return false
            if (BuiltinTexturePathRegistry.resolveRuleForPlatform(platformSlug) == null) return false
            if (isManifestPath(localPath)) return true
            if (BuiltinTexturePathRegistry.isHtcOrHtsFileName(fileName)) return true
            if (!BuiltinTexturePathRegistry.isTexturePackFileName(fileName)) return false
            val localFile = localPath?.let(::File)?.takeIf { it.isFile }
            if (localFile != null) {
                return looksLikeTextureZip(localFile)
            }
            return true
        }

        fun looksLikeTextureZip(zipFile: File): Boolean {
            if (!zipFile.isFile) return false
            return try {
                ZipFile(zipFile).use { zf ->
                    var hasTextureAsset = false
                    var hasRomOrPatch = false
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val cleanPath = entry.name.replace('\\', '/').trim('/')
                        if (cleanPath.isEmpty() || cleanPath.startsWith("__MACOSX/") || cleanPath == "__MACOSX") {
                            continue
                        }
                        val fileName = cleanPath.substringAfterLast('/').lowercase(Locale.ROOT)
                        val ext = fileName.substringAfterLast('.', "")
                        if (ext in ROM_OR_PATCH_EXTENSIONS) {
                            hasRomOrPatch = true
                        }
                        if (ext in TEXTURE_ASSET_EXTENSIONS || fileName in TEXTURE_METADATA_FILENAMES) {
                            hasTextureAsset = true
                        }
                    }
                    hasTextureAsset && !hasRomOrPatch
                }
            } catch (_: Exception) {
                false
            }
        }

        fun measureManifestUsage(
            manifestFile: File,
            seenCanonicalPaths: MutableSet<String>? = null
        ): Pair<Long, Int> {
            if (!manifestFile.isFile) return 0L to 0
            val targetDir = manifestFile.parentFile ?: return 0L to 0
            val lines = runCatching { manifestFile.readLines() }.getOrDefault(emptyList())
            var totalBytes = 0L
            var fileCount = 0
            for (rawLine in lines) {
                val rel = rawLine.trim()
                if (rel.isEmpty()) continue
                val targetFile = File(targetDir, rel)
                if (!FileNames.isWithin(targetFile, targetDir) || !targetFile.isFile) continue
                if (seenCanonicalPaths != null) {
                    val canonical = runCatching { targetFile.canonicalPath }.getOrNull() ?: targetFile.absolutePath
                    if (!seenCanonicalPaths.add(canonical)) continue
                }
                totalBytes += targetFile.length()
                fileCount += 1
            }
            return totalBytes to fileCount
        }

        fun measureManifestBytes(
            manifestFile: File,
            seenCanonicalPaths: MutableSet<String>? = null
        ): Long = measureManifestUsage(manifestFile, seenCanonicalPaths).first

        fun uninstallFromManifestFile(manifestFile: File): Boolean {
            if (!manifestFile.isFile) return false
            val targetDir = manifestFile.parentFile ?: return false
            val lines = try {
                manifestFile.readLines()
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to read texture pack manifest ${manifestFile.name}: ${e.message}")
                return false
            }

            val siblingClaimedPaths = targetDir.listFiles()
                .orEmpty()
                .filter { it.isFile && isManifestFile(it) && it.name != manifestFile.name }
                .flatMap { sibling ->
                    runCatching { sibling.readLines() }
                        .getOrDefault(emptyList())
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }
                .toSet()

            val touchedParents = mutableSetOf<File>()
            for (rawLine in lines) {
                val rel = rawLine.trim()
                if (rel.isEmpty() || rel in siblingClaimedPaths) continue
                val targetFile = File(targetDir, rel)
                if (!FileNames.isWithin(targetFile, targetDir)) continue
                if (targetFile.isFile) {
                    targetFile.delete()
                    var p = targetFile.parentFile
                    while (p != null && p.canonicalPath != targetDir.canonicalPath && FileNames.isWithin(p, targetDir)) {
                        touchedParents.add(p)
                        p = p.parentFile
                    }
                }
            }
            if (!manifestFile.delete() && manifestFile.exists()) {
                return false
            }
            touchedParents
                .sortedByDescending { it.absolutePath.length }
                .forEach { dir ->
                    if (dir.isDirectory && dir.listFiles().isNullOrEmpty()) {
                        dir.delete()
                    }
                }
            if (targetDir.isDirectory && targetDir.listFiles().isNullOrEmpty()) {
                targetDir.delete()
            }
            return true
        }

        internal fun computeRedundantPrefix(
            entryPaths: List<String>,
            folderKey: String
        ): String {
            if (entryPaths.isEmpty()) return ""
            val cleanKey = folderKey.trim().trim('/', '\\')
            if (cleanKey.isEmpty()) return ""

            val cleanLower = cleanKey.lowercase(Locale.ROOT)
            val keyVariants = mutableSetOf(cleanLower)
            if (cleanKey.length == 6 && cleanKey.all { it.isLetterOrDigit() }) {
                keyVariants.add(cleanKey.substring(0, 3).lowercase(Locale.ROOT))
                keyVariants.add(cleanKey.substring(0, 4).lowercase(Locale.ROOT))
            } else if (cleanKey.length in 3..4 && cleanKey.all { it.isLetterOrDigit() }) {
                keyVariants.add(cleanKey.substring(0, 3).lowercase(Locale.ROOT))
            }

            val splitEntries = entryPaths.map { it.split('/') }
            if (splitEntries.any { it.size < 2 }) return ""

            val maxCommonDepth = splitEntries.minOf { it.size - 1 }
            var commonDepth = 0
            while (commonDepth < maxCommonDepth) {
                val firstSeg = splitEntries[0][commonDepth]
                if (splitEntries.all { it[commonDepth] == firstSeg }) {
                    commonDepth++
                } else {
                    break
                }
            }
            if (commonDepth == 0) return ""

            val commonSegments = splitEntries[0].subList(0, commonDepth)
            for (i in commonSegments.indices.reversed()) {
                val segLower = commonSegments[i].lowercase(Locale.ROOT)
                val matchesKey = segLower in keyVariants ||
                    (cleanLower.length in 3..6 && segLower.length in 3..6 &&
                        (segLower.startsWith(cleanLower) || cleanLower.startsWith(segLower))) ||
                    (cleanLower.length > 6 && (segLower.startsWith(cleanLower) || cleanLower.startsWith(segLower)))
                if (matchesKey) {
                    return commonSegments.subList(0, i + 1).joinToString("/") + "/"
                }
            }

            var lastContainerIdx = -1
            for (i in commonSegments.indices) {
                val segLower = commonSegments[i].lowercase(Locale.ROOT)
                if (segLower in REDUNDANT_CONTAINER_NAMES) {
                    lastContainerIdx = i
                } else {
                    break
                }
            }
            if (lastContainerIdx >= 0) {
                return commonSegments.subList(0, lastContainerIdx + 1).joinToString("/") + "/"
            }

            return ""
        }
    }
}
