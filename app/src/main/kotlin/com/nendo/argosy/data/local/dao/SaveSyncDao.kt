package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveSyncDao {

    @Query("SELECT * FROM save_sync WHERE gameId = :gameId AND emulatorId = :emulatorId AND channelName IS NULL")
    suspend fun getByGameAndEmulator(gameId: Long, emulatorId: String): SaveSyncEntity?

    @Query("SELECT * FROM save_sync WHERE gameId = :gameId AND emulatorId = :emulatorId AND channelName = :channelName")
    suspend fun getByGameEmulatorAndChannel(gameId: Long, emulatorId: String, channelName: String): SaveSyncEntity?

    @Query("SELECT * FROM save_sync WHERE gameId = :gameId")
    suspend fun getByGame(gameId: Long): List<SaveSyncEntity>

    @Query("SELECT * FROM save_sync WHERE syncStatus IN (:statuses)")
    suspend fun getByStatuses(vararg statuses: String): List<SaveSyncEntity>

    @Query("SELECT * FROM save_sync WHERE syncStatus = 'SERVER_NEWER' OR syncStatus = 'CONFLICT'")
    suspend fun getPendingDownloads(): List<SaveSyncEntity>

    @Query("SELECT * FROM save_sync WHERE syncStatus = 'SERVER_NEWER'")
    fun observeGamesWithNewerServerSaves(): Flow<List<SaveSyncEntity>>

    @Query("SELECT COUNT(*) FROM save_sync WHERE syncStatus = 'SERVER_NEWER'")
    fun observeNewSavesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SaveSyncEntity): Long

    @Query("UPDATE save_sync SET syncStatus = :status, lastSyncError = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("""
        UPDATE save_sync
        SET localSavePath = :path, localUpdatedAt = :updatedAt
        WHERE gameId = :gameId AND emulatorId = :emulatorId
    """)
    suspend fun updateLocalSave(gameId: Long, emulatorId: String, path: String, updatedAt: Long)

    @Query("DELETE FROM save_sync WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)
}
