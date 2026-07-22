package com.nendo.argosy.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateTombstoneDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val database: ALauncherDatabase,
    private val saveCacheDao: SaveCacheDao,
    private val stateCacheDao: StateCacheDao,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val stateTombstoneDao: StateTombstoneDao,
    private val pendingConflictDao: PendingConflictDao,
    private val attributionRepository: StorageAttributionRepository
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

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

    /** Deletes all cached save/state rows AND files; returns false when blocked by an active session. */
    suspend fun resetSaveCache(): Boolean = withContext(Dispatchers.IO) {
        if (sessionStateStore.hasActiveSession()) return@withContext false
        database.withTransaction {
            pendingSyncQueueDao.deleteAll()
            stateCacheDao.deleteAll()
            saveCacheDao.deleteAll()
            stateTombstoneDao.deleteAll()
            pendingConflictDao.deleteAll()
        }
        val saveCacheDir = AppPaths.saveCacheDir(context.filesDir)
        val stateCacheDir = AppPaths.stateCacheDir(context.filesDir)
        saveCacheDir.deleteRecursively()
        stateCacheDir.deleteRecursively()
        saveCacheDir.mkdirs()
        stateCacheDir.mkdirs()
        attributionRepository.markDirty(StorageCategory.SAVE_STATE_CACHE)
        true
    }

    suspend fun clearPathCache() = withContext(Dispatchers.IO) {
        saveSyncDao.clearAllPaths()
    }
}
