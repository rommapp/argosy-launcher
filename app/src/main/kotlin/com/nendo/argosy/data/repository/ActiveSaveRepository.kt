package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The save a game resumes from, for the signed-in RomM account.
 *
 * The target is a `save_cache` row rather than a column on `games`: the channel is that row's
 * `channelName` and the restore point is its `cachedAt`, so a row whose timestamp the server
 * moves carries the pointer with it and needs no second write. A row with no cache entry cannot
 * be the target, which is why selecting an empty channel leaves the game with no active save.
 */
@Singleton
class ActiveSaveRepository @Inject constructor(
    private val saveCacheDao: SaveCacheDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    private suspend fun activeOwnerId(): Long? = syncPreferencesRepository.getRommUserId()

    suspend fun getActiveRow(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getActive(gameId, activeOwnerId())

    suspend fun getActiveChannel(gameId: Long): String? = getActiveRow(gameId)?.channelName

    suspend fun getActiveTimestamp(gameId: Long): Long? =
        getActiveRow(gameId)?.cachedAt?.toEpochMilli()

    suspend fun isActiveSaveApplied(gameId: Long): Boolean =
        saveCacheDao.hasActiveSaveApplied(gameId)

    suspend fun getPendingDeviceSyncSaveId(gameId: Long): Long? =
        getActiveRow(gameId)?.pendingDeviceSyncSaveId

    /**
     * Re-emits whenever the active row changes or its own contents change, which covers both a
     * different save becoming active and the active save's timestamp being rewritten by a sync.
     */
    fun observeActiveRow(gameId: Long): Flow<SaveCacheEntity?> = flow {
        val ownerUserId = activeOwnerId()
        emitAll(saveCacheDao.observeActive(gameId, ownerUserId).map { it.firstOrNull() })
    }

    /**
     * False when [cacheId] belongs to another owner, in which case nothing is activated.
     */
    suspend fun activateCache(gameId: Long, cacheId: Long): Boolean =
        saveCacheDao.setActiveRow(gameId, activeOwnerId(), cacheId)

    suspend fun activateChannel(gameId: Long, channelName: String?): Boolean {
        val ownerUserId = activeOwnerId()
        val cacheId = saveCacheDao.getNewestIdInChannelForOwner(gameId, ownerUserId, channelName)
        if (cacheId == null) {
            saveCacheDao.clearActive(gameId, ownerUserId)
            return false
        }
        saveCacheDao.setActiveRow(gameId, ownerUserId, cacheId)
        return true
    }

    suspend fun activateTimestamp(gameId: Long, timestamp: Long): Boolean {
        val ownerUserId = activeOwnerId()
        val cacheId = saveCacheDao.getIdAtTimestampForOwner(gameId, ownerUserId, timestamp)
            ?: return false
        saveCacheDao.setActiveRow(gameId, ownerUserId, cacheId)
        return true
    }

    suspend fun clearActive(gameId: Long) {
        saveCacheDao.clearActive(gameId, activeOwnerId())
    }

    suspend fun setActiveSaveApplied(gameId: Long, applied: Boolean) {
        saveCacheDao.setActiveSaveApplied(gameId, activeOwnerId(), applied)
    }

    /**
     * Boot-time reset of the explicit-restore latch for every account: it says a save was placed
     * and not yet played, which cannot survive a process restart.
     */
    suspend fun resetAllActiveSaveApplied() = saveCacheDao.resetAllActiveSaveApplied()

    suspend fun setPendingDeviceSyncSaveId(gameId: Long, saveId: Long?) {
        saveCacheDao.setPendingDeviceSyncSaveId(gameId, activeOwnerId(), saveId)
    }
}
