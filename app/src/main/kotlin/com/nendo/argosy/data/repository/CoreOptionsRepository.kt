package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.CoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.GameCoreOptionOverrideDao
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import com.nendo.argosy.data.local.entity.GameCoreOptionOverrideEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings UI facade over the global [CoreOptionOverrideDao] and the per-game
 * [GameCoreOptionOverrideDao] for libretro core option overrides.
 */
@Singleton
class CoreOptionsRepository @Inject constructor(
    private val coreOptionOverrideDao: CoreOptionOverrideDao,
    private val gameCoreOptionOverrideDao: GameCoreOptionOverrideDao
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

    suspend fun getOverridesForGame(gameId: Long, coreId: String): List<GameCoreOptionOverrideEntity> =
        gameCoreOptionOverrideDao.getForGame(gameId, coreId)

    fun observeOverridesForGame(gameId: Long, coreId: String): Flow<List<GameCoreOptionOverrideEntity>> =
        gameCoreOptionOverrideDao.observeForGame(gameId, coreId)

    suspend fun countForGame(gameId: Long, coreId: String): Int =
        gameCoreOptionOverrideDao.countForGame(gameId, coreId)

    suspend fun upsertForGame(override: GameCoreOptionOverrideEntity) =
        gameCoreOptionOverrideDao.upsert(override)

    suspend fun deleteForGame(gameId: Long, coreId: String, optionKey: String) =
        gameCoreOptionOverrideDao.delete(gameId, coreId, optionKey)

    suspend fun deleteAllForGame(gameId: Long, coreId: String) =
        gameCoreOptionOverrideDao.deleteAllForGame(gameId, coreId)
}
