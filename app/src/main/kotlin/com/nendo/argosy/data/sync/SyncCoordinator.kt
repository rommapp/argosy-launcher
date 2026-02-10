package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.util.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val saveCacheDao: SaveCacheDao,
    private val gameDao: GameDao,
    private val romMRepository: Lazy<RomMRepository>,
    private val saveSyncRepository: Lazy<SaveSyncRepository>,
    private val stateCacheManager: Lazy<StateCacheManager>,
    private val syncQueueManager: SyncQueueManager
) {
    companion object {
        private const val TAG = "SyncCoordinator"
    }

    private val mutex = Mutex()

    sealed class ProcessResult {
        data object NotConnected : ProcessResult()
        data class Completed(val processed: Int, val failed: Int) : ProcessResult()
    }

    suspend fun processQueue(): ProcessResult = withContext(Dispatchers.IO) {
        val romM = romMRepository.get()
        if (romM.connectionState.value !is RomMRepository.ConnectionState.Connected) {
            Logger.debug(TAG, "processQueue: Not connected to RomM, skipping")
            return@withContext ProcessResult.NotConnected
        }

        mutex.withLock {
            var processed = 0
            var failed = 0

            // Process queued items by priority (lower priority value = higher importance)
            for (priority in listOf(SyncPriority.SAVE_FILE, SyncPriority.SAVE_STATE, SyncPriority.PROPERTY)) {
                val items = pendingSyncQueueDao.getPendingByPriorityTier(priority)
                Logger.debug(TAG, "processQueue: Processing ${items.size} items at priority $priority")

                for (item in items) {
                    if (romM.connectionState.value !is RomMRepository.ConnectionState.Connected) {
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

            // Process dirty save_cache entries (saves marked for remote sync)
            val dirtySaves = processDirtySaveCaches()
            processed += dirtySaves

            Logger.info(TAG, "processQueue: Completed | processed=$processed, failed=$failed")
            ProcessResult.Completed(processed, failed)
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
        val payload = SaveFilePayload.fromJson(item.payloadJson) ?: return false

        val result = saveSyncRepository.get().uploadSave(
            gameId = item.gameId,
            emulatorId = payload.emulatorId,
            forceOverwrite = false
        )

        return result is SaveSyncResult.Success
    }

    private suspend fun processSaveState(item: PendingSyncQueueEntity): Boolean {
        // State upload requires more context - for now, skip and let StateCacheManager handle it
        // This can be expanded when state sync is integrated
        Logger.debug(TAG, "processSaveState: State sync via queue not yet implemented")
        return true
    }

    private suspend fun processProperty(item: PendingSyncQueueEntity): Boolean {
        val payload = PropertyPayload.fromJson(item.payloadJson) ?: return false

        return romMRepository.get().updateRomUserProps(
            rommId = item.rommId,
            userRating = if (item.syncType == SyncType.RATING) payload.intValue else null,
            userDifficulty = if (item.syncType == SyncType.DIFFICULTY) payload.intValue else null,
            userStatus = if (item.syncType == SyncType.STATUS) payload.stringValue else null
        )
    }

    private suspend fun processFavorite(item: PendingSyncQueueEntity): Boolean {
        val payload = PropertyPayload.fromJson(item.payloadJson) ?: return false
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
        var synced = 0

        for (cache in dirtySaves) {
            val game = gameDao.getById(cache.gameId) ?: continue
            if (game.rommId == null) continue

            syncQueueManager.addOperation(SyncOperation(
                gameId = cache.gameId,
                gameName = game.title,
                channelName = cache.channelName,
                coverPath = game.coverPath,
                direction = SyncDirection.UPLOAD,
                status = com.nendo.argosy.data.sync.SyncStatus.PENDING
            ))
            syncQueueManager.updateOperation(cache.gameId) { it.copy(status = com.nendo.argosy.data.sync.SyncStatus.IN_PROGRESS) }

            val result = saveSyncRepository.get().uploadSave(
                gameId = cache.gameId,
                emulatorId = cache.emulatorId,
                channelName = cache.channelName,
                forceOverwrite = false
            )

            when (result) {
                is SaveSyncResult.Success -> {
                    saveCacheDao.markSynced(cache.id, Instant.now())
                    syncQueueManager.completeOperation(cache.gameId)
                    synced++
                    Logger.debug(TAG, "processDirtySaveCaches: Synced gameId=${cache.gameId}")
                }
                is SaveSyncResult.Error -> {
                    saveCacheDao.markSyncError(cache.id, result.message)
                    syncQueueManager.completeOperation(cache.gameId, result.message)
                    Logger.warn(TAG, "processDirtySaveCaches: Failed gameId=${cache.gameId} | ${result.message}")
                }
                else -> {
                    syncQueueManager.completeOperation(cache.gameId, "Skipped")
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped gameId=${cache.gameId} | result=$result")
                }
            }
        }

        return synced
    }

    // Queue operations for callers to add items
    suspend fun queueSaveUpload(gameId: Long, rommId: Long, emulatorId: String) {
        val payload = SaveFilePayload(emulatorId)
        // Remove any existing pending for same game+type
        pendingSyncQueueDao.deleteByGameAndType(gameId, SyncType.SAVE_FILE)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = payload.toJson()
            )
        )
    }

    suspend fun queueStateUpload(gameId: Long, rommId: Long, stateCacheId: Long, emulatorId: String) {
        val payload = SaveStatePayload(stateCacheId, emulatorId)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_STATE,
                priority = SyncPriority.SAVE_STATE,
                payloadJson = payload.toJson()
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
                payloadJson = payload.toJson()
            )
        )
    }

    suspend fun queueFavoriteChange(gameId: Long, rommId: Long, isFavorite: Boolean) {
        queuePropertyChange(gameId, rommId, SyncType.FAVORITE, intValue = if (isFavorite) 1 else 0)
    }
}

// Payload classes for JSON serialization
data class SaveFilePayload(val emulatorId: String) {
    fun toJson(): String = """{"emulatorId":"$emulatorId"}"""

    companion object {
        fun fromJson(json: String): SaveFilePayload? {
            return try {
                val emulatorId = json.substringAfter("\"emulatorId\":\"").substringBefore("\"")
                SaveFilePayload(emulatorId)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class SaveStatePayload(val stateCacheId: Long, val emulatorId: String) {
    fun toJson(): String = """{"stateCacheId":$stateCacheId,"emulatorId":"$emulatorId"}"""

    companion object {
        fun fromJson(json: String): SaveStatePayload? {
            return try {
                val stateCacheId = json.substringAfter("\"stateCacheId\":").substringBefore(",").toLong()
                val emulatorId = json.substringAfter("\"emulatorId\":\"").substringBefore("\"")
                SaveStatePayload(stateCacheId, emulatorId)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class PropertyPayload(val intValue: Int? = null, val stringValue: String? = null) {
    fun toJson(): String = """{"intValue":${intValue ?: "null"},"stringValue":${stringValue?.let { "\"$it\"" } ?: "null"}}"""

    companion object {
        fun fromJson(json: String): PropertyPayload? {
            return try {
                val intValueStr = json.substringAfter("\"intValue\":").substringBefore(",")
                val intValue = if (intValueStr == "null") null else intValueStr.toInt()
                val stringValueStr = json.substringAfter("\"stringValue\":").substringBefore("}")
                val stringValue = if (stringValueStr == "null") null else stringValueStr.trim('"')
                PropertyPayload(intValue, stringValue)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class AchievementPayload(
    val achievementRaId: Long,
    val forHardcoreMode: Boolean,
    val earnedAt: Long
) {
    fun toJson(): String = """{"achievementRaId":$achievementRaId,"forHardcoreMode":$forHardcoreMode,"earnedAt":$earnedAt}"""

    companion object {
        fun fromJson(json: String): AchievementPayload? {
            return try {
                val achievementRaId = json.substringAfter("\"achievementRaId\":").substringBefore(",").toLong()
                val forHardcoreMode = json.substringAfter("\"forHardcoreMode\":").substringBefore(",").toBoolean()
                val earnedAt = json.substringAfter("\"earnedAt\":").substringBefore("}").toLong()
                AchievementPayload(achievementRaId, forHardcoreMode, earnedAt)
            } catch (e: Exception) {
                null
            }
        }
    }
}
