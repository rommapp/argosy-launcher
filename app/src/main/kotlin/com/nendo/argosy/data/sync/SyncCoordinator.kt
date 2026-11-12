package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.resolved
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncStatus as DbSyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.remote.romm.AccountApi
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMApiProvider
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMUserPropsUpdateData
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
import kotlinx.coroutines.delay
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
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val romMRepository: Lazy<RomMRepository>,
    private val saveSyncRepository: Lazy<SaveSyncRepository>,
    private val saveCacheManager: Lazy<SaveCacheManager>,
    private val stateCacheManager: Lazy<StateCacheManager>,
    private val syncQueueManager: SyncQueueManager,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val payloadCodec: SyncPayloadCodec,
    private val savePathResolver: SavePathResolver,
    private val strategySelector: SaveSyncStrategySelector,
    private val pendingConflictDao: PendingConflictDao,
    private val reconcileEffectApplier: ReconcileEffectApplier,
    private val saveRecoveryGate: SaveRecoveryGate,
    private val screenshotUploads: ScreenshotUploadQueue,
    private val rommApiProvider: RomMApiProvider,
    private val accountSwitchMarkerStore: com.nendo.argosy.data.preferences.AccountSwitchMarkerStore,
    private val syncStatesOnSessionEndUseCase:
        Lazy<com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase>,
    private val negotiateInventory: NegotiateInventory,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao,
    private val snapshotRouter: Lazy<com.nendo.argosy.data.sync.snapshot.SnapshotSyncRouter>
) {
    companion object {
        private const val TAG = "SyncCoordinator"
        private val NEGOTIATE_COOLDOWN: Duration = Duration.ofMinutes(5)
        private const val RECONNECT_RECONCILE_COOLDOWN_MS = 2 * 60_000L
        private const val QUEUE_ITEM_SPACING_MS = 750L
        private const val ORPHAN_RECOVERY_TIMEOUT_MS = 10_000L
    }

    private val mutex = Mutex()

    private sealed interface OwnerRoute {
        data object Live : OwnerRoute
        data class Delegated(val account: AccountApi) : OwnerRoute
        data class Unavailable(val ownerUserId: Long) : OwnerRoute
    }

    private suspend fun routeForOwner(ownerUserId: Long?, signedInUserId: Long?): OwnerRoute {
        if (ownerUserId == null || ownerUserId == signedInUserId) return OwnerRoute.Live
        val account = rommApiProvider.forRommUser(ownerUserId) ?: return OwnerRoute.Unavailable(ownerUserId)
        return OwnerRoute.Delegated(account)
    }

    sealed class ProcessResult {
        data object NotConnected : ProcessResult()
        data class Completed(val processed: Int, val failed: Int) : ProcessResult()
    }

    @Volatile private var lastReconnectReconcileAtMs = 0L

    /**
     * [reconcileAll] for a connection that just came up. Reconnects inside
     * [RECONNECT_RECONCILE_COOLDOWN_MS] of the last one, from any caller, reconcile once.
     */
    suspend fun reconcileAfterReconnect(): ReconcileSummary? {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            if (lastReconnectReconcileAtMs != 0L &&
                now - lastReconnectReconcileAtMs < RECONNECT_RECONCILE_COOLDOWN_MS
            ) {
                Logger.debug(TAG, "reconcileAfterReconnect: cooldown active, skipping")
                return null
            }
            lastReconnectReconcileAtMs = now
        }
        return reconcileAll()
    }

    suspend fun reconcileAll(bypassCooldown: Boolean = false): ReconcileSummary = withContext(Dispatchers.IO) {
        if (accountSwitchMarkerStore.isSwitching()) {
            Logger.info(TAG, "reconcileAll: account switch in progress, not touching save files")
            return@withContext ReconcileSummary(ProcessResult.NotConnected, planConflicts = 0, planApplied = 0)
        }

        val prefs = syncPreferencesRepository.preferences.first()
        val secureSaves = prefs.secureSaves
        canonicalizeStaleEmulatorIds()
        val queueResult = processQueue()

        val state = romMRepository.get().connectionState.value
        val caps = (state as? ConnectionState.Connected)?.capabilities
        if (caps?.supportsSyncNegotiate != true) {
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }

        val now = Instant.now()
        val last = syncPreferencesRepository.getLastNegotiateAt()
        if (!bypassCooldown && last != null && Duration.between(last, now) < NEGOTIATE_COOLDOWN) {
            Logger.debug(TAG, "reconcileAll: negotiate cooldown active (last=$last), skipping")
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0)
        }
        if (!secureSaves && prefs.saveSyncEnabled) {
            val refreshed = saveSyncRepository.get().scanAndQueueLocalChanges(secureSaves = false)
            if (refreshed > 0) {
                Logger.info(TAG, "reconcileAll: secure-saves-off discovery refreshed $refreshed caches, draining before negotiate")
            }
            val adoptedStates = runCatching {
                syncStatesOnSessionEndUseCase.get().adoptOffSessionStatesForDownloadedGames()
            }.onFailure { Logger.error(TAG, "reconcileAll: off-session state adoption failed", it) }
                .getOrDefault(0)
            if (adoptedStates > 0) {
                Logger.info(TAG, "reconcileAll: secure-saves-off discovery adopted $adoptedStates states")
            }
            processQueue()
        }

        val inventory = negotiateInventory.build(secureSaves)
        val games = inventory.map { it.romId }.distinct().size
        val plan = strategySelector.current().planReconcile(inventory) ?: run {
            Logger.warn(TAG, "reconcileAll: the server did not answer negotiate; nothing applied")
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0, planGames = games, serverAnswered = false)
        }
        syncPreferencesRepository.setLastNegotiateAt(now)
        if (plan.operations.isEmpty()) {
            return@withContext ReconcileSummary(queueResult, planConflicts = 0, planApplied = 0, planGames = games)
        }

        val (conflicts, applied) = applyPlan(plan)
        Logger.info(TAG, "reconcileAll: plan applied | conflicts=$conflicts handled=$applied sessionId=${plan.sessionId}")
        ReconcileSummary(
            queueResult,
            planConflicts = conflicts,
            planApplied = applied,
            planGames = games
        )
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
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val existing = saveSyncDao.getByGameEmulatorAndChannel(
            gameId, emu, SaveSyncApiClient.syncKeyOf(slot), ownerUserId
        )
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
        val planApplied: Int,
        val planGames: Int = 0,
        val serverAnswered: Boolean = true
    )

    suspend fun processQueue(): ProcessResult = withContext(Dispatchers.IO) {
        if (accountSwitchMarkerStore.isSwitching()) {
            Logger.info(TAG, "processQueue: account switch in progress, not touching save files")
            return@withContext ProcessResult.NotConnected
        }

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
            val screenshots = ScreenshotTally()

            val prefs = syncPreferencesRepository.preferences.first()
            val saveSyncEnabled = prefs.saveSyncEnabled
            val signedInUserId = prefs.rommUserId

            val promoted = pendingSyncQueueDao.promoteEligibleFailedToPending()
            if (promoted > 0) {
                Logger.debug(TAG, "processQueue: Promoted $promoted FAILED rows back to PENDING")
            }

            if (saveSyncEnabled) {
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
                    .filterNot { it.syncType == SyncType.ACHIEVEMENT }
                if (items.isNotEmpty()) Logger.debug(TAG, "processQueue: Processing ${items.size} items at priority $priority")

                for ((index, item) in items.withIndex()) {
                    if (index > 0) delay(QUEUE_ITEM_SPACING_MS)
                    if (romM.connectionState.value !is ConnectionState.Connected) {
                        Logger.debug(TAG, "processQueue: Connection lost, stopping")
                        break
                    }

                    when (val outcome = processItem(item, signedInUserId, screenshots)) {
                        ItemOutcome.Done -> {
                            if (item.sessionId != null) {
                                pendingSyncQueueDao.markCompleted(item.id)
                            } else {
                                pendingSyncQueueDao.deleteById(item.id)
                            }
                            processed++
                        }
                        is ItemOutcome.Failed -> {
                            Logger.warn(TAG, "processQueue: ${item.syncType} row ${item.id} gameId=${item.gameId} failed | reason=${outcome.reason}")
                            pendingSyncQueueDao.markFailed(item.id, outcome.reason)
                            failed++
                        }
                        is ItemOutcome.Deferred -> {
                            Logger.info(TAG, "processQueue: ${item.syncType} row ${item.id} gameId=${item.gameId} stays queued | reason=${outcome.reason}")
                            pendingSyncQueueDao.recordRetryableFailure(item.id, outcome.reason)
                            failed++
                        }
                        is ItemOutcome.Rejected -> {
                            Logger.warn(TAG, "processQueue: ${item.syncType} row ${item.id} gameId=${item.gameId} rejected by the server, dropped | reason=${outcome.reason}")
                            pendingSyncQueueDao.deleteById(item.id)
                            failed++
                        }
                    }
                }
            }
            screenshotUploads.announce(screenshots.uploaded, screenshots.rejected)

            if (saveSyncEnabled) {
                val dirtySaves = processDirtySaveCaches(signedInUserId)
                processed += dirtySaves

                val downloaded = saveSyncRepository.get().downloadPendingServerSaves()
                processed += downloaded

                val validated = validateSaveStates()
                processed += validated
            }

            screenshotUploads.tidyOnce()

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

    private sealed interface ItemOutcome {
        data object Done : ItemOutcome
        data class Failed(val reason: String) : ItemOutcome
        data class Deferred(val reason: String) : ItemOutcome
        data class Rejected(val reason: String) : ItemOutcome
    }

    private class ScreenshotTally {
        var uploaded = 0
        var rejected = 0
    }

    private fun Boolean.toOutcome(item: PendingSyncQueueEntity): ItemOutcome =
        if (this) ItemOutcome.Done else ItemOutcome.Failed("${item.syncType} sync failed")

    private suspend fun processItem(
        item: PendingSyncQueueEntity,
        signedInUserId: Long?,
        screenshots: ScreenshotTally
    ): ItemOutcome {
        pendingSyncQueueDao.markInProgress(item.id)

        return try {
            when (item.syncType) {
                SyncType.SAVE_FILE -> processSaveFile(item, signedInUserId)
                SyncType.SAVE_STATE -> processSaveState(item, signedInUserId)
                SyncType.RATING -> processProperty(item, signedInUserId).toOutcome(item)
                SyncType.DIFFICULTY -> processProperty(item, signedInUserId).toOutcome(item)
                SyncType.COMPLETION -> processProperty(item, signedInUserId).toOutcome(item)
                SyncType.STATUS -> processProperty(item, signedInUserId).toOutcome(item)
                SyncType.FAVORITE -> processFavorite(item, signedInUserId).toOutcome(item)
                SyncType.HIDDEN -> processHidden(item, signedInUserId).toOutcome(item)
                SyncType.ACHIEVEMENT -> false.toOutcome(item)
                SyncType.SCREENSHOT -> processScreenshot(item, signedInUserId, screenshots)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "processItem: Exception processing ${item.syncType}", e)
            val reason = e.message ?: e.javaClass.simpleName
            if (item.syncType == SyncType.SCREENSHOT) ItemOutcome.Deferred(reason) else ItemOutcome.Failed(reason)
        }
    }

    /**
     * Drains one queued save upload under the account that enqueued it.
     *
     * A row carrying a cache id is uploaded from those pinned bytes; the live save path is only
     * re-resolved for rows with nothing pinned, and never for a row owned by an account other
     * than the signed-in one, because the bytes on that path are then not the owner's.
     */
    private suspend fun processSaveFile(item: PendingSyncQueueEntity, signedInUserId: Long?): ItemOutcome {
        val game = gameDao.getById(item.gameId) ?: return ItemOutcome.Failed("Game ${item.gameId} not found")
        if (game.localPath == null) {
            Logger.debug(TAG, "processSaveFile: dropping queue item for non-local game gameId=${item.gameId}")
            return ItemOutcome.Done
        }
        if (!item.targetsRomOf(game)) {
            Logger.warn(
                TAG,
                "processSaveFile: queue row rommId=${item.rommId} no longer matches gameId=${item.gameId} rommId=${game.rommId}; keeping the save local and dropping the row"
            )
            return ItemOutcome.Done
        }
        val payload = payloadCodec.decodeSaveFile(item.payloadJson)
            ?: return ItemOutcome.Failed("Unreadable save file payload")
        val channel = payload.channelName
        if (channel != null && Regex("""^state_""", RegexOption.IGNORE_CASE).containsMatchIn(channel)) {
            Logger.debug(TAG, "processSaveFile: dropping queue item for state-shaped channel gameId=${item.gameId} channel=$channel")
            return ItemOutcome.Done
        }

        val route = routeForOwner(item.ownerUserId, signedInUserId)
        if (route is OwnerRoute.Unavailable) {
            Logger.warn(TAG, "processSaveFile: no client for owner ${route.ownerUserId} on gameId=${item.gameId}; leaving row queued")
            return ItemOutcome.Failed("No client for owner ${route.ownerUserId}")
        }
        val ownerApi = (route as? OwnerRoute.Delegated)?.account
        val pinnedCache = item.cacheId?.let { saveCacheDao.getById(it) }

        if (pinnedCache == null && ownerApi != null) {
            Logger.warn(
                TAG,
                "processSaveFile: queue row for gameId=${item.gameId} belongs to user ${item.ownerUserId} but pins no cache row; refusing to upload live-path bytes"
            )
            return ItemOutcome.Failed("Row for user ${item.ownerUserId} pins no cache row")
        }

        val namedChannel = SaveSyncApiClient.namedChannelOrNull(channel)
        if (pinnedCache == null && namedChannel != null) {
            Logger.warn(
                TAG,
                "processSaveFile: queue row for gameId=${item.gameId} channel=$namedChannel pins no cache row (cacheId=${item.cacheId}); refusing to upload live-path bytes under a named channel"
            )
            return ItemOutcome.Failed("Named channel $namedChannel pins no cache row")
        }

        syncQueueManager.addOperation(
            SyncOperation(
                gameId = item.gameId,
                gameName = game.title,
                channelName = payload.channelName,
                coverPath = gameArtDao.resolved(item.gameId).coverPath,
                direction = SyncDirection.UPLOAD,
                status = SyncStatus.IN_PROGRESS
            )
        )

        val result = if (pinnedCache != null) {
            uploadPinnedCache(item, game.rommId, pinnedCache, payload.emulatorId, ownerApi)
        } else {
            saveSyncRepository.get().uploadSave(
                gameId = item.gameId,
                emulatorId = payload.emulatorId,
                channelName = payload.channelName,
                forceOverwrite = false
            )
        }

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
                pendingConflictDao.record(
                    result.toPendingConflict(
                        fileName = payload.channelName ?: game.title,
                        slot = payload.channelName,
                        emulatorId = payload.emulatorId,
                        localUpdatedAt = localTime,
                        ownerUserId = item.ownerUserId ?: signedInUserId ?: PendingConflictEntity.UNATTRIBUTED
                    )
                )
                syncQueueManager.removeOperation(item.gameId)
            }
            else -> syncQueueManager.removeOperation(item.gameId)
        }

        return when (result) {
            is SaveSyncResult.Success -> ItemOutcome.Done
            is SaveSyncResult.Error -> ItemOutcome.Failed(result.message)
            is SaveSyncResult.Conflict -> ItemOutcome.Failed("Conflict with server save ${result.serverSaveId}")
            is SaveSyncResult.NeedsHardcoreResolution -> ItemOutcome.Failed("Hardcore save requires resolution")
            SaveSyncResult.NoSaveFound -> ItemOutcome.Failed("No save found")
            SaveSyncResult.NotConfigured -> ItemOutcome.Failed("Save sync not configured")
        }
    }

    private suspend fun uploadPinnedCache(
        item: PendingSyncQueueEntity,
        rommId: Long?,
        cache: SaveCacheEntity,
        payloadEmulatorId: String,
        ownerApi: AccountApi?
    ): SaveSyncResult {
        val targetRommId = rommId ?: item.rommId
        val cacheFile = saveCacheManager.get().getCacheFile(cache)
        if (!cacheFile.exists()) {
            Logger.warn(TAG, "uploadPinnedCache: pinned cache ${cache.id} missing on disk for gameId=${item.gameId}")
            return SaveSyncResult.NoSaveFound
        }
        val channelName = cache.channelName
        if (channelName == null) {
            Logger.warn(TAG, "uploadPinnedCache: pinned cache ${cache.id} has no channel; cannot address a server slot")
            return SaveSyncResult.NotConfigured
        }
        return saveSyncRepository.get().uploadCacheEntry(
            gameId = item.gameId,
            rommId = targetRommId,
            emulatorId = cache.emulatorId.takeIf { it.isNotBlank() && it != "default" } ?: payloadEmulatorId,
            channelName = channelName,
            cacheFile = cacheFile,
            contentHash = cache.contentHash,
            uploadedCacheId = cache.id,
            ownerApi = ownerApi
        )
    }

    private suspend fun processSaveState(item: PendingSyncQueueEntity, signedInUserId: Long?): ItemOutcome {
        val payload = payloadCodec.decodeSaveState(item.payloadJson)
            ?: return ItemOutcome.Failed("Unreadable save state payload")
        val state = stateCacheManager.get().getStateById(payload.stateCacheId)
            ?: return ItemOutcome.Failed("State cache ${payload.stateCacheId} not found")
        if (state.gameId != item.gameId) {
            Logger.warn(
                TAG,
                "processSaveState: state ${state.id} no longer belongs to gameId=${item.gameId}; keeping it local and dropping the row"
            )
            return ItemOutcome.Done
        }
        val game = gameDao.getById(item.gameId)
        if (!item.targetsRomOf(game)) {
            Logger.warn(
                TAG,
                "processSaveState: queue row rommId=${item.rommId} no longer matches gameId=${item.gameId} rommId=${game?.rommId}; keeping state ${state.id} local and dropping the row"
            )
            return ItemOutcome.Done
        }
        val route = routeForOwner(item.ownerUserId, signedInUserId)
        if (route is OwnerRoute.Unavailable) {
            Logger.warn(
                TAG,
                "processSaveState: no client for owner ${route.ownerUserId} on gameId=${item.gameId}; leaving row queued"
            )
            return ItemOutcome.Failed("No client for owner ${route.ownerUserId}")
        }
        val api = (route as? OwnerRoute.Delegated)?.account?.api
            ?: saveSyncRepository.get().getApi()
            ?: return ItemOutcome.Failed("No RomM client")
        val romBaseName = game?.localPath?.let { java.io.File(it).nameWithoutExtension } ?: state.platformSlug

        return when (val result = stateCacheManager.get().uploadStateToRomM(state, item.rommId, romBaseName, api)) {
            StateCacheManager.StateCloudResult.Success,
            StateCacheManager.StateCloudResult.AlreadySynced -> ItemOutcome.Done
            is StateCacheManager.StateCloudResult.Error -> ItemOutcome.Failed(result.message)
            is StateCacheManager.StateCloudResult.Conflict ->
                ItemOutcome.Failed("Conflict with server state | local=${result.localTime}, server=${result.serverTime}")
            StateCacheManager.StateCloudResult.NoStateFound -> ItemOutcome.Failed("State file not found")
            StateCacheManager.StateCloudResult.NotConfigured -> ItemOutcome.Failed("State sync not configured")
        }
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

    /**
     * Writes user properties under the account that queued them, not whoever is signed in now.
     * A delegated write goes straight at the owner's client because the repository facade always
     * resolves the live connection.
     */
    private suspend fun processProperty(item: PendingSyncQueueEntity, signedInUserId: Long?): Boolean {
        val payload = payloadCodec.decodeProperty(item.payloadJson) ?: return false
        val props = RomMUserPropsUpdateData(
            rating = if (item.syncType == SyncType.RATING) payload.intValue else null,
            difficulty = if (item.syncType == SyncType.DIFFICULTY) payload.intValue else null,
            completion = if (item.syncType == SyncType.COMPLETION) payload.intValue else null,
            status = if (item.syncType == SyncType.STATUS) payload.stringValue else null,
            hidden = null
        )
        return writeUserProps(item, signedInUserId, props)
    }

    private suspend fun processHidden(item: PendingSyncQueueEntity, signedInUserId: Long?): Boolean {
        val payload = payloadCodec.decodeProperty(item.payloadJson) ?: return false
        val props = RomMUserPropsUpdateData(hidden = payload.intValue == 1)
        return writeUserProps(item, signedInUserId, props)
    }

    private suspend fun writeUserProps(
        item: PendingSyncQueueEntity,
        signedInUserId: Long?,
        props: RomMUserPropsUpdateData
    ): Boolean {
        return when (val route = routeForOwner(item.ownerUserId, signedInUserId)) {
            is OwnerRoute.Unavailable -> {
                Logger.warn(
                    TAG,
                    "writeUserProps: no client for owner ${route.ownerUserId} on gameId=${item.gameId}; leaving row queued"
                )
                false
            }
            is OwnerRoute.Delegated -> try {
                route.account.api.updateRomUserProps(item.rommId, props).isSuccessful
            } catch (e: Exception) {
                Logger.error(TAG, "writeUserProps: delegated write failed for gameId=${item.gameId}", e)
                false
            }
            OwnerRoute.Live -> romMRepository.get().updateRomUserProps(
                rommId = item.rommId,
                userRating = props.rating,
                userDifficulty = props.difficulty,
                completion = props.completion,
                userStatus = props.status,
                hidden = props.hidden
            )
        }
    }

    /**
     * Favourites are not delegable: the write is a read-modify-write of the account's favourites
     * collection and it also stamps a device-global last-sync time. A foreign row stays queued
     * until its own account is signed in rather than being written to the wrong collection.
     */
    private suspend fun processFavorite(item: PendingSyncQueueEntity, signedInUserId: Long?): Boolean {
        val payload = payloadCodec.decodeProperty(item.payloadJson) ?: return false
        if (routeForOwner(item.ownerUserId, signedInUserId) !is OwnerRoute.Live) {
            Logger.warn(
                TAG,
                "processFavorite: row for gameId=${item.gameId} belongs to user ${item.ownerUserId}; leaving row queued"
            )
            return false
        }
        val isFavorite = payload.intValue == 1

        return romMRepository.get().syncFavorite(item.rommId, isFavorite)
    }

    private suspend fun processScreenshot(
        item: PendingSyncQueueEntity,
        signedInUserId: Long?,
        screenshots: ScreenshotTally
    ): ItemOutcome {
        if (routeForOwner(item.ownerUserId, signedInUserId) !is OwnerRoute.Live) {
            return ItemOutcome.Deferred("Row belongs to user ${item.ownerUserId}")
        }
        return when (val outcome = screenshotUploads.upload(item, gameDao.getById(item.gameId))) {
            ScreenshotUploadQueue.Outcome.Uploaded -> {
                screenshots.uploaded++
                ItemOutcome.Done
            }
            is ScreenshotUploadQueue.Outcome.Dropped -> {
                Logger.info(TAG, "processScreenshot: dropping row ${item.id} gameId=${item.gameId} | reason=${outcome.reason}")
                ItemOutcome.Done
            }
            is ScreenshotUploadQueue.Outcome.Deferred -> ItemOutcome.Deferred(outcome.reason)
            is ScreenshotUploadQueue.Outcome.Rejected -> {
                screenshots.rejected++
                ItemOutcome.Rejected(outcome.reason)
            }
        }
    }

    private suspend fun clearChainDirtyFlags(cache: SaveCacheEntity) {
        val channel = cache.channelName
        if (channel == null) saveCacheDao.clearDirtyFlagForNoChannel(cache.gameId, cache.ownerUserId)
        else saveCacheDao.clearDirtyFlagForChannel(cache.gameId, cache.ownerUserId, channel, excludeId = -1)
    }

    /**
     * The second, cache-driven upload queue. Each dirty cache row is drained under the account
     * that owns it: a row owned by an absent account uploads from its own cached bytes through
     * that account's client, and never through the live connection or the live save path.
     */
    private suspend fun processDirtySaveCaches(signedInUserId: Long?): Int {
        kotlinx.coroutines.withTimeoutOrNull(ORPHAN_RECOVERY_TIMEOUT_MS) { saveRecoveryGate.await() }
            ?: Logger.warn(TAG, "processDirtySaveCaches: orphan-recovery gate timed out, draining anyway")

        val dirtySaves = saveCacheDao.getNeedingRemoteSync()
        if (dirtySaves.isEmpty()) return 0

        Logger.debug(TAG, "processDirtySaveCaches: Found ${dirtySaves.size} saves needing sync")

        val routes = dirtySaves
            .map { it.ownerUserId }
            .distinct()
            .associateWith { routeForOwner(it, signedInUserId) }

        val affectedGameIds = dirtySaves.map { it.gameId }.distinct()
        for (gid in affectedGameIds) {
            saveSyncRepository.get().flushPendingDeviceSync(gid)
        }

        val snapshotGames = affectedGameIds.filter { snapshotRouter.get().handles(it) }.toSet()
        val (channelCaches, nonChannelCaches) = dirtySaves
            .partition { it.channelName != null || it.gameId in snapshotGames }
            .let { (channel, live) -> channel.sortedBy { it.cachedAt } to live }

        val conflicts = mutableMapOf<Long, Pair<SaveCacheEntity, ConflictInfo>>()
        val resolutions = mutableMapOf<Long, ConflictResolution>()

        for (cache in nonChannelCaches) {
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue
            if (game.localPath == null) {
                Logger.debug(TAG, "processDirtySaveCaches: skipping conflict-check for non-local game gameId=${cache.gameId}")
                continue
            }
            if (routes[cache.ownerUserId] !is OwnerRoute.Live) continue

            val conflictInfo = saveSyncRepository.get().checkForConflict(
                gameId = cache.gameId,
                emulatorId = cache.emulatorId,
                channelName = null
            )
            if (conflictInfo != null) {
                conflicts[cache.gameId] = cache to conflictInfo
            }
        }

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
        val conflictedChains = mutableSetOf<Triple<Long, Long?, String?>>()

        for (cache in channelCaches) {
            val chain = Triple(cache.gameId, cache.ownerUserId, cache.channelName)
            if (chain in conflictedChains) continue
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue
            if (game.localPath == null) {
                Logger.debug(TAG, "processDirtySaveCaches: skipping channel cache for non-local game gameId=${cache.gameId}, cacheId=${cache.id}")
                continue
            }

            val route = routes[cache.ownerUserId] ?: OwnerRoute.Live
            if (route is OwnerRoute.Unavailable) {
                Logger.warn(TAG, "processDirtySaveCaches: no client for owner ${route.ownerUserId}; leaving cache id=${cache.id} dirty")
                continue
            }
            val ownerApi = (route as? OwnerRoute.Delegated)?.account

            val cacheFile = saveCacheManager.get().getCacheFile(cache)
            if (!cacheFile.exists()) {
                Logger.warn(TAG, "processDirtySaveCaches: Cache file missing for id=${cache.id}, path=${cache.cachePath}")
                saveCacheDao.markSyncError(cache.id, "Cache file missing")
                continue
            }

            if (ownerApi == null) {
                val conflictInfo = saveSyncRepository.get().checkForConflict(
                    gameId = cache.gameId,
                    emulatorId = cache.emulatorId,
                    channelName = cache.channelName
                )
                if (conflictInfo != null) {
                    conflictedChains += chain
                    clearChainDirtyFlags(cache)
                    val conflictId = pendingConflictDao.record(
                        PendingConflictEntity(
                            gameId = cache.gameId,
                            rommSaveId = conflictInfo.serverSaveId,
                            fileName = cache.channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
                            slot = cache.channelName,
                            emulator = cache.emulatorId,
                            localUpdatedAt = cache.cachedAt,
                            serverUpdatedAt = conflictInfo.serverTimestamp,
                            localHash = cache.contentHash,
                            reason = conflictInfo.serverDeviceName?.let { "Server has newer save from $it" } ?: "Server has newer save",
                            ownerUserId = cache.ownerUserId ?: PendingConflictEntity.UNATTRIBUTED
                        )
                    )
                    syncQueueManager.addConflict(conflictInfo.copy(conflictId = conflictId))
                    Logger.warn(TAG, "processDirtySaveCaches: Pre-upload conflict for channel cache id=${cache.id}")
                    continue
                }
            }

            syncQueueManager.addOperation(SyncOperation(
                gameId = cache.gameId,
                gameName = game.title,
                channelName = cache.channelName,
                coverPath = gameArtDao.resolved(cache.gameId).coverPath,
                direction = SyncDirection.UPLOAD,
                status = SyncStatus.IN_PROGRESS
            ))

            val result = saveSyncRepository.get().uploadCacheEntry(
                gameId = cache.gameId,
                rommId = game.rommId,
                emulatorId = cache.emulatorId,
                channelName = cache.channelName ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
                cacheFile = cacheFile,
                contentHash = cache.contentHash,
                uploadedCacheId = cache.id,
                ownerApi = ownerApi
            )

            when (result) {
                is SaveSyncResult.Success -> {
                    saveCacheDao.markSynced(cache.id, Instant.now())
                    if (result.rommSaveId != null) {
                        saveCacheDao.updateRommSaveId(cache.id, result.rommSaveId)
                    }
                    if (result.serverTimestamp != null) {
                        saveCacheDao.updateCachedAt(cache.id, result.serverTimestamp)
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
                    conflictedChains += chain
                    clearChainDirtyFlags(cache)
                    if (ownerApi != null) {
                        parkConflictForOwner(cache, game.title, result, ownerApi.rommUserId)
                        Logger.warn(TAG, "processDirtySaveCaches: Parked conflict for absent owner ${ownerApi.rommUserId} | cacheId=${cache.id} gameId=${cache.gameId} channel=${cache.channelName}")
                    } else {
                        val conflictId = parkConflictForOwner(
                            cache, game.title, result, cache.ownerUserId ?: PendingConflictEntity.UNATTRIBUTED
                        )
                        syncQueueManager.addConflict(ConflictInfo(
                            gameId = cache.gameId,
                            gameName = game.title,
                            channelName = cache.channelName,
                            localTimestamp = cache.cachedAt,
                            serverTimestamp = result.serverTimestamp,
                            isHashConflict = false,
                            serverDeviceName = result.serverDeviceName,
                            serverSaveId = result.serverSaveId,
                            conflictId = conflictId
                        ))
                        Logger.warn(TAG, "processDirtySaveCaches: Conflict for channel cache id=${cache.id} gameId=${cache.gameId} channel=${cache.channelName} | cleared dirty flag, awaiting resolution")
                    }
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

        for (cache in nonChannelCaches) {
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue
            if (game.localPath == null) {
                Logger.debug(TAG, "processDirtySaveCaches: skipping non-channel cache for non-local game gameId=${cache.gameId}, cacheId=${cache.id}")
                continue
            }
            if (routes[cache.ownerUserId] !is OwnerRoute.Live) {
                Logger.warn(
                    TAG,
                    "processDirtySaveCaches: non-channel cache id=${cache.id} belongs to user ${cache.ownerUserId}; its upload path reads live disk, leaving it for that account"
                )
                continue
            }

            val resolution = resolutions[cache.gameId]

            if (resolution == ConflictResolution.SKIP) {
                saveCacheDao.clearAllDirtyFlags(cache.gameId, cache.ownerUserId)
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
                    activeSaveRepository.setActiveSaveApplied(cache.gameId, false)
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
                coverPath = gameArtDao.resolved(cache.gameId).coverPath,
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
                        saveCacheDao.updateCachedAt(cache.id, result.serverTimestamp)
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

    /**
     * Records a conflict against the account that owns the save instead of asking the person at
     * the device. The signed-in user cannot judge another account's save, and resolving it would
     * act under the wrong identity, so the row waits until its owner is back.
     */
    private suspend fun parkConflictForOwner(
        cache: SaveCacheEntity,
        gameTitle: String,
        result: SaveSyncResult.Conflict,
        ownerUserId: Long
    ): Long =
        pendingConflictDao.record(
            result.toPendingConflict(
                fileName = cache.channelName ?: gameTitle,
                slot = cache.channelName,
                emulatorId = cache.emulatorId,
                localUpdatedAt = cache.cachedAt,
                ownerUserId = ownerUserId
            ).copy(gameId = cache.gameId, localHash = cache.contentHash)
        )

    suspend fun queueStateUpload(gameId: Long, rommId: Long, stateCacheId: Long, emulatorId: String) {
        val payload = SaveStatePayload(stateCacheId, emulatorId)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_STATE,
                priority = SyncPriority.SAVE_STATE,
                payloadJson = payloadCodec.encode(payload),
                ownerUserId = syncPreferencesRepository.getRommUserId()
            )
        )
    }

    suspend fun queuePropertyChange(gameId: Long, rommId: Long, syncType: SyncType, intValue: Int? = null, stringValue: String? = null) {
        val payload = PropertyPayload(intValue, stringValue)
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        pendingSyncQueueDao.deleteByGameAndType(gameId, syncType, ownerUserId)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = syncType,
                priority = SyncPriority.PROPERTY,
                payloadJson = payloadCodec.encode(payload),
                ownerUserId = ownerUserId
            )
        )
    }

    suspend fun queueFavoriteChange(gameId: Long, rommId: Long, isFavorite: Boolean) {
        queuePropertyChange(gameId, rommId, SyncType.FAVORITE, intValue = if (isFavorite) 1 else 0)
    }

    private suspend fun canonicalizeStaleEmulatorIds() {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val stale = saveSyncDao.getStaleDefaultEmulatorRows()
        if (stale.isEmpty()) return
        val gameIds = stale.map { it.gameId }.toSet()
        var rekeyed = 0
        for (gameId in gameIds) {
            val game = gameDao.getById(gameId) ?: continue
            val canonical = saveSyncRepository.get().resolveEmulatorForGame(game) ?: continue
            val touched = saveSyncDao.rekeyEmulatorForGame(gameId, ownerUserId, canonical)
            if (touched > 0) rekeyed += touched
        }
        if (rekeyed > 0) {
            Logger.info(TAG, "canonicalizeStaleEmulatorIds: rekeyed $rekeyed save_sync rows from 'default' to canonical emulator")
        }
    }
}

