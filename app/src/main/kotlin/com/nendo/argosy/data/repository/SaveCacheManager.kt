package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.util.Logger
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveOwnershipDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.emulator.SwitchTitleIds
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.ArchiveRoot
import com.nendo.argosy.data.sync.ResolvedSaveUnit
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.domain.model.SaveSlotClassifier
import com.nendo.argosy.domain.model.SaveSlotKind
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveCacheDao: SaveCacheDao,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val savePathResolver: SavePathResolver,
    private val saveArchiver: SaveArchiver,
    private val fal: FileAccessLayer,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry,
    private val saveOwnershipTracker: SaveOwnershipTracker,
    private val saveOwnershipDao: SaveOwnershipDao,
    private val saveUnitResolver: SaveUnitResolver,
    private val gciSaveHandler: GciSaveHandler,
    private val sigilSaveHandler: SigilSaveHandler
) {
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        .withZone(ZoneId.systemDefault())

    companion object {
        private const val TAG = "SaveCacheManager"
        private const val SWITCH_TITLE_ID_LENGTH = 16
        private const val COPY_DIR_SUFFIX = "_copy"
        const val UNIT_CACHE_SUFFIX = ".unit.zip"
        private val RZIP_MAGIC = "#RZIPv".toByteArray(Charsets.US_ASCII)

        /**
         * Platforms whose save is a set of sibling folders sharing a prefix rather than one
         * directory, so every match has to be archived together.
         */
        private val FOLDER_PREFIX_PLATFORMS = setOf("psp", "ps2")
        private const val MIN_UNLOCKED_SLOTS = 5
    }

    sealed class CacheResult {
        data class Created(val timestamp: Long, val cacheId: Long = 0) : CacheResult()
        data class Duplicate(val cacheId: Long, val contentHash: String) : CacheResult()
        data object Failed : CacheResult()

        val success: Boolean get() = this != Failed
    }

    sealed interface LocalSaveProbe {
        data object Absent : LocalSaveProbe
        data object Unreadable : LocalSaveProbe
        data class Present(val contentHash: String) : LocalSaveProbe
    }

    private val cacheBaseDir: File
        get() = com.nendo.argosy.util.AppPaths.saveCacheDir(context.filesDir)

    private fun cacheRelativeDir(ownerUserId: Long?, gameId: Long, timestamp: String): String =
        "${com.nendo.argosy.util.AppPaths.ownerCacheSegment(ownerUserId)}$gameId/$timestamp"

    suspend fun cacheCurrentSave(
        gameId: Long,
        emulatorId: String,
        savePath: String,
        channelName: String? = null,
        isLocked: Boolean = false,
        cheatsUsed: Boolean = false,
        isHardcore: Boolean = false,
        slotName: String? = null,
        skipDuplicateCheck: Boolean = false,
        needsRemoteSync: Boolean = false,
        precomputedContentHash: String? = null,
        coreName: String? = null,
        claimNewSaves: Boolean = false
    ): CacheResult = withContext(Dispatchers.IO) {
        @Suppress("NAME_SHADOWING")
        val channelName = resolveDefaultChannel(channelName, isHardcore)
        val secureSaves = syncPreferencesRepository.isSecureSaves()
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val sigil = when (val collected = sigilSaveHandler.collect(gameId, emulatorId, claimNewSaves)) {
            SigilCollect.NotRouted -> null
            is SigilCollect.Found -> collected
            SigilCollect.Absent -> {
                Logger.debug(TAG, "No save to cache for game $gameId under $emulatorId")
                return@withContext CacheResult.Failed
            }
            is SigilCollect.Unreadable -> {
                Logger.warn(TAG, "Save for game $gameId under $emulatorId is unreadable: ${collected.reason}")
                return@withContext CacheResult.Failed
            }
        }
        if (sigil == null && !fal.exists(savePath)) {
            Logger.warn(TAG,"Save file does not exist: $savePath")
            return@withContext CacheResult.Failed
        }

        fal.prepareSaveAccess(savePath)
        val saveFile = fal.getTransformedFile(savePath)
        var tempFile: File? = null
        val unit = if (sigil != null || fal.isDirectory(savePath)) null else multiMemberUnit(gameId, emulatorId, savePath, coreName)

        try {
            val archived = archiveForCache(gameId, savePath, saveFile, unit, precomputedContentHash, sigil)
                ?: return@withContext CacheResult.Failed
            tempFile = archived.tempFile
            val contentHash = archived.contentHash
            val tempOrSource = archived.source

            val identityHash = archived.identityHash
                ?: unit?.unit?.identityHash?.takeIf { it.isNotEmpty() }
                ?: contentHash
            val identityKnown = unit != null || archived.identityHash != null

            if (!skipDuplicateCheck) {
                val existingWithHash = saveCacheDao.getAllByGameChannelAndHash(gameId, ownerUserId, channelName, contentHash).firstOrNull()
                    ?: identityHash.takeIf { identityKnown }?.let { saveCacheDao.getLatestByGameChannelAndIdentity(gameId, ownerUserId, channelName, it) }
                    ?: identityHash.takeIf { identityKnown }?.let { saveCacheDao.getAllByGameChannelAndHash(gameId, ownerUserId, channelName, it).lastOrNull() }
                if (existingWithHash != null) {
                    Log.d(TAG, "Duplicate save detected for game $gameId (hash=$contentHash, identity=$identityHash, hardcore=$isHardcore), skipping cache")
                    SaveDebugLogger.logCacheDuplicate(
                        gameId = gameId,
                        gameName = null,
                        channel = channelName,
                        contentHash = contentHash
                    )
                    tempFile?.delete()
                    if (channelName != null) saveCacheDao.setActiveRow(gameId, ownerUserId, existingWithHash.id)
                    if (!secureSaves && !isHardcore) recordLocalWriteAnchor(gameId, emulatorId, channelName, savePath, ownerUserId, createMissing = false)
                    saveOwnershipTracker.record(savePath, emulatorId, contentHash, gameId, channelName)
                    return@withContext CacheResult.Duplicate(existingWithHash.id, contentHash)
                }
            }

            val now = Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val relativeDir = cacheRelativeDir(ownerUserId, gameId, timestamp)
            val gameDir = File(cacheBaseDir, relativeDir)
            gameDir.mkdirs()

            val (cachePath, cachedFile) = if (archived.isArchive) {
                val zipName = archived.fileName
                    ?: if (unit != null) "${unit.unit.key}$UNIT_CACHE_SUFFIX" else "save.zip"
                val finalZip = File(gameDir, zipName)
                tempOrSource.renameTo(finalZip).let { renamed ->
                    if (!renamed) {
                        tempOrSource.copyTo(finalZip, overwrite = true)
                        tempOrSource.delete()
                    }
                }
                tempFile = null
                "$relativeDir/$zipName" to finalZip
            } else {
                val destFile = File(gameDir, saveFile.name)
                saveFile.copyTo(destFile, overwrite = true)
                "$relativeDir/${saveFile.name}" to destFile
            }

            if (isHardcore) {
                saveArchiver.appendHardcoreTrailer(cachedFile)
            }

            val saveSize = cachedFile.length()

            if (slotName != null) {
                val existing = saveCacheDao.getByGameAndSlot(gameId, slotName)
                if (existing != null) {
                    val oldFile = File(cacheBaseDir, existing.cachePath)
                    oldFile.delete()
                    oldFile.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
                    saveCacheDao.deleteById(existing.id)
                    Log.d(TAG, "Replaced existing slot '$slotName' for game $gameId")
                }
            }

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = channelName,
                isLocked = isLocked,
                contentHash = contentHash,
                identityHash = identityHash,
                cheatsUsed = cheatsUsed,
                isHardcore = isHardcore,
                slotName = slotName,
                channelName = channelName,
                needsRemoteSync = needsRemoteSync,
                ownerUserId = ownerUserId,
                saveFormat = formatOf(sigil)
            )
            val insertedId = saveCacheDao.insert(entity)

            if (channelName != null) {
                saveCacheDao.setActiveRow(gameId, ownerUserId, insertedId)
                saveCacheDao.clearDirtyFlagForChannel(gameId, ownerUserId, channelName, excludeId = insertedId)
            } else {
                saveCacheDao.clearDirtyFlagForLatest(gameId, ownerUserId)
            }
            if (!secureSaves && !isHardcore) recordLocalWriteAnchor(gameId, emulatorId, channelName, savePath, ownerUserId, createMissing = false)
            saveOwnershipTracker.record(savePath, emulatorId, contentHash, gameId, channelName)
            val slotInfo = when {
                isHardcore -> " [HARDCORE]"
                channelName != null -> " (channel: $channelName)"
                else -> ""
            }
            Log.d(TAG, "Cached save for game $gameId at $cachePath (hash=$contentHash)$slotInfo")

            SaveDebugLogger.logCacheCreated(
                gameId = gameId,
                gameName = null,
                channel = channelName,
                sizeBytes = saveSize,
                contentHash = contentHash,
                isHardcore = isHardcore,
                needsRemoteSync = needsRemoteSync,
                emulatorId = emulatorId
            )

            pruneOldCaches(gameId, ownerUserId)
            CacheResult.Created(now.toEpochMilli(), insertedId)
        } catch (e: CancellationException) {
            tempFile?.delete()
            throw e
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to cache save", e)
            tempFile?.delete()
            CacheResult.Failed
        }
    }

    /**
     * Files a server save into the cache. [activate] decides whether the new row also becomes the
     * game's active save and the game's dirty flags are cleared; a history fill passes false so
     * the save the user resumes from does not move under them.
     */
    suspend fun cacheServerDownload(
        gameId: Long,
        emulatorId: String,
        downloadedFile: File,
        channelName: String?,
        activate: Boolean,
        serverTimestamp: Instant? = null,
        isLocked: Boolean = false,
        needsRemoteSync: Boolean = false,
        rommSaveId: Long? = null
    ): CacheResult = withContext(Dispatchers.IO) {
        @Suppress("NAME_SHADOWING")
        val channelName = resolveDefaultChannel(channelName, isHardcore = false)
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        if (!downloadedFile.exists() || downloadedFile.length() == 0L) {
            Logger.warn(TAG,"Downloaded file missing or empty: ${downloadedFile.absolutePath}")
            return@withContext CacheResult.Failed
        }

        try {
            val isZip = downloadedFile.inputStream().use { input ->
                val magic = ByteArray(2)
                input.read(magic) == 2 &&
                    magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte()
            }

            val contentHash = if (isZip) {
                saveArchiver.calculateZipHash(downloadedFile)
            } else {
                saveArchiver.calculateFileHash(downloadedFile)
            }

            val now = serverTimestamp ?: Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val relativeDir = cacheRelativeDir(ownerUserId, gameId, timestamp)
            val gameDir = File(cacheBaseDir, relativeDir)
            gameDir.mkdirs()

            val (cachePath, cachedFile) = if (isZip) {
                val zipName = if (downloadIsUnitBundle(gameId, emulatorId, downloadedFile)) "save$UNIT_CACHE_SUFFIX" else "save.zip"
                val finalZip = File(gameDir, zipName)
                downloadedFile.copyTo(finalZip, overwrite = true)
                "$relativeDir/$zipName" to finalZip
            } else {
                val destFile = File(gameDir, downloadedFile.name)
                downloadedFile.copyTo(destFile, overwrite = true)
                "$relativeDir/${downloadedFile.name}" to destFile
            }

            val saveSize = cachedFile.length()

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = channelName,
                isLocked = isLocked,
                contentHash = contentHash,
                channelName = channelName,
                needsRemoteSync = needsRemoteSync,
                rommSaveId = rommSaveId,
                ownerUserId = ownerUserId,
                serverCurrentAtSync = true
            )
            val insertedId = saveCacheDao.insert(entity)

            if (activate) {
                saveCacheDao.setActiveRow(gameId, ownerUserId, insertedId)
                saveCacheDao.clearDirtyFlagForLatest(gameId, ownerUserId)
            }

            Log.d(TAG, "Cached server download for game $gameId at $cachePath (zip=$isZip, channel=$channelName, activate=$activate)")

            SaveDebugLogger.logCacheCreated(
                gameId = gameId,
                gameName = null,
                channel = channelName,
                sizeBytes = saveSize,
                contentHash = contentHash,
                isHardcore = false,
                needsRemoteSync = needsRemoteSync,
                emulatorId = emulatorId
            )

            pruneOldCaches(gameId, ownerUserId)
            CacheResult.Created(now.toEpochMilli(), insertedId)
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to cache server download", e)
            CacheResult.Failed
        }
    }

    fun isUnitCache(entity: SaveCacheEntity): Boolean = entity.cachePath.endsWith(UNIT_CACHE_SUFFIX)

    private suspend fun downloadIsUnitBundle(gameId: Long, emulatorId: String, zip: File): Boolean {
        if (emulatorId !in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) return false
        val game = gameDao.getById(gameId) ?: return false
        val config = SavePathRegistry.getConfigForPlatform(emulatorId, game.platformSlug) ?: return false
        if (config.usesFolderBasedSaves || config.usesGciFormat) return false
        val layout = saveUnitResolver.layoutFor(game, emulatorId, null) ?: return false
        val contentName = game.localPath?.let { File(it).name } ?: return false
        val entries = saveArchiver.listFileEntries(zip)
        if (entries.isEmpty()) return false
        val placed = saveUnitResolver.placeEntries(
            entries, layout, game.platformSlug, contentName, game, saveUnitResolver.optionsFor(layout, gameId)
        )
        return placed.size == entries.size
    }

    private class Archived(
        val contentHash: String,
        val source: File,
        val tempFile: File?,
        val fileName: String? = null,
        val identityHash: String? = null
    ) {
        val isArchive: Boolean get() = tempFile != null
    }

    private suspend fun archiveForCache(
        gameId: Long,
        savePath: String,
        saveFile: File,
        unit: ResolvedSaveUnit?,
        precomputedContentHash: String?,
        sigil: SigilCollect.Found? = null
    ): Archived? {
        if (sigil != null) {
            val file = File(context.cacheDir, "temp_sigil_${System.nanoTime()}")
            file.writeBytes(sigil.data)
            return Archived(
                contentHash = precomputedContentHash ?: sigil.contentHash,
                source = file,
                tempFile = file,
                fileName = sigil.artifact,
                identityHash = sigil.identityHash.takeIf { it.isNotEmpty() }
            )
        }
        val gciMembers = if (unit == null) gciUnitMembers(savePath)?.takeIf { it.size > 1 } else null
        val members = unit?.memberPaths ?: gciMembers
        if (members != null) {
            if (!fal.prepareSaveAccess(*members.toTypedArray())) return null
            val zip = File(context.cacheDir, "temp_unit_${System.currentTimeMillis()}.zip")
            if (!saveArchiver.zipFiles(members.map { fal.getTransformedFile(it) }, zip)) {
                Logger.error(TAG,"Failed to bundle save unit | members=$members")
                zip.delete()
                return null
            }
            val bundleHash = saveArchiver.calculateZipHash(zip)
            if (unit != null && unit.unit.contentHash.isNotEmpty() && unit.unit.contentHash != bundleHash) {
                Logger.warn(TAG,"Unit hash parity mismatch | sigil=${unit.unit.contentHash} archive=$bundleHash members=$members")
            }
            return Archived(precomputedContentHash ?: bundleHash, zip, zip)
        }
        if (fal.isDirectory(savePath)) {
            val roots = resolveArchiveRoots(saveFile, savePath, gameDao.getById(gameId))
            if (roots.isEmpty()) {
                Logger.warn(TAG,"No save folders matched for game $gameId at $savePath -- skipping cache to avoid zipping unrelated saves")
                return null
            }
            val zip = File(context.cacheDir, "temp_save_${System.currentTimeMillis()}.zip")
            if (!zipArchiveRoots(roots, zip)) {
                Logger.error(TAG,"Failed to zip save folder(s)")
                zip.delete()
                return null
            }
            return Archived(precomputedContentHash ?: hashArchiveRoots(roots), zip, zip)
        }
        return Archived(precomputedContentHash ?: saveArchiver.calculateContentHash(saveFile), saveFile, null)
    }

    @androidx.annotation.VisibleForTesting
    internal fun placedMatchesArchive(archive: File, placed: List<String>): Boolean =
        java.util.zip.ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
                .filter { !it.isDirectory && it.name.endsWith(".gci", ignoreCase = true) }
                .associateBy { File(it.name).name }
            if (placed.map { File(it).name }.toSet() != entries.keys) return@use false
            placed.all { path ->
                val entry = entries[File(path).name] ?: return@all false
                val written = fal.readBytes(path) ?: return@all false
                zip.getInputStream(entry).use { it.readBytes() }.contentEquals(written)
            }
        }

    private fun isGciUnitArchive(entity: SaveCacheEntity, targetPath: String): Boolean =
        entity.cachePath.endsWith(".zip") && targetPath.endsWith(".gci", ignoreCase = true)

    private fun gciUnitMembers(savePath: String): List<String>? =
        if (savePath.endsWith(".gci", ignoreCase = true)) gciSaveHandler.unitMembers(savePath) else null

    private suspend fun isFolderCache(entity: SaveCacheEntity): Boolean {
        if (!entity.cachePath.endsWith(".zip") || isUnitCache(entity)) return false
        if (entity.emulatorId !in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) return true
        val game = gameDao.getById(entity.gameId) ?: return true
        val config = SavePathRegistry.getConfigForPlatform(entity.emulatorId, game.platformSlug) ?: return true
        return config.usesFolderBasedSaves || config.usesGciFormat
    }

    /**
     * Every live path the save at [savePath] occupies for this game and emulator, the primary
     * included, so a caller that clears or backs up a save acts on the whole unit. Just the
     * path itself when no unit resolves.
     */
    suspend fun unitMemberPaths(gameId: Long, emulatorId: String, savePath: String): List<String> {
        val game = gameDao.getById(gameId) ?: return listOf(savePath)
        return saveUnitResolver.resolveForSavePath(savePath, game, emulatorId, null, hash = false)
            ?.memberPaths?.takeIf { it.isNotEmpty() }
            ?: listOf(savePath)
    }

    /**
     * Writes every member of a cached unit under the root [primaryPath] sits in, without the
     * ownership and anchor bookkeeping [restoreSave] does, for the built-in launch path that
     * hands the primary bytes to the core itself.
     */
    suspend fun materializeUnit(entity: SaveCacheEntity, primaryPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!isUnitCache(entity)) return@withContext false
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) return@withContext false
        restoreUnit(entity, cacheFile, primaryPath)
    }

    private suspend fun restoreUnit(entity: SaveCacheEntity, cacheFile: File, targetPath: String): Boolean {
        val game = gameDao.getById(entity.gameId) ?: return false
        val layout = saveUnitResolver.layoutFor(game, entity.emulatorId, null) ?: return false
        val contentName = game.localPath?.let { File(it).name } ?: return false
        val destinations = saveUnitResolver.placeBundle(
            saveArchiver.listFileEntries(cacheFile), targetPath, layout, game.platformSlug, contentName, game
        )
        if (destinations == null) {
            Logger.error(TAG,"[RESTORE] cache=${entity.id} unit bundle has entries the layout cannot place | zip=${cacheFile.name}")
            return false
        }
        val ok = saveArchiver.unzipEntriesTo(cacheFile, destinations)
        Log.d(TAG, "[RESTORE] cache=${entity.id} unit=${cacheFile.name} placed=${destinations.values} ok=$ok")
        return ok
    }

    private suspend fun multiMemberUnit(
        gameId: Long,
        emulatorId: String,
        savePath: String,
        coreName: String?
    ): ResolvedSaveUnit? {
        val game = gameDao.getById(gameId) ?: return null
        return saveUnitResolver.resolveForSavePath(savePath, game, emulatorId, coreName, hash = true)
            ?.takeIf { it.isMulti }
    }

    /**
     * Caches the save currently at [savePath] as a rollback version before something overwrites
     * it. True when there is nothing to protect or it is now cached (or already was); false when
     * the bytes could not be cached, and the caller must not overwrite them.
     */
    suspend fun protectBeforeOverwrite(gameId: Long, emulatorId: String, savePath: String): Boolean = withContext(Dispatchers.IO) {
        if (!fal.prepareSaveAccess(savePath)) return@withContext false
        when (sigilSaveHandler.collect(gameId, emulatorId)) {
            SigilCollect.NotRouted -> isProvenAbsent(savePath) || when (holdsSaveForGame(gameId, savePath)) {
                false -> true
                null -> false
                true -> cacheAsRollback(gameId, emulatorId, savePath) !is CacheResult.Failed
            }
            SigilCollect.Absent -> true
            is SigilCollect.Unreadable -> false
            is SigilCollect.Found -> cacheAsRollback(gameId, emulatorId, savePath) !is CacheResult.Failed
        }
    }

    private fun isProvenAbsent(savePath: String): Boolean {
        if (fal.exists(savePath)) return false
        var child = File(savePath)
        while (true) {
            val parent = child.parentFile ?: return false
            if (fal.isListable(parent.path)) {
                return fal.listFilesUnion(parent.path).none { it.name.equals(child.name, ignoreCase = true) }
            }
            child = parent
        }
    }

    private suspend fun holdsSaveForGame(gameId: Long, savePath: String): Boolean? {
        if (!fal.isDirectory(savePath)) return true
        if (!fal.isListable(savePath)) return null
        if (fal.listFilesUnion(savePath).isEmpty()) return false
        val game = gameDao.getById(gameId) ?: return true
        val saveId = game.saveId ?: game.titleId ?: return true
        if (PlatformDefinitions.getCanonicalSlug(game.platformSlug) !in FOLDER_PREFIX_PLATFORMS) return true
        val handler = saveHandlerRegistry.getFolderHandler(game.platformSlug) ?: return true
        if (fal.listFiles(savePath) == null) return true
        return handler.findAllSaveFoldersBySaveId(savePath, saveId).isNotEmpty()
    }

    suspend fun cacheAsRollback(
        gameId: Long,
        emulatorId: String,
        savePath: String
    ): CacheResult = withContext(Dispatchers.IO) {
        if (!fal.prepareSaveAccess(savePath)) return@withContext CacheResult.Failed
        val sigil = when (val collected = sigilSaveHandler.collect(gameId, emulatorId)) {
            SigilCollect.NotRouted -> null
            is SigilCollect.Found -> collected
            SigilCollect.Absent, is SigilCollect.Unreadable -> return@withContext CacheResult.Failed
        }
        if (sigil == null && !fal.exists(savePath)) {
            Logger.warn(TAG,"Save file does not exist for rollback: $savePath")
            return@withContext CacheResult.Failed
        }

        val saveFile = fal.getTransformedFile(savePath)
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        var tempFile: File? = null
        val unit = if (sigil != null || fal.isDirectory(savePath)) null else multiMemberUnit(gameId, emulatorId, savePath, null)

        try {
            val archived = archiveForCache(gameId, savePath, saveFile, unit, precomputedContentHash = null, sigil = sigil)
                ?: return@withContext CacheResult.Failed
            tempFile = archived.tempFile
            val contentHash = archived.contentHash
            val tempOrSource = archived.source

            val existingWithHash = saveCacheDao.getAllByGameChannelAndHash(gameId, ownerUserId, null, contentHash).firstOrNull()
            if (existingWithHash != null) {
                Log.d(TAG, "Rollback skipped - identical save already cached (hash=$contentHash)")
                tempFile?.delete()
                return@withContext CacheResult.Duplicate(existingWithHash.id, contentHash)
            }

            val now = Instant.now()
            val timestamp = TIMESTAMP_FORMAT.format(now)
            val relativeDir = cacheRelativeDir(ownerUserId, gameId, timestamp)
            val gameDir = File(cacheBaseDir, relativeDir)
            gameDir.mkdirs()

            val (cachePath, cachedFile) = if (archived.isArchive) {
                val zipName = archived.fileName
                    ?: if (unit != null) "${unit.unit.key}$UNIT_CACHE_SUFFIX" else "save.zip"
                val finalZip = File(gameDir, zipName)
                tempOrSource.renameTo(finalZip).let { renamed ->
                    if (!renamed) {
                        tempOrSource.copyTo(finalZip, overwrite = true)
                        tempOrSource.delete()
                    }
                }
                tempFile = null
                "$relativeDir/$zipName" to finalZip
            } else {
                val destFile = File(gameDir, saveFile.name)
                saveFile.copyTo(destFile, overwrite = true)
                "$relativeDir/${saveFile.name}" to destFile
            }

            val saveSize = cachedFile.length()

            val entity = SaveCacheEntity(
                gameId = gameId,
                emulatorId = emulatorId,
                cachedAt = now,
                saveSize = saveSize,
                cachePath = cachePath,
                note = "Rollback",
                isLocked = false,
                contentHash = contentHash,
                isRollback = true,
                ownerUserId = ownerUserId,
                saveFormat = formatOf(sigil)
            )
            val rollbackId = saveCacheDao.insert(entity)
            Log.d(TAG, "Created rollback save for game $gameId at $cachePath")

            CacheResult.Created(now.toEpochMilli(), rollbackId)
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to cache rollback save", e)
            tempFile?.delete()
            CacheResult.Failed
        }
    }

    suspend fun restoreThroughSigil(entity: SaveCacheEntity): SigilRestore = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) return@withContext SigilRestore.NotRouted
        restoreUnlessNative(entity, cacheFile, entity.emulatorId)
    }

    private fun formatOf(sigil: SigilCollect.Found?): String =
        if (sigil != null) SaveCacheEntity.FORMAT_NEUTRAL else SaveCacheEntity.FORMAT_NATIVE

    private suspend fun restoreUnlessNative(entity: SaveCacheEntity, cacheFile: File, emulatorId: String?): SigilRestore {
        if (entity.saveFormat == SaveCacheEntity.FORMAT_NATIVE) return SigilRestore.NotRouted
        if (entity.saveFormat != SaveCacheEntity.FORMAT_NEUTRAL) {
            return sigilSaveHandler.restore(entity.gameId, cacheFile, emulatorId)
        }
        val unrouted = SigilRestore.Refused("cache ${entity.id} holds a Sigil unit and no Sigil layout covers this emulator now")
        val route = sigilSaveHandler.localRoute(entity.gameId, emulatorId) ?: return unrouted
        val restored = sigilSaveHandler.restoreInto(route, entity.gameId, cacheFile.readBytes())
        return if (restored == SigilRestore.NotRouted) unrouted else restored
    }

    private suspend fun isSigilRouteRoot(gameId: Long, emulatorId: String, path: String): Boolean =
        sigilSaveHandler.localRoute(gameId, emulatorId)?.root?.trimEnd('/') == path.trimEnd('/')

    suspend fun findCachedByHash(gameId: Long, contentHash: String): com.nendo.argosy.data.local.entity.SaveCacheEntity? =
        withContext(Dispatchers.IO) { saveCacheDao.getByGameAndHash(gameId, syncPreferencesRepository.getRommUserId(), contentHash) }

    /**
     * Writes cache [cacheId] to [targetPath] for [targetEmulatorId], the emulator that reads the
     * save there. Anchor, ownership and the post-restore hash are recorded under that emulator,
     * whichever emulator the cached save came from.
     */
    suspend fun restoreSave(cacheId: Long, targetPath: String, targetEmulatorId: String): Boolean = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId)
        if (entity == null) {
            Logger.error(TAG,"Cache entry not found: $cacheId")
            return@withContext false
        }
        val secureSaves = syncPreferencesRepository.isSecureSaves()

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) {
            Logger.error(TAG,"Cache file not found: ${entity.cachePath}")
            return@withContext false
        }

        fal.prepareSaveAccess(targetPath)
        var placedGciMembers: List<String>? = null
        try {
            val sigilRestore = restoreUnlessNative(entity, cacheFile, targetEmulatorId).let { routed ->
                if (routed == SigilRestore.NotRouted && isSigilRouteRoot(entity.gameId, targetEmulatorId, targetPath)) {
                    sigilSaveHandler.restore(entity.gameId, cacheFile, targetEmulatorId)
                } else {
                    routed
                }
            }
            when (sigilRestore) {
                SigilRestore.NotRouted -> if (isSigilRouteRoot(entity.gameId, targetEmulatorId, targetPath)) {
                    Logger.warn(TAG, "Restore of cache $cacheId refused: $targetPath is the Sigil save root of $targetEmulatorId and no Sigil layout takes the cached files")
                    return@withContext false
                }
                is SigilRestore.Refused -> {
                    Logger.warn(TAG, "Restore of cache $cacheId refused: ${sigilRestore.reason}")
                    return@withContext false
                }
                is SigilRestore.Restored -> {
                    Log.d(TAG, "Restored save from cache $cacheId through Sigil (legacy hardcore marker=${sigilRestore.hardcoreMarker})")
                    recordPlacement(entity, targetPath, targetEmulatorId, secureSaves)
                    return@withContext true
                }
            }
            val writeOk = if (isUnitCache(entity)) {
                restoreUnit(entity, cacheFile, targetPath)
            } else if (isGciUnitArchive(entity, targetPath)) {
                gciSaveHandler.placeUnitArchive(cacheFile, File(targetPath).parent ?: targetPath)
                    .also { placedGciMembers = it }
                    .isNotEmpty()
            } else if (isFolderCache(entity)) {
                val game = gameDao.getById(entity.gameId)
                if (!archiveHoldsThisSave(cacheFile, game, targetPath)) {
                    return@withContext false
                }
                fal.mkdirs(targetPath)
                val targetFile = fal.getTransformedFile(targetPath)
                val preserveRoots = game?.platformSlug
                    ?.let { PlatformDefinitions.getCanonicalSlug(it) } in FOLDER_PREFIX_PLATFORMS
                val folderHandler = game?.platformSlug?.let { saveHandlerRegistry.getFolderHandler(it) }
                Log.d(TAG, "[RESTORE] cache=$cacheId zip=${cacheFile.name} size=${cacheFile.length()} target=$targetPath transformed=${targetFile.absolutePath} exists=${targetFile.exists()} dir=${targetFile.isDirectory} preserveRoots=$preserveRoots platform=${game?.platformSlug}")
                val ok = when {
                    saveArchiver.isJksvFormat(cacheFile) ->
                        saveArchiver.unzipPreservingStructure(cacheFile, targetFile, SwitchSaveHandler.JKSV_EXCLUDE_FILES)
                    preserveRoots -> saveArchiver.unzipToFolder(cacheFile, targetFile).also { placed ->
                        if (placed) folderHandler?.ensureContainerPrepared(targetFile)
                    }
                    folderHandler != null -> folderHandler.placeArchive(cacheFile, targetFile, game?.saveId ?: game?.titleId)
                    else -> saveArchiver.unzipSingleFolder(cacheFile, targetFile)
                }
                Log.d(TAG, "[RESTORE] unzip returned=$ok | post-restore listing of $targetPath: ${restoreListing(targetPath)}")
                ok
            } else {
                val parentPath = targetPath.substringBeforeLast('/')
                if (parentPath.isNotEmpty() && parentPath != targetPath) {
                    fal.mkdirs(parentPath)
                }
                val bytesWithoutTrailer = saveArchiver.readBytesWithoutTrailer(cacheFile)
                if (bytesWithoutTrailer != null) {
                    fal.writeBytes(targetPath, bytesWithoutTrailer)
                } else {
                    fal.copyFile(cacheFile.absolutePath, targetPath)
                }
            }

            if (!writeOk) {
                Logger.error(TAG,"Failed to materialize cache $cacheId at $targetPath (write returned false)")
                SaveDebugLogger.logError(
                    operation = "restoreSave",
                    gameId = entity.gameId,
                    gameName = null,
                    channel = entity.channelName,
                    error = IllegalStateException("write returned false for $targetPath")
                )
                return@withContext false
            }

            if (!fal.commitSaveAccess(targetPath)) {
                Logger.error(TAG, "Restored cache $cacheId into the mirrored copy but could not write it to $targetPath")
                return@withContext false
            }

            Log.d(TAG, "Restored save from cache $cacheId to $targetPath")

            SaveDebugLogger.logCacheRestored(
                gameId = entity.gameId,
                gameName = null,
                channel = entity.channelName,
                cacheId = cacheId,
                targetPath = targetPath
            )

            val placed = placedGciMembers
            if (placed != null) {
                val intact = placedMatchesArchive(cacheFile, placed)
                Logger.debug(TAG, "[RESTORE_VERIFY] cache=$cacheId gci bundle members=${placed.size} intact=$intact")
                if (!intact) {
                    Logger.warn(TAG, "Restore of cache $cacheId wrote GameCube files that differ from the archive | target=$targetPath")
                    return@withContext false
                }
            } else try {
                val game = gameDao.getById(entity.gameId)
                val actualHash = computeRestoredHash(game, targetPath, targetEmulatorId)
                val match = actualHash != null && actualHash == entity.contentHash
                Log.d(TAG, "[RESTORE_VERIFY] cache=$cacheId target=$targetPath expected=${entity.contentHash} actual=$actualHash match=$match | hashedListing=${restoreListing(targetPath)}")
                SaveDebugLogger.logRestoreVerify(
                    gameId = entity.gameId,
                    cacheId = cacheId,
                    targetPath = targetPath,
                    expectedHash = entity.contentHash,
                    actualHash = actualHash,
                    match = match
                )
                if (entity.contentHash != null && actualHash != null && !match) {
                    Logger.warn(TAG,"Restore hash mismatch for cache $cacheId: expected=${entity.contentHash}, actual=$actualHash, target=$targetPath -- failing restore so caller falls through to network fetch")
                    return@withContext false
                }
            } catch (e: Exception) {
                Logger.warn(TAG,"Restore verify failed for cache $cacheId: ${e.message}")
            }

            recordPlacement(entity, targetPath, targetEmulatorId, secureSaves)
            true
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to restore save from cache", e)
            SaveDebugLogger.logError(
                operation = "restoreSave",
                gameId = entity.gameId,
                gameName = null,
                channel = entity.channelName,
                error = e
            )
            false
        }
    }

    /**
     * Records that [entity] now sits at [targetPath] for [targetEmulatorId]: the local-write anchor
     * (Secure Saves off, casual saves only) and the owning account, both under that emulator.
     */
    suspend fun recordPlacement(
        entity: SaveCacheEntity,
        targetPath: String,
        targetEmulatorId: String,
        secureSaves: Boolean
    ) {
        if (!secureSaves && !entity.isHardcore) {
            recordLocalWriteAnchor(
                entity.gameId,
                targetEmulatorId,
                entity.channelName,
                targetPath,
                entity.ownerUserId ?: syncPreferencesRepository.getRommUserId(),
                createMissing = true
            )
        }
        saveOwnershipTracker.record(targetPath, targetEmulatorId, entity.contentHash, entity.gameId, entity.channelName)
    }

    /**
     * The files a native cache row holds, by path relative to the folder they were cached from:
     * each entry of a zipped row, or the single file under its own name. Null when the cache file
     * is missing or unreadable, or an entry names a path outside that folder.
     */
    suspend fun nativeFiles(entity: SaveCacheEntity): Map<String, ByteArray>? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.isFile) return@withContext null
        if (!entity.cachePath.endsWith(".zip")) {
            val bytes = saveArchiver.readBytesWithoutTrailer(cacheFile) ?: cacheFile.readBytes()
            return@withContext mapOf(cacheFile.name to bytes)
        }
        try {
            java.util.zip.ZipInputStream(cacheFile.inputStream()).use { zip ->
                val files = LinkedHashMap<String, ByteArray>()
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    val name = entry.name.trimStart('/')
                    if (name.split('/').any { it == ".." }) return@withContext null
                    files[name] = zip.readBytes()
                }
                files
            }
        } catch (e: java.io.IOException) {
            Logger.warn(TAG, "Cache ${entity.id} could not be read as a zip: ${e.message}")
            null
        }
    }

    suspend fun deleteCachedSave(cacheId: Long) = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId) ?: return@withContext

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        val parentDir = cacheFile.parentFile
        cacheFile.delete()
        if (parentDir?.listFiles()?.isEmpty() == true) {
            parentDir.delete()
        }

        saveCacheDao.deleteById(cacheId)
        Log.d(TAG, "Deleted cached save $cacheId")

        SaveDebugLogger.logCacheDeleted(
            gameId = entity.gameId,
            gameName = null,
            channel = entity.channelName,
            cacheId = cacheId,
            reason = "user_delete"
        )
    }

    suspend fun renameSave(cacheId: Long, name: String?) = withContext(Dispatchers.IO) {
        saveCacheDao.setNote(cacheId, name?.takeIf { it.isNotBlank() })
    }

    suspend fun renameChannel(gameId: Long, oldName: String, newName: String) = withContext(Dispatchers.IO) {
        saveCacheDao.renameChannel(
            gameId = gameId,
            ownerUserId = syncPreferencesRepository.getRommUserId(),
            oldName = oldName,
            newName = newName
        )
    }

    suspend fun getSavesInChannel(gameId: Long, channelName: String): List<SaveCacheEntity> =
        withContext(Dispatchers.IO) {
            saveCacheDao.getAllInChannel(
                gameId = gameId,
                ownerUserId = syncPreferencesRepository.getRommUserId(),
                channelName = channelName
            )
        }

    suspend fun channelExists(gameId: Long, channelName: String): Boolean = withContext(Dispatchers.IO) {
        saveCacheDao.getByGameAndChannel(gameId, channelName) != null
    }

    suspend fun contentHashOf(cacheId: Long): String? = withContext(Dispatchers.IO) {
        saveCacheDao.getById(cacheId)?.contentHash
    }

    suspend fun channelsHoldingHash(gameId: Long, contentHash: String): Set<String> =
        withContext(Dispatchers.IO) {
            saveCacheDao.getAllByGameAndHash(
                gameId = gameId,
                ownerUserId = syncPreferencesRepository.getRommUserId(),
                hash = contentHash
            ).mapNotNull { it.channelName?.lowercase() }.toSet()
        }

    suspend fun copyToChannel(cacheId: Long, channelName: String): Long? = withContext(Dispatchers.IO) {
        val source = saveCacheDao.getById(cacheId)
        if (source == null) {
            Logger.error(TAG,"Source cache entry not found: $cacheId")
            return@withContext null
        }

        val sourceFile = File(cacheBaseDir, source.cachePath)
        if (!sourceFile.exists()) {
            Logger.error(TAG,"Source cache file not found: ${source.cachePath}")
            return@withContext null
        }

        val now = Instant.now()
        val timestamp = TIMESTAMP_FORMAT.format(now)
        val baseDir = cacheRelativeDir(source.ownerUserId, source.gameId, timestamp)
        val landsOnSource = File(cacheBaseDir, "$baseDir/${sourceFile.name}").canonicalPath == sourceFile.canonicalPath
        val relativeDir = if (landsOnSource) "${baseDir}$COPY_DIR_SUFFIX" else baseDir
        val gameDir = File(cacheBaseDir, relativeDir)

        try {
            gameDir.mkdirs()
            val destFile = File(gameDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)

            val cachePath = "$relativeDir/${sourceFile.name}"
            val entity = SaveCacheEntity(
                gameId = source.gameId,
                emulatorId = source.emulatorId,
                cachedAt = now,
                saveSize = destFile.length(),
                cachePath = cachePath,
                note = channelName,
                isLocked = SaveSlotClassifier.kindOf(
                    channelName = channelName,
                    isLatest = false,
                    isArchival = false
                ) == SaveSlotKind.NAMED,
                contentHash = source.contentHash,
                channelName = channelName,
                needsRemoteSync = true,
                ownerUserId = source.ownerUserId,
                saveFormat = source.saveFormat
            )

            val newId = saveCacheDao.insert(entity)
            Log.d(TAG, "Created channel '$channelName' from cache $cacheId -> $newId")
            newId
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to copy cache to channel", e)
            gameDir.deleteRecursively()
            null
        }
    }

    suspend fun deleteSave(cacheId: Long) = deleteCachedSave(cacheId)

    fun getCachesForGame(gameId: Long): Flow<List<SaveCacheEntity>> =
        saveCacheDao.observeByGame(gameId)

    /**
     * A restore unpacks straight over the live save directory, so an archive that does not
     * hold this game's save must not be written. Mirrors the download path's check; the two
     * differ only in where the archive came from.
     */
    private suspend fun archiveHoldsThisSave(
        cacheFile: File,
        game: GameEntity?,
        targetPath: String
    ): Boolean {
        val entry = game ?: return true
        val saveId = entry.saveId ?: entry.titleId ?: return true
        if (PlatformDefinitions.getCanonicalSlug(entry.platformSlug) == "switch") {
            return switchArchiveHoldsTitle(cacheFile, saveId, targetPath)
        }
        val handler = saveHandlerRegistry.getFolderHandler(entry.platformSlug) ?: return true

        val roots = saveArchiver.peekRootEntryNames(cacheFile)
        val tier = handler.matchArchive(cacheFile, saveId)
        if (tier == null) {
            Logger.error(
                TAG,
                "[RESTORE] refusing archive that does not hold saveId=$saveId | " +
                    "roots=$roots, target=$targetPath, zip=${cacheFile.name}"
            )
            return false
        }
        if (tier != com.nendo.argosy.data.sync.platform.FolderSaveHandler.ArchiveRootMatch.EXACT) {
            Log.d(TAG, "[RESTORE] archive matched on $tier | saveId=$saveId, roots=$roots")
        }
        return true
    }

    private fun switchArchiveHoldsTitle(cacheFile: File, saveId: String, targetPath: String): Boolean {
        val root = saveArchiver.peekRootFolderName(cacheFile)
            ?.takeIf { it.length == SWITCH_TITLE_ID_LENGTH && it.all { c -> c.isDigit() || c.uppercaseChar() in 'A'..'F' } }
            ?: return true
        val archiveTitle = SwitchTitleIds.baseApplicationId(root.uppercase())
        val gameTitle = SwitchTitleIds.baseApplicationId(saveId.uppercase())
        if (archiveTitle == gameTitle) return true
        Logger.error(
            TAG,
            "[RESTORE] refusing Switch archive rooted at another title | root=$root, saveId=$saveId, " +
                "target=$targetPath, zip=${cacheFile.name}"
        )
        return false
    }

    /**
     * The folders an archive of this save is built from and the root name each takes, which is
     * the same set [SaveUploader] ships: prefix platforms bundle every sibling under its own
     * name, a layout with named roots (3DS `data` plus `extdata`) answers for itself, and every
     * other save is the one folder at [savePath].
     */
    private fun resolveArchiveRoots(saveFile: File, savePath: String, game: GameEntity?): List<ArchiveRoot> {
        val saveId = game?.saveId ?: game?.titleId
        val handler = game?.platformSlug?.let { saveHandlerRegistry.getFolderHandler(it) }
        val canonical = game?.platformSlug?.let { PlatformDefinitions.getCanonicalSlug(it) }
        if (saveId == null || handler == null) return listOf(ArchiveRoot(saveFile.name, saveFile))
        if (canonical in FOLDER_PREFIX_PLATFORMS) {
            return handler.findAllSaveFoldersBySaveId(savePath, saveId)
                .map { fal.getTransformedFile(it) }
                .filter { it.exists() && it.isDirectory }
                .map { ArchiveRoot(it.name, it) }
        }
        return handler.namedArchiveRoots(savePath, saveId)
            ?.filter { it.folder.exists() && it.folder.isDirectory }
            ?: listOf(ArchiveRoot(saveFile.name, saveFile))
    }

    private fun zipArchiveRoots(roots: List<ArchiveRoot>, target: File): Boolean =
        if (roots.size == 1 && roots[0].isOwnName) {
            saveArchiver.zipFolder(roots[0].folder, target)
        } else {
            saveArchiver.zipNamedFolders(roots, target)
        }

    private fun hashArchiveRoots(roots: List<ArchiveRoot>): String =
        if (roots.size == 1 && roots[0].isOwnName) {
            saveArchiver.calculateFolderAsZipHash(roots[0].folder)
        } else {
            saveArchiver.calculateNamedFoldersAsZipHash(roots)
        }

    private fun restoreListing(targetPath: String): String = try {
        val f = fal.getTransformedFile(targetPath)
        when {
            !f.exists() -> "MISSING"
            f.isFile -> "FILE size=${f.length()}"
            else -> {
                val children = f.listFiles()?.sortedBy { it.name } ?: emptyList()
                "DIR count=${children.size} [" + children.joinToString(", ") { "${it.name}:${if (it.isDirectory) "d" else it.length().toString()}" } + "]"
            }
        }
    } catch (e: Exception) {
        "LISTING_ERROR ${e.message}"
    }

    private suspend fun computeRestoredHash(game: GameEntity?, targetPath: String, emulatorId: String? = null): String? {
        if (game != null && emulatorId != null && fal.isFile(targetPath)) {
            saveUnitResolver.resolveForSavePath(targetPath, game, emulatorId, null, hash = true)
                ?.takeIf { it.isMulti && it.unit.contentHash.isNotEmpty() }
                ?.let { return it.unit.contentHash }
        }
        return calculateLocalSaveHash(targetPath, game?.id, emulatorId)
    }

    suspend fun dedupeIdenticalCaches(gameId: Long): Int = withContext(Dispatchers.IO) {
        val all = saveCacheDao.getByGame(gameId)
            .filter { !it.contentHash.isNullOrBlank() }
        val groups = all.groupBy { listOf(it.ownerUserId, it.contentHash, it.channelName, it.isHardcore) }
        var deleted = 0
        for ((_, dupes) in groups) {
            if (dupes.size <= 1) continue
            val keeper = dupes.maxWithOrNull(
                compareBy(
                    { if (it.isActive) 1 else 0 },
                    { if (it.isLocked) 1 else 0 },
                    { if (it.rommSaveId != null) 1 else 0 },
                    { it.cachedAt },
                    { it.id }
                )
            ) ?: continue
            if (keeper.isActive) {
                val serverId = keeper.rommSaveId ?: dupes.firstNotNullOfOrNull { it.rommSaveId }
                if (serverId != null && keeper.rommSaveId == null) saveCacheDao.updateRommSaveId(keeper.id, serverId)
                if (!keeper.isLocked && dupes.any { it.isLocked }) saveCacheDao.setLocked(keeper.id, true)
            }
            for (entry in dupes) {
                if (entry.id == keeper.id || entry.isActive) continue
                val cacheFile = File(cacheBaseDir, entry.cachePath)
                val parentDir = cacheFile.parentFile
                cacheFile.delete()
                if (parentDir?.listFiles()?.isEmpty() == true) parentDir.delete()
                saveCacheDao.deleteById(entry.id)
                deleted++
                SaveDebugLogger.logCacheDeleted(
                    gameId = gameId,
                    gameName = null,
                    channel = entry.channelName,
                    cacheId = entry.id,
                    reason = "dedupe (kept id=${keeper.id}, hash=${entry.contentHash?.take(12)})"
                )
            }
        }
        if (deleted > 0) {
            Log.d(TAG, "Deduped $deleted identical caches for game $gameId")
        }
        deleted
    }

    suspend fun getCachesForGameOnce(gameId: Long): List<SaveCacheEntity> =
        saveCacheDao.getByGame(gameId)

    /**
     * Trims a game's cache down to the configured limit. Rows a pending queue row points at are
     * never evicted: that cache row is the payload of a deferred upload, and dropping it would
     * leave the queue row pointing at bytes that no longer exist.
     */
    suspend fun pruneOldCaches(
        gameId: Long,
        owner: Long? = null
    ) = withContext(Dispatchers.IO) {
        val ownerUserId = owner ?: syncPreferencesRepository.getRommUserId()
        val prefs = preferencesRepository.userPreferences.first()
        val limit = prefs.saveCacheLimit

        val caches = saveCacheDao.getByGameAndOwner(gameId, ownerUserId).filter { it.rommSaveId == null }
        val totalCount = caches.size
        if (totalCount <= limit) return@withContext

        val lockedCount = caches.count { it.isLocked }
        val effectiveLimit = maxOf(limit, lockedCount + MIN_UNLOCKED_SLOTS)

        val toDeleteCount = totalCount - effectiveLimit
        if (toDeleteCount <= 0) return@withContext

        val pinnedIds = pendingSyncQueueDao.getPinnedCacheIdsForGame(gameId)
        val toDelete = saveCacheDao
            .getOldestUnlockedForOwnerExcluding(gameId, ownerUserId, pinnedIds)
            .filterNot { it.rommSaveId != null || it.isActive || it.needsRemoteSync || it.isHardcore }
            .take(toDeleteCount)
        if (toDelete.isEmpty()) return@withContext

        for (cache in toDelete) {
            val cacheFile = File(cacheBaseDir, cache.cachePath)
            val parentDir = cacheFile.parentFile
            cacheFile.delete()
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }

        saveCacheDao.deleteByIds(toDelete.map { it.id })
        Log.d(TAG, "Pruned ${toDelete.size} old caches for game $gameId (pinned=${pinnedIds.size})")

        SaveDebugLogger.logCachePruned(
            gameId = gameId,
            gameName = null,
            prunedCount = toDelete.size,
            remainingCount = totalCount - toDelete.size
        )
    }

    suspend fun getCacheById(cacheId: Long): SaveCacheEntity? =
        saveCacheDao.getById(cacheId)

    /**
     * The top-level entry names of a cached archive, or null when the cache is not an archive
     * or its file is gone. A pre-restore clear uses this to remove only what the archive replaces.
     */
    suspend fun archiveRootNames(cacheId: Long): Set<String>? = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId) ?: return@withContext null
        if (!entity.cachePath.endsWith(".zip")) return@withContext null
        val file = File(cacheBaseDir, entity.cachePath)
        if (!file.exists()) return@withContext null
        saveArchiver.peekRootEntryNames(file)
    }

    suspend fun getLatestHardcoreSave(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getLatestHardcoreSave(gameId, syncPreferencesRepository.getRommUserId())

    suspend fun hasHardcoreSave(gameId: Long): Boolean =
        saveCacheDao.hasHardcoreSave(gameId, syncPreferencesRepository.getRommUserId())

    suspend fun isValidHardcoreSave(entity: SaveCacheEntity): Boolean = withContext(Dispatchers.IO) {
        if (!entity.isHardcore) return@withContext false
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) return@withContext false
        saveArchiver.hasHardcoreTrailer(cacheFile)
    }

    suspend fun getLatestCasualSave(gameId: Long, channelName: String?): SaveCacheEntity? {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        return if (channelName != null) {
            saveCacheDao.getLatestCasualSaveInChannel(gameId, ownerUserId, channelName)
        } else {
            saveCacheDao.getLatestCasualSave(gameId)
        }
    }

    suspend fun getMostRecentSave(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getMostRecent(gameId, syncPreferencesRepository.getRommUserId())

    suspend fun getByTimestamp(gameId: Long, timestampMillis: Long): SaveCacheEntity? =
        saveCacheDao.getByTimestamp(gameId, timestampMillis)

    suspend fun getMostRecentInChannel(gameId: Long, channelName: String): SaveCacheEntity? =
        saveCacheDao.getMostRecentInChannel(gameId, syncPreferencesRepository.getRommUserId(), channelName)

    suspend fun getByGameAndHash(gameId: Long, hash: String): SaveCacheEntity? =
        saveCacheDao.getByGameAndHash(gameId, syncPreferencesRepository.getRommUserId(), hash)

    suspend fun getSaveBytes(cacheId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId) ?: return@withContext null
        getSaveBytesFromEntity(entity)
    }

    suspend fun getSaveBytesFromEntity(entity: SaveCacheEntity): ByteArray? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) {
            Logger.error(TAG,"Cache file not found: ${entity.cachePath}")
            return@withContext null
        }

        try {
            if (isUnitCache(entity)) {
                saveArchiver.listFileEntries(cacheFile).firstOrNull()?.let { primary ->
                    saveArchiver.readEntryBytes(cacheFile, primary)
                }
            } else if (isFolderCache(entity)) {
                val tempDir = File(context.cacheDir, "save_extract_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                if (saveArchiver.unzipToFolder(cacheFile, tempDir)) {
                    val srmFile = tempDir.walkTopDown().firstOrNull { it.extension == "srm" }
                    val bytes = srmFile?.readBytes()
                    tempDir.deleteRecursively()
                    bytes
                } else {
                    tempDir.deleteRecursively()
                    null
                }
            } else {
                saveArchiver.readBytesWithoutTrailer(cacheFile) ?: cacheFile.readBytes()
            }
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to read cache file: ${entity.cachePath}", e)
            null
        }
    }

    /**
     * Deletes the cached versions the server also holds and keeps every version that exists only
     * on this device. Returns the number of versions kept.
     */
    suspend fun deleteServerHeldCachesForGame(gameId: Long): Int = withContext(Dispatchers.IO) {
        val (localOnly, serverHeld) = saveCacheDao.getByGame(gameId)
            .partition { it.rommSaveId == null || it.needsRemoteSync }
        for (cache in serverHeld) {
            val cacheFile = File(cacheBaseDir, cache.cachePath)
            val parentDir = cacheFile.parentFile
            if (cacheFile.isDirectory) cacheFile.deleteRecursively() else cacheFile.delete()
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }
        saveCacheDao.deleteByIds(serverHeld.map { it.id })

        if (localOnly.isEmpty()) {
            val gameDirs = listOf(File(cacheBaseDir, gameId.toString())) +
                (cacheBaseDir.listFiles { f ->
                    f.isDirectory && com.nendo.argosy.util.AppPaths.isOwnerCacheDir(f.name)
                }?.map { File(it, gameId.toString()) } ?: emptyList())
            for (gameDir in gameDirs) {
                if (gameDir.exists() && gameDir.isDirectory) {
                    gameDir.deleteRecursively()
                }
            }
        }

        Log.d(TAG, "Deleted ${serverHeld.size} server-held cached saves for game $gameId, kept ${localOnly.size} local-only")
        localOnly.size
    }

    fun getCacheFile(entity: SaveCacheEntity): File = File(cacheBaseDir, entity.cachePath)

    /**
     * Reads the archive written for [cacheId] back off disk and re-derives its hash.
     *
     * An archive is only evidence that the live bytes are safe to remove once it has been read
     * back and matched. A write that reported success but landed truncated, unreadable, or
     * against a full volume is precisely the case that would otherwise cost the save, and it is
     * indistinguishable from a good one without re-reading.
     */
    suspend fun verifyCachedArchive(cacheId: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = saveCacheDao.getById(cacheId) ?: return@withContext false
        val expected = entity.contentHash
        if (expected.isNullOrBlank()) return@withContext false
        val file = getCacheFile(entity)
        if (!file.exists() || file.length() == 0L) return@withContext false
        try {
            val actual = when {
                file.name.endsWith(".zip") -> saveArchiver.calculateZipHash(file)
                saveArchiver.getTrailerSize(file) > 0L ->
                    saveArchiver.readBytesWithoutTrailer(file)?.let { saveArchiver.calculateBytesHash(it) }
                else -> saveArchiver.calculateFileHash(file)
            }
            val match = actual == expected
            if (!match) {
                Logger.error(TAG,"Archive verify failed for cache $cacheId: expected=$expected, actual=$actual")
            }
            match
        } catch (e: Exception) {
            Logger.error(TAG,"Archive verify threw for cache $cacheId", e)
            false
        }
    }

    suspend fun reassignCacheOwner(cacheId: Long, ownerUserId: Long?) =
        withContext(Dispatchers.IO) { saveCacheDao.updateOwner(cacheId, ownerUserId) }

    /**
     * Hash of the save at [savePath] exactly as [cacheCurrentSave] archives it and the server
     * hashes the artifact Argosy sends: a folder save as the archive of the game's own folders
     * (never a shared container), a multi-member unit as its bundle, anything else as the file.
     * The game and emulator come from the caller when it has them, else from the ownership row
     * the last cache or restore recorded for the path.
     */
    suspend fun calculateLocalSaveHash(
        savePath: String,
        gameId: Long? = null,
        emulatorId: String? = null
    ): String? = (probeLocalSave(savePath, gameId, emulatorId) as? LocalSaveProbe.Present)?.contentHash

    /**
     * The save at [savePath] hashed as [calculateLocalSaveHash] hashes it, telling a save that is
     * not there apart from one that is there and cannot be read.
     */
    suspend fun probeLocalSave(
        savePath: String,
        gameId: Long? = null,
        emulatorId: String? = null
    ): LocalSaveProbe = withContext(Dispatchers.IO) {
        val owner = if (gameId == null || emulatorId == null) saveOwnershipDao.getLatestByPath(savePath) else null
        val ownerGameId = gameId ?: owner?.gameId
        if (ownerGameId != null) {
            when (val collected = sigilSaveHandler.collect(ownerGameId, emulatorId ?: owner?.emulatorId)) {
                SigilCollect.NotRouted -> Unit
                is SigilCollect.Found -> return@withContext LocalSaveProbe.Present(collected.contentHash)
                SigilCollect.Absent -> return@withContext LocalSaveProbe.Absent
                is SigilCollect.Unreadable -> return@withContext LocalSaveProbe.Unreadable
            }
        }
        if (!fal.exists(savePath)) return@withContext LocalSaveProbe.Absent
        fal.prepareSaveAccess(savePath)
        val hash = try {
            val saveFile = fal.getTransformedFile(savePath)
            val gciMembers = gciUnitMembers(savePath)?.takeIf { it.size > 1 }
            if (fal.isDirectory(savePath)) {
                val ownerGameId = gameId ?: saveOwnershipDao.getLatestByPath(savePath)?.gameId
                val roots = resolveArchiveRoots(saveFile, savePath, ownerGameId?.let { gameDao.getById(it) })
                if (roots.isEmpty()) null else hashArchiveRoots(roots)
            } else if (gciMembers != null) {
                gciMembers.forEach { fal.prepareSaveAccess(it) }
                saveArchiver.calculateFilesAsZipHash(gciMembers)
            } else {
                unitHashFor(savePath, gameId, emulatorId) ?: saveArchiver.calculateContentHash(saveFile)
            }
        } catch (e: Exception) {
            Logger.error(TAG,"Failed to calculate hash for $savePath", e)
            null
        }
        hash?.let { LocalSaveProbe.Present(it) } ?: LocalSaveProbe.Unreadable
    }

    private suspend fun unitHashFor(savePath: String, gameId: Long?, emulatorId: String?): String? {
        val owner = if (gameId == null || emulatorId == null) saveOwnershipDao.getLatestByPath(savePath) else null
        val resolvedGameId = gameId ?: owner?.gameId ?: return null
        val resolvedEmulatorId = emulatorId ?: owner?.emulatorId ?: return null
        if (resolvedEmulatorId !in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) return null
        val game = gameDao.getById(resolvedGameId) ?: return null
        val resolved = saveUnitResolver.resolveForSavePath(savePath, game, resolvedEmulatorId, null, hash = true)
            ?.takeIf { it.isMulti && it.unit.contentHash.isNotEmpty() }
            ?: return null
        val unit = resolved.unit
        val contentHash = if (resolved.memberPaths.any { isRetroArchCompressed(it) }) {
            saveArchiver.calculateFilesAsZipHash(resolved.memberPaths)
        } else {
            unit.contentHash
        }
        if (unit.identityHash.isEmpty() || unit.identityHash == unit.contentHash) return contentHash
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        return saveCacheDao.getLatestByGameAndIdentity(resolvedGameId, ownerUserId, unit.identityHash)?.contentHash
            ?: saveCacheDao.getByGameAndHash(resolvedGameId, ownerUserId, unit.identityHash)?.contentHash
            ?: contentHash
    }

    private fun isRetroArchCompressed(path: String): Boolean = try {
        fal.getTransformedFile(path).inputStream().use { input ->
            val head = ByteArray(RZIP_MAGIC.size)
            input.read(head) == head.size && head.contentEquals(RZIP_MAGIC)
        }
    } catch (e: java.io.IOException) {
        false
    }

    private suspend fun recordLocalWriteAnchor(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        savePath: String,
        ownerUserId: Long?,
        createMissing: Boolean
    ) {
        if (channelName == null) return
        val key = SaveSyncApiClient.syncKeyOf(channelName)
        val existing = saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, key, ownerUserId)
        if (existing == null && !createMissing) return
        val rommId = if (existing == null) gameDao.getById(gameId)?.rommId ?: return else existing.rommId
        val newest = if (fal.isDirectory(savePath)) {
            newestUnitWriteTime(gameId, savePath)
        } else {
            fal.lastModified(savePath)
        }
        if (newest <= 0L) return
        val row = existing ?: SaveSyncEntity(
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = key,
            localSavePath = savePath,
            syncStatus = SaveSyncEntity.STATUS_PENDING_UPLOAD,
            ownerUserId = ownerUserId
        )
        saveSyncDao.upsert(row.copy(localUpdatedAt = Instant.ofEpochMilli(newest)))
    }

    /**
     * Newest write across every directory the archive covers, not only the resolved one. The
     * anchor is compared against the same set on the next scan; anchoring on one component
     * while scanning both would report the other component dirty on every pass.
     */
    private suspend fun newestUnitWriteTime(gameId: Long, savePath: String): Long {
        val game = gameDao.getById(gameId)
        val saveId = game?.saveId ?: game?.titleId
        val handler = game?.platformSlug?.let { saveHandlerRegistry.getFolderHandler(it) }
        val components = if (saveId != null && handler != null) {
            handler.namedArchiveRoots(savePath, saveId).orEmpty().map { it.folder.path }
        } else {
            emptyList()
        }
        return (components + savePath).maxOf { savePathResolver.findNewestFileTime(it) }
    }

    private fun resolveDefaultChannel(channelName: String?, isHardcore: Boolean): String? {
        if (channelName != null) return channelName
        if (isHardcore) return null
        return SaveSyncApiClient.AUTOSAVE_SLOT_NAME
    }
}
