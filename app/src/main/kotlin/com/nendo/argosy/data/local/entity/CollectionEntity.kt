package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CollectionType {
    REGULAR,
    GENRE,
    GAME_MODE
}

@Entity(
    tableName = "collections",
    indices = [
        Index(value = ["rommId"], unique = true),
        Index("name"),
        Index("type")
    ]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rommId: Long? = null,
    val name: String,
    val description: String? = null,
    val type: CollectionType = CollectionType.REGULAR,
    val isUserCreated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
