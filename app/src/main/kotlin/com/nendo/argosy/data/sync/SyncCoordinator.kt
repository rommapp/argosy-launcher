package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncStatus as DbSyncStatus
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
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
    private val saveCacheManager: Lazy<SaveCacheManager>,
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
        if (romM.connectionState.value !is ConnectionState.Connected) {
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
            channelName = payload.channelName,
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

            val cacheFile = saveCacheManager.get().getCacheFile(cache)
            if (!cacheFile.exists()) {
                Logger.warn(TAG, "processDirtySaveCaches: Cache file missing for id=${cache.id}, path=${cache.cachePath}")
                saveCacheDao.markSyncError(cache.id, "Cache file missing")
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
                        isHashConflict = false
                    ))
                    Logger.warn(TAG, "processDirtySaveCaches: Conflict for channel cache id=${cache.id} gameId=${cache.gameId} channel=${cache.channelName} | cleared dirty flag, awaiting resolution")
                }
                is SaveSyncResult.Error -> {
                    saveCacheDao.markSyncError(cache.id, result.message)
                    syncQueueManager.completeOperation(cache.gameId, result.message)
                    Logger.warn(TAG, "processDirtySaveCaches: Failed channel cache id=${cache.id} gameId=${cache.gameId} | ${result.message}")
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

            val resolution = resolutions[cache.gameId]

            if (resolution == ConflictResolution.SKIP) {
                saveCacheDao.clearAllDirtyFlags(cache.gameId)
                Logger.debug(TAG, "processDirtySaveCaches: Skipped gameId=${cache.gameId}, cleared dirty flags")
                continue
            }
            if (resolution == ConflictResolution.KEEP_SERVER) {
                val downloadResult = saveSyncRepository.get().downloadSave(
                    gameId = cache.gameId,
                    emulatorId = cache.emulatorId,
                    channelName = null
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
                else -> {
                    syncQueueManager.completeOperation(cache.gameId, "Skipped")
                    Logger.debug(TAG, "processDirtySaveCaches: Skipped gameId=${cache.gameId} | result=$result")
                }
            }
        }

        return synced
    }

    // Queue operations for callers to add items
    suspend fun queueSaveUpload(gameId: Long, rommId: Long, emulatorId: String, channelName: String? = null) {
        val payload = SaveFilePayload(emulatorId, channelName)
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
data class SaveFilePayload(val emulatorId: String, val channelName: String? = null) {
    fun toJson(): String {
        val channel = if (channelName != null) ""","channelName":"$channelName"""" else ""
        return """{"emulatorId":"$emulatorId"$channel}"""
    }

    companion object {
        fun fromJson(json: String): SaveFilePayload? {
            return try {
                val emulatorId = json.substringAfter("\"emulatorId\":\"").substringBefore("\"")
                val channelName = if (json.contains("\"channelName\":"))
                    json.substringAfter("\"channelName\":\"").substringBefore("\"")
                else null
                SaveFilePayload(emulatorId, channelName)
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
