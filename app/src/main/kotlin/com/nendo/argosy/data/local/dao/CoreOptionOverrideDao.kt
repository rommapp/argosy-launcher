package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreOptionOverrideDao {

    @Query("SELECT * FROM core_option_overrides WHERE coreId = :coreId")
    suspend fun getOverridesForCore(coreId: String): List<CoreOptionOverrideEntity>

    @Query("SELECT * FROM core_option_overrides WHERE coreId = :coreId")
    fun observeOverridesForCore(coreId: String): Flow<List<CoreOptionOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: CoreOptionOverrideEntity)

    @Query("DELETE FROM core_option_overrides WHERE coreId = :coreId AND optionKey = :optionKey")
    suspend fun delete(coreId: String, optionKey: String)

    @Query("DELETE FROM core_option_overrides WHERE coreId = :coreId")
    suspend fun deleteAllForCore(coreId: String)

    @Query("DELETE FROM core_option_overrides WHERE coreId = :coreId AND optionKey NOT IN (:keptKeys)")
    suspend fun deleteRetired(coreId: String, keptKeys: List<String>)
}
