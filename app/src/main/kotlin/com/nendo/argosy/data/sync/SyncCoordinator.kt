package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncStatus as DbSyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.strategy.ConflictAutoResolver
import com.nendo.argosy.data.sync.strategy.LocalSaveState
import com.nendo.argosy.data.sync.strategy.ReconcileAction
import com.nendo.argosy.data.sync.strategy.ReconcileOperation
import com.nendo.argosy.data.sync.strategy.ReconcilePlan
import com.nendo.argosy.data.sync.strategy.SaveSyncStrategySelector
import com.nendo.argosy.util.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val saveCacheDao: SaveCacheDao,
    private val saveSyncDao: com.nendo.argosy.data.local.dao.SaveSyncDao,
    private val emulatorSaveConfigDao: com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao,
    private val gameDao: GameDao,
    private val romMRepository: Lazy<RomMRepository>,
    private val saveSyncRepository: Lazy<SaveSyncRepository>,
    private val saveCacheManager: Lazy<SaveCacheManager>,
    private val stateCacheManager: Lazy<StateCacheManager>,
    private val syncQueueManager: SyncQueueManager,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val payloadCodec: SyncPayloadCodec,
    private val strategySelector: SaveSyncStrategySelector,
    private val conflictAutoResolver: ConflictAutoResolver,
    private val pendingConflictDao: PendingConflictDao
) {
    companion object {
        private const val TAG = "SyncCoordinator"
        private val NEGOTIATE_COOLDOWN: Duration = Duration.ofMinutes(5)
    }

    private val mutex = Mutex()

    sealed class ProcessResult {
        data object NotConnected : ProcessResult()
        data class Completed(val processed: Int, val failed: Int) : ProcessResult()
    }

    @Volatile
    private var lastNegotiateAt: Instant? = null

    suspend fun reconcileAll(): ReconcileSummary = withContext(Dispatchers.IO) {
        val queueResult = processQueue()

        val state = romMRepository.get().connectionState.value
        val caps = (state as? ConnectionState.Connected)?.capabilities
        if (caps?.supportsSyncNegotiate != true) {
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }

        val now = Instant.now()
        val last = lastNegotiateAt
        if (last != null && Duration.between(last, now) < NEGOTIATE_COOLDOWN) {
            Logger.debug(TAG, "reconcileAll: negotiate cooldown active (last=$last), skipping")
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }
        lastNegotiateAt = now

        val inventory = buildInventory()
        val plan = strategySelector.current().planReconcile(inventory)
        if (plan.operations.isEmpty()) {
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }

        val (conflicts, applied) = applyPlan(plan)
        Logger.info(TAG, "reconcileAll: plan applied | conflicts=$conflicts handled=$applied sessionId=${plan.sessionId}")
        ReconcileSummary(queueResult, planConflicts = conflicts, planApplied = applied)
    }

    private suspend fun buildInventory(): List<LocalSaveState> {
        val rows = saveSyncDao.getAllWithLocalPath()
        return rows.mapNotNull { row ->
            val path = row.localSavePath ?: return@mapNotNull null
            val file = File(path)
            if (!file.exists()) return@mapNotNull null

            val lastSynced = row.lastSyncedAt
            if (lastSynced != null) {
                val mtime = Instant.ofEpochMilli(file.lastModified())
                if (!mtime.isAfter(lastSynced)) return@mapNotNull null
            }

            LocalSaveState(
                romId = row.rommId,
                fileName = file.name,
                slot = row.channelName,
                emulator = row.emulatorId,
                contentHash = row.lastUploadedHash,
                updatedAt = (row.localUpdatedAt ?: Instant.ofEpochMilli(file.lastModified())).toString(),
                fileSizeBytes = file.length()
            )
        }
    }

    private suspend fun applyPlan(plan: ReconcilePlan): Pair<Int, Int> {
        var conflicts = 0
        var applied = 0
        for (op in plan.operations) {
            when (op.action) {
                ReconcileAction.NO_OP -> Unit
                ReconcileAction.UPLOAD -> {
                    if (queueUploadForOp(op)) applied++
                }
                ReconcileAction.DOWNLOAD -> {
                    if (markServerNewerForOp(op)) applied++
                }
                ReconcileAction.CONFLICT -> {
                    val game = gameDao.getByRommId(op.romId)
                    val clientHash = game?.id?.let { gid ->
                        saveSyncDao.getByGameAndEmulator(gid, op.emulator ?: "")?.lastUploadedHash
                    }
                    when (val res = conflictAutoResolver.classify(op, clientHash)) {
                        is ConflictAutoResolver.Resolution.AsIs -> {
                            if (game != null) {
                                pendingConflictDao.upsert(
                                    PendingConflictEntity(
                                        gameId = game.id,
                                        rommSaveId = op.saveId,
                                        fileName = op.fileName,
                                        slot = op.slot,
                                        emulator = op.emulator,
                                        localUpdatedAt = null,
                                        serverUpdatedAt = op.serverUpdatedAt?.let { parseInstantOrNull(it) },
                                        localHash = clientHash,
                                        serverHash = op.serverContentHash,
                                        reason = op.reason
                                    )
                                )
                                conflicts++
                            }
                        }
                        is ConflictAutoResolver.Resolution.KeepLocal -> {
                            if (queueUploadForOp(op)) applied++
                            Logger.info(TAG, "auto-resolved conflict romId=${op.romId} -> KeepLocal (${res.ruleId})")
                        }
                        is ConflictAutoResolver.Resolution.KeepServer -> {
                            if (markServerNewerForOp(op)) applied++
                            Logger.info(TAG, "auto-resolved conflict romId=${op.romId} -> KeepServer (${res.ruleId})")
                        }
                    }
                }
            }
        }
        return conflicts to applied
    }

    private suspend fun queueUploadForOp(op: ReconcileOperation): Boolean {
        val game = gameDao.getByRommId(op.romId) ?: run {
            Logger.debug(TAG, "applyPlan UPLOAD: no local game for romId=${op.romId}, skipping")
            return false
        }
        val emulatorId = op.emulator ?: return false
        val payload = SaveFilePayload(emulatorId = emulatorId, channelName = op.slot)
        pendingSyncQueueDao.deleteByGameAndType(game.id, SyncType.SAVE_FILE)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = game.id,
                rommId = op.romId,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = payloadCodec.encode(payload)
            )
        )
        return true
    }

    private suspend fun markServerNewerForOp(op: ReconcileOperation): Boolean {
        val game = gameDao.getByRommId(op.romId) ?: run {
            Logger.debug(TAG, "applyPlan DOWNLOAD: no local game for romId=${op.romId}, skipping")
            return false
        }
        if (game.localPath == null) {
            Logger.debug(TAG, "applyPlan DOWNLOAD: game ${game.id} has no local ROM, skipping")
            return false
        }
        val emulatorId = op.emulator ?: return false
        val existing = if (op.slot != null) {
            saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, op.slot)
        } else {
            saveSyncDao.getByGameAndEmulator(game.id, emulatorId)
        }
        val serverTime = op.serverUpdatedAt?.let { parseInstantOrNull(it) }
        saveSyncDao.upsert(
            SaveSyncEntity(
                id = existing?.id ?: 0,
                gameId = game.id,
                rommId = op.romId,
                emulatorId = emulatorId,
                channelName = op.slot,
                rommSaveId = op.saveId,
                localSavePath = existing?.localSavePath,
                localUpdatedAt = existing?.localUpdatedAt,
                serverUpdatedAt = serverTime,
                lastSyncedAt = existing?.lastSyncedAt,
                syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
            )
        )
        return true
    }

    private fun parseInstantOrNull(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: Exception) {
        null
    }

    data class ReconcileSummary(
        val queue: ProcessResult,
        val planConflicts: Int,
        val planApplied: Int
    )

    suspend fun processQueue(): ProcessResult = withContext(Dispatchers.IO) {
        val romM = romMRepository.get()
        if (romM.connectionState.value !is ConnectionState.Connected) {
            Logger.debug(TAG, "processQueue: Not connected to RomM, skipping")
            return@withContext ProcessResult.NotConnected
        }

        if (!mutex.tryLock()) {
            Logger.debug(TAG, "processQueue: Already in progress, skipping duplicate call")
            return@withContext ProcessResult.Completed(processed = 0, failed = 0)
        }

        try {
            var processed = 0
            var failed = 0

            val saveSyncEnabled = syncPreferencesRepository.preferences.first().saveSyncEnabled

            val promoted = pendingSyncQueueDao.promoteEligibleFailedToPending()
            if (promoted > 0) {
                Logger.debug(TAG, "processQueue: Promoted $promoted FAILED rows back to PENDING")
            }

            if (saveSyncEnabled) {
                // Rekey runs every cycle, not once: server saves can arrive any
                // time carrying RomM-side emulator labels (libretro core names like
                // mGBA, gpSP, prosystem) that don't match the user's local emulator.
                // UPDATE OR REPLACE makes this idempotent -- a no-op when no rows
                // need migration. The "once" flag was missing this drift case.
                val rewritten = saveSyncRepository.get().rekeySaveSyncToLocalEmulators()
                if (rewritten > 0) {
                    Logger.info(TAG, "processQueue: Save-sync rekey migration complete | rowsRewritten=$rewritten")
                }

                if (!syncPreferencesRepository.isSavePathCachePurged()) {
                    emulatorSaveConfigDao.clearAutoDetected()
                    Logger.info(TAG, "processQueue: Purged auto-detected save-path cache (no longer used)")
                    syncPreferencesRepository.setSavePathCachePurged()
                }

                val deduped = saveSyncDao.deleteDuplicateRows()
                if (deduped > 0) {
                    Logger.info(TAG, "processQueue: Deduped $deduped redundant save_sync rows")
                }
            }

            val priorities = if (saveSyncEnabled) {
                listOf(SyncPriority.SAVE_FILE, SyncPriority.SAVE_STATE, SyncPriority.PROPERTY)
            } else {
                listOf(SyncPriority.PROPERTY)
            }
            for (priority in priorities) {
                val items = pendingSyncQueueDao.getPendingByPriorityTier(priority)
                if (items.isNotEmpty()) Logger.debug(TAG, "processQueue: Processing ${items.size} items at priority $priority")

                for (item in items) {
                    if (romM.connectionState.value !is ConnectionState.Connected) {
                        Logger.debug(TAG, "processQueue: Connection lost, stopping")
                        break
                    }

                    val result = processItem(item)
                    if (result) {
                        pendingSyncQueueDao.deleteById(item.id)
                        processed++
                    } else {
                        pendingSyncQueueDao.markFailed(item.id, "Processing failed")
                        failed++
                    }
                }
            }

            if (saveSyncEnabled) {
                val dirtySaves = processDirtySaveCaches()
                processed += dirtySaves

                val downloaded = saveSyncRepository.get().downloadPendingServerSaves()
                processed += downloaded

                val validated = validateSaveStates()
                processed += validated
            }

            Logger.info(TAG, "processQueue: Completed | processed=$processed, failed=$failed")
            ProcessResult.Completed(processed, failed)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun processItem(item: PendingSyncQueueEntity): Boolean {
        pendingSyncQueueDao.markInProgress(item.id)

        return try {
            when (item.syncType) {
                SyncType.SAVE_FILE -> processSaveFile(item)
                SyncType.SAVE_STATE -> processSaveState(item)
                SyncType.RATING -> processProperty(item)
                SyncType.DIFFICULTY -> processProperty(item)
                SyncType.STATUS -> processProperty(item)
                SyncType.FAVORITE -> processFavorite(item)
                SyncType.ACHIEVEMENT -> processAchievement(item)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "processItem: Exception processing ${item.syncType}", e)
            false
        }
    }

    private suspend fun processSaveFile(item: PendingSyncQueueEntity): Boolean {
        val game = gameDao.getById(item.gameId) ?: return false
        if (game.localPath == null) {
            Logger.debug(TAG, "processSaveFile: dropping queue item for non-local game gameId=${item.gameId}")
            return true
        }
        val payload = payloadCodec.decodeSaveFile(item.payloadJson) ?: return false

        syncQueueManager.addOperation(
            SyncOperation(
                gameId = item.gameId,
                gameName = game.title,
                channelName = payload.channelName,
                coverPath = game.coverPath,
                direction = SyncDirection.UPLOAD,
                status = SyncStatus.IN_PROGRESS
            )
        )

        val result = saveSyncRepository.get().uploadSave(
            gameId = item.gameId,
            emulatorId = payload.emulatorId,
            channelName = payload.channelName,
            forceOverwrite = false
        )

        when (result) {
            is SaveSyncResult.Success -> syncQueueManager.completeOperation(item.gameId)
            is SaveSyncResult.NoSaveFound,
            is SaveSyncResult.NotConfigured -> syncQueueManager.completeOperation(item.gameId)
            is SaveSyncResult.Error -> syncQueueManager.completeOperation(item.gameId, result.message)
            is SaveSyncResult.Conflict -> syncQueueManager.completeOperation(item.gameId, "Server has newer save")
            else -> syncQueueManager.completeOperation(item.gameId, "Skipped")
        }

        return result is SaveSyncResult.Success
    }

    private suspend fun processSaveState(item: PendingSyncQueueEntity): Boolean {
        val payload = payloadCodec.decodeSaveState(item.payloadJson) ?: return false
        val state = stateCacheManager.get().getStateById(payload.stateCacheId) ?: return false
        val api = saveSyncRepository.get().getApi() ?: return false
        val game = gameDao.getById(item.gameId)
        val romBaseName = game?.localPath?.let { java.io.File(it).nameWithoutExtension } ?: state.platformSlug

        val result = stateCacheManager.get().uploadStateToRomM(state, item.rommId, romBaseName, api)
        return result is StateCacheManager.StateCloudResult.Success ||
            result is StateCacheManager.StateCloudResult.AlreadySynced
    }

    private suspend fun validateSaveStates(): Int {
        val prefs = syncPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return 0

        val lastValidation = prefs.lastStateValidation
        if (lastValidation != null && Duration.between(lastValidation, Instant.now()).toDays() < 7) {
            return 0
        }

        val api = saveSyncRepository.get().getApi() ?: return 0
        Logger.info(TAG, "validateSaveStates: Validating save states")
        val migrated = stateCacheManager.get().validateAllSaveStates(api)
        syncPreferencesRepository.setLastStateValidationTime(Instant.now())
        Logger.info(TAG, "validateSaveStates: Complete | migrated=$migrated")
        return migrated
    }

    private suspend fun processProperty(item: PendingSyncQueueEntity): Boolean {
        val payload = payloadCodec.decodeProperty(item.payloadJson) ?: return false

        return romMRepository.get().updateRomUserProps(
            rommId = item.rommId,
            userRating = if (item.syncType == SyncType.RATING) payload.intValue else null,
            userDifficulty = if (item.syncType == SyncType.DIFFICULTY) payload.intValue else null,
            userStatus = if (item.syncType == SyncType.STATUS) payload.stringValue else null
        )
    }

    private suspend fun processFavorite(item: PendingSyncQueueEntity): Boolean {
        val payload = payloadCodec.decodeProperty(item.payloadJson) ?: return false
        val isFavorite = payload.intValue == 1

        return romMRepository.get().syncFavorite(item.rommId, isFavorite)
    }

    private suspend fun processAchievement(item: PendingSyncQueueEntity): Boolean {
        // Achievement sync would go to RetroAchievements API, not RomM
        // For now, just mark as processed - this can be expanded later
        Logger.debug(TAG, "processAchievement: Achievement sync not yet implemented")
        return true
    }

    private suspend fun processDirtySaveCaches(): Int {
        val dirtySaves = saveCacheDao.getNeedingRemoteSync()
        if (dirtySaves.isEmpty()) return 0

        Logger.debug(TAG, "processDirtySaveCaches: Found ${dirtySaves.size} saves needing sync")

        // Flush any pending device sync notifications before uploading
        val affectedGameIds = dirtySaves.map { it.gameId }.distinct()
        for (gid in affectedGameIds) {
            saveSyncRepository.get().flushPendingDeviceSync(gid)
        }

        val channelCaches = dirtySaves.filter { it.channelName != null }
        val nonChannelCaches = dirtySaves.filter { it.channelName == null }

        // Phase 1: Pre-check non-channel saves for conflicts (channel saves skip conflict check)
        val conflicts = mutableMapOf<Long, Pair<SaveCacheEntity, ConflictInfo>>()
        val resolutions = mutableMapOf<Long, ConflictResolution>()

        for (cache in nonChannelCaches) {
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue
            if (game.localPath == null) {
                Logger.debug(TAG, "processDirtySaveCaches: skipping conflict-check for non-local game gameId=${cache.gameId}")
                continue
            }

            val conflictInfo = saveSyncRepository.get().checkForConflict(
                gameId = cache.gameId,
                emulatorId = cache.emulatorId,
                channelName = null
            )
            if (conflictInfo != null) {
                conflicts[cache.gameId] = cache to conflictInfo
            }
        }

        // Phase 2: Wait for conflict resolutions if any
        if (conflicts.isNotEmpty()) {
            Logger.debug(TAG, "processDirtySaveCaches: Found ${conflicts.size} conflicts, awaiting resolution")

            for ((_, pair) in conflicts) {
                val (_, info) = pair
                syncQueueManager.addConflict(info)
            }

            for ((gameId, _) in conflicts) {
                val resolution = syncQueueManager.awaitResolution(gameId)
                resolutions[gameId] = resolution
                Logger.debug(TAG, "processDirtySaveCaches: Conflict resolved gameId=$gameId resolution=$resolution")
            }

            syncQueueManager.clearResolutions()
        }

        var synced = 0

        // Phase 3a: Upload channel caches directly from cached files
        for (cache in channelCaches) {
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue
            if (game.localPath == null) {
                Logger.debug(TAG, "processDirtySaveCaches: skipping channel cache for non-local game gameId=${cache.gameId}, cacheId=${cache.id}")
                continue
            }

            val cacheFile = saveCacheManager.get().getCacheFile(cache)
            if (!cacheFile.exists()) {
                Logger.warn(TAG, "processDirtySaveCaches: Cache file missing for id=${cache.id}, path=${cache.cachePath}")
                saveCacheDao.markSyncError(cache.id, "Cache file missing")
                continue
            }

            val conflictInfo = saveSyncRepository.get().checkForConflict(
                gameId = cache.gameId,
                emulatorId = cache.emulatorId,
                channelName = cache.channelName
            )
            if (conflictInfo != null) {
                saveCacheDao.clearDirtyFlagForChannel(cache.gameId, cache.channelName!!, excludeId = -1)
                syncQueueManager.addConflict(conflictInfo)
                Logger.warn(TAG, "processDirtySaveCaches: Pre-upload conflict for channel cache id=${cache.id}")
                continue
            }

            syncQueueManager.addOperation(SyncOperation(
                gameId = cache.gameId,
                gameName = game.title,
                channelName = cache.channelName,
                coverPath = game.coverPath,
                direction = SyncDirection.UPLOAD,
                status = SyncStatus.IN_PROGRESS
            ))

            val result = saveSyncRepository.get().uploadCacheEntry(
                gameId = cache.gameId,
                rommId = game.rommId,
                emulatorId = cache.emulatorId,
                channelName = cache.channelName!!,
                cacheFile = cacheFile,
                contentHash = cache.contentHash
            )

            when (result) {
                is SaveSyncResult.Success -> {
                    saveCacheDao.markSynced(cache.id, Instant.now())
                    if (result.rommSaveId != null) {
                        saveCacheDao.updateRommSaveId(cache.id, result.rommSaveId)
                    }
                    if (result.serverTimestamp != null) {
                        val oldCachedAtMillis = cache.cachedAt.toEpochMilli()
                        saveCacheDao.updateCachedAt(cache.id, result.serverTimestamp)
                        val game = gameDao.getById(cache.gameId)
                        if (game?.activeSaveTimestamp == oldCachedAtMillis) {
                            gameDao.updateActiveSaveTimestamp(cache.gameId, result.serverTimestamp.toEpochMilli())
                        }
                    }
                    syncQueueManager.completeOperation(cache.gameId)
                    synced++
                    Logger.debug(TAG, "processDirtySaveCaches: Synced channel cache id=${cache.id} gameId=${cache.gameId} channel=${cache.channelName} rommSaveId=${result.rommSaveId}")
                }
                is SaveSyncResult.Conflict -> {
                    saveCacheDao.clearDirtyFlagForChannel(cache.gameId, cache.channelName, excludeId = -1)
                    syncQueueManager.addConflict(ConflictInfo(
                        gameId = cache.gameId,
                        gameName = game.title,
                        channelName = cache.channelName,
                        localTimestamp = cache.cachedAt,
                        serverTimestamp = result.serverTimestamp,
                        isHashConflict = false,
                        serverDeviceName = result.serverDeviceName,
                        serverSaveId = result.serverSaveId
                    ))
                    Logger.warn(TAG, "processDirtySaveCaches: Conflict for channel cache id=${cache.id} gameId=${cache.gameId} channel=${cache.channelName} | cleared dirty flag, awaiting resolution")
                }
                is SaveSyncResult.Error -> {
                    saveCacheDao.markSyncError(cache.id, result.message)
                    syncQueueManager.completeOperation(cache.gameId, result.message)
                    Logger.warn(TAG, "processDirtySaveCaches: Failed channel cache id=${cache.id} gameId=${cache.gameId} | ${result.message}")
                }
                is SaveSyncResult.NoSaveFound,
                is SaveSyncResult.NotConfigured -> {
                    syncQueueManager.completeOperation(cache.gameId)
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped channel cache id=${cache.id} | result=$result")
                }
                else -> {
                    syncQueueManager.completeOperation(cache.gameId, "Skipped")
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped channel cache id=${cache.id} | result=$result")
                }
            }
        }

        // Phase 3b: Process non-channel saves with conflict resolution
        for (cache in nonChannelCaches) {
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue
            if (game.localPath == null) {
                Logger.debug(TAG, "processDirtySaveCaches: skipping non-channel cache for non-local game gameId=${cache.gameId}, cacheId=${cache.id}")
                continue
            }

            val resolution = resolutions[cache.gameId]

            if (resolution == ConflictResolution.SKIP) {
                saveCacheDao.clearAllDirtyFlags(cache.gameId)
                Logger.debug(TAG, "processDirtySaveCaches: Skipped gameId=${cache.gameId}, cleared dirty flags")
                continue
            }
            if (resolution == ConflictResolution.KEEP_SERVER) {
                val serverSaveId = conflicts[cache.gameId]?.second?.serverSaveId
                val downloadResult = saveSyncRepository.get().downloadSave(
                    gameId = cache.gameId,
                    emulatorId = cache.emulatorId,
                    channelName = null,
                    knownServerSaveId = serverSaveId
                )
                if (downloadResult is SaveSyncResult.Success) {
                    saveCacheDao.markSynced(cache.id, Instant.now())
                    gameDao.updateActiveSaveApplied(cache.gameId, false)
                    Logger.debug(TAG, "processDirtySaveCaches: Downloaded server save for gameId=${cache.gameId}")
                } else {
                    Logger.warn(TAG, "processDirtySaveCaches: Failed to download server save for gameId=${cache.gameId}")
                }
                continue
            }

            syncQueueManager.addOperation(SyncOperation(
                gameId = cache.gameId,
                gameName = game.title,
                channelName = null,
                coverPath = game.coverPath,
                direction = SyncDirection.UPLOAD,
                status = SyncStatus.PENDING
            ))
            syncQueueManager.updateOperation(cache.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            val forceOverwrite = resolution == ConflictResolution.KEEP_LOCAL
            val result = saveSyncRepository.get().uploadSave(
                gameId = cache.gameId,
                emulatorId = cache.emulatorId,
                channelName = null,
                forceOverwrite = forceOverwrite
            )

            when (result) {
                is SaveSyncResult.Success -> {
                    saveCacheDao.markSynced(cache.id, Instant.now())
                    if (result.rommSaveId != null) {
                        saveCacheDao.updateRommSaveId(cache.id, result.rommSaveId)
                    }
                    if (result.serverTimestamp != null) {
                        val oldCachedAtMillis = cache.cachedAt.toEpochMilli()
                        saveCacheDao.updateCachedAt(cache.id, result.serverTimestamp)
                        val game = gameDao.getById(cache.gameId)
                        if (game?.activeSaveTimestamp == oldCachedAtMillis) {
                            gameDao.updateActiveSaveTimestamp(cache.gameId, result.serverTimestamp.toEpochMilli())
                        }
                    }
                    syncQueueManager.completeOperation(cache.gameId)
                    synced++
                    Logger.debug(TAG, "processDirtySaveCaches: Synced gameId=${cache.gameId}")
                }
                is SaveSyncResult.Error -> {
                    saveCacheDao.markSyncError(cache.id, result.message)
                    syncQueueManager.completeOperation(cache.gameId, result.message)
                    Logger.warn(TAG, "processDirtySaveCaches: Failed gameId=${cache.gameId} | ${result.message}")
                }
                is SaveSyncResult.Conflict -> {
                    syncQueueManager.completeOperation(cache.gameId, "Conflict not resolved")
                    Logger.debug(TAG, "processDirtySaveCaches: Conflict gameId=${cache.gameId}")
                }
                is SaveSyncResult.NoSaveFound,
                is SaveSyncResult.NotConfigured -> {
                    syncQueueManager.completeOperation(cache.gameId)
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped gameId=${cache.gameId} | result=$result")
                }
                else -> {
                    syncQueueManager.completeOperation(cache.gameId, "Skipped")
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped gameId=${cache.gameId} | result=$result")
                }
            }
        }

        return synced
    }

    suspend fun queueStateUpload(gameId: Long, rommId: Long, stateCacheId: Long, emulatorId: String) {
        val payload = SaveStatePayload(stateCacheId, emulatorId)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_STATE,
                priority = SyncPriority.SAVE_STATE,
                payloadJson = payloadCodec.encode(payload)
            )
        )
    }

    suspend fun queuePropertyChange(gameId: Long, rommId: Long, syncType: SyncType, intValue: Int? = null, stringValue: String? = null) {
        val payload = PropertyPayload(intValue, stringValue)
        // Replace any existing pending for same game+type
        pendingSyncQueueDao.deleteByGameAndType(gameId, syncType)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = syncType,
                priority = SyncPriority.PROPERTY,
                payloadJson = payloadCodec.encode(payload)
            )
        )
    }

    suspend fun queueFavoriteChange(gameId: Long, rommId: Long, isFavorite: Boolean) {
        queuePropertyChange(gameId, rommId, SyncType.FAVORITE, intValue = if (isFavorite) 1 else 0)
    }
}

