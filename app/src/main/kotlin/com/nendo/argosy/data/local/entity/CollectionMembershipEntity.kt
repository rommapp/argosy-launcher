package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Which RomM account can see a locally stored collection.
 *
 * RomM collections are per-user with is_public sharing, so a collection missing from the
 * connected user's response means "not mine to see", not "deleted". Absence of a row is read
 * as membership so collections created before the account existed keep showing.
 */
@Entity(
    tableName = "collection_membership",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ownerUserId", "collectionId"], unique = true),
        Index("collectionId"),
        Index("ownerUserId")
    ]
)
data class CollectionMembershipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: Long,
    val collectionId: Long,
    val isMember: Boolean = true
)
