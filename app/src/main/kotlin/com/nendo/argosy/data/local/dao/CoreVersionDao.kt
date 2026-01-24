package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CoreVersionDao {

    @Query("SELECT * FROM core_versions WHERE coreId = :coreId")
    suspend fun getByCoreId(coreId: String): CoreVersionEntity?

    @Query("SELECT * FROM core_versions")
    suspend fun getAll(): List<CoreVersionEntity>

    @Query("SELECT * FROM core_versions WHERE updateAvailable = 1")
    suspend fun getWithUpdatesAvailable(): List<CoreVersionEntity>

    @Query("SELECT * FROM core_versions")
    fun observeAll(): Flow<List<CoreVersionEntity>>

    @Query("SELECT COUNT(*) FROM core_versions WHERE updateAvailable = 1")
    fun observeUpdateCount(): Flow<Int>

    @Upsert
    suspend fun upsert(coreVersion: CoreVersionEntity)

    @Upsert
    suspend fun upsertAll(coreVersions: List<CoreVersionEntity>)

    @Query("UPDATE core_versions SET latestVersion = :latestVersion, lastCheckedAt = :checkedAt, updateAvailable = :updateAvailable WHERE coreId = :coreId")
    suspend fun updateVersionCheck(coreId: String, latestVersion: String?, checkedAt: Instant, updateAvailable: Boolean)

    @Query("UPDATE core_versions SET installedVersion = :version, installedAt = :installedAt, updateAvailable = 0 WHERE coreId = :coreId")
    suspend fun markInstalled(coreId: String, version: String, installedAt: Instant)

    @Query("DELETE FROM core_versions WHERE coreId = :coreId")
    suspend fun delete(coreId: String)

    @Query("DELETE FROM core_versions")
    suspend fun deleteAll()
}
