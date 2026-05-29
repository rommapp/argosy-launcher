package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncQueueState
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
        val serverContentHash: String? = null
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
        val serverSaveId: Long? = null
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
    private val syncQueueManager: SyncQueueManager
) {
    val syncQueueState: StateFlow<SyncQueueState> = entityManager.syncQueueState

    fun setApi(api: RomMApi?) = apiClient.setApi(api)

    fun getApi(): RomMApi? = apiClient.getApi()

    fun setDeviceId(id: String?) = apiClient.setDeviceId(id)

    fun getDeviceId(): String? = apiClient.getDeviceId()

    fun setCapabilities(caps: RomMCapabilities) = apiClient.setCapabilities(caps)

    fun getCapabilities(): RomMCapabilities = apiClient.getCapabilities()

    suspend fun resolveEmulatorForGame(game: com.nendo.argosy.data.local.entity.GameEntity): String? =
        apiClient.resolveEmulatorForGame(game)

    fun setSessionOnOlderSave(gameId: Long, isOlder: Boolean) =
        apiClient.setSessionOnOlderSave(gameId, isOlder)

    fun clearSessionOnOlderSave(gameId: Long) =
        apiClient.clearSessionOnOlderSave(gameId)

    fun isSessionOnOlderSave(gameId: Long): Boolean =
        apiClient.isSessionOnOlderSave(gameId)

    suspend fun deleteServerSaves(saveIds: List<Long>): Boolean =
        apiClient.deleteServerSaves(saveIds)

    fun clearCompletedOperations() = entityManager.clearCompletedOperations()

    fun observeNewSavesCount(): Flow<Int> = entityManager.observeNewSavesCount()

    fun observePendingCount(): Flow<Int> = entityManager.observePendingCount()

    suspend fun clearDirtyFlags(gameId: Long) = entityManager.clearDirtyFlags(gameId)

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
        cachedSaveId: String? = null
    ): String? = apiClient.constructSavePath(emulatorId, gameTitle, platformSlug, romPath, coreName, cachedSaveId)

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

    suspend fun uploadCacheEntry(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String,
        cacheFile: File,
        contentHash: String?,
        overwrite: Boolean = false,
        uploadedCacheId: Long? = null
    ): SaveSyncResult = apiClient.uploadCacheEntry(gameId, rommId, emulatorId, channelName, cacheFile, contentHash, overwrite, uploadedCacheId)

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false,
        knownServerSaveId: Long? = null
    ): SaveSyncResult = apiClient.downloadSave(gameId, emulatorId, channelName, skipBackup, knownServerSaveId)

    suspend fun downloadSaveById(
        serverSaveId: Long,
        targetPath: String,
        emulatorId: String,
        emulatorPackage: String? = null,
        gameId: Long? = null,
        romPath: String? = null
    ): Boolean = apiClient.downloadSaveById(serverSaveId, targetPath, emulatorId, emulatorPackage, gameId, romPath)

    suspend fun downloadSaveAsChannel(
        gameId: Long,
        serverSaveId: Long,
        channelName: String,
        emulatorId: String?,
        skipDeviceId: Boolean = false
    ): Boolean = apiClient.downloadSaveAsChannel(gameId, serverSaveId, channelName, emulatorId, skipDeviceId)

    suspend fun downloadAndCacheSave(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?
    ): Boolean = apiClient.downloadAndCacheSave(serverSaveId, gameId, channelName)

    suspend fun queueUpload(gameId: Long, emulatorId: String, localPath: String) =
        orchestrator.queueUpload(gameId, emulatorId, localPath)

    suspend fun scanAndQueueLocalChanges(): Int = orchestrator.scanAndQueueLocalChanges()


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
        contentHash: String?
    ) = entityManager.markRestored(gameId, rommId, emulatorId, channelName, localPath, rommSaveId, serverTimestamp, contentHash)

    suspend fun preLaunchSync(gameId: Long, rommId: Long, emulatorId: String): PreLaunchSyncResult =
        conflictResolver.preLaunchSync(gameId, rommId, emulatorId)

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
        is SaveSyncResult.Conflict -> ForceSyncResult.Error("Server changed during sync — try again")
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

    suspend fun flushPendingDeviceSync(gameId: Long) =
        apiClient.flushPendingDeviceSync(gameId)

    suspend fun confirmDeviceSynced(saveId: Long) =
        apiClient.confirmDeviceSynced(saveId)
}

