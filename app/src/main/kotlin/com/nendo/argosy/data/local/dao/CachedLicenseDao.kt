package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.CachedLicenseEntity

@Dao
interface CachedLicenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(licenses: List<CachedLicenseEntity>)

    @Query("SELECT * FROM cached_licenses ORDER BY id")
    suspend fun getAll(): List<CachedLicenseEntity>

    @Query("DELETE FROM cached_licenses")
    suspend fun deleteAll()
}
