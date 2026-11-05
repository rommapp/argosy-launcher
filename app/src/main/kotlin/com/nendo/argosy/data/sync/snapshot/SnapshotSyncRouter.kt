package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.util.Logger
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands every save to [SnapshotSyncEngine] when the server speaks the snapshot API, translating
 * the outcome into what the save sync facade returns. Each call answers null when the server
 * doesn't, so the legacy endpoints take the save.
 */
@Singleton
class SnapshotSyncRouter @Inject constructor(
    private val engine: SnapshotSyncEngine,
    private val activeSaveRepository: ActiveSaveRepository
) {
    suspend fun handles(gameId: Long): Boolean = engine.isEligible(gameId)

    /**
     * The channel a play session's saves belong to. Older servers keep hardcore saves outside any
     * channel; on a snapshot server hardcore is a property of the snapshot, so the channel stays.
     */
    suspend fun sessionChannel(gameId: Long, isHardcore: Boolean, activeChannel: String?): String? =
        if (isHardcore && !handles(gameId)) null else activeChannel

    /**
     * The channel a launch syncs: the one asked for, else on a snapshot server the game's active
     * channel, since every channel syncs there. Older servers keep syncing autosave at launch.
     */
    suspend fun launchChannel(gameId: Long, requested: String?): String? =
        if (requested != null || !engine.isEligible(gameId)) requested
        else activeSaveRepository.getActiveChannel(gameId)

    suspend fun preLaunch(gameId: Long, emulatorId: String, channelName: String?): PreLaunchSyncResult? {
        if (!engine.isEligible(gameId)) return null
        return when (val result = engine.sync(gameId, emulatorId, channelName)) {
            SnapshotSyncResult.NotEligible -> null
            SnapshotSyncResult.NoConnection -> PreLaunchSyncResult.NoConnection
            is SnapshotSyncResult.Conflict -> PreLaunchSyncResult.LocalModified(
                localSavePath = result.localSavePath.orEmpty(),
                serverTimestamp = timestampOf(result),
                channelName = channelName,
                snapshotConflict = true
            )
            is SnapshotSyncResult.Failed -> {
                Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=$gameId | pre-launch sync failed, launching the local save | ${result.reason}")
                PreLaunchSyncResult.LocalIsNewer
            }
            SnapshotSyncResult.UpToDate, is SnapshotSyncResult.Pushed, is SnapshotSyncResult.Applied,
            is SnapshotSyncResult.Branched, is SnapshotSyncResult.HardcoreDowngrade -> PreLaunchSyncResult.LocalIsNewer
        }
    }

    suspend fun upload(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        keepLocal: Boolean,
        isHardcore: Boolean
    ): SaveSyncResult? {
        if (!engine.isEligible(gameId)) return null
        val result = if (keepLocal) {
            engine.keepLocal(gameId, emulatorId, channelName, isHardcore)
        } else {
            engine.sync(gameId, emulatorId, channelName, isHardcore)
        }
        return toSaveSyncResult(gameId, result)
    }

    suspend fun approveHardcoreDowngrade(gameId: Long, emulatorId: String, channelName: String?): SaveSyncResult? {
        if (!engine.isEligible(gameId)) return null
        return toSaveSyncResult(gameId, engine.keepLocal(gameId, emulatorId, channelName, approveHardcoreDowngrade = true))
    }

    suspend fun uploadCached(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        cacheFile: File,
        onTopOfCurrent: Boolean,
        cacheId: Long?,
        approveHardcoreDowngrade: Boolean = false
    ): SaveSyncResult? {
        if (!engine.isEligible(gameId)) return null
        val pushed = engine.pushCached(
            gameId, emulatorId, channelName, cacheFile, onTopOfCurrent, cacheId,
            approveHardcoreDowngrade = approveHardcoreDowngrade
        )
        return toSaveSyncResult(gameId, pushed)
    }

    suspend fun download(gameId: Long, emulatorId: String, channelName: String?): SaveSyncResult? {
        if (!engine.isEligible(gameId)) return null
        return toSaveSyncResult(gameId, engine.keepServer(gameId, emulatorId, channelName))
    }

    private fun toSaveSyncResult(gameId: Long, result: SnapshotSyncResult): SaveSyncResult? = when (result) {
        SnapshotSyncResult.NotEligible -> null
        SnapshotSyncResult.NoConnection -> SaveSyncResult.NotConfigured
        SnapshotSyncResult.UpToDate -> SaveSyncResult.Success(noOp = true)
        is SnapshotSyncResult.Pushed -> SaveSyncResult.Success()
        is SnapshotSyncResult.Branched -> SaveSyncResult.Success()
        is SnapshotSyncResult.Applied -> SaveSyncResult.Success()
        is SnapshotSyncResult.Conflict -> SaveSyncResult.Conflict(
            gameId = gameId,
            localTimestamp = Instant.now(),
            serverTimestamp = timestampOf(result),
            serverDeviceName = result.current?.device?.takeIf { it.isOwn }?.name
        )
        is SnapshotSyncResult.HardcoreDowngrade -> SaveSyncResult.Conflict(
            gameId = gameId,
            localTimestamp = Instant.now(),
            serverTimestamp = Instant.now(),
            isHardcoreDowngrade = true
        )
        is SnapshotSyncResult.Failed -> SaveSyncResult.Error(result.reason)
    }

    private fun timestampOf(result: SnapshotSyncResult.Conflict): Instant =
        result.current?.createdAt?.let { runCatching { SaveSyncApiClient.parseTimestamp(it) }.getOrNull() } ?: Instant.now()

    private companion object {
        const val TAG = "SnapshotSyncRouter"
    }
}
