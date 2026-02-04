package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformLibretroSettingsDao {
    @Query("SELECT * FROM platform_libretro_settings WHERE platformId = :platformId")
    suspend fun getByPlatformId(platformId: Long): PlatformLibretroSettingsEntity?

    @Query("SELECT * FROM platform_libretro_settings WHERE platformId = :platformId")
    fun observeByPlatformId(platformId: Long): Flow<PlatformLibretroSettingsEntity?>

    @Query("SELECT * FROM platform_libretro_settings")
    fun observeAll(): Flow<List<PlatformLibretroSettingsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: PlatformLibretroSettingsEntity): Long

    @Query("DELETE FROM platform_libretro_settings WHERE platformId = :platformId")
    suspend fun deleteByPlatformId(platformId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM platform_libretro_settings WHERE platformId = :platformId)")
    suspend fun hasOverrides(platformId: Long): Boolean
}
