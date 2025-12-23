package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.AppCategoryEntity

@Dao
interface AppCategoryDao {

    @Query("SELECT * FROM app_category_cache WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AppCategoryEntity)

    @Query("SELECT * FROM app_category_cache WHERE isGame = 1")
    suspend fun getAllGames(): List<AppCategoryEntity>

    @Query("SELECT * FROM app_category_cache WHERE isGame = 1 AND isManualOverride = 0")
    suspend fun getAutoDetectedGames(): List<AppCategoryEntity>

    @Query("""
        UPDATE app_category_cache
        SET isGame = :isGame, isManualOverride = 1
        WHERE packageName = :packageName
    """)
    suspend fun setManualGameFlag(packageName: String, isGame: Boolean)

    @Query("DELETE FROM app_category_cache WHERE fetchedAt < :olderThan AND isManualOverride = 0")
    suspend fun pruneStaleEntries(olderThan: Long)

    @Query("DELETE FROM app_category_cache WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM app_category_cache WHERE isGame = 1")
    suspend fun countGames(): Int
}
