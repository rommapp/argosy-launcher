package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PinnedCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedCollectionDao {

    @Query("SELECT * FROM pinned_collections ORDER BY displayOrder ASC")
    fun observeAllPinned(): Flow<List<PinnedCollectionEntity>>

    @Query("SELECT * FROM pinned_collections ORDER BY displayOrder ASC")
    suspend fun getAllPinned(): List<PinnedCollectionEntity>

    @Query("SELECT * FROM pinned_collections WHERE collectionId = :collectionId")
    suspend fun getPinnedByCollectionId(collectionId: Long): PinnedCollectionEntity?

    @Query("SELECT * FROM pinned_collections WHERE virtualType = :type AND virtualName = :name")
    suspend fun getPinnedVirtual(type: String, name: String): PinnedCollectionEntity?

    @Query("SELECT MAX(displayOrder) FROM pinned_collections")
    suspend fun getMaxDisplayOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pinned: PinnedCollectionEntity): Long

    @Delete
    suspend fun delete(pinned: PinnedCollectionEntity)

    @Query("DELETE FROM pinned_collections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pinned_collections WHERE collectionId = :collectionId")
    suspend fun deleteByCollectionId(collectionId: Long)

    @Query("DELETE FROM pinned_collections WHERE virtualType = :type AND virtualName = :name")
    suspend fun deleteVirtual(type: String, name: String)

    @Query("UPDATE pinned_collections SET displayOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)
}
