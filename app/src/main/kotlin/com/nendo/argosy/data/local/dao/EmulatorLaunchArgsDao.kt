package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmulatorLaunchArgsDao {

    @Query("SELECT * FROM emulator_launch_args WHERE platformId = :platformId AND emulatorId = :emulatorId LIMIT 1")
    suspend fun getByPlatformAndEmulator(platformId: Long, emulatorId: String): EmulatorLaunchArgsEntity?

    @Query("SELECT * FROM emulator_launch_args WHERE platformId = :platformId AND emulatorId = :emulatorId LIMIT 1")
    fun observeByPlatformAndEmulator(platformId: Long, emulatorId: String): Flow<EmulatorLaunchArgsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmulatorLaunchArgsEntity)

    @Query("DELETE FROM emulator_launch_args WHERE platformId = :platformId AND emulatorId = :emulatorId")
    suspend fun deleteByPlatformAndEmulator(platformId: Long, emulatorId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM emulator_launch_args WHERE platformId = :platformId AND emulatorId = :emulatorId)")
    suspend fun hasOverride(platformId: Long, emulatorId: String): Boolean
}
