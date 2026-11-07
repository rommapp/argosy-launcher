package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
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
import kotlinx.coroutines.NonCancellable
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
    private val activeSaveRepository: ActiveSaveRepository,
    private val serverSaveGate: ServerSaveGate,
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
    private val saveOwnershipTracker: SaveOwnershipTracker,
    private val accountSwitchMarkerStore: com.nendo.argosy.data.preferences.AccountSwitchMarkerStore,
    private val fileAccessLayer: com.nendo.argosy.data.storage.FileAccessLayer,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao,
    private val diskJudge: ActiveSaveDiskJudge
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
     * re-reading a live path that may by then hold another account's progress. A null channel (a
     * game with no active channel, or hardcore on a server without snapshots) is left unpinned and
     * drains through the live path, because the cache-pinned upload addresses a named server slot.
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
        pendingSyncQueueDao.deleteByGameAndType(gameId, SyncType.SAVE_FILE, ownerUserId)
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
        if (accountSwitchMarkerStore.isSwitching()) {
            Logger.info(TAG, "[SaveSync] SCAN | account switch in progress, not adopting on-disk saves")
            return@withContext 0
        }
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val downloadedGames = gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())
        var queued = 0
        val client = apiClient.get()
        val processedWholePaths = mutableSetOf<String>()
        val unreadableLocations = mutableListOf<SaveAccessNotices.InaccessibleLocation>()

        for (game in downloadedGames) {
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue
            val activeChannel = activeSaveRepository.getActiveChannel(game.id)

            if (!secureSaves) {
                when (val outcome = refreshCacheFromSystem(game, emulatorId, activeChannel, ownerUserId, processedWholePaths)) {
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

            val syncEntity = saveSyncDao.getByGameEmulatorAndChannel(
                game.id, emulatorId, SaveSyncApiClient.syncKeyOf(activeChannel), ownerUserId
            )
            val lastSynced = syncEntity?.lastSyncedAt

            if (lastSynced == null || localModified.isAfter(lastSynced)) {
                Logger.debug(TAG, "[SaveSync] SCAN gameId=${game.id} | Local newer than sync | local=$localModified, lastSync=$lastSynced")
                queueUpload(game.id, emulatorId, savePath, activeChannel)
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

    suspend fun refreshCacheFromSystem(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        ownerUserId: Long?
    ): RefreshOutcome {
        val game = gameDao.getById(gameId) ?: return RefreshOutcome.Unchanged
        return refreshCacheFromSystem(game, emulatorId, channelName, ownerUserId)
    }

    /**
     * Secure-saves-OFF preamble: reconciles the save cache with the on-system save so
     * existing dirty-flag logic sees off-Argosy changes. Never call in secure-saves-ON flows.
     *
     * [ownerUserId] is resolved once by the caller and passed down: a scan walks every downloaded
     * game and the account must not be re-read part way through one pass.
     */
    suspend fun refreshCacheFromSystem(
        game: GameEntity,
        emulatorId: String,
        channelName: String?,
        ownerUserId: Long?,
        processedWholePaths: MutableSet<String>? = null
    ): RefreshOutcome = withContext(Dispatchers.IO) {
        val channel = channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        if (isStateChannel(channel)) return@withContext RefreshOutcome.Unchanged
        if (game.localPath == null) return@withContext RefreshOutcome.Unchanged
        if (saveCacheDao.getMostRecent(game.id, syncPreferencesRepository.getRommUserId())?.isHardcore == true) {
            Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} | latest cache is hardcore, skipping on-system derivation")
            return@withContext RefreshOutcome.Unchanged
        }

        val savePath = when (val lookup = lookupSystemSave(game, emulatorId)) {
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

        val latest = saveCacheDao.getMostRecentInChannel(game.id, syncPreferencesRepository.getRommUserId(), channel)
        if (latest == null) {
            Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} channel=$channel | no cache entry, adopting on-system save")
            return@withContext if (cacheSystemSave(game.id, emulatorId, savePath, channelName)) RefreshOutcome.Dirtied else RefreshOutcome.Unchanged
        }

        val anchor = saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, SaveSyncApiClient.syncKeyOf(channel), ownerUserId)
            ?.localUpdatedAt ?: latest.cachedAt
        val anchorMillis = anchor.toEpochMilli()
        val dirty = if (localFile.isDirectory) {
            scan.newestMillis > anchorMillis
        } else {
            scan.newestMillis > anchorMillis && systemDiffersFromCache(savePath, latest)
        }
        if (!dirty) return@withContext RefreshOutcome.Unchanged

        Logger.debug(TAG, "[SaveSync] REFRESH gameId=${game.id} channel=$channel | on-system save newer than anchor=$anchor, caching as dirty")
        if (cacheSystemSave(game.id, emulatorId, savePath, channelName)) RefreshOutcome.Dirtied else RefreshOutcome.Unchanged
    }

    /**
     * Where [emulatorId] keeps [game]'s save on this device, found, absent or unreadable. A null
     * [coreName] is resolved the way the launch resolves it.
     */
    suspend fun lookupSystemSave(game: GameEntity, emulatorId: String, coreName: String? = null): SaveLookup =
        savePathResolver.discoverSavePathChecked(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName ?: apiClient.get().resolveCoreForGame(game, emulatorId),
            emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug),
            gameId = game.id
        )

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

    private suspend fun systemDiffersFromCache(savePath: String, latest: SaveCacheEntity): Boolean {
        val systemHash = saveCacheManager.get().calculateLocalSaveHash(savePath, latest.gameId, latest.emulatorId)
            ?: return false
        return systemHash != latest.contentHash
    }

    sealed interface DiskCheck {
        data object Matches : DiskCheck
        data object Adopted : DiskCheck
        data object Restored : DiskCheck
        data object Untouched : DiskCheck
        data object Failed : DiskCheck
        data class Unreadable(val dirPath: String) : DiskCheck
    }

    suspend fun checkDiskAgainstActive(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        secureSaves: Boolean,
        ownerUserId: Long?
    ): DiskCheck {
        val game = gameDao.getById(gameId) ?: return DiskCheck.Untouched
        return checkDiskAgainstActive(game, emulatorId, channelName, secureSaves, ownerUserId)
    }

    suspend fun checkDiskAgainstActive(
        game: GameEntity,
        emulatorId: String,
        channelName: String?,
        secureSaves: Boolean,
        ownerUserId: Long?
    ): DiskCheck = withContext(Dispatchers.IO) {
        val channel = channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        if (isStateChannel(channel) || game.localPath == null) {
            return@withContext DiskCheck.Untouched
        }
        val active = activeSaveRepository.getActiveRow(game.id)
        if (active?.isHardcore == true || saveCacheDao.getMostRecent(game.id, ownerUserId)?.isHardcore == true) {
            Logger.debug(TAG, "[SaveSync] DISK gameId=${game.id} | hardcore save in play, leaving the disk to the hardcore gate")
            return@withContext DiskCheck.Untouched
        }
        if (active != null && active.emulatorId != emulatorId) {
            Logger.debug(TAG, "[SaveSync] DISK gameId=${game.id} | active cache id=${active.id} belongs to emulator=${active.emulatorId}, not $emulatorId; the cross-emulator migrator owns it")
            return@withContext DiskCheck.Untouched
        }
        reconcileDisk(game, emulatorId, channelName, active, secureSaves, ownerUserId, expectedExtension = null)
    }

    /**
     * The disk check against [active] for [emulatorId], whichever emulator wrote [active]. A
     * non-null [expectedExtension] writes nothing to a save path with another extension.
     */
    suspend fun reconcileDisk(
        game: GameEntity,
        emulatorId: String,
        channelName: String?,
        active: SaveCacheEntity?,
        secureSaves: Boolean,
        ownerUserId: Long?,
        expectedExtension: String?
    ): DiskCheck = withContext(Dispatchers.IO) {
        val channel = channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        val client = apiClient.get()
        val coreName = client.resolveCoreForGame(game, emulatorId)
        val cacheManager = saveCacheManager.get()
        val savePath = when (val lookup = lookupSystemSave(game, emulatorId, coreName)) {
            is SaveLookup.Found -> lookup.path
            SaveLookup.Absent -> {
                if (active == null) return@withContext DiskCheck.Untouched
                val target = savePathResolver.constructSavePath(
                    emulatorId = emulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    coreName = coreName,
                    cachedSaveId = game.saveId ?: game.titleId,
                    gameId = game.id,
                    emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
                ) ?: return@withContext DiskCheck.Untouched
                if (!hasExtension(target, expectedExtension)) return@withContext DiskCheck.Failed
                Logger.info(TAG, "[SaveSync] DISK gameId=${game.id} | no save on disk for emulator=$emulatorId, placing active cache id=${active.id} | path=$target")
                return@withContext if (cacheManager.restoreSave(active.id, target, emulatorId)) DiskCheck.Restored else DiskCheck.Failed
            }
            is SaveLookup.Unreadable -> {
                saveAccessNotices.record(lookup.dirPath, emulatorId)
                return@withContext DiskCheck.Unreadable(lookup.dirPath)
            }
        }

        val diskHash = cacheManager.calculateLocalSaveHash(savePath, game.id, emulatorId)
            ?: return@withContext DiskCheck.Untouched

        if (active == null) {
            if (saveOwnershipTracker.claim(savePath, emulatorId) is SaveClaim.Foreign) return@withContext DiskCheck.Untouched
            Logger.info(TAG, "[SaveSync] DISK gameId=${game.id} | no active version, adopting the disk save | path=$savePath")
            return@withContext if (cacheSystemSave(game.id, emulatorId, savePath, channelName)) DiskCheck.Adopted else DiskCheck.Untouched
        }

        val judgement = diskJudge.judge(game.id, emulatorId, channel, savePath, diskHash, active, secureSaves, ownerUserId)
        val syncRow = judgement.syncRow
        when (val verdict = judgement.verdict) {
            DiskVerdict.Matches -> return@withContext DiskCheck.Matches
            is DiskVerdict.Progress -> {
                Logger.info(TAG, "[SaveSync] DISK gameId=${game.id} | disk matches unsynced cache id=${verdict.cacheId}, keeping it as the active version")
                activeSaveRepository.activateCache(game.id, verdict.cacheId)
                return@withContext DiskCheck.Adopted
            }
            DiskVerdict.Foreign -> return@withContext DiskCheck.Untouched
            DiskVerdict.Adopt -> {
                Logger.info(TAG, "[SaveSync] DISK gameId=${game.id} | disk differs from active cache id=${active.id}, adopting it (Secure Saves off) | path=$savePath")
                cacheSystemSave(game.id, emulatorId, savePath, channelName)
                return@withContext DiskCheck.Adopted
            }
            DiskVerdict.ProtectThenRestore -> Unit
        }

        if (!hasExtension(savePath, expectedExtension)) return@withContext DiskCheck.Failed
        Logger.info(TAG, "[SaveSync] DISK gameId=${game.id} | disk differs from active cache id=${active.id}, restoring it (Secure Saves on) | path=$savePath")
        withContext(NonCancellable) {
            val rollbackId = when (val rollback = cacheManager.cacheAsRollback(game.id, emulatorId, savePath)) {
                is SaveCacheManager.CacheResult.Created -> rollback.cacheId
                is SaveCacheManager.CacheResult.Duplicate -> rollback.cacheId
                SaveCacheManager.CacheResult.Failed -> return@withContext DiskCheck.Failed
            }
            val roots = cacheManager.archiveRootNames(active.id)
            val placed = client.clearSavesBeforeRestore(savePath, game.platformSlug, game.saveId ?: game.titleId, roots) &&
                cacheManager.restoreSave(active.id, savePath, emulatorId)
            if (!placed) {
                val putBack = cacheManager.restoreSave(rollbackId, savePath, emulatorId)
                Logger.error(TAG, "[SaveSync] DISK gameId=${game.id} | restore of cache id=${active.id} failed; disk save put back from rollback id=$rollbackId ok=$putBack")
                return@withContext DiskCheck.Failed
            }
            val placedHash = cacheManager.calculateLocalSaveHash(savePath, game.id, emulatorId)
            if (syncRow != null && placedHash != null && syncRow.rommSaveId != null && syncRow.rommSaveId == active.rommSaveId) {
                saveSyncDao.updateLocalContentHash(syncRow.id, placedHash)
            }
            DiskCheck.Restored
        }
    }

    private fun hasExtension(path: String, expectedExtension: String?): Boolean {
        if (expectedExtension == null) return true
        val matches = File(path).extension.equals(expectedExtension, ignoreCase = true)
        if (!matches) {
            Logger.info(TAG, "[SaveSync] DISK | save path $path is not a .$expectedExtension file, writing nothing")
        }
        return matches
    }

    private suspend fun cacheSystemSave(
        gameId: Long,
        emulatorId: String,
        savePath: String,
        channelName: String?
    ): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        val result = saveCacheManager.get().cacheCurrentSave(
            gameId = gameId,
            emulatorId = emulatorId,
            savePath = savePath,
            channelName = channelName,
            needsRemoteSync = true
        )
        result is SaveCacheManager.CacheResult.Created
    }

    suspend fun downloadPendingServerSaves(): Int = withContext(Dispatchers.IO) {
        if (accountSwitchMarkerStore.isSwitching()) {
            Logger.info(TAG, "[SaveSync] DOWNLOAD | account switch in progress, not writing save files")
            return@withContext 0
        }
        val pendingDownloads = saveSyncDao.getPendingDownloads(syncPreferencesRepository.getRommUserId())
        if (pendingDownloads.isEmpty()) {
            return@withContext 0
        }

        val actionable = pendingDownloads.mapNotNull { entity ->
            val game = gameDao.getById(entity.gameId) ?: return@mapNotNull null
            if (game.localPath == null) {
                Logger.debug(TAG, "downloadPendingServerSaves: skipping non-installed gameId=${entity.gameId} silently")
                return@mapNotNull null
            }
            serverSaveGate.holdFor(entity.gameId, game.localPath, entity.channelName, entity.serverUpdatedAt)?.let { hold ->
                Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=${entity.gameId} channel=${entity.channelName} | Not placed on disk: ${hold.reason}")
                if (hold is ServerSaveHold.Superseded && entity.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER) {
                    saveSyncDao.upsert(entity.copy(syncStatus = SaveSyncEntity.STATUS_SYNCED))
                }
                return@mapNotNull null
            }
            if (localMovedSinceUpload(entity, entity.gameId, entity.emulatorId)) {
                if (entity.syncStatus != SaveSyncEntity.STATUS_CONFLICT) {
                    saveSyncDao.upsert(entity.copy(syncStatus = SaveSyncEntity.STATUS_CONFLICT))
                }
                Logger.warn(
                    TAG,
                    "downloadPendingServerSaves: gameId=${entity.gameId} channel=${entity.channelName} changed locally since its last upload; leaving it as a conflict rather than overwriting"
                )
                return@mapNotNull null
            }
            if (holdsUntrackedLocalSave(entity, game)) {
                if (entity.syncStatus != SaveSyncEntity.STATUS_CONFLICT) {
                    saveSyncDao.upsert(entity.copy(syncStatus = SaveSyncEntity.STATUS_CONFLICT))
                }
                Logger.warn(
                    TAG,
                    "downloadPendingServerSaves: gameId=${entity.gameId} channel=${entity.channelName} has a save on disk with no sync record proving it older than the server's; leaving it as a conflict rather than overwriting"
                )
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
                    coverPath = gameArtDao.resolved(entity.gameId).coverPath,
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
        val serverSaves = try {
            client.checkSavesForGame(gameId, rommId)
        } catch (e: SaveInventoryUnavailableException) {
            Logger.warn(TAG, "[SaveSync] NEW_DOWNLOAD gameId=$gameId | Server saves unavailable, skipping: ${e.message}")
            return@withContext
        }.filterNot { SaveSyncApiClient.isStateShapedSave(it) }
        if (serverSaves.isEmpty()) return@withContext

        val game = gameDao.getById(gameId) ?: return@withContext
        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }

        val canonicalEmulatorId = if (emulatorId.isBlank() || emulatorId == "default") {
            client.resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "syncSavesForNewDownload: cannot resolve canonical emulator for gameId=$gameId; skipping")
                return@withContext
            }
        } else emulatorId

        val slotOf = serverSaves.associateWith { save ->
            SaveSyncApiClient.syncKeyOf(SaveSyncApiClient.resolveServerChannelName(save, romBaseName))
        }
        val head = serverSaves
            .filter { slotOf[it] == SaveSyncApiClient.AUTOSAVE_SLOT_NAME }
            .maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
            ?: serverSaves.maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
            ?: return@withContext

        var cached = 0
        for (serverSave in serverSaves.sortedBy { SaveSyncApiClient.parseTimestamp(it.updatedAt) }) {
            if (serverSave.id == head.id) continue
            if (client.downloadToCache(serverSave.id, gameId, slotOf[serverSave]) != null) cached++
        }

        val headSlot = slotOf[head]
        val result = client.downloadSave(gameId, canonicalEmulatorId, headSlot, skipBackup = false, knownServerSaveId = head.id)
        Logger.info(TAG, "syncSavesForNewDownload: gameId=$gameId | history cached=$cached of ${serverSaves.size - 1}, head saveId=${head.id} slot=$headSlot placed=${result::class.simpleName}")
        if (result is SaveSyncResult.Error) {
            Logger.error(TAG, "syncSavesForNewDownload: failed to place head '${head.fileName}': ${result.message}")
        }
    }

    private suspend fun holdsUntrackedLocalSave(entity: SaveSyncEntity, game: GameEntity): Boolean {
        if (entity.lastUploadedHash != null || entity.localContentHash != null) return false
        if (activeSaveRepository.getActiveRow(entity.gameId) != null) return false
        val path = when (val lookup = lookupSystemSave(game, entity.emulatorId)) {
            is SaveLookup.Found -> lookup.path
            SaveLookup.Absent -> return false
            is SaveLookup.Unreadable -> return true
        }
        return saveCacheManager.get().probeLocalSave(path, entity.gameId, entity.emulatorId) !is SaveCacheManager.LocalSaveProbe.Absent
    }

    private suspend fun localMovedSinceUpload(
        existing: SaveSyncEntity?,
        gameId: Long,
        emulatorId: String
    ): Boolean {
        val path = existing?.localSavePath ?: return false
        val transferredForms = setOfNotNull(existing.lastUploadedHash, existing.localContentHash)
        if (transferredForms.isEmpty() || !fileAccessLayer.exists(path)) return false
        val liveHash = saveCacheManager.get().calculateLocalSaveHash(path, gameId, emulatorId) ?: return false
        return liveHash !in transferredForms
    }

    suspend fun forceSaveCheck(): ForceSaveCheckResult = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return@withContext ForceSaveCheckResult(0, 0, "Save sync disabled")

        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val client = apiClient.get()
        val downloadedIds = gameDao.getDownloadedRommGameIds()
        val downloadedGames = gameDao.getByIdsChunked(downloadedIds)
        var inspected = 0
        var queued = 0

        for (game in downloadedGames) {
            val rommId = game.rommId ?: continue
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue
            val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
            val heldHashByRow = saveSyncDao.getByGame(game.id, ownerUserId).associate { it.id to it.lastUploadedHash }
            val serverSaves = try {
                client.checkSavesForGame(game.id, rommId)
            } catch (e: SaveInventoryUnavailableException) {
                Logger.warn(TAG, "[SaveSync] RECONCILE gameId=${game.id} | Server saves unavailable, skipping: ${e.message}")
                continue
            }
            if (serverSaves.isEmpty()) continue
            inspected++

            val firstTimeForGame = heldHashByRow.isEmpty()

            val latestPerChannel = serverSaves
                .filter { !SaveSyncApiClient.isStateShapedSave(it) }
                .groupBy { save -> SaveSyncApiClient.resolveServerChannelName(save, romBaseName) }
                .mapValues { (_, saves) ->
                    saves.maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                }
                .values
                .filterNotNull()

            for (latest in latestPerChannel) {
                val channelName = SaveSyncApiClient.resolveServerChannelName(latest, romBaseName)
                val syncKey = SaveSyncApiClient.syncKeyOf(channelName)
                val existing = saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, syncKey, ownerUserId)
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
                val wouldDownload = firstTimeForGame || isActiveChannel
                val localMoved = wouldDownload && localMovedSinceUpload(existing, game.id, emulatorId)
                val status = when {
                    localMoved -> SaveSyncEntity.STATUS_CONFLICT
                    wouldDownload -> SaveSyncEntity.STATUS_SERVER_NEWER
                    else -> SaveSyncEntity.STATUS_SYNCED
                }
                if (localMoved) {
                    Logger.warn(
                        TAG,
                        "forceSaveCheck: gameId=${game.id} channel=$channelName changed on both sides; parking a conflict instead of overwriting the local save"
                    )
                }

                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = game.id,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        channelName = syncKey,
                        rommSaveId = latest.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = status,
                        lastUploadedHash = existing?.lastUploadedHash,
                        localContentHash = existing?.localContentHash,
                        lastSyncDeviceId = existing?.lastSyncDeviceId,
                        lastSyncDeviceName = existing?.lastSyncDeviceName,
                        userSelectedRestorePoint = existing?.userSelectedRestorePoint ?: false,
                        userSelectedRestorePointAt = existing?.userSelectedRestorePointAt,
                        ownerUserId = existing?.ownerUserId ?: ownerUserId
                    )
                )
                if (status == SaveSyncEntity.STATUS_SERVER_NEWER) queued++
            }
        }
        val downloaded = downloadPendingServerSaves()
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

        fun isStateChannel(channel: String): Boolean = STATE_CHANNEL_PATTERN.containsMatchIn(channel)
    }
}
