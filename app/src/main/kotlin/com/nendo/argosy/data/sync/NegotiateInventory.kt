package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.strategy.LocalSaveState
import com.nendo.argosy.util.Logger
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NegotiateInventory @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val gameDao: GameDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val savePathResolver: SavePathResolver,
    private val fal: FileAccessLayer,
    private val liveSavePath: LiveSavePath
) {
    suspend fun build(secureSaves: Boolean, gameId: Long? = null): List<LocalSaveState> {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val rows = saveSyncDao.getAllWithLocalPath(ownerUserId)
            .filter { gameId == null || it.gameId == gameId }
        var fromCache = 0
        var fromDisk = 0
        val livePaths = liveSavePath.perGame()
        val result = rows.mapNotNull { row ->
            if (row.localSavePath == null) return@mapNotNull null
            val channel = row.channelName
            if (channel != null && STATE_SLOT.containsMatchIn(channel)) return@mapNotNull null
            val game = gameDao.getById(row.gameId) ?: return@mapNotNull null
            val romPath = game.localPath ?: return@mapNotNull null
            val path = livePaths.currentLocalSavePath(row, game) ?: return@mapNotNull null
            val slot = SaveSyncApiClient.syncKeyOf(channel)
            val fileName = SaveSyncApiClient.computeUploadFileName(
                localSavePath = path,
                channelName = row.channelName,
                romBaseName = File(romPath).nameWithoutExtension
            )
            val version = versionFor(row.gameId, slot, ownerUserId)
            if (version != null) {
                fromCache++
                stateFromVersion(row, version, fileName, slot)
            } else {
                val slotInPlay = SaveSyncApiClient.syncKeyOf(activeSaveRepository.getActiveChannel(row.gameId))
                if (slot != slotInPlay) return@mapNotNull null
                if (!fal.exists(path)) return@mapNotNull null
                fromDisk++
                stateFromDisk(row, path, fileName, slot, secureSaves)
            }
        }
        Logger.debug(TAG, "build: rows=${result.size} fromCache=$fromCache fromDisk=$fromDisk game=$gameId")
        return result
    }

    private suspend fun versionFor(gameId: Long, slot: String, ownerUserId: Long?): SaveCacheEntity? {
        val active = activeSaveRepository.getActiveRow(gameId)
        if (active != null && !active.isRollback && SaveSyncApiClient.syncKeyOf(active.channelName) == slot) return active
        return saveCacheDao.getMostRecentInChannel(gameId, ownerUserId, slot)?.takeIf { !it.isRollback }
    }

    private fun stateFromVersion(
        row: SaveSyncEntity,
        version: SaveCacheEntity,
        fileName: String,
        slot: String
    ): LocalSaveState {
        val transferredForms = if (row.syncStatus == SaveSyncEntity.STATUS_SYNCED) {
            setOfNotNull(row.lastUploadedHash, row.localContentHash)
        } else {
            emptySet()
        }
        val isTransferredSave = version.rommSaveId != null && version.rommSaveId == row.rommSaveId
        val unchangedSinceTransfer = isTransferredSave ||
            setOfNotNull(version.contentHash, version.identityHash).any { it in transferredForms }
        val reportedHash = if (unchangedSinceTransfer) row.lastUploadedHash ?: version.contentHash else version.contentHash
        val reportedTime = if (unchangedSinceTransfer) row.serverUpdatedAt ?: version.cachedAt else version.cachedAt
        return LocalSaveState(
            romId = row.rommId,
            fileName = fileName,
            slot = slot,
            emulator = row.emulatorId,
            contentHash = reportedHash,
            updatedAt = reportedTime.toString(),
            fileSizeBytes = version.saveSize
        )
    }

    private fun stateFromDisk(
        row: SaveSyncEntity,
        path: String,
        fileName: String,
        slot: String,
        secureSaves: Boolean
    ): LocalSaveState {
        val modified = if (!secureSaves && fal.isDirectory(path)) {
            savePathResolver.findNewestFileTime(path).takeIf { it > 0 } ?: fal.lastModified(path)
        } else {
            fal.lastModified(path)
        }
        return LocalSaveState(
            romId = row.rommId,
            fileName = fileName,
            slot = slot,
            emulator = row.emulatorId,
            contentHash = row.lastUploadedHash,
            updatedAt = Instant.ofEpochMilli(modified).toString(),
            fileSizeBytes = fal.length(path)
        )
    }

    private companion object {
        const val TAG = "NegotiateInventory"
        val STATE_SLOT = Regex("""^state_""", RegexOption.IGNORE_CASE)
    }
}
