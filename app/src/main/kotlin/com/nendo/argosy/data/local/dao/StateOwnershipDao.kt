package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.StateOwnershipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StateOwnershipDao {

    @Query("SELECT * FROM state_ownership WHERE statePath = :statePath AND emulatorId = :emulatorId LIMIT 1")
    suspend fun get(statePath: String, emulatorId: String): StateOwnershipEntity?

    @Query("SELECT * FROM state_ownership WHERE id = :id")
    suspend fun getById(id: Long): StateOwnershipEntity?

    @Query("SELECT * FROM state_ownership WHERE ownerUserId = :ownerUserId")
    suspend fun getByOwner(ownerUserId: Long): List<StateOwnershipEntity>

    @Query("SELECT * FROM state_ownership WHERE gameId = :gameId")
    suspend fun getByGame(gameId: Long): List<StateOwnershipEntity>

    @Query("SELECT * FROM state_ownership WHERE transitionState != :stableState")
    suspend fun getInTransition(stableState: String = StateOwnershipEntity.STATE_STABLE): List<StateOwnershipEntity>

    @Query("SELECT * FROM state_ownership WHERE needsSync = 1")
    suspend fun getNeedingSync(): List<StateOwnershipEntity>

    @Query("SELECT * FROM state_ownership WHERE needsSync = 1")
    fun observeNeedingSync(): Flow<List<StateOwnershipEntity>>

    @Upsert
    suspend fun upsert(entity: StateOwnershipEntity)

    @Query("UPDATE state_ownership SET transitionState = :state WHERE id = :id")
    suspend fun setTransitionState(id: Long, state: String)

    @Query("DELETE FROM state_ownership WHERE statePath = :statePath AND emulatorId = :emulatorId")
    suspend fun delete(statePath: String, emulatorId: String)

    @Query("DELETE FROM state_ownership WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)
}
