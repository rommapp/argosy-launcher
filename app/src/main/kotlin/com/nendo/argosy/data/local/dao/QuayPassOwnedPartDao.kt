package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.QuayPassOwnedPartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuayPassOwnedPartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(part: QuayPassOwnedPartEntity)

    @Query("SELECT * FROM quaypass_owned_parts")
    suspend fun all(): List<QuayPassOwnedPartEntity>

    @Query("SELECT partKey FROM quaypass_owned_parts")
    fun observeOwnedKeys(): Flow<List<String>>

    @Query("SELECT * FROM quaypass_owned_parts WHERE synced = 0 ORDER BY acquiredAt ASC")
    suspend fun unsynced(): List<QuayPassOwnedPartEntity>

    @Query("UPDATE quaypass_owned_parts SET synced = 1 WHERE partKey = :partKey")
    suspend fun markSynced(partKey: String)

    @Query("DELETE FROM quaypass_owned_parts WHERE partKey = :partKey")
    suspend fun delete(partKey: String)

    @Query("DELETE FROM quaypass_owned_parts")
    suspend fun clear()
}
