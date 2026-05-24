package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.TouchLayoutOverrideEntity

@Dao
interface TouchLayoutOverrideDao {
    @Query("SELECT * FROM touch_layout_overrides WHERE platformSlug = :platformSlug AND orientation = :orientation LIMIT 1")
    suspend fun get(platformSlug: String, orientation: String): TouchLayoutOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TouchLayoutOverrideEntity)

    @Query("DELETE FROM touch_layout_overrides WHERE platformSlug = :platformSlug AND orientation = :orientation")
    suspend fun delete(platformSlug: String, orientation: String)

    @Query("DELETE FROM touch_layout_overrides WHERE platformSlug = :platformSlug")
    suspend fun deleteAllForPlatform(platformSlug: String)
}
