package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "game_core_option_overrides",
    primaryKeys = ["gameId", "coreId", "optionKey"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class GameCoreOptionOverrideEntity(
    val gameId: Long,
    val coreId: String,
    val optionKey: String,
    val value: String
)
