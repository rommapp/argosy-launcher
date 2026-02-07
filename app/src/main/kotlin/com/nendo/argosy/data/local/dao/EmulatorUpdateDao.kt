package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmulatorUpdateDao {

    @Query("SELECT * FROM emulator_updates")
    fun observeAll(): Flow<List<EmulatorUpdateEntity>>

    @Query("SELECT * FROM emulator_updates WHERE hasUpdate = 1")
    fun observeAvailableUpdates(): Flow<List<EmulatorUpdateEntity>>

    @Query("SELECT * FROM emulator_updates WHERE emulatorId = :emulatorId")
    suspend fun getByEmulatorId(emulatorId: String): EmulatorUpdateEntity?

    @Query("SELECT * FROM emulator_updates WHERE hasUpdate = 1")
    suspend fun getAvailableUpdates(): List<EmulatorUpdateEntity>

    @Query("SELECT COUNT(*) FROM emulator_updates WHERE hasUpdate = 1")
    fun observeUpdateCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmulatorUpdateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EmulatorUpdateEntity>)

    @Query("UPDATE emulator_updates SET hasUpdate = 0, installedVersion = :version WHERE emulatorId = :emulatorId")
    suspend fun markAsInstalled(emulatorId: String, version: String)

    @Query("UPDATE emulator_updates SET installedVariant = :variant WHERE emulatorId = :emulatorId")
    suspend fun updateInstalledVariant(emulatorId: String, variant: String)

    @Query("UPDATE emulator_updates SET installedVariant = NULL WHERE emulatorId = :emulatorId")
    suspend fun clearInstalledVariant(emulatorId: String)

    @Query("DELETE FROM emulator_updates WHERE emulatorId = :emulatorId")
    suspend fun delete(emulatorId: String)

    @Query("DELETE FROM emulator_updates")
    suspend fun deleteAll()
}
