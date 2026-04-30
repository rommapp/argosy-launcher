package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.CoreOptionOverrideDao
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings UI facade over [CoreOptionOverrideDao] for libretro core option
 * overrides.
 */
@Singleton
class CoreOptionsRepository @Inject constructor(
    private val coreOptionOverrideDao: CoreOptionOverrideDao
) {
    suspend fun getOverridesForCore(coreId: String): List<CoreOptionOverrideEntity> =
        coreOptionOverrideDao.getOverridesForCore(coreId)

    fun observeOverridesForCore(coreId: String): Flow<List<CoreOptionOverrideEntity>> =
        coreOptionOverrideDao.observeOverridesForCore(coreId)

    suspend fun upsert(override: CoreOptionOverrideEntity) =
        coreOptionOverrideDao.upsert(override)

    suspend fun delete(coreId: String, optionKey: String) =
        coreOptionOverrideDao.delete(coreId, optionKey)

    suspend fun deleteAllForCore(coreId: String) =
        coreOptionOverrideDao.deleteAllForCore(coreId)
}
