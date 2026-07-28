package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.CollectionMembershipEntity

@Dao
interface CollectionMembershipDao {

    @Query("SELECT * FROM collection_membership WHERE ownerUserId = :ownerUserId AND collectionId = :collectionId")
    suspend fun get(ownerUserId: Long, collectionId: Long): CollectionMembershipEntity?

    @Query(
        """
        INSERT OR IGNORE INTO collection_membership (ownerUserId, collectionId, isMember)
        SELECT :ownerUserId, id, 1 FROM collections WHERE id = :collectionId
        """
    )
    suspend fun ensureRow(ownerUserId: Long, collectionId: Long)

    @Query("UPDATE collection_membership SET isMember = :member WHERE ownerUserId = :ownerUserId AND collectionId = :collectionId")
    suspend fun writeMembership(ownerUserId: Long, collectionId: Long, member: Boolean)

    @Transaction
    suspend fun setMembership(ownerUserId: Long, collectionId: Long, member: Boolean) {
        ensureRow(ownerUserId, collectionId)
        writeMembership(ownerUserId, collectionId, member)
    }

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM collection_membership
            WHERE ownerUserId = :ownerUserId AND collectionId = :collectionId AND isMember = 0
        )
        """
    )
    suspend fun isMasked(ownerUserId: Long, collectionId: Long): Boolean

    @Query("DELETE FROM collection_membership WHERE ownerUserId = :ownerUserId")
    suspend fun deleteForOwner(ownerUserId: Long)

    @Query("SELECT COUNT(*) FROM collection_membership WHERE collectionId = :collectionId AND isMember = 1")
    suspend fun countMembers(collectionId: Long): Int
}
