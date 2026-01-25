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

    @Query("SELECT * FROM controller_mappings WHERE controllerId = :controllerId")
    suspend fun getByControllerId(controllerId: String): ControllerMappingEntity?

    @Query("SELECT * FROM controller_mappings WHERE vendorId = :vendorId AND productId = :productId LIMIT 1")
    suspend fun getByVendorAndProduct(vendorId: Int, productId: Int): ControllerMappingEntity?

    @Upsert
    suspend fun upsert(mapping: ControllerMappingEntity)

    @Query("UPDATE controller_mappings SET mappingJson = :mappingJson, presetName = :presetName, isAutoDetected = :isAutoDetected, updatedAt = :updatedAt WHERE controllerId = :controllerId")
    suspend fun updateMapping(
        controllerId: String,
        mappingJson: String,
        presetName: String?,
        isAutoDetected: Boolean,
        updatedAt: Instant = Instant.now()
    )

    @Query("DELETE FROM controller_mappings WHERE controllerId = :controllerId")
    suspend fun deleteByControllerId(controllerId: String)

    @Query("DELETE FROM controller_mappings")
    suspend fun deleteAll()
}
