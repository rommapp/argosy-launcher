package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "game_controller_mappings",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("gameId"),
        Index(value = ["gameId", "controllerId", "profileKey"], unique = true)
    ]
)
data class GameControllerMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val controllerId: String,
    /**
     * The MappingPlatform id this mapping was authored against. Empty means the platform's default
     * profile, which is what every row predating per-device profiles carries.
     */
    val profileKey: String = "",
    val controllerName: String,
    val vendorId: Int,
    val productId: Int,
    val mappingJson: String,
    val presetName: String? = null,
    val isAutoDetected: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
