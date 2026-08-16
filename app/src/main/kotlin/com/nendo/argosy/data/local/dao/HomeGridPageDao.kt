package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.HomeGridPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeGridPageDao {

    @Query("SELECT * FROM home_grid_pages WHERE ownerUserId IS :ownerUserId ORDER BY sortOrder ASC")
    fun observeAll(ownerUserId: Long?): Flow<List<HomeGridPageEntity>>

    @Query("SELECT * FROM home_grid_pages WHERE ownerUserId IS :ownerUserId ORDER BY sortOrder ASC")
    suspend fun getAll(ownerUserId: Long?): List<HomeGridPageEntity>

    @Query("SELECT * FROM home_grid_pages WHERE id = :id")
    suspend fun getById(id: Long): HomeGridPageEntity?

    @Query(
        "SELECT * FROM home_grid_pages WHERE ownerUserId IS :ownerUserId AND sortOrder = :sortOrder"
    )
    suspend fun getAt(ownerUserId: Long?, sortOrder: Int): HomeGridPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: HomeGridPageEntity): Long

    @Update
    suspend fun update(page: HomeGridPageEntity)

    @Delete
    suspend fun delete(page: HomeGridPageEntity)

    /**
     * Closes the gap left by a removed page. Run inside the same transaction as the delete so the
     * stored order never has a hole in it.
     */
    @Query(
        """
        UPDATE home_grid_pages SET sortOrder = sortOrder - 1
        WHERE ownerUserId IS :ownerUserId AND sortOrder > :removedOrder
        """
    )
    suspend fun closeGapAfter(ownerUserId: Long?, removedOrder: Int)
}
