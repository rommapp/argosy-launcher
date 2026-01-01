package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.FirmwareEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface FirmwareDao {

    @Query("SELECT * FROM firmware WHERE platformId = :platformId ORDER BY fileName ASC")
    suspend fun getByPlatform(platformId: Long): List<FirmwareEntity>

    @Query("SELECT * FROM firmware WHERE platformSlug = :platformSlug ORDER BY fileName ASC")
    suspend fun getByPlatformSlug(platformSlug: String): List<FirmwareEntity>

    @Query("SELECT * FROM firmware WHERE localPath IS NULL ORDER BY platformSlug, fileName")
    suspend fun getMissing(): List<FirmwareEntity>

    @Query("SELECT * FROM firmware WHERE localPath IS NOT NULL ORDER BY platformSlug, fileName")
    suspend fun getDownloaded(): List<FirmwareEntity>

    @Query("SELECT * FROM firmware ORDER BY platformSlug, fileName")
    suspend fun getAll(): List<FirmwareEntity>

    @Query("SELECT * FROM firmware WHERE platformSlug = :platformSlug AND localPath IS NULL ORDER BY fileName")
    suspend fun getMissingByPlatformSlug(platformSlug: String): List<FirmwareEntity>

    @Query("SELECT * FROM firmware ORDER BY platformSlug, fileName")
    fun observeAll(): Flow<List<FirmwareEntity>>

    @Query("SELECT * FROM firmware WHERE platformSlug = :platformSlug ORDER BY fileName")
    fun observeByPlatformSlug(platformSlug: String): Flow<List<FirmwareEntity>>

    @Query("SELECT COUNT(*) FROM firmware WHERE localPath IS NULL")
    fun observeMissingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM firmware WHERE localPath IS NOT NULL")
    fun observeDownloadedCount(): Flow<Int>

    @Upsert
    suspend fun upsert(firmware: FirmwareEntity)

    @Upsert
    suspend fun upsertAll(firmware: List<FirmwareEntity>)

    @Query("UPDATE firmware SET localPath = :localPath, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun updateLocalPath(id: Long, localPath: String?, downloadedAt: Instant?)

    @Query("UPDATE firmware SET lastVerifiedAt = :verifiedAt WHERE id = :id")
    suspend fun updateVerifiedAt(id: Long, verifiedAt: Instant)

    @Query("DELETE FROM firmware WHERE platformId = :platformId AND rommId NOT IN (:keepRommIds)")
    suspend fun deleteRemovedFirmware(platformId: Long, keepRommIds: List<Long>)

    @Query("DELETE FROM firmware WHERE platformId = :platformId")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("SELECT * FROM firmware WHERE rommId = :rommId LIMIT 1")
    suspend fun getByRommId(rommId: Long): FirmwareEntity?

    @Query("SELECT DISTINCT platformSlug FROM firmware WHERE localPath IS NOT NULL")
    suspend fun getPlatformSlugsWithDownloadedFirmware(): List<String>
}
