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

    @Query("SELECT * FROM save_sync WHERE gameId = :gameId AND emulatorId = :emulatorId AND channelName IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getByGameEmulatorAndNullChannel(gameId: Long, emulatorId: String): SaveSyncEntity?

    @Query("""
        DELETE FROM save_sync
        WHERE id NOT IN (
            SELECT MAX(id) FROM save_sync
            GROUP BY gameId, emulatorId, IFNULL(channelName, '__null__')
        )
    """)
    suspend fun deleteDuplicateRows(): Int

    @Query("SELECT * FROM save_sync WHERE gameId = :gameId AND emulatorId = :emulatorId AND (channelName IS NULL OR channelName = :defaultChannelName)")
    suspend fun getByGameAndEmulatorWithDefault(gameId: Long, emulatorId: String, defaultChannelName: String): SaveSyncEntity?

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

    @Query("SELECT COUNT(*) FROM save_sync WHERE syncStatus = :status")
    suspend fun countByStatus(status: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SaveSyncEntity): Long

    @Query("UPDATE save_sync SET syncStatus = :status, lastSyncError = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("""
        UPDATE save_sync
        SET corruptZipTimestamp = :serverTimestamp,
            lastSyncError = :error
        WHERE gameId = :gameId AND emulatorId = :emulatorId
          AND (:channelName IS NULL OR channelName = :channelName)
    """)
    suspend fun markCorruptZip(gameId: Long, emulatorId: String, channelName: String?, serverTimestamp: String, error: String)

    @Query("""
        SELECT corruptZipTimestamp FROM save_sync
        WHERE gameId = :gameId AND emulatorId = :emulatorId
          AND (:channelName IS NULL OR channelName = :channelName)
        LIMIT 1
    """)
    suspend fun getCorruptZipTimestamp(gameId: Long, emulatorId: String, channelName: String?): String?

    @Query("""
        UPDATE save_sync SET corruptZipTimestamp = NULL
        WHERE gameId = :gameId AND emulatorId = :emulatorId
          AND (:channelName IS NULL OR channelName = :channelName)
    """)
    suspend fun clearCorruptZip(gameId: Long, emulatorId: String, channelName: String?)

    @Query("""
        UPDATE save_sync
        SET localSavePath = :path, localUpdatedAt = :updatedAt
        WHERE gameId = :gameId AND emulatorId = :emulatorId
    """)
    suspend fun updateLocalSave(gameId: Long, emulatorId: String, path: String, updatedAt: Long)

    @Query("DELETE FROM save_sync WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("UPDATE save_sync SET lastUploadedHash = :hash WHERE id = :id")
    suspend fun updateLastUploadedHash(id: Long, hash: String)

    @Query("DELETE FROM save_sync WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT gameId FROM save_sync")
    suspend fun getAllGameIds(): List<Long>

    // UPDATE OR REPLACE: when rewriting emulatorId would collide with an
    // existing row on the unique (gameId, emulatorId, channelName) index,
    // SQLite drops the conflicting row instead of aborting. Without this
    // the migration crash-loops the app on a game that already has rows
    // for both the old and the new emulator under the same channel.
    @Query("UPDATE OR REPLACE save_sync SET emulatorId = :newEmulatorId WHERE gameId = :gameId AND emulatorId != :newEmulatorId")
    suspend fun rekeyEmulatorForGame(gameId: Long, newEmulatorId: String): Int

    @Query("DELETE FROM save_sync WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM save_sync WHERE channelName LIKE '%state%' OR channelName LIKE 'state_%'")
    suspend fun deleteStaleStateEntries(): Int

    @Query("UPDATE save_sync SET localSavePath = NULL")
    suspend fun clearAllPaths()

    @Query("SELECT COUNT(*) FROM save_sync WHERE localSavePath IS NOT NULL")
    suspend fun countWithPaths(): Int
}
