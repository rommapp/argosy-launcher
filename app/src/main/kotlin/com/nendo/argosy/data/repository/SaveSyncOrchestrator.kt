package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SaveAccessNotices
import com.nendo.argosy.data.sync.SaveClaim
import com.nendo.argosy.data.sync.SaveFilePayload
import com.nendo.argosy.data.sync.SaveLookup
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.SyncOperation
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncStatus
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncOrchestrator @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val syncQueueManager: SyncQueueManager,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val payloadCodec: SyncPayloadCodec,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry,
    private val saveAccessNotices: SaveAccessNotices,
    private val saveOwnershipTracker: SaveOwnershipTracker
) {
    sealed interface RefreshOutcome {
        data object Dirtied : RefreshOutcome
        data object Unchanged : RefreshOutcome
        data class Unreadable(val dirPath: String) : RefreshOutcome
    }

    private data class SystemSaveScan(val newestMillis: Long, val wholePath: Boolean)

    /**
     * Defers a save upload under the signed-in account.
     *
     * When a channel is known the bytes are captured into the save cache now and the queue row is
     * pinned to that cache id, so the drain uploads what was on disk at enqueue time rather than
     * re-reading a live path that may by then hold another account's progress. A null channel
     * (hardcore, or a game with no active channel) is left unpinned and drains through the live
     * path as before, because the cache-pinned upload addresses a named server slot.
     */
    suspend fun queueUpload(
        gameId: Long,
        emulatorId: String,
        localPath: String,
        channelName: String? = null
    ) {
        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val cacheId = channelName?.let { pinCacheForQueuedUpload(gameId, emulatorId, localPath, it) }

        val payload = SaveFilePayload(emulatorId = emulatorId, channelName = channelName)
        pendingSyncQueueDao.deleteByGameAndType(gameId, SyncType.SAVE_FILE)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = payloadCodec.encode(payload),
                ownerUserId = ownerUserId,
                cacheId = cacheId
            )
        )
    }

    private suspend fun pinCacheForQueuedUpload(
        gameId: Long,
        emulatorId: String,
        localPath: String,
        channel: String
    ): Long? {
        val result = saveCacheManager.get().cacheCurrentSave(
            gameId = gameId,
            emulatorId = emulatorId,
            savePath = localPath,
            channelName = channel
        )
        return when (result) {
            is SaveCacheManager.CacheResult.Created -> result.cacheId.takeIf { it > 0L }
            is SaveCacheManager.CacheResult.Duplicate -> result.cacheId
            SaveCacheManager.CacheResult.Failed -> null
        }
    }

    suspend fun scanAndQueueLocalChanges(secureSaves: Boolean): Int = withContext(Dispatchers.IO) {
        val downloadedGames = gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())
        var queued = 0
        val client = apiClient.get()
        val processedWholePaths = mutableSetOf<String>()
        val unreadableLocations = mutableListOf<SaveAccessNotices.InaccessibleLocation>()

        for (game in downloadedGames) {
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue

            if (!secureSaves) {
                when (val outcome = refreshCacheFromSystem(game, emulatorId, game.activeSaveChannel, processedWholePaths)) {
                    RefreshOutcome.Dirtied -> queued++
                    is RefreshOutcome.Unreadable ->
                        unreadableLocations.add(SaveAccessNotices.InaccessibleLocation(outcome.dirPath, emulatorId))
                    RefreshOutcome.Unchanged -> Unit
                }
                continue
            }

            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
            val coreName = client.resolveCoreForGame(game, emulatorId)

            val savePath = savePathResolver.discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedSaveId = game.saveId ?: game.titleId,
                coreName = coreName,
                emulatorPackage = emulatorPackage,
                gameId = game.id
            ) ?: continue

            val localFile = File(savePath)
            if (!localFile.exists()) continue

            val localModified = if (localFile.isDirectory) {
                Instant.ofEpochMilli(savePathResolver.findNewestFileTime(savePath))
            } else {
                Instant.ofEpochMilli(localFile.lastModified())
            }

            val syncEntity = saveSyncDao.getByGameAndEmulator(game.id, emulatorId)
            val lastSynced = syncEntity?.lastSyncedAt

            if (lastSynced == null || localModified.isAfter(lastSynced)) {
                Logger.debug(TAG, "[SaveSync] SCAN gameId=${game.id} | Local newer than sync | local=$localModified, lastSync=$lastSynced")
                queueUpload(game.id, emulatorId, savePath, game.activeSaveChannel)
                queued++
            }
        }

        if (secureSaves) {
            Logger.info(TAG, "[SaveSync] SCAN | Queued $queued local saves for upload")
        } else {
            saveAccessNotices.publishPass(unreadableLocations)
            Logger.info(TAG, "[SaveSync] SCAN | Refreshed $queued save caches from system | unreadableLocations=${unreadableLocations.size}")
        }
        queued
    }

    suspend fun refreshCacheFromSystem(gameId: Long, emulatorId: String, channelName: String?): RefreshOutcome {
        val game = gameDao.getById(gameId) ?: return RefreshOutcome.Unchanged
        return refreshCacheFromSystem(game, emulatorId, channelName)
    }

    /**
     * Secure-saves-OFF preamble: reconciles the save cache with the on-system save so
     * existing dirty-flag logic sees off-Argosy changes. Never call in secure-saves-ON flows.
     */
    suspend fun refreshCacheFromSystem(
        game: GameEntity,
        emulatorId: String,
        channelName: String?,
        processedWholePaths: MutableSet<String>? = null
    ): RefreshOutcome = withContext(Dispatchers.IO) {
        val channel = channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        if (STATE_CHANNEL_PATTERN.containsMatchIn(channel)) return@withContext RefreshOutcome.Unchanged
        if (game.localPath == null) return@withContext RefreshOutcome.Unchanged
        if (saveCacheDao.getMostRecent(game.id)?.isHardcore == true) {
            Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} | latest cache is hardcore, skipping on-system derivation")
            return@withContext RefreshOutcome.Unchanged
        }

        val client = apiClient.get()
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
        val coreName = client.resolveCoreForGame(game, emulatorId)
        val savePath = when (val lookup = savePathResolver.discoverSavePathChecked(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = game.id
        )) {
            is SaveLookup.Found -> lookup.path
            SaveLookup.Absent -> return@withContext RefreshOutcome.Unchanged
            is SaveLookup.Unreadable -> {
                Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} | save location unreadable, leaving cache untouched | dir=${lookup.dirPath}")
                saveAccessNotices.record(lookup.dirPath, emulatorId)
                return@withContext RefreshOutcome.Unreadable(lookup.dirPath)
            }
        }

        val localFile = File(savePath)
        if (!localFile.exists()) return@withContext RefreshOutcome.Unchanged

        val scan = if (localFile.isDirectory) {
            scanFolderSave(game, savePath) ?: return@withContext RefreshOutcome.Unchanged
        } else {
            SystemSaveScan(newestMillis = localFile.lastModified(), wholePath = true)
        }

        if (scan.wholePath && processedWholePaths != null) {
            val pathKey = runCatching { localFile.canonicalPath }.getOrDefault(savePath)
            if (!processedWholePaths.add(pathKey)) {
                Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} | resolved path already processed this pass | path=$pathKey")
                return@withContext RefreshOutcome.Unchanged
            }
        }

        val claim = saveOwnershipTracker.claim(savePath, emulatorId)
        if (claim is SaveClaim.Foreign) {
            Logger.info(
                TAG,
                "[SaveSync] REFRESH gameId=${game.id} channel=$channel | on-system save belongs to user ${claim.ownerUserId}, not adopting | path=$savePath"
            )
            return@withContext RefreshOutcome.Unchanged
        }

        val latest = saveCacheDao.getMostRecentInChannel(game.id, channel)
        if (latest == null) {
            Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} channel=$channel | no cache entry, adopting on-system save")
            return@withContext if (cacheSystemSave(game.id, emulatorId, savePath, channelName)) RefreshOutcome.Dirtied else RefreshOutcome.Unchanged
        }

        val anchor = saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, channel)
            ?.localUpdatedAt ?: latest.cachedAt
        val anchorMillis = anchor.toEpochMilli()
        val dirty = if (localFile.isDirectory) {
            scan.newestMillis > anchorMillis
        } else {
            scan.newestMillis > anchorMillis && systemDiffersFromCache(savePath, latest.contentHash)
        }
        if (!dirty) return@withContext RefreshOutcome.Unchanged

        Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} channel=$channel | on-system save newer than anchor=$anchor, caching as dirty")
        if (cacheSystemSave(game.id, emulatorId, savePath, channelName)) RefreshOutcome.Dirtied else RefreshOutcome.Unchanged
    }

    private suspend fun scanFolderSave(game: GameEntity, savePath: String): SystemSaveScan? {
        val handler = saveHandlerRegistry.getFolderHandler(game.platformSlug)
            ?: return SystemSaveScan(savePathResolver.findNewestFileTime(savePath), wholePath = true)
        val saveId = game.saveId ?: game.titleId ?: refreshedSaveId(game.id)
        if (saveId == null) {
            Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} | folder save without saveId, skipping to avoid whole-card scan | path=$savePath")
            return null
        }
        if (handler.isEntryForSaveId(File(savePath).name, saveId)) {
            return SystemSaveScan(savePathResolver.findNewestFileTime(savePath), wholePath = true)
        }
        val matched = handler.findAllSaveFoldersBySaveId(savePath, saveId)
        if (matched.isNotEmpty()) {
            return SystemSaveScan(matched.maxOf { savePathResolver.findNewestFileTime(it) }, wholePath = false)
        }
        if (handler.constructSavePath(savePath, saveId) == savePath) {
            Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} | shared card has no entries for saveId=$saveId | path=$savePath")
            return null
        }
        return SystemSaveScan(savePathResolver.findNewestFileTime(savePath), wholePath = true)
    }

    private suspend fun refreshedSaveId(gameId: Long): String? =
        gameDao.getById(gameId)?.let { it.saveId ?: it.titleId }

    private suspend fun systemDiffersFromCache(savePath: String, cachedHash: String?): Boolean {
        val systemHash = saveCacheManager.get().calculateLocalSaveHash(savePath) ?: return false
        return systemHash != cachedHash
    }

    private suspend fun cacheSystemSave(
        gameId: Long,
        emulatorId: String,
        savePath: String,
        channelName: String?
    ): Boolean {
        val result = saveCacheManager.get().cacheCurrentSave(
            gameId = gameId,
            emulatorId = emulatorId,
            savePath = savePath,
            channelName = channelName,
            needsRemoteSync = true
        )
        return result is SaveCacheManager.CacheResult.Created
    }

    suspend fun downloadPendingServerSaves(): Int = withContext(Dispatchers.IO) {
        val pendingDownloads = saveSyncDao.getPendingDownloads()
        if (pendingDownloads.isEmpty()) {
            return@withContext 0
        }

        val actionable = pendingDownloads.mapNotNull { entity ->
            val game = gameDao.getById(entity.gameId) ?: return@mapNotNull null
            if (game.localPath == null) {
                Logger.debug(TAG, "downloadPendingServerSaves: skipping non-installed gameId=${entity.gameId} silently")
                return@mapNotNull null
            }
            entity to game
        }
        if (actionable.isEmpty()) {
            return@withContext 0
        }

        for ((entity, game) in actionable) {
            syncQueueManager.addOperation(
                SyncOperation(
                    gameId = entity.gameId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    direction = SyncDirection.DOWNLOAD,
                    status = SyncStatus.PENDING
                )
            )
        }

        var downloaded = 0
        val client = apiClient.get()

        for ((syncEntity, _) in actionable) {
            syncQueueManager.updateOperation(syncEntity.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = client.downloadSave(syncEntity.gameId, syncEntity.emulatorId, syncEntity.channelName, knownServerSaveId = syncEntity.rommSaveId)) {
                is SaveSyncResult.Success -> {
                    syncQueueManager.completeOperation(syncEntity.gameId)
                    downloaded++
                }
                is SaveSyncResult.NoSaveFound,
                is SaveSyncResult.NotConfigured -> {
                    syncQueueManager.removeOperation(syncEntity.gameId)
                    Logger.debug(TAG, "downloadPendingServerSaves: skipping gameId=${syncEntity.gameId} | result=$result")
                }
                is SaveSyncResult.NeedsHardcoreResolution -> {
                    saveSyncDao.upsert(syncEntity.copy(syncStatus = SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION))
                    syncQueueManager.removeOperation(syncEntity.gameId)
                    Logger.info(TAG, "downloadPendingServerSaves: gameId=${syncEntity.gameId} needs hardcore resolution; parked row as ${SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION} and dropped from bulk queue")
                }
                is SaveSyncResult.Error -> {
                    syncQueueManager.completeOperation(syncEntity.gameId, result.message)
                }
                else -> {
                    syncQueueManager.completeOperation(syncEntity.gameId, "Download failed")
                }
            }
        }

        downloaded
    }

    suspend fun syncSavesForNewDownload(gameId: Long, rommId: Long, emulatorId: String) = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return@withContext

        val client = apiClient.get()
        val serverSaves = client.checkSavesForGame(gameId, rommId)
        if (serverSaves.isEmpty()) return@withContext

        val game = gameDao.getById(gameId) ?: return@withContext
        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }

        val canonicalEmulatorId = if (emulatorId.isBlank() || emulatorId == "default") {
            client.resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "syncSavesForNewDownload: cannot resolve canonical emulator for gameId=$gameId; skipping")
                return@withContext
            }
        } else emulatorId

        for (serverSave in serverSaves) {
            val channelName = SaveSyncApiClient.parseServerChannelNameForSync(serverSave.fileName, romBaseName)
            val serverTime = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)

            val existing = if (channelName != null) {
                saveSyncDao.getByGameEmulatorAndChannel(gameId, canonicalEmulatorId, channelName)
            } else {
                saveSyncDao.getByGameEmulatorAndNullChannel(gameId, canonicalEmulatorId)
            }

            saveSyncDao.upsert(
                SaveSyncEntity(
                    id = existing?.id ?: 0,
                    gameId = gameId,
                    rommId = rommId,
                    emulatorId = canonicalEmulatorId,
                    channelName = channelName,
                    rommSaveId = serverSave.id,
                    localSavePath = existing?.localSavePath,
                    localUpdatedAt = existing?.localUpdatedAt,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = existing?.lastSyncedAt,
                    syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
                    lastUploadedHash = existing?.lastUploadedHash,
                    localContentHash = existing?.localContentHash,
                    lastSyncDeviceId = existing?.lastSyncDeviceId,
                    lastSyncDeviceName = existing?.lastSyncDeviceName
                )
            )

            val result = client.downloadSave(gameId, canonicalEmulatorId, channelName, skipBackup = false, knownServerSaveId = serverSave.id)
            if (result is SaveSyncResult.Error) {
                Logger.error(TAG, "syncSavesForNewDownload: failed '${serverSave.fileName}': ${result.message}")
            }
        }
    }

    suspend fun forceSaveCheck(): ForceSaveCheckResult = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return@withContext ForceSaveCheckResult(0, 0, "Save sync disabled")

        val client = apiClient.get()
        val downloadedIds = gameDao.getDownloadedRommGameIds()
        val downloadedGames = gameDao.getByIdsChunked(downloadedIds)
        var inspected = 0
        var queued = 0

        for (game in downloadedGames) {
            val rommId = game.rommId ?: continue
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue
            val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
            val heldHashByRow = saveSyncDao.getByGame(game.id).associate { it.id to it.lastUploadedHash }
            val serverSaves = client.checkSavesForGame(game.id, rommId)
            if (serverSaves.isEmpty()) continue
            inspected++

            val firstTimeForGame = heldHashByRow.isEmpty()

            val latestPerChannel = serverSaves
                .filter { !SaveSyncApiClient.isStateShapedSave(it) }
                .groupBy { save ->
                    save.slot ?: SaveSyncApiClient.parseServerChannelNameForSync(save.fileName, romBaseName)
                }
                .mapValues { (_, saves) ->
                    saves.maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                }
                .values
                .filterNotNull()

            for (latest in latestPerChannel) {
                val channelName = latest.slot ?: SaveSyncApiClient.parseServerChannelNameForSync(latest.fileName, romBaseName)
                val existing = if (channelName != null) {
                    saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, channelName)
                } else {
                    saveSyncDao.getByGameEmulatorAndNullChannel(game.id, emulatorId)
                }
                val serverTime = SaveSyncApiClient.parseTimestamp(latest.updatedAt)
                val localPresent = existing?.localSavePath?.let { File(it).exists() } == true
                if (existing != null && localPresent) {
                    val heldHash = heldHashByRow[existing.id]
                    val hashKnown = heldHash != null && latest.contentHash != null
                    if (hashKnown) {
                        if (heldHash == latest.contentHash) continue
                    } else if (existing.rommSaveId == latest.id && existing.serverUpdatedAt == serverTime) {
                        continue
                    }
                }

                val isActiveChannel = channelName == null ||
                    channelName.equals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, ignoreCase = true) ||
                    channelName.equals(SaveSyncApiClient.DEFAULT_SAVE_NAME, ignoreCase = true)
                val shouldDownload = firstTimeForGame || isActiveChannel
                val status = if (shouldDownload) SaveSyncEntity.STATUS_SERVER_NEWER else SaveSyncEntity.STATUS_SYNCED

                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = game.id,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        channelName = channelName,
                        rommSaveId = latest.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = status,
                        lastUploadedHash = existing?.lastUploadedHash,
                        localContentHash = existing?.localContentHash,
                        lastSyncDeviceId = existing?.lastSyncDeviceId,
                        lastSyncDeviceName = existing?.lastSyncDeviceName
                    )
                )
                if (shouldDownload) queued++
            }
        }
        val downloaded = downloadPendingServerSaves()
        syncPreferencesRepository.setLastNegotiateAt(Instant.now())
        Logger.info(TAG, "forceSaveCheck: inspected=$inspected queued=$queued downloaded=$downloaded")
        ForceSaveCheckResult(inspected = inspected, queued = queued, message = null, downloaded = downloaded)
    }

    data class ForceSaveCheckResult(
        val inspected: Int,
        val queued: Int,
        val message: String?,
        val downloaded: Int = 0
    )

    companion object {
        private const val TAG = "SaveSyncOrchestrator"
        private val STATE_CHANNEL_PATTERN = Regex("""^state_""", RegexOption.IGNORE_CASE)
    }
}
