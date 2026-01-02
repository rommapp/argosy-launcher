package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collections",
    indices = [
        Index(value = ["rommId"], unique = true),
        Index("name")
    ]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rommId: Long? = null,
    val name: String,
    val description: String? = null,
    val isUserCreated: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
