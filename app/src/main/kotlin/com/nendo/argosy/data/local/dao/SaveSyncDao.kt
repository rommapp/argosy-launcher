package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import kotlinx.coroutines.flow.Flow

/**
 * Owner scoping here is deliberately tolerant: a null `ownerUserId` is a row written before the
 * schema had accounts, so it stays reachable for whoever is signed in instead of being orphaned.
 * Single-row resolvers order the signed-in account's row ahead of an unattributed one, because
 * both can now match where the unique index previously allowed only one.
 */
@Dao
interface SaveSyncDao {

    @Query("""
        SELECT * FROM save_sync
        WHERE gameId = :gameId AND emulatorId = :emulatorId AND channelName IS NULL
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getByGameAndEmulator(gameId: Long, emulatorId: String, ownerUserId: Long?): SaveSyncEntity?

    @Query("""
        SELECT * FROM save_sync
        WHERE gameId = :gameId AND emulatorId = :emulatorId AND channelName = :channelName
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getByGameEmulatorAndChannel(
        gameId: Long,
        emulatorId: String,
        channelName: String,
        ownerUserId: Long?
    ): SaveSyncEntity?

    @Query("""
        SELECT * FROM save_sync
        WHERE gameId = :gameId AND emulatorId = :emulatorId AND channelName IS NULL
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getByGameEmulatorAndNullChannel(gameId: Long, emulatorId: String, ownerUserId: Long?): SaveSyncEntity?

    @Query("""
        DELETE FROM save_sync
        WHERE id NOT IN (
            SELECT MAX(id) FROM save_sync
            GROUP BY gameId, emulatorId, IFNULL(channelName, '__null__'), IFNULL(ownerUserId, -1)
        )
    """)
    suspend fun deleteDuplicateRows(): Int

    @Query("""
        SELECT * FROM save_sync
        WHERE gameId = :gameId AND emulatorId = :emulatorId
          AND (channelName IS NULL OR channelName = :defaultChannelName)
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getByGameAndEmulatorWithDefault(
        gameId: Long,
        emulatorId: String,
        defaultChannelName: String,
        ownerUserId: Long?
    ): SaveSyncEntity?

    @Query("""
        SELECT * FROM save_sync
        WHERE gameId = :gameId AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun getByGame(gameId: Long, ownerUserId: Long?): List<SaveSyncEntity>

    @Query("SELECT * FROM save_sync WHERE syncStatus IN (:statuses)")
    suspend fun getByStatuses(vararg statuses: String): List<SaveSyncEntity>

    @Query("""
        SELECT * FROM save_sync
        WHERE gameId = :gameId
          AND syncStatus = :status
          AND IFNULL(channelName, '') = IFNULL(:channelName, '')
        LIMIT 1
    """)
    suspend fun getByGameStatusAndChannel(gameId: Long, status: String, channelName: String?): SaveSyncEntity?

    @Query("""
        SELECT * FROM save_sync
        WHERE (syncStatus = 'SERVER_NEWER' OR syncStatus = 'CONFLICT')
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun getPendingDownloads(ownerUserId: Long?): List<SaveSyncEntity>

    @Query("""
        SELECT * FROM save_sync
        WHERE syncStatus = 'SERVER_NEWER' AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    fun observeGamesWithNewerServerSaves(ownerUserId: Long?): Flow<List<SaveSyncEntity>>

    @Query("""
        SELECT COUNT(*) FROM save_sync
        WHERE syncStatus = 'SERVER_NEWER' AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    fun observeNewSavesCount(ownerUserId: Long?): Flow<Int>

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
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun markCorruptZip(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        ownerUserId: Long?,
        serverTimestamp: String,
        error: String
    )

    @Query("""
        SELECT corruptZipTimestamp FROM save_sync
        WHERE gameId = :gameId AND emulatorId = :emulatorId
          AND (:channelName IS NULL OR channelName = :channelName)
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY (ownerUserId IS NULL) ASC, id DESC
        LIMIT 1
    """)
    suspend fun getCorruptZipTimestamp(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        ownerUserId: Long?
    ): String?

    @Query("""
        UPDATE save_sync SET corruptZipTimestamp = NULL
        WHERE gameId = :gameId AND emulatorId = :emulatorId
          AND (:channelName IS NULL OR channelName = :channelName)
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
    """)
    suspend fun clearCorruptZip(gameId: Long, emulatorId: String, channelName: String?, ownerUserId: Long?)

    @Query("""
        UPDATE save_sync
        SET localSavePath = :path, localUpdatedAt = :updatedAt
        WHERE gameId = :gameId AND emulatorId = :emulatorId
    """)
    suspend fun updateLocalSave(gameId: Long, emulatorId: String, path: String, updatedAt: Long)

    @Query("DELETE FROM save_sync WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    /**
     * Points a game's sync rows at the rom id it was renumbered to. The server save ids go
     * with them: they identify saves belonging to the rom that was replaced, so the next
     * negotiate has to establish them again against the new one.
     */
    @Query("""
        UPDATE save_sync
        SET rommId = :newRommId, rommSaveId = NULL, lastUploadedHash = NULL
        WHERE gameId = :gameId
    """)
    suspend fun realignToRommId(gameId: Long, newRommId: Long)

    @Query("DELETE FROM save_sync WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("UPDATE save_sync SET lastUploadedHash = :hash WHERE id = :id")
    suspend fun updateLastUploadedHash(id: Long, hash: String)

    @Query("UPDATE save_sync SET localContentHash = :hash WHERE id = :id")
    suspend fun updateLocalContentHash(id: Long, hash: String)

    @Query("UPDATE save_sync SET userSelectedRestorePoint = 1, userSelectedRestorePointAt = :nowMs WHERE id = :id")
    suspend fun setUserSelectedRestorePoint(id: Long, nowMs: Long)

    @Query("UPDATE save_sync SET userSelectedRestorePoint = 0, userSelectedRestorePointAt = NULL WHERE id = :id AND userSelectedRestorePoint = 1")
    suspend fun clearUserSelectedRestorePoint(id: Long)

    @Query("UPDATE save_sync SET userSelectedRestorePoint = 0, userSelectedRestorePointAt = NULL WHERE gameId = :gameId AND userSelectedRestorePoint = 1")
    suspend fun clearUserSelectedRestorePointForGame(gameId: Long)

    @Query("DELETE FROM save_sync WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT gameId FROM save_sync")
    suspend fun getAllGameIds(): List<Long>

    @Query("SELECT * FROM save_sync WHERE localSavePath IS NOT NULL")
    suspend fun getAllWithLocalPath(): List<SaveSyncEntity>

    // UPDATE OR REPLACE: when rewriting emulatorId would collide with an
    // existing row on the unique (gameId, emulatorId, channelName) index,
    // SQLite drops the conflicting row instead of aborting. Without this
    // the migration crash-loops the app on a game that already has rows
    // for both the old and the new emulator under the same channel.
    @Query("UPDATE OR REPLACE save_sync SET emulatorId = :newEmulatorId WHERE gameId = :gameId AND emulatorId != :newEmulatorId")
    suspend fun rekeyEmulatorForGame(gameId: Long, newEmulatorId: String): Int

    @Query("SELECT * FROM save_sync WHERE emulatorId = 'default' OR emulatorId = ''")
    suspend fun getStaleDefaultEmulatorRows(): List<SaveSyncEntity>

    @Query("DELETE FROM save_sync WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM save_sync WHERE channelName LIKE '%state%' OR channelName LIKE 'state_%'")
    suspend fun deleteStaleStateEntries(): Int

    @Query("UPDATE save_sync SET localSavePath = NULL")
    suspend fun clearAllPaths()

    @Query("UPDATE save_sync SET localSavePath = NULL WHERE gameId = :gameId")
    suspend fun clearLocalPathsForGame(gameId: Long)

    @Query("SELECT COUNT(*) FROM save_sync WHERE localSavePath IS NOT NULL")
    suspend fun countWithPaths(): Int

    @Query("""
        SELECT * FROM save_sync
        WHERE ownerUserId IS NULL OR ownerUserId IS :ownerUserId
        ORDER BY lastSyncedAt DESC, gameId ASC
    """)
    fun observeAll(ownerUserId: Long?): Flow<List<SaveSyncEntity>>

    @Query("UPDATE save_sync SET lastSyncDeviceId = :deviceId, lastSyncDeviceName = :deviceName WHERE id = :id")
    suspend fun updateDeviceTag(id: Long, deviceId: String?, deviceName: String?)

    @Query("DELETE FROM save_sync WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("""
        SELECT lastSyncDeviceId AS deviceId,
               lastSyncDeviceName AS deviceName,
               COUNT(DISTINCT gameId) AS saveCount,
               MAX(lastSyncedAt) AS latestSyncAt
        FROM save_sync
        WHERE ownerUserId IS NULL OR ownerUserId IS :ownerUserId
        GROUP BY lastSyncDeviceId
    """)
    fun observeSaveCountsByDevice(ownerUserId: Long?): Flow<List<SaveCountByDevice>>

    @Query("SELECT COUNT(*) FROM save_sync WHERE ownerUserId IS NULL")
    suspend fun countUnowned(): Int

    @Query("UPDATE save_sync SET ownerUserId = :ownerUserId WHERE ownerUserId IS NULL")
    suspend fun adoptUnowned(ownerUserId: Long)
}

data class SaveCountByDevice(
    val deviceId: String?,
    val deviceName: String?,
    val saveCount: Int,
    val latestSyncAt: java.time.Instant?
)
