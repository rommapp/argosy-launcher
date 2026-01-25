package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "controller_order",
    indices = [Index("controllerId")]
)
data class ControllerOrderEntity(
    @PrimaryKey
    val port: Int,
    val controllerId: String,
    val controllerName: String,
    val assignedAt: Instant = Instant.now()
)
