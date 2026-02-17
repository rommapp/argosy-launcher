package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformRepository @Inject constructor(
    private val platformDao: PlatformDao
) {
    fun observeVisiblePlatforms(): Flow<List<PlatformEntity>> =
        platformDao.observeVisiblePlatforms()

    fun observeAllPlatforms(): Flow<List<PlatformEntity>> =
        platformDao.observeAllPlatforms()

    fun observePlatformsWithGames(): Flow<List<PlatformEntity>> =
        platformDao.observePlatformsWithGames()

    fun observeConfigurablePlatforms(): Flow<List<PlatformEntity>> =
        platformDao.observeConfigurablePlatforms()

    suspend fun getById(id: Long): PlatformEntity? =
        platformDao.getById(id)

    suspend fun getPlatformsWithGames(): List<PlatformEntity> =
        platformDao.getPlatformsWithGames()

    suspend fun getAllPlatforms(): List<PlatformEntity> =
        platformDao.getAllPlatforms()

    suspend fun getAllPlatformsOrdered(): List<PlatformEntity> =
        platformDao.getAllPlatformsOrdered()

    suspend fun getSyncEnabledPlatforms(): List<PlatformEntity> =
        platformDao.getSyncEnabledPlatforms()

    suspend fun getEnabledPlatformCount(): Int =
        platformDao.getEnabledPlatformCount()

    suspend fun getTotalPlatformCount(): Int =
        platformDao.getTotalPlatformCount()

    suspend fun insert(platform: PlatformEntity) =
        platformDao.insert(platform)

    suspend fun updateSyncEnabled(platformId: Long, enabled: Boolean) =
        platformDao.updateSyncEnabled(platformId, enabled)

    suspend fun updateCustomRomPath(platformId: Long, path: String?) =
        platformDao.updateCustomRomPath(platformId, path)
}
