package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.SyncQueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed class SaveSyncResult {
    data class Success(val rommSaveId: Long? = null, val serverTimestamp: Instant? = null) : SaveSyncResult()
    data class Conflict(
        val gameId: Long,
        val localTimestamp: Instant,
        val serverTimestamp: Instant
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

@Singleton
class SaveSyncRepository @Inject constructor(
    private val apiClient: SaveSyncApiClient,
    private val conflictResolver: SaveSyncConflictResolver,
    private val orchestrator: SaveSyncOrchestrator,
    private val entityManager: SaveSyncEntityManager
) {
    val syncQueueState: StateFlow<SyncQueueState> = entityManager.syncQueueState

    fun setApi(api: RomMApi?) = apiClient.setApi(api)

    fun getApi(): RomMApi? = apiClient.getApi()

    fun setDeviceId(id: String?) = apiClient.setDeviceId(id)

    fun getDeviceId(): String? = apiClient.getDeviceId()

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
        cachedTitleId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null
    ): String? = apiClient.discoverSavePath(
        emulatorId = emulatorId,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        romPath = romPath,
        cachedTitleId = cachedTitleId,
        coreName = coreName,
        emulatorPackage = emulatorPackage,
        gameId = gameId
    )

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?
    ): String? = apiClient.constructSavePath(emulatorId, gameTitle, platformSlug, romPath)

    suspend fun getSyncStatus(gameId: Long, emulatorId: String): SaveSyncEntity? =
        entityManager.getSyncStatus(gameId, emulatorId)

    suspend fun checkForAllServerUpdates(): List<SaveSyncEntity> =
        apiClient.checkForAllServerUpdates()

    suspend fun checkForServerUpdates(platformId: Long): List<SaveSyncEntity> =
        apiClient.checkForServerUpdates(platformId)

    suspend fun checkSavesForGame(gameId: Long, rommId: Long): List<RomMSave> =
        apiClient.checkSavesForGame(gameId, rommId)

    suspend fun uploadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        forceOverwrite: Boolean = false,
        isHardcore: Boolean = false
    ): SaveSyncResult = apiClient.uploadSave(gameId, emulatorId, channelName, forceOverwrite, isHardcore)

    suspend fun uploadCacheEntry(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String,
        cacheFile: File,
        contentHash: String?,
        overwrite: Boolean = false
    ): SaveSyncResult = apiClient.uploadCacheEntry(gameId, rommId, emulatorId, channelName, cacheFile, contentHash, overwrite)

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false
    ): SaveSyncResult = apiClient.downloadSave(gameId, emulatorId, channelName, skipBackup)

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

    suspend fun processPendingUploads(): Int = orchestrator.processPendingUploads()

    suspend fun downloadPendingServerSaves(): Int = orchestrator.downloadPendingServerSaves()

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

    suspend fun preLaunchSync(gameId: Long, rommId: Long, emulatorId: String): PreLaunchSyncResult =
        conflictResolver.preLaunchSync(gameId, rommId, emulatorId).toRepoResult()

    sealed class PreLaunchSyncResult {
        data object NoConnection : PreLaunchSyncResult()
        data object NoServerSave : PreLaunchSyncResult()
        data object LocalIsNewer : PreLaunchSyncResult()
        data class ServerIsNewer(val serverTimestamp: Instant, val channelName: String?) : PreLaunchSyncResult()
        data class LocalModified(
            val localSavePath: String,
            val serverTimestamp: Instant,
            val channelName: String?
        ) : PreLaunchSyncResult()
    }

    suspend fun checkForConflict(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): ConflictInfo? = conflictResolver.checkForConflict(gameId, emulatorId, channelName)

    enum class HardcoreResolutionChoice {
        KEEP_HARDCORE,
        DOWNGRADE_TO_CASUAL,
        KEEP_LOCAL
    }

    suspend fun resolveHardcoreConflict(
        resolution: SaveSyncResult.NeedsHardcoreResolution,
        choice: HardcoreResolutionChoice
    ): SaveSyncResult = conflictResolver.resolveHardcoreConflict(resolution, choice.toResolverChoice())

    suspend fun syncSavesForNewDownload(gameId: Long, rommId: Long, emulatorId: String) =
        orchestrator.syncSavesForNewDownload(gameId, rommId, emulatorId)

    suspend fun clearSaveAtPath(targetPath: String): Boolean =
        apiClient.clearSaveAtPath(targetPath)

    suspend fun flushPendingDeviceSync(gameId: Long) =
        apiClient.flushPendingDeviceSync(gameId)

    suspend fun confirmDeviceSynced(saveId: Long) =
        apiClient.confirmDeviceSynced(saveId)
}

private fun SaveSyncConflictResolver.PreLaunchSyncResult.toRepoResult(): SaveSyncRepository.PreLaunchSyncResult {
    return when (this) {
        is SaveSyncConflictResolver.PreLaunchSyncResult.NoConnection ->
            SaveSyncRepository.PreLaunchSyncResult.NoConnection
        is SaveSyncConflictResolver.PreLaunchSyncResult.NoServerSave ->
            SaveSyncRepository.PreLaunchSyncResult.NoServerSave
        is SaveSyncConflictResolver.PreLaunchSyncResult.LocalIsNewer ->
            SaveSyncRepository.PreLaunchSyncResult.LocalIsNewer
        is SaveSyncConflictResolver.PreLaunchSyncResult.ServerIsNewer ->
            SaveSyncRepository.PreLaunchSyncResult.ServerIsNewer(serverTimestamp, channelName)
        is SaveSyncConflictResolver.PreLaunchSyncResult.LocalModified ->
            SaveSyncRepository.PreLaunchSyncResult.LocalModified(localSavePath, serverTimestamp, channelName)
    }
}

private fun SaveSyncRepository.HardcoreResolutionChoice.toResolverChoice(): SaveSyncConflictResolver.HardcoreResolutionChoice {
    return when (this) {
        SaveSyncRepository.HardcoreResolutionChoice.KEEP_HARDCORE ->
            SaveSyncConflictResolver.HardcoreResolutionChoice.KEEP_HARDCORE
        SaveSyncRepository.HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL ->
            SaveSyncConflictResolver.HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
        SaveSyncRepository.HardcoreResolutionChoice.KEEP_LOCAL ->
            SaveSyncConflictResolver.HardcoreResolutionChoice.KEEP_LOCAL
    }
}
