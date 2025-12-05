package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "emulator_configs",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("platformId"),
        Index("gameId", unique = true)
    ]
)
data class EmulatorConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val platformId: String?,
    val gameId: Long?,

    val packageName: String,
    val displayName: String,
    val coreName: String?,
    val isDefault: Boolean = false
)
