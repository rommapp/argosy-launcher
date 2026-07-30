package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.SaveOwnershipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveOwnershipDao {

    @Query("SELECT * FROM save_ownership WHERE savePath = :savePath AND emulatorId = :emulatorId LIMIT 1")
    suspend fun get(savePath: String, emulatorId: String): SaveOwnershipEntity?

    @Query("SELECT * FROM save_ownership WHERE id = :id")
    suspend fun getById(id: Long): SaveOwnershipEntity?

    @Query("SELECT * FROM save_ownership WHERE ownerUserId = :ownerUserId")
    suspend fun getByOwner(ownerUserId: Long): List<SaveOwnershipEntity>

    @Query("SELECT * FROM save_ownership WHERE transitionState != :stableState")
    suspend fun getInTransition(stableState: String = SaveOwnershipEntity.STATE_STABLE): List<SaveOwnershipEntity>

    @Query("SELECT * FROM save_ownership WHERE needsSync = 1")
    fun observeNeedingSync(): Flow<List<SaveOwnershipEntity>>

    @Upsert
    suspend fun upsert(entity: SaveOwnershipEntity)

    @Query("DELETE FROM save_ownership WHERE savePath = :savePath AND emulatorId = :emulatorId")
    suspend fun delete(savePath: String, emulatorId: String)

    @Query("DELETE FROM save_ownership WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)
}
