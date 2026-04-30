package com.nendo.argosy.data.repository

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.ALauncherDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseAdminRepository @Inject constructor(
    private val database: ALauncherDatabase,
    private val imageCacheManager: ImageCacheManager
) {
    suspend fun purgeAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        imageCacheManager.clearCache()
    }
}
