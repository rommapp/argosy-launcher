package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.OrphanedFileEntity

@Dao
interface OrphanedFileDao {

    @Query("SELECT * FROM orphaned_files ORDER BY createdAt ASC")
    suspend fun getAll(): List<OrphanedFileEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(orphan: OrphanedFileEntity)

    @Query("DELETE FROM orphaned_files WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM orphaned_files WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
