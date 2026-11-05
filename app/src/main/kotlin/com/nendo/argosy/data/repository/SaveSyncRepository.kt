package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveCountByDevice
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.AccountApi
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.NegotiateInventory
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.strategy.NegotiatorSaveSyncStrategy
import com.nendo.argosy.data.sync.strategy.ReconcileAction
import com.nendo.argosy.data.sync.strategy.SaveSyncStrategySelector
import com.nendo.argosy.data.sync.SyncQueueState
import com.nendo.argosy.domain.model.SaveSlotClassifier
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class SaveSyncResult {
    data class Success(
        val rommSaveId: Long? = null,
        val serverTimestamp: Instant? = null,
        val noOp: Boolean = false
    ) : SaveSyncResult()
    data class Conflict(
        val gameId: Long,
        val localTimestamp: Instant,
        val serverTimestamp: Instant,
        val serverDeviceName: String? = null,
        val serverSaveId: Long? = null,
        val localContentHash: String? = null,
        val serverContentHash: String? = null,
        val isHardcoreDowngrade: Boolean = false
    ) : SaveSyncResult()
    data class NeedsHardcoreResolution(
        val tempFilePath: String,
        val gameId: Long,
        val gameName: String,
        val emulatorId: String,
        val targetPath: String,
        val isFolderBased: Boolean,
        val channelName: String?
    ) : SaveSyncResult()
    data class Error(val message: String) : SaveSyncResult()
    data object NoSaveFound : SaveSyncResult()
    data object NotConfigured : SaveSyncResult()
}

sealed class PreLaunchSyncResult {
    data object NoConnection : PreLaunchSyncResult()
    data object TimedOut : PreLaunchSyncResult()
    data object NoServerSave : PreLaunchSyncResult()
    data object LocalIsNewer : PreLaunchSyncResult()
    data class ServerIsNewer(
        val serverTimestamp: Instant,
        val channelName: String?,
        val serverSaveId: Long? = null
    ) : PreLaunchSyncResult()
    data class LocalModified(
        val localSavePath: String,
        val serverTimestamp: Instant,
        val channelName: String?,
        val serverSaveId: Long? = null,
        val restoreFailed: Boolean = false,
        val snapshotConflict: Boolean = false
    ) : PreLaunchSyncResult()
}

sealed class SyncAnalysis {
    data object NoConnection : SyncAnalysis()
    data object NoServerSave : SyncAnalysis()
    data object NoLocalSave : SyncAnalysis()
    data object InSync : SyncAnalysis()
    data class LocalNewer(
        val localSavePath: String,
        val channelName: String?
    ) : SyncAnalysis()
    data class ServerNewer(
        val serverSaveId: Long,
        val serverTimestamp: Instant,
        val channelName: String?
    ) : SyncAnalysis()
    data class Conflict(val info: ConflictInfo) : SyncAnalysis()
}

sealed class ForceSyncResult {
    data object AlreadyInSync : ForceSyncResult()
    data class Uploaded(val rommSaveId: Long?) : ForceSyncResult()
    data class Downloaded(val channelName: String?) : ForceSyncResult()
    data object SkippedByUser : ForceSyncResult()
    data class Error(val message: String) : ForceSyncResult()
}

enum class HardcoreResolutionChoice {
    KEEP_HARDCORE,
    DOWNGRADE_TO_CASUAL,
    KEEP_LOCAL
}

@Singleton
class SaveSyncRepository @Inject constructor(
    private val apiClient: SaveSyncApiClient,
    private val conflictResolver: SaveSyncConflictResolver,
    private val orchestrator: SaveSyncOrchestrator,
    private val entityManager: SaveSyncEntityManager,
    private val stateCacheManager: StateCacheManager,
    private val syncQueueManager: SyncQueueManager,
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val strategySelector: SaveSyncStrategySelector,
    private val negotiateInventory: NegotiateInventory,
    private val saveRecoveryGate: com.nendo.argosy.data.sync.SaveRecoveryGate,
    private val snapshotRouter: dagger.Lazy<com.nendo.argosy.data.sync.snapshot.SnapshotSyncRouter>
) {
    private val PRE_LAUNCH_TAG = "SaveSyncRepository"
    val syncQueueState: StateFlow<SyncQueueState> = entityManager.syncQueueState

    fun setApi(api: RomMApi?) = apiClient.setApi(api)

    fun getApi(): RomMApi? = apiClient.getApi()

    fun setDeviceId(id: String?) = apiClient.setDeviceId(id)

    fun getDeviceId(): String? = apiClient.getDeviceId()

    fun setCapabilities(caps: RomMCapabilities) = apiClient.setCapabilities(caps)

    fun getCapabilities(): RomMCapabilities = apiClient.getCapabilities()

    suspend fun resolveEmulatorForGame(game: com.nendo.argosy.data.local.entity.GameEntity): String? =
        apiClient.resolveEmulatorForGame(game)

    suspend fun deleteServerSaves(saveIds: List<Long>): Boolean =
        apiClient.deleteServerSaves(saveIds)

    fun clearCompletedOperations() = entityManager.clearCompletedOperations()

    fun observeNewSavesCount(): Flow<Int> = entityManager.observeNewSavesCount()

    fun observePendingCount(): Flow<Int> = entityManager.observePendingCount()

    fun observeSaveCountsByPlatform(): Flow<Map<Long, Int>> =
        entityManager.observeSaveCountsByPlatform()

    fun observeSaveRows(ownerUserId: Long?): Flow<List<SaveSyncEntity>> =
        saveSyncDao.observeAll(ownerUserId)

    fun observeSaveCountsByDevice(ownerUserId: Long?): Flow<List<SaveCountByDevice>> =
        saveSyncDao.observeSaveCountsByDevice(ownerUserId)

    suspend fun clearDirtyFlags(gameId: Long) = entityManager.clearDirtyFlags(gameId)

    suspend fun discoverSavePathChecked(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedSaveId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null
    ): com.nendo.argosy.data.sync.SaveLookup = apiClient.discoverSavePathChecked(
        emulatorId = emulatorId,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        romPath = romPath,
        cachedSaveId = cachedSaveId,
        coreName = coreName,
        emulatorPackage = emulatorPackage,
        gameId = gameId
    )

    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedSaveId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null
    ): String? = apiClient.discoverSavePath(
        emulatorId = emulatorId,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        romPath = romPath,
        cachedSaveId = cachedSaveId,
        coreName = coreName,
        emulatorPackage = emulatorPackage,
        gameId = gameId
    )

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?,
        coreName: String? = null,
        cachedSaveId: String? = null,
        gameId: Long? = null,
        folderShaped: Boolean? = null,
        emulatorPackage: String? = null
    ): String? = apiClient.constructSavePath(
        emulatorId, gameTitle, platformSlug, romPath, coreName, cachedSaveId, gameId, folderShaped, emulatorPackage
    )

    suspend fun predictFolderSavePath(
        emulatorId: String,
        platformSlug: String,
        romPath: String?,
        gameId: Long,
        gameTitle: String,
        cachedSaveId: String? = null,
        emulatorPackage: String? = null
    ): String? = apiClient.predictFolderSavePath(
        emulatorId, platformSlug, romPath, gameId, gameTitle, cachedSaveId, emulatorPackage
    )

    suspend fun resolveCoreForGame(gameId: Long): String? =
        apiClient.resolveCoreForGame(gameId)

    suspend fun rekeySaveSyncToLocalEmulators(): Int =
        apiClient.rekeySaveSyncToLocalEmulators()

    suspend fun getSyncStatus(gameId: Long, emulatorId: String): SaveSyncEntity? =
        entityManager.getSyncStatus(gameId, emulatorId)

    suspend fun checkForAllServerUpdates(): List<SaveSyncEntity> =
        apiClient.checkForAllServerUpdates()

    suspend fun checkForServerUpdates(platformId: Long): List<SaveSyncEntity> =
        apiClient.checkForServerUpdates(platformId)

    suspend fun checkSavesForGame(gameId: Long, rommId: Long): List<RomMSave> {
        val saves = apiClient.checkSavesForGame(gameId, rommId)
        val api = apiClient.getApi()
        if (api != null && saves.any { it.slot?.startsWith("state_") == true }) {
            stateCacheManager.migrateStatesFromSaves(rommId, api, saves)
        }
        return saves.filter { it.slot?.startsWith("state_") != true }
    }

    private val uploadMutexes = ConcurrentHashMap<Pair<Long, String?>, Mutex>()

    suspend fun uploadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        forceOverwrite: Boolean = false,
        isHardcore: Boolean = false,
        uploadedCacheId: Long? = null
    ): SaveSyncResult = uploadMutexes.computeIfAbsent(gameId to channelName) { Mutex() }.withLock {
        apiClient.uploadSave(gameId, emulatorId, channelName, forceOverwrite, isHardcore, uploadedCacheId)
    }

    suspend fun approveHardcoreDowngrade(gameId: Long, emulatorId: String, channelName: String?): SaveSyncResult =
        snapshotRouter.get().approveHardcoreDowngrade(gameId, emulatorId, channelName)
            ?: SaveSyncResult.NotConfigured

    suspend fun uploadCacheEntry(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String,
        cacheFile: File,
        contentHash: String?,
        overwrite: Boolean = false,
        uploadedCacheId: Long? = null,
        ownerApi: AccountApi? = null,
        approveHardcoreDowngrade: Boolean = false
    ): SaveSyncResult =
        apiClient.uploadCacheEntry(
            gameId, rommId, emulatorId, channelName, cacheFile, contentHash, overwrite, uploadedCacheId, ownerApi,
            approveHardcoreDowngrade
        )

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false,
        knownServerSaveId: Long? = null
    ): SaveSyncResult = apiClient.downloadSave(gameId, emulatorId, channelName, skipBackup, knownServerSaveId)

    suspend fun downloadToCache(serverSaveId: Long, gameId: Long, channelName: String?): Long? =
        apiClient.downloadToCache(serverSaveId, gameId, channelName)

    suspend fun downloadSaveAsChannel(
        gameId: Long,
        serverSaveId: Long,
        channelName: String,
        emulatorId: String?,
        skipDeviceId: Boolean = false
    ): Boolean = apiClient.downloadSaveAsChannel(gameId, serverSaveId, channelName, emulatorId, skipDeviceId)

    /**
     * Pulls one server save into the cache. [activate] true makes it the game's active save, as an
     * explicit restore intends; false files it as history only.
     */
    suspend fun downloadAndCacheSave(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?,
        activate: Boolean
    ): Boolean = apiClient.downloadAndCacheSave(serverSaveId, gameId, channelName, activate)

    suspend fun queueUpload(
        gameId: Long,
        emulatorId: String,
        localPath: String,
        channelName: String? = null
    ) = orchestrator.queueUpload(gameId, emulatorId, localPath, channelName)

    suspend fun scanAndQueueLocalChanges(secureSaves: Boolean): Int = orchestrator.scanAndQueueLocalChanges(secureSaves)


    suspend fun downloadPendingServerSaves(): Int = orchestrator.downloadPendingServerSaves()

    suspend fun forceSaveCheck(): SaveSyncOrchestrator.ForceSaveCheckResult = orchestrator.forceSaveCheck()

    suspend fun updateSyncEntity(
        gameId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?
    ) = entityManager.updateSyncEntity(gameId, emulatorId, localPath, localUpdatedAt)

    suspend fun createOrUpdateSyncEntity(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?,
        channelName: String? = null
    ): SaveSyncEntity = entityManager.createOrUpdateSyncEntity(gameId, rommId, emulatorId, localPath, localUpdatedAt, channelName)

    suspend fun markRestored(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String?,
        localPath: String,
        rommSaveId: Long?,
        serverTimestamp: Instant?,
        contentHash: String? = null
    ) = entityManager.markRestored(gameId, rommId, emulatorId, channelName, localPath, rommSaveId, serverTimestamp, contentHash)

    suspend fun preLaunchSyncForGame(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String?,
        secureSaves: Boolean
    ): PreLaunchSyncResult = withContext(Dispatchers.IO) {
        val syncChannel = snapshotRouter.get().launchChannel(gameId, channelName)
        val myDeviceId = apiClient.getDeviceId() ?: run {
            Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No deviceId (pre-4.7 server or sync disabled) | decision=NoConnection")
            return@withContext PreLaunchSyncResult.NoConnection
        }
        val effectiveChannel = SaveSyncApiClient.syncKeyOf(syncChannel)

        val ownerUserId = syncPreferencesRepository.getRommUserId()
        saveRecoveryGate.awaitSettled()
        val disk = orchestrator.checkDiskAgainstActive(gameId, emulatorId, syncChannel, secureSaves, ownerUserId)
        Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId channel=$effectiveChannel | disk=${disk::class.simpleName}")
        if (disk is SaveSyncOrchestrator.DiskCheck.Unreadable) {
            Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | save folder unreadable; skipping sync decision | decision=LocalIsNewer")
            return@withContext PreLaunchSyncResult.LocalIsNewer
        }

        val existing = saveSyncDao.getByGameEmulatorAndChannel(
            gameId,
            emulatorId,
            effectiveChannel,
            ownerUserId
        )
        if (disk is SaveSyncOrchestrator.DiskCheck.Failed) {
            val localPath = existing?.localSavePath
            if (localPath == null) {
                Logger.warn(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | active save could not be restored and no local path is known | decision=LocalIsNewer")
                return@withContext PreLaunchSyncResult.LocalIsNewer
            }
            Logger.warn(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | active save could not be restored; asking how to launch | decision=LocalModified(restoreFailed)")
            return@withContext PreLaunchSyncResult.LocalModified(
                localSavePath = localPath,
                serverTimestamp = Instant.now(),
                channelName = effectiveChannel,
                serverSaveId = existing.rommSaveId,
                restoreFailed = true
            )
        }
        if (existing?.userSelectedRestorePoint == true) {
            Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId channel=$effectiveChannel | userSelectedRestorePoint=true | decision=LocalIsNewer")
            return@withContext PreLaunchSyncResult.LocalIsNewer
        }

        snapshotRouter.get().preLaunch(gameId, emulatorId, syncChannel)?.let { decision ->
            Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | snapshot sync | decision=${decision::class.simpleName}")
            return@withContext decision
        }

        apiClient.flushPendingDeviceSync(gameId)

        val negotiator = strategySelector.current() as? NegotiatorSaveSyncStrategy
        if (negotiator != null) {
            return@withContext negotiatePreLaunch(negotiator, gameId, rommId, emulatorId, effectiveChannel, secureSaves, existing)
        }

        val serverSaves = try {
            apiClient.checkSavesForGame(gameId, rommId)
                .filterNot { SaveSyncApiClient.isStateShapedSave(it) }
        } catch (e: Exception) {
            Logger.warn(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | checkSavesForGame failed; decision=NoConnection", e)
            return@withContext PreLaunchSyncResult.NoConnection
        }

        val active = serverSaves
            .filter { save -> save.slot != null && SaveSyncApiClient.equalsNormalized(save.slot, effectiveChannel) }
            .maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
        val localDirty = SaveSlotClassifier.storedChannelsFor(effectiveChannel)
            .any { stored -> saveCacheDao.hasNeedingRemoteSync(gameId, stored) }

        if (active == null) {
            val decision = if (localDirty) PreLaunchSyncResult.LocalIsNewer else PreLaunchSyncResult.NoServerSave
            Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId channel=$effectiveChannel | no server save for slot | localDirty=$localDirty | decision=${decision::class.simpleName}")
            return@withContext decision
        }

        val ourSync = active.deviceSyncs?.firstOrNull { it.deviceId == myDeviceId }
        val serverHash = active.contentHash?.takeIf { apiClient.getCapabilities().trustsServerHash }
        val contentMatchesLastSync = serverHash != null &&
            existing?.lastUploadedHash != null &&
            serverHash == existing.lastUploadedHash
        val serverHasNewer = !contentMatchesLastSync && ourSync?.isCurrent != true

        val decision: PreLaunchSyncResult = when {
            !serverHasNewer -> PreLaunchSyncResult.LocalIsNewer
            !localDirty -> PreLaunchSyncResult.ServerIsNewer(
                serverTimestamp = SaveSyncApiClient.parseTimestamp(active.updatedAt),
                channelName = effectiveChannel,
                serverSaveId = active.id
            )
            else -> PreLaunchSyncResult.LocalModified(
                localSavePath = existing?.localSavePath ?: "",
                serverTimestamp = SaveSyncApiClient.parseTimestamp(active.updatedAt),
                channelName = effectiveChannel,
                serverSaveId = active.id
            )
        }

        Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId channel=$effectiveChannel | serverIsCurrent=${ourSync?.isCurrent == true} contentMatchesLastSync=$contentMatchesLastSync serverHasNewer=$serverHasNewer localDirty=$localDirty | decision=${decision::class.simpleName} serverSaveId=${active.id}")
        decision
    }

    private suspend fun negotiatePreLaunch(
        negotiator: NegotiatorSaveSyncStrategy,
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        slot: String,
        secureSaves: Boolean,
        existing: SaveSyncEntity?
    ): PreLaunchSyncResult {
        val inventory = negotiateInventory.build(secureSaves, gameId)
        val plan = negotiator.planForGame(inventory, rommId) ?: run {
            Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId slot=$slot | negotiate unavailable | decision=NoConnection")
            return PreLaunchSyncResult.NoConnection
        }
        val op = plan.operations.firstOrNull { SaveSyncApiClient.syncKeyOf(it.slot) == slot }
        val serverTimestamp = op?.serverUpdatedAt?.let { SaveSyncApiClient.parseTimestamp(it) } ?: Instant.now()
        val flaggedConflict = existing?.syncStatus == SaveSyncEntity.STATUS_CONFLICT
        val decision = when (op?.action) {
            ReconcileAction.DOWNLOAD -> if (flaggedConflict) {
                PreLaunchSyncResult.LocalModified(
                    localSavePath = existing?.localSavePath.orEmpty(),
                    serverTimestamp = serverTimestamp,
                    channelName = slot,
                    serverSaveId = op.saveId
                )
            } else {
                PreLaunchSyncResult.ServerIsNewer(serverTimestamp, slot, op.saveId)
            }
            ReconcileAction.CONFLICT -> PreLaunchSyncResult.LocalModified(
                localSavePath = existing?.localSavePath.orEmpty(),
                serverTimestamp = serverTimestamp,
                channelName = slot,
                serverSaveId = op.saveId
            )
            ReconcileAction.UPLOAD -> {
                existing?.localSavePath?.let { orchestrator.queueUpload(gameId, emulatorId, it, slot) }
                PreLaunchSyncResult.LocalIsNewer
            }
            ReconcileAction.NO_OP, null -> PreLaunchSyncResult.LocalIsNewer
        }
        Logger.debug(PRE_LAUNCH_TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId slot=$slot | negotiate session=${plan.sessionId} action=${op?.action} reason=${op?.reason} saveId=${op?.saveId} | decision=${decision::class.simpleName}")
        return decision
    }

    suspend fun markUserSelectedRestorePoint(gameId: Long, emulatorId: String, channelName: String?) =
        entityManager.markUserSelectedRestorePoint(gameId, emulatorId, channelName)

    suspend fun clearUserSelectedRestorePointForGame(gameId: Long) =
        entityManager.clearUserSelectedRestorePointForGame(gameId)

    suspend fun checkForConflict(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): ConflictInfo? = conflictResolver.checkForConflict(gameId, emulatorId, channelName)


    suspend fun analyzeChannel(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): SyncAnalysis = conflictResolver.analyzeChannel(gameId, emulatorId, channelName)

    suspend fun forceSyncChannel(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): ForceSyncResult = withContext(Dispatchers.IO) {
        when (val analysis = conflictResolver.analyzeChannel(gameId, emulatorId, channelName)) {
            SyncAnalysis.NoConnection -> ForceSyncResult.Error("Not connected to RomM")
            SyncAnalysis.NoLocalSave,
            SyncAnalysis.NoServerSave -> ForceSyncResult.Error("Nothing to sync")
            SyncAnalysis.InSync -> ForceSyncResult.AlreadyInSync
            is SyncAnalysis.LocalNewer -> uploadAndMap(gameId, emulatorId, channelName, forceOverwrite = true)
            is SyncAnalysis.ServerNewer -> downloadAndMap(gameId, emulatorId, analysis.channelName, analysis.serverSaveId)
            is SyncAnalysis.Conflict -> resolveConflictAndApply(analysis.info, emulatorId)
        }
    }

    private suspend fun uploadAndMap(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        forceOverwrite: Boolean
    ): ForceSyncResult = when (val result = apiClient.uploadSave(gameId, emulatorId, channelName, forceOverwrite)) {
        is SaveSyncResult.Success -> ForceSyncResult.Uploaded(result.rommSaveId)
        is SaveSyncResult.Conflict -> ForceSyncResult.Error("Server changed during sync - try again")
        is SaveSyncResult.Error -> ForceSyncResult.Error(result.message)
        is SaveSyncResult.NeedsHardcoreResolution -> ForceSyncResult.Error("Hardcore save requires resolution")
        SaveSyncResult.NoSaveFound -> ForceSyncResult.Error("No local save to upload")
        SaveSyncResult.NotConfigured -> ForceSyncResult.Error("Save sync not configured")
    }

    private suspend fun downloadAndMap(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        knownServerSaveId: Long? = null
    ): ForceSyncResult = when (val result = apiClient.downloadSave(gameId, emulatorId, channelName, knownServerSaveId = knownServerSaveId)) {
        is SaveSyncResult.Success -> ForceSyncResult.Downloaded(channelName)
        is SaveSyncResult.NeedsHardcoreResolution -> ForceSyncResult.Error("Hardcore save requires resolution")
        is SaveSyncResult.Error -> ForceSyncResult.Error(result.message)
        is SaveSyncResult.Conflict -> ForceSyncResult.Error("Conflict during download")
        SaveSyncResult.NoSaveFound -> ForceSyncResult.Error("Nothing to download")
        SaveSyncResult.NotConfigured -> ForceSyncResult.Error("Save sync not configured")
    }

    private suspend fun resolveConflictAndApply(
        info: ConflictInfo,
        emulatorId: String
    ): ForceSyncResult {
        syncQueueManager.addConflict(info)
        return when (syncQueueManager.awaitResolution(info.gameId)) {
            ConflictResolution.KEEP_LOCAL -> uploadAndMap(info.gameId, emulatorId, info.channelName, forceOverwrite = true)
            ConflictResolution.KEEP_SERVER -> downloadAndMap(info.gameId, emulatorId, info.channelName, info.serverSaveId)
            ConflictResolution.SKIP -> ForceSyncResult.SkippedByUser
        }
    }

    suspend fun resolveHardcoreConflict(
        resolution: SaveSyncResult.NeedsHardcoreResolution,
        choice: HardcoreResolutionChoice
    ): SaveSyncResult = conflictResolver.resolveHardcoreConflict(resolution, choice)

    suspend fun syncSavesForNewDownload(gameId: Long, rommId: Long, emulatorId: String) =
        orchestrator.syncSavesForNewDownload(gameId, rommId, emulatorId)

    suspend fun clearSaveAtPath(targetPath: String): Boolean =
        apiClient.clearSaveAtPath(targetPath)

    suspend fun clearSavesForTitle(
        targetPath: String,
        platformSlug: String,
        titleId: String?
    ): Boolean = apiClient.clearSavesForTitle(targetPath, platformSlug, titleId)

    suspend fun clearSavesBeforeRestore(
        targetPath: String,
        platformSlug: String,
        titleId: String?,
        archiveRoots: Set<String>?
    ): Boolean = apiClient.clearSavesBeforeRestore(targetPath, platformSlug, titleId, archiveRoots)

    suspend fun flushPendingDeviceSync(gameId: Long) =
        apiClient.flushPendingDeviceSync(gameId)

    suspend fun confirmOrQueueDeviceSync(gameId: Long, saveId: Long) =
        apiClient.confirmOrQueueDeviceSync(gameId, saveId)
}

