package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pinned_collections",
    indices = [
        Index("displayOrder")
    ]
)
data class PinnedCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val collectionId: Long? = null,
    val virtualType: String? = null,
    val virtualName: String? = null,
    val displayOrder: Int,
    val createdAt: Long = System.currentTimeMillis()
)
