package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SaveCacheCounts(
    val saveCacheCount: Int,
    val stateCacheCount: Int,
    val pathCacheCount: Int
)

data class PendingSyncCounts(
    val pendingUploads: Int,
    val pendingDownloads: Int
) {
    val total: Int get() = pendingUploads + pendingDownloads
}

@Singleton
class SaveCacheRepository @Inject constructor(
    private val saveCacheDao: SaveCacheDao,
    private val stateCacheDao: StateCacheDao,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao
) {
    suspend fun getCounts(): SaveCacheCounts = withContext(Dispatchers.IO) {
        SaveCacheCounts(
            saveCacheCount = saveCacheDao.count(),
            stateCacheCount = stateCacheDao.count(),
            pathCacheCount = saveSyncDao.countWithPaths()
        )
    }

    suspend fun getPendingSyncCounts(): PendingSyncCounts = withContext(Dispatchers.IO) {
        PendingSyncCounts(
            pendingUploads = saveCacheDao.countNeedingRemoteSync(),
            pendingDownloads = saveSyncDao.countByStatus(SaveSyncEntity.STATUS_SERVER_NEWER)
        )
    }

    suspend fun resetSaveCache() = withContext(Dispatchers.IO) {
        pendingSyncQueueDao.deleteAll()
        stateCacheDao.deleteAll()
        saveCacheDao.deleteAll()
    }

    suspend fun clearPathCache() = withContext(Dispatchers.IO) {
        saveSyncDao.clearAllPaths()
    }
}
