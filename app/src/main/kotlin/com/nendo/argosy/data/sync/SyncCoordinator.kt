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
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.strategy.LocalSaveState
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
    private val pendingConflictDao: PendingConflictDao,
    private val reconcileEffectApplier: ReconcileEffectApplier,
    private val saveRecoveryGate: SaveRecoveryGate
) {
    companion object {
        private const val TAG = "SyncCoordinator"
        private val NEGOTIATE_COOLDOWN: Duration = Duration.ofMinutes(5)
        private const val ORPHAN_RECOVERY_TIMEOUT_MS = 10_000L
    }

    private val mutex = Mutex()

    sealed class ProcessResult {
        data object NotConnected : ProcessResult()
        data class Completed(val processed: Int, val failed: Int) : ProcessResult()
    }

    suspend fun reconcileAll(): ReconcileSummary = withContext(Dispatchers.IO) {
        canonicalizeStaleEmulatorIds()
        val queueResult = processQueue()

        val state = romMRepository.get().connectionState.value
        val caps = (state as? ConnectionState.Connected)?.capabilities
        if (caps?.supportsSyncNegotiate != true) {
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }

        val now = Instant.now()
        val last = syncPreferencesRepository.getLastNegotiateAt()
        if (last != null && Duration.between(last, now) < NEGOTIATE_COOLDOWN) {
            Logger.debug(TAG, "reconcileAll: negotiate cooldown active (last=$last), skipping")
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }
        syncPreferencesRepository.setLastNegotiateAt(now)

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
        var skippedNoGame = 0
        var skippedNoRom = 0
        var skippedStateShaped = 0
        val result = rows.mapNotNull { row ->
            val path = row.localSavePath ?: return@mapNotNull null
            val file = File(path)
            if (!file.exists()) return@mapNotNull null

            val channel = row.channelName
            if (channel != null && Regex("""^state_""", RegexOption.IGNORE_CASE).containsMatchIn(channel)) {
                skippedStateShaped++
                return@mapNotNull null
            }

            val game = gameDao.getById(row.gameId) ?: run { skippedNoGame++; return@mapNotNull null }
            if (game.localPath == null) { skippedNoRom++; return@mapNotNull null }

            val romBaseName = File(game.localPath).nameWithoutExtension
            val serverFileName = SaveSyncApiClient.computeUploadFileName(
                localSavePath = path,
                channelName = row.channelName,
                romBaseName = romBaseName
            )

            val reportedTime = Instant.ofEpochMilli(file.lastModified())

            LocalSaveState(
                romId = row.rommId,
                fileName = serverFileName,
                slot = row.channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
                emulator = row.emulatorId,
                contentHash = row.lastUploadedHash,
                updatedAt = reportedTime.toString(),
                fileSizeBytes = file.length()
            )
        }
        val nullHashCount = result.count { it.contentHash == null }
        Logger.debug(TAG, "buildInventory: rows=${result.size} nullHash=$nullHashCount skippedNoGame=$skippedNoGame skippedNoRom=$skippedNoRom skippedStateShaped=$skippedStateShaped")
        return result
    }

    private suspend fun applyPlan(plan: ReconcilePlan): Pair<Int, Int> {
        var outcome = ReconcileEffectOutcome.NONE
        var skippedNullSlot = 0
        var skippedStateShaped = 0
        val stateSlotPattern = Regex("""^state_""", RegexOption.IGNORE_CASE)
        for (op in plan.operations) {
            if (op.slot == null) {
                skippedNullSlot++
                continue
            }
            if (stateSlotPattern.containsMatchIn(op.slot)) {
                skippedStateShaped++
                continue
            }
            outcome += reconcileEffectApplier.apply(op, plan.sessionId)
        }
        if (skippedNullSlot > 0) {
            Logger.info(TAG, "applyPlan: skipped $skippedNullSlot operations with null slot (legacy archived saves)")
        }
        if (skippedStateShaped > 0) {
            Logger.info(TAG, "applyPlan: skipped $skippedStateShaped operations with state-shaped slot (legacy state-in-save rows)")
        }
        return outcome.conflicts to outcome.applied
    }

    private suspend fun resolveConflictLocalTime(
        gameId: Long,
        emulatorId: String?,
        slot: String?,
        fallback: Instant?
    ): Instant? {
        val emu = emulatorId ?: return fallback
        val existing = if (slot != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emu, slot)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emu)
        }
        return resolveLocalTimeFromEntity(existing, fallback)
    }

    private fun resolveLocalTimeFromEntity(
        existing: SaveSyncEntity?,
        fallback: Instant?
    ): Instant? {
        if (existing == null) return fallback
        val fileMtime = existing.localSavePath?.let { path ->
            val f = File(path)
            if (f.exists()) Instant.ofEpochMilli(f.lastModified()) else null
        }
        return when (existing.syncStatus) {
            SaveSyncEntity.STATUS_SYNCED, SaveSyncEntity.STATUS_SERVER_NEWER ->
                existing.serverUpdatedAt ?: fileMtime ?: existing.localUpdatedAt ?: fallback
            else -> fileMtime ?: existing.localUpdatedAt ?: fallback
        }
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
                        if (item.sessionId != null) {
                            pendingSyncQueueDao.markCompleted(item.id)
                        } else {
                            pendingSyncQueueDao.deleteById(item.id)
                        }
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

            finalizeDrainedSessions()

            Logger.info(TAG, "processQueue: Completed | processed=$processed, failed=$failed")
            ProcessResult.Completed(processed, failed)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun finalizeDrainedSessions() {
        val sessions = pendingSyncQueueDao.distinctSessions()
        for (sid in sessions) {
            val active = pendingSyncQueueDao.countActiveBySession(sid)
            if (active > 0) continue
            val completed = pendingSyncQueueDao.countCompletedBySession(sid)
            val failed = pendingSyncQueueDao.countFailedBySession(sid)
            val outcome = strategySelector.current().completeSession(
                sessionId = sid,
                operationsCompleted = completed,
                operationsFailed = failed
            )
            when (outcome) {
                com.nendo.argosy.data.sync.strategy.CompleteOutcome.ACCEPTED -> {
                    pendingSyncQueueDao.deleteBySession(sid)
                    Logger.info(TAG, "finalizeDrainedSessions: session $sid finalized | completed=$completed failed=$failed")
                }
                com.nendo.argosy.data.sync.strategy.CompleteOutcome.ALREADY_FINALIZED -> {
                    pendingSyncQueueDao.deleteBySession(sid)
                    Logger.info(TAG, "finalizeDrainedSessions: session $sid drained (server reported terminal); rows deleted")
                }
                com.nendo.argosy.data.sync.strategy.CompleteOutcome.RETRY_LATER -> {
                    Logger.warn(TAG, "finalizeDrainedSessions: session $sid completeSession transient failure; rows retained for retry next pass")
                }
            }
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
        val channel = payload.channelName
        if (channel != null && Regex("""^state_""", RegexOption.IGNORE_CASE).containsMatchIn(channel)) {
            Logger.debug(TAG, "processSaveFile: dropping queue item for state-shaped channel gameId=${item.gameId} channel=$channel")
            return true
        }

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
            is SaveSyncResult.Success -> {
                if (result.noOp) {
                    syncQueueManager.removeOperation(item.gameId)
                } else {
                    syncQueueManager.completeOperation(item.gameId)
                }
            }
            is SaveSyncResult.NoSaveFound,
            is SaveSyncResult.NotConfigured -> syncQueueManager.removeOperation(item.gameId)
            is SaveSyncResult.Error -> syncQueueManager.completeOperation(item.gameId, result.message)
            is SaveSyncResult.Conflict -> {
                val localTime = resolveConflictLocalTime(
                    item.gameId, payload.emulatorId, payload.channelName, result.localTimestamp
                )
                pendingConflictDao.upsert(
                    PendingConflictEntity(
                        gameId = item.gameId,
                        rommSaveId = result.serverSaveId,
                        fileName = payload.channelName ?: game.title,
                        slot = payload.channelName,
                        emulator = payload.emulatorId,
                        localUpdatedAt = localTime,
                        serverUpdatedAt = result.serverTimestamp,
                        localHash = result.localContentHash,
                        serverHash = result.serverContentHash,
                        reason = result.serverDeviceName?.let { "Server has newer save from $it" } ?: "Server has newer save"
                    )
                )
                syncQueueManager.removeOperation(item.gameId)
            }
            else -> syncQueueManager.removeOperation(item.gameId)
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
        kotlinx.coroutines.withTimeoutOrNull(ORPHAN_RECOVERY_TIMEOUT_MS) { saveRecoveryGate.await() }
            ?: Logger.warn(TAG, "processDirtySaveCaches: orphan-recovery gate timed out, draining anyway")

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
                contentHash = cache.contentHash,
                uploadedCacheId = cache.id
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
                    if (result.noOp) {
                        syncQueueManager.removeOperation(cache.gameId)
                    } else {
                        syncQueueManager.completeOperation(cache.gameId)
                        synced++
                    }
                    Logger.debug(TAG, "processDirtySaveCaches: Synced channel cache id=${cache.id} gameId=${cache.gameId} channel=${cache.channelName} rommSaveId=${result.rommSaveId} noOp=${result.noOp}")
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
                    syncQueueManager.removeOperation(cache.gameId)
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped channel cache id=${cache.id} | result=$result")
                }
                else -> {
                    syncQueueManager.removeOperation(cache.gameId)
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
                forceOverwrite = forceOverwrite,
                isHardcore = cache.isHardcore,
                uploadedCacheId = cache.id
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
                    if (result.noOp) {
                        syncQueueManager.removeOperation(cache.gameId)
                    } else {
                        syncQueueManager.completeOperation(cache.gameId)
                        synced++
                    }
                    Logger.debug(TAG, "processDirtySaveCaches: Synced gameId=${cache.gameId} noOp=${result.noOp}")
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
                    syncQueueManager.removeOperation(cache.gameId)
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

    private suspend fun canonicalizeStaleEmulatorIds() {
        val stale = saveSyncDao.getStaleDefaultEmulatorRows()
        if (stale.isEmpty()) return
        val gameIds = stale.map { it.gameId }.toSet()
        var rekeyed = 0
        for (gameId in gameIds) {
            val game = gameDao.getById(gameId) ?: continue
            val canonical = saveSyncRepository.get().resolveEmulatorForGame(game) ?: continue
            val touched = saveSyncDao.rekeyEmulatorForGame(gameId, canonical)
            if (touched > 0) rekeyed += touched
        }
        if (rekeyed > 0) {
            Logger.info(TAG, "canonicalizeStaleEmulatorIds: rekeyed $rekeyed save_sync rows from 'default' to canonical emulator")
        }
    }
}

