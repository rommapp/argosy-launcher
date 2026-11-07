package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TexturePackExtractor @Inject constructor() {

    data class HtcHtsZipSummary(
        val hasHtc: Boolean,
        val hasHts: Boolean
    )

    fun inspectHtcHtsOnlyZip(zipFile: File): HtcHtsZipSummary? {
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

    suspend fun installHtcOrHtsFile(
        sourceFile: File,
        cacheDir: File,
        gameFileId: Long,
        fileAccessLayer: FileAccessLayer? = null,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): File? {
        val isRestricted = fileAccessLayer?.isRestrictedPath(cacheDir.absolutePath) == true
        if (isRestricted) {
            fileAccessLayer?.mkdirs(cacheDir.absolutePath)
        } else {
            cacheDir.mkdirs()
        }
        val destFile = File(cacheDir, sourceFile.name)
        if (!FileNames.isWithin(destFile, cacheDir)) {
            throw SecurityException("Target file outside cache dir: ${sourceFile.name}")
        }
        val tmpFile = File(cacheDir, "${sourceFile.name}.$gameFileId.tmp")
        var destCommitted = false
        var manifestCommitted = false
        try {
            if (sourceFile.canonicalPath != destFile.canonicalPath) {
                val total = sourceFile.length()
                if (onProgress != null && total > 0L) {
                    onProgress(0f)
                    sourceFile.inputStream().use { input ->
                        val outputStream = if (isRestricted) {
                            fileAccessLayer?.getOutputStream(tmpFile.absolutePath) ?: FileOutputStream(tmpFile)
                        } else {
                            FileOutputStream(tmpFile)
                        }
                        outputStream.use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            var lastReported = -1f
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                if (isCancelled?.invoke() == true) {
                                    throw CancellationException("Texture pack installation cancelled")
                                }
                                output.write(buffer, 0, read)
                                copied += read
                                val fraction = (copied.toFloat() / total).coerceIn(0f, 1f)
                                if (fraction - lastReported >= 0.01f || copied == total) {
                                    lastReported = fraction
                                    onProgress(fraction)
                                }
                            }
                        }
                    }
                } else {
                    currentCoroutineContext().ensureActive()
                    if (isCancelled?.invoke() == true) {
                        throw CancellationException("Texture pack installation cancelled")
                    }
                    if (isRestricted) {
                        fileAccessLayer?.copyFile(sourceFile.absolutePath, tmpFile.absolutePath)
                    } else {
                        sourceFile.copyTo(tmpFile, overwrite = true)
                    }
                }
                currentCoroutineContext().ensureActive()
                if (isCancelled?.invoke() == true) {
                    throw CancellationException("Texture pack installation cancelled")
                }
                if (isRestricted) {
                    fileAccessLayer?.delete(destFile.absolutePath)
                    fileAccessLayer?.copyFile(tmpFile.absolutePath, destFile.absolutePath)
                    fileAccessLayer?.delete(tmpFile.absolutePath)
                } else {
                    if (destFile.exists()) destFile.delete()
                    if (!tmpFile.renameTo(destFile)) {
                        tmpFile.copyTo(destFile, overwrite = true)
                        tmpFile.delete()
                    }
                }
                destCommitted = true
            }
            currentCoroutineContext().ensureActive()
            if (isCancelled?.invoke() == true) {
                throw CancellationException("Texture pack installation cancelled")
            }
            val manifestFile = manifestFileFor(cacheDir, gameFileId)
            val manifestBytes = destFile.name.toByteArray(Charsets.UTF_8)
            if (isRestricted) {
                fileAccessLayer?.writeBytes(manifestFile.absolutePath, manifestBytes)
            } else {
                manifestFile.writeBytes(manifestBytes)
            }
            manifestCommitted = true
            onProgress?.invoke(1f)
            return manifestFile
        } catch (e: Throwable) {
            if (!manifestCommitted) {
                val manifestFile = manifestFileFor(cacheDir, gameFileId)
                if (isRestricted) {
                    fileAccessLayer?.delete(manifestFile.absolutePath)
                } else {
                    runCatching { manifestFile.delete() }
                }
            }
            if (destCommitted && sourceFile.canonicalPath != destFile.canonicalPath) {
                if (isRestricted) {
                    fileAccessLayer?.delete(destFile.absolutePath)
                } else {
                    destFile.delete()
                }
            }
            throw e
        } finally {
            if (isRestricted) {
                fileAccessLayer?.delete(tmpFile.absolutePath)
            } else if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
    }

    suspend fun extractHtcOrHtsZip(
        zipFile: File,
        cacheDir: File,
        gameFileId: Long,
        stagingBaseDir: File? = null,
        fileAccessLayer: FileAccessLayer? = null,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): File? {
        return extractZipEntries(
            zipFile = zipFile,
            targetDir = cacheDir,
            gameFileId = gameFileId,
            resolveRelativePath = { cleanPath -> cleanPath.substringAfterLast('/').takeIf { it.isNotEmpty() } },
            stagingBaseDir = stagingBaseDir,
            fileAccessLayer = fileAccessLayer,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
    }

    suspend fun extractTextureZip(
        zipFile: File,
        targetDir: File,
        folderKey: String,
        gameFileId: Long,
        stagingBaseDir: File? = null,
        fileAccessLayer: FileAccessLayer? = null,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): File? {
        var stripPrefix: String? = null
        return extractZipEntries(
            zipFile = zipFile,
            targetDir = targetDir,
            gameFileId = gameFileId,
            resolveRelativePath = { cleanPath ->
                val prefix = stripPrefix ?: ""
                if (prefix.isNotEmpty() && cleanPath.startsWith(prefix)) {
                    cleanPath.removePrefix(prefix).trimStart('/')
                } else {
                    cleanPath
                }
            },
            onEntriesValidated = { entries ->
                stripPrefix = computeRedundantPrefix(
                    entryPaths = entries.map { it.second },
                    folderKey = folderKey
                )
            },
            stagingBaseDir = stagingBaseDir,
            fileAccessLayer = fileAccessLayer,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
    }

    private suspend fun extractZipEntries(
        zipFile: File,
        targetDir: File,
        gameFileId: Long,
        resolveRelativePath: (cleanPath: String) -> String?,
        onEntriesValidated: ((entries: List<Pair<ZipEntry, String>>) -> Unit)? = null,
        stagingBaseDir: File? = null,
        fileAccessLayer: FileAccessLayer? = null,
        isCancelled: (() -> Boolean)? = null,
        onProgress: ((fraction: Float?) -> Unit)? = null
    ): File? {
        val isRestricted = fileAccessLayer?.isRestrictedPath(targetDir.absolutePath) == true
        val isDirectlyWritable = !isRestricted && (targetDir.canWrite() || targetDir.parentFile?.canWrite() == true || targetDir.mkdirs())

        val stagingDir = if (isDirectlyWritable) {
            File(targetDir, ".staging_$gameFileId")
        } else {
            val safeStagingBase = stagingBaseDir
                ?: File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
            File(safeStagingBase, "texture_staging_$gameFileId")
        }
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        val extractedRelativePaths = LinkedHashSet<String>()

        try {
            ZipFile(zipFile).use { zf ->
                val fileEntries = collectValidatedZipEntries(zf, stagingDir)
                if (fileEntries.isEmpty()) return null

                onEntriesValidated?.invoke(fileEntries)

                val allSizesKnown = fileEntries.all { it.first.size >= 0L }
                val totalBytes = if (allSizesKnown) fileEntries.sumOf { it.first.size }.takeIf { it > 0L } else null
                var extractedBytes = 0L
                var extractedEntries = 0
                var lastReported = -1f
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                onProgress?.invoke(0f)
                for ((entry, cleanPath) in fileEntries) {
                    currentCoroutineContext().ensureActive()
                    if (isCancelled?.invoke() == true) {
                        throw CancellationException("Texture pack installation cancelled")
                    }

                    val relativePath = resolveRelativePath(cleanPath) ?: continue
                    if (relativePath.isEmpty()) continue

                    val stagedDestFile = File(stagingDir, relativePath)
                    if (!FileNames.isWithin(stagedDestFile, stagingDir)) {
                        throw SecurityException("Zip entry outside staging dir: ${entry.name}")
                    }
                    stagedDestFile.parentFile?.mkdirs()

                    zf.getInputStream(entry).use { input ->
                        FileOutputStream(stagedDestFile).use { output ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                if (isCancelled?.invoke() == true) {
                                    throw CancellationException("Texture pack installation cancelled")
                                }
                                output.write(buffer, 0, read)
                                extractedBytes += read
                                if (totalBytes != null) {
                                    val fraction = (extractedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    if (fraction - lastReported >= 0.01f || extractedBytes == totalBytes) {
                                        lastReported = fraction
                                        onProgress?.invoke(fraction)
                                    }
                                }
                            }
                        }
                    }

                    if (totalBytes == null && fileEntries.isNotEmpty()) {
                        extractedEntries++
                        val fraction = (extractedEntries.toFloat() / fileEntries.size).coerceIn(0f, 1f)
                        if (fraction - lastReported >= 0.01f || extractedEntries == fileEntries.size) {
                            lastReported = fraction
                            onProgress?.invoke(fraction)
                        }
                    }
                    extractedRelativePaths.add(relativePath)
                }
            }

            currentCoroutineContext().ensureActive()
            if (isCancelled?.invoke() == true) {
                throw CancellationException("Texture pack installation cancelled")
            }

            val committedFiles = mutableListOf<File>()
            var commitSucceeded = false
            try {
                if (isDirectlyWritable) {
                    targetDir.mkdirs()
                    for (relativePath in extractedRelativePaths) {
                        currentCoroutineContext().ensureActive()
                        if (isCancelled?.invoke() == true) {
                            throw CancellationException("Texture pack installation cancelled")
                        }
                        val stagedFile = File(stagingDir, relativePath)
                        if (!stagedFile.exists()) continue
                        val finalFile = File(targetDir, relativePath)
                        finalFile.parentFile?.mkdirs()
                        if (finalFile.exists()) finalFile.delete()
                        if (!stagedFile.renameTo(finalFile)) {
                            stagedFile.copyTo(finalFile, overwrite = true)
                            stagedFile.delete()
                        }
                        committedFiles.add(finalFile)
                    }

                    currentCoroutineContext().ensureActive()
                    if (isCancelled?.invoke() == true) {
                        throw CancellationException("Texture pack installation cancelled")
                    }

                    val manifestFile = manifestFileFor(targetDir, gameFileId)
                    manifestFile.writeText(extractedRelativePaths.joinToString("\n"))
                    commitSucceeded = true
                    onProgress?.invoke(1f)
                    return manifestFile
                } else {
                    fileAccessLayer?.mkdirs(targetDir.absolutePath)
                    for (relativePath in extractedRelativePaths) {
                        currentCoroutineContext().ensureActive()
                        if (isCancelled?.invoke() == true) {
                            throw CancellationException("Texture pack installation cancelled")
                        }
                        val stagedFile = File(stagingDir, relativePath)
                        if (!stagedFile.exists()) continue
                        val finalFile = File(targetDir, relativePath)
                        finalFile.parentFile?.let { fileAccessLayer?.mkdirs(it.absolutePath) }
                        val output = fileAccessLayer?.getOutputStream(finalFile.absolutePath)
                            ?: FileOutputStream(finalFile)
                        output.use { out ->
                            stagedFile.inputStream().use { input ->
                                val copyBuffer = ByteArray(64 * 1024)
                                var read: Int
                                while (input.read(copyBuffer).also { read = it } != -1) {
                                    out.write(copyBuffer, 0, read)
                                }
                            }
                        }
                        committedFiles.add(finalFile)
                    }

                    currentCoroutineContext().ensureActive()
                    if (isCancelled?.invoke() == true) {
                        throw CancellationException("Texture pack installation cancelled")
                    }

                    val manifestFile = manifestFileFor(targetDir, gameFileId)
                    val manifestBytes = extractedRelativePaths.joinToString("\n").toByteArray(Charsets.UTF_8)
                    if (fileAccessLayer != null) {
                        fileAccessLayer.writeBytes(manifestFile.absolutePath, manifestBytes)
                    } else {
                        manifestFile.writeBytes(manifestBytes)
                    }
                    commitSucceeded = true
                    onProgress?.invoke(1f)
                    return manifestFile
                }
            } catch (e: Throwable) {
                if (!commitSucceeded) {
                    val manifestFile = manifestFileFor(targetDir, gameFileId)
                    if (fileAccessLayer != null && fileAccessLayer.isRestrictedPath(manifestFile.absolutePath)) {
                        fileAccessLayer.delete(manifestFile.absolutePath)
                        for (file in committedFiles) {
                            fileAccessLayer.delete(file.absolutePath)
                        }
                    } else {
                        runCatching { manifestFile.delete() }
                        for (file in committedFiles) {
                            runCatching { file.delete() }
                        }
                    }
                }
                throw e
            }
        } finally {
            stagingDir.deleteRecursively()
        }
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

    companion object {
        private const val TAG = "TexturePackExtractor"
        private const val MANIFEST_PREFIX = ".argosy_mod_"
        private const val MANIFEST_SUFFIX = ".manifest"

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

        fun deleteSourceAndPruneEmptyModParent(sourceFile: File) {
            val parent = sourceFile.parentFile
            sourceFile.delete()
            if (parent != null &&
                parent.name.lowercase(Locale.ROOT) in setOf("mods", "mod", ZipExtractor.EXTCONTENT_FOLDER) &&
                parent.listFiles().isNullOrEmpty()
            ) {
                parent.delete()
            }
        }

        fun isSupplementalTexturePack(
            fileName: String,
            category: String?,
            platformSlug: String,
            localPath: String? = null
        ): Boolean {
            if (VariantCategory.fromKey(category) != VariantCategory.MOD) return false
            val hasRule = BuiltinTexturePathRegistry.resolveRuleForPlatform(platformSlug) != null ||
                ExternalTexturePathRegistry.resolveRuleForPlatform(platformSlug) != null
            if (!hasRule) return false
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
            seenCanonicalPaths: MutableSet<String>? = null,
            fileAccessLayer: FileAccessLayer? = null
        ): Pair<Long, Int> {
            val manifestPath = manifestFile.absolutePath
            val isRestricted = fileAccessLayer?.isRestrictedPath(manifestPath) == true
            val lines = runCatching {
                if (isRestricted) {
                    fileAccessLayer?.readBytes(manifestPath)?.let { String(it, Charsets.UTF_8).lines() } ?: emptyList()
                } else if (manifestFile.isFile) {
                    manifestFile.readLines()
                } else emptyList()
            }.getOrDefault(emptyList())

            if (lines.isEmpty()) return 0L to 0
            val targetDir = manifestFile.parentFile ?: return 0L to 0
            var totalBytes = 0L
            var fileCount = 0
            for (rawLine in lines) {
                val rel = rawLine.trim()
                if (rel.isEmpty()) continue
                val targetFile = File(targetDir, rel)
                if (!FileNames.isWithin(targetFile, targetDir)) continue
                val targetPath = targetFile.absolutePath

                val length = if (isRestricted) {
                    fileAccessLayer?.length(targetPath)?.takeIf { it > 0 } ?: 0L
                } else if (targetFile.isFile) {
                    targetFile.length()
                } else 0L

                if (length <= 0 && !targetFile.isFile && fileAccessLayer?.exists(targetPath) != true) continue

                if (seenCanonicalPaths != null) {
                    val canonical = runCatching { targetFile.canonicalPath }.getOrNull() ?: targetFile.absolutePath
                    if (!seenCanonicalPaths.add(canonical)) continue
                }
                totalBytes += length
                fileCount += 1
            }
            return totalBytes to fileCount
        }

        fun measureManifestBytes(
            manifestFile: File,
            seenCanonicalPaths: MutableSet<String>? = null,
            fileAccessLayer: FileAccessLayer? = null
        ): Long = measureManifestUsage(manifestFile, seenCanonicalPaths, fileAccessLayer).first

        fun uninstallFromManifestFile(
            manifestFile: File,
            fileAccessLayer: FileAccessLayer? = null
        ): Boolean {
            val manifestPath = manifestFile.absolutePath
            val isRestricted = fileAccessLayer?.isRestrictedPath(manifestPath) == true
            val lines = try {
                if (isRestricted) {
                    fileAccessLayer?.readBytes(manifestPath)?.let { String(it, Charsets.UTF_8).lines() } ?: emptyList()
                } else if (manifestFile.isFile) {
                    manifestFile.readLines()
                } else emptyList()
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to read texture pack manifest ${manifestFile.name}: ${e.message}")
                return false
            }
            if (lines.isEmpty()) return false

            val targetDir = manifestFile.parentFile ?: return false
            val targetDirPath = targetDir.absolutePath

            val siblingClaimedPaths = if (isRestricted) {
                fileAccessLayer?.listFiles(targetDirPath).orEmpty()
                    .filter { it.isFile && isManifestPath(it.path) && it.name != manifestFile.name }
                    .flatMap { sibling ->
                        fileAccessLayer?.readBytes(sibling.path)?.let { String(it, Charsets.UTF_8).lines() }.orEmpty()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }
                    .toSet()
            } else {
                targetDir.listFiles().orEmpty()
                    .filter { it.isFile && isManifestFile(it) && it.name != manifestFile.name }
                    .flatMap { sibling ->
                        runCatching { sibling.readLines() }
                            .getOrDefault(emptyList())
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }
                    .toSet()
            }

            val touchedParents = mutableSetOf<String>()
            for (rawLine in lines) {
                val rel = rawLine.trim()
                if (rel.isEmpty() || rel in siblingClaimedPaths) continue
                val targetFile = File(targetDir, rel)
                if (!FileNames.isWithin(targetFile, targetDir)) continue
                val filePath = targetFile.absolutePath

                if (isRestricted) {
                    fileAccessLayer?.delete(filePath)
                    var p = targetFile.parentFile
                    while (p != null && p.canonicalPath != targetDir.canonicalPath && FileNames.isWithin(p, targetDir)) {
                        touchedParents.add(p.absolutePath)
                        p = p.parentFile
                    }
                } else if (targetFile.isFile) {
                    targetFile.delete()
                    var p = targetFile.parentFile
                    while (p != null && p.canonicalPath != targetDir.canonicalPath && FileNames.isWithin(p, targetDir)) {
                        touchedParents.add(p.canonicalPath)
                        p = p.parentFile
                    }
                }
            }

            if (isRestricted) {
                fileAccessLayer?.delete(manifestPath)
            } else {
                if (!manifestFile.delete() && manifestFile.exists()) {
                    return false
                }
            }

            touchedParents
                .sortedByDescending { it.length }
                .forEach { parentPath ->
                    if (isRestricted) {
                        val list = fileAccessLayer?.listFiles(parentPath)
                        if (list != null && list.isEmpty()) {
                            fileAccessLayer?.delete(parentPath)
                        }
                    } else {
                        val dir = File(parentPath)
                        if (dir.isDirectory && dir.listFiles().isNullOrEmpty()) {
                            dir.delete()
                        }
                    }
                }

            if (isRestricted) {
                val list = fileAccessLayer?.listFiles(targetDirPath)
                if (list != null && list.isEmpty()) {
                    fileAccessLayer?.delete(targetDirPath)
                }
            } else if (targetDir.isDirectory && targetDir.listFiles().isNullOrEmpty()) {
                targetDir.delete()
            }

            return true
        }

        fun computeRedundantPrefix(
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
