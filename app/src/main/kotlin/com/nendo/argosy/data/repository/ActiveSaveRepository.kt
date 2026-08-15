package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveChannelDao
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
 * moves carries the pointer with it and needs no second write.
 *
 * A slot with nothing saved in it yet has no such row, so the slot registry carries which slot is
 * selected in that case. Reads prefer the cache row, because a slot holding saves knows its own
 * restore point; the registry answers only when there is no save to ask.
 */
@Singleton
class ActiveSaveRepository @Inject constructor(
    private val saveCacheDao: SaveCacheDao,
    private val saveChannelDao: SaveChannelDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend fun activeOwnerId(): Long? = syncPreferencesRepository.getRommUserId()

    suspend fun getActiveRow(gameId: Long): SaveCacheEntity? =
        saveCacheDao.getActive(gameId, activeOwnerId())

    suspend fun getActiveChannel(gameId: Long): String? =
        getActiveRow(gameId)?.channelName
            ?: saveChannelDao.getActiveChannel(gameId, activeOwnerId())

    suspend fun getActiveTimestamp(gameId: Long): Long? =
        getActiveRow(gameId)?.cachedAt?.toEpochMilli()

    suspend fun isActiveSaveApplied(gameId: Long): Boolean =
        saveCacheDao.hasActiveSaveApplied(gameId, activeOwnerId())

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
        saveChannelDao.setActive(gameId, channelName, ownerUserId)
        val cacheId = saveCacheDao.getNewestIdInChannelForOwner(gameId, ownerUserId, channelName)
        if (cacheId == null) {
            saveCacheDao.clearActive(gameId, ownerUserId)
            return false
        }
        saveCacheDao.setActiveRow(gameId, ownerUserId, cacheId)
        return true
    }

    /**
     * Records a slot that holds nothing yet and points this game at it.
     *
     * A slot used to be inferred from the saves in it, so creating one and not yet saving into it
     * left nothing behind: the slot vanished from the list and the next save went to whichever slot
     * was active before. Registering it is what makes the empty slot a real destination.
     */
    suspend fun createChannel(gameId: Long, channelName: String) {
        val ownerUserId = activeOwnerId()
        saveChannelDao.registerAndActivate(gameId, channelName, ownerUserId)
        saveCacheDao.clearActive(gameId, ownerUserId)
    }

    /**
     * Every slot this device knows of for a game, including ones nothing has been saved into.
     */
    suspend fun registeredChannels(gameId: Long): List<String> =
        saveChannelDao.getForGame(gameId, activeOwnerId()).map { it.channelName }

    suspend fun forgetChannel(gameId: Long, channelName: String) {
        saveChannelDao.delete(gameId, channelName, activeOwnerId())
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
