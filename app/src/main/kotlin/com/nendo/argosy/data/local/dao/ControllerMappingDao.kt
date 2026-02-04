package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.ControllerMappingEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ControllerMappingDao {

    @Query("SELECT * FROM controller_mappings")
    suspend fun getAll(): List<ControllerMappingEntity>

    @Query("SELECT * FROM controller_mappings")
    fun observeAll(): Flow<List<ControllerMappingEntity>>

    @Query("SELECT * FROM controller_mappings WHERE controllerId = :controllerId AND platformId IS NULL LIMIT 1")
    suspend fun getByControllerIdGlobal(controllerId: String): ControllerMappingEntity?

    @Query("SELECT * FROM controller_mappings WHERE controllerId = :controllerId AND platformId = :platformId LIMIT 1")
    suspend fun getByControllerIdAndPlatform(controllerId: String, platformId: String): ControllerMappingEntity?

    @Query("SELECT * FROM controller_mappings WHERE vendorId = :vendorId AND productId = :productId AND platformId IS NULL LIMIT 1")
    suspend fun getByVendorAndProduct(vendorId: Int, productId: Int): ControllerMappingEntity?

    @Upsert
    suspend fun upsert(mapping: ControllerMappingEntity)

    @Query("UPDATE controller_mappings SET mappingJson = :mappingJson, presetName = :presetName, isAutoDetected = :isAutoDetected, updatedAt = :updatedAt WHERE controllerId = :controllerId AND platformId IS NULL")
    suspend fun updateMappingGlobal(
        controllerId: String,
        mappingJson: String,
        presetName: String?,
        isAutoDetected: Boolean,
        updatedAt: Instant = Instant.now()
    )

    @Query("UPDATE controller_mappings SET mappingJson = :mappingJson, presetName = :presetName, isAutoDetected = :isAutoDetected, updatedAt = :updatedAt WHERE controllerId = :controllerId AND platformId = :platformId")
    suspend fun updateMappingForPlatform(
        controllerId: String,
        platformId: String,
        mappingJson: String,
        presetName: String?,
        isAutoDetected: Boolean,
        updatedAt: Instant = Instant.now()
    )

    @Query("DELETE FROM controller_mappings WHERE controllerId = :controllerId")
    suspend fun deleteByControllerId(controllerId: String)

    @Query("DELETE FROM controller_mappings WHERE isAutoDetected = 1")
    suspend fun deleteAllAutoDetected()

    @Query("DELETE FROM controller_mappings")
    suspend fun deleteAll()
}
