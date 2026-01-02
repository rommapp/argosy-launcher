package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "collection_games",
    primaryKeys = ["collectionId", "gameId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
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
        Index("collectionId"),
        Index("gameId")
    ]
)
data class CollectionGameEntity(
    val collectionId: Long,
    val gameId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
