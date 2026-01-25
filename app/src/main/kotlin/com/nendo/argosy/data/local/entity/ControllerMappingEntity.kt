package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "controller_mappings",
    indices = [
        Index("controllerId"),
        Index(value = ["vendorId", "productId"], name = "index_controller_mappings_vendorProduct")
    ]
)
data class ControllerMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val controllerId: String,
    val controllerName: String,
    val vendorId: Int,
    val productId: Int,
    val mappingJson: String,
    val presetName: String? = null,
    val isAutoDetected: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
