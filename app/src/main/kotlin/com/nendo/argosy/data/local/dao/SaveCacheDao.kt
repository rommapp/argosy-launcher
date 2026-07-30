package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Pending upload tally for one account; a null [ownerUserId] is unattributed legacy content.
 */
data class OwnerPendingUploads(
    val ownerUserId: Long?,
    val pendingCount: Int
)

@Dao
interface SaveCacheDao {

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId ORDER BY cachedAt DESC")
    fun observeByGame(gameId: Long): Flow<List<SaveCacheEntity>>

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId ORDER BY cachedAt DESC")
    suspend fun getByGame(gameId: Long): List<SaveCacheEntity>

    @Query("SELECT * FROM save_cache WHERE id = :id")
    suspend fun getById(id: Long): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND channelName = :channelName LIMIT 1")
    suspend fun getByGameAndChannel(gameId: Long, channelName: String): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND contentHash = :hash LIMIT 1")
    suspend fun getByGameAndHash(gameId: Long, hash: String): SaveCacheEntity?

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId
          AND ((channelName IS NULL AND :channelName IS NULL) OR channelName = :channelName)
          AND contentHash = :hash
        ORDER BY cachedAt ASC
    """)
    suspend fun getAllByGameChannelAndHash(gameId: Long, channelName: String?, hash: String): List<SaveCacheEntity>

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND slotName = :slotName LIMIT 1")
    suspend fun getByGameAndSlot(gameId: Long, slotName: String): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND isHardcore = 1 ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getLatestHardcoreSave(gameId: Long): SaveCacheEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM save_cache WHERE gameId = :gameId AND isHardcore = 1)")
    suspend fun hasHardcoreSave(gameId: Long): Boolean

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND isHardcore = 0
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestCasualSave(gameId: Long): SaveCacheEntity?

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND isHardcore = 0 AND channelName = :channelName
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestCasualSaveInChannel(gameId: Long, channelName: String): SaveCacheEntity?

    @Update
    suspend fun update(entity: SaveCacheEntity)

    @Query("SELECT COUNT(*) FROM save_cache WHERE gameId = :gameId")
    suspend fun countByGame(gameId: Long): Int

    /**
     * Cache budget is per account, not per game: one account filling its history must not
     * evict another's restore points for the same rom. A null [ownerUserId] is its own bucket
     * of unattributed legacy rows rather than a wildcard.
     */
    @Query(
        """
        SELECT COUNT(*) FROM save_cache
        WHERE gameId = :gameId AND IFNULL(ownerUserId, -1) = IFNULL(:ownerUserId, -1)
        """
    )
    suspend fun countByGameAndOwner(gameId: Long, ownerUserId: Long?): Int

    @Query(
        """
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND IFNULL(ownerUserId, -1) = IFNULL(:ownerUserId, -1)
        ORDER BY cachedAt DESC
        """
    )
    suspend fun getByGameAndOwner(gameId: Long, ownerUserId: Long?): List<SaveCacheEntity>

    @Query(
        """
        SELECT * FROM save_cache
        WHERE gameId = :gameId
          AND IFNULL(ownerUserId, -1) = IFNULL(:ownerUserId, -1)
          AND isLocked = 0
          AND id NOT IN (:pinnedIds)
        ORDER BY cachedAt ASC
        """
    )
    suspend fun getOldestUnlockedForOwnerExcluding(
        gameId: Long,
        ownerUserId: Long?,
        pinnedIds: List<Long>
    ): List<SaveCacheEntity>

    @Query("SELECT COUNT(*) FROM save_cache WHERE gameId = :gameId AND cachedAt >= :sinceMillis")
    suspend fun countByGameSince(gameId: Long, sinceMillis: Long): Int

    @Insert
    suspend fun insert(entity: SaveCacheEntity): Long

    @Query("UPDATE save_cache SET note = :note, channelName = :note, isLocked = (:note IS NOT NULL) WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("DELETE FROM save_cache WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM save_cache WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    /**
     * Drops the server-side half of a game's cached saves, keeping the files themselves.
     * Used when the rom they were synced against is gone or has been renumbered: the save
     * ids name saves on a rom that no longer answers, and a pending upload against it can
     * only fail.
     */
    @Query("UPDATE save_cache SET rommSaveId = NULL, needsRemoteSync = 0, remoteSyncError = NULL WHERE gameId = :gameId")
    suspend fun clearRemoteLinkage(gameId: Long)

    @Query("DELETE FROM save_cache WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    @Query("DELETE FROM save_cache WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getMostRecent(gameId: Long): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND saveSize = :size AND cachedAt >= :fileMtime ORDER BY cachedAt DESC LIMIT 1")
    suspend fun findUnchangedSinceMtime(gameId: Long, size: Long, fileMtime: Instant): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND cachedAt = :timestamp LIMIT 1")
    suspend fun getByTimestamp(gameId: Long, timestamp: Long): SaveCacheEntity?

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND channelName = :channelName ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getMostRecentInChannel(gameId: Long, channelName: String): SaveCacheEntity?

    @Query("DELETE FROM save_cache WHERE gameId IN (SELECT id FROM games WHERE platformId = :platformId)")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("DELETE FROM save_cache WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("SELECT COUNT(*) FROM save_cache WHERE ownerUserId = :ownerUserId AND needsRemoteSync = 1")
    suspend fun countNeedingRemoteSyncForOwner(ownerUserId: Long): Int

    @Query("DELETE FROM save_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM save_cache WHERE channelName LIKE '%state%' OR channelName LIKE 'state_%'")
    suspend fun deleteStaleStateEntries(): Int

    @Query("SELECT COUNT(*) FROM save_cache")
    suspend fun count(): Int

    @Query("SELECT * FROM save_cache WHERE needsRemoteSync = 1 ORDER BY cachedAt DESC")
    suspend fun getNeedingRemoteSync(): List<SaveCacheEntity>

    @Query("SELECT * FROM save_cache WHERE gameId = :gameId AND needsRemoteSync = 1 ORDER BY cachedAt DESC LIMIT 1")
    suspend fun getLatestNeedingSync(gameId: Long): SaveCacheEntity?

    /**
     * Stops one entry asking to be uploaded, keeping the row and its file. For a cache whose
     * archive can never be accepted, so that it stops being retried on every sync without the
     * user losing the restore point it still represents.
     */
    @Query("UPDATE save_cache SET needsRemoteSync = 0 WHERE id = :cacheId")
    suspend fun clearRemoteSyncFlag(cacheId: Long)

    @Query("""
        UPDATE save_cache
        SET needsRemoteSync = 0
        WHERE gameId = :gameId AND channelName = :channelName AND id != :excludeId
    """)
    suspend fun clearDirtyFlagForChannel(gameId: Long, channelName: String?, excludeId: Long)

    @Query("""
        UPDATE save_cache
        SET needsRemoteSync = 0
        WHERE gameId = :gameId AND channelName IS NULL
    """)
    suspend fun clearDirtyFlagForLatest(gameId: Long)

    @Query("""
        UPDATE save_cache
        SET needsRemoteSync = 0, lastSyncedAt = :syncedAt, remoteSyncError = NULL, serverCurrentAtSync = 1
        WHERE id = :id
    """)
    suspend fun markSynced(id: Long, syncedAt: Instant)

    @Query("UPDATE save_cache SET ownerUserId = :ownerUserId WHERE id = :id")
    suspend fun updateOwner(id: Long, ownerUserId: Long?)

    @Query("UPDATE save_cache SET serverCurrentAtSync = :serverCurrent WHERE id = :id")
    suspend fun updateServerCurrentAtSync(id: Long, serverCurrent: Boolean)

    /**
     * Distinct games the account holds a cache row for, newest first. The account switch uses
     * this to decide which artifacts the incoming account has anything to place at all.
     */
    @Query("""
        SELECT DISTINCT gameId FROM save_cache
        WHERE ownerUserId = :ownerUserId
    """)
    suspend fun getGameIdsForOwner(ownerUserId: Long): List<Long>

    /**
     * The newest cache row for this account and game that is safe to write over a live save.
     * A row that neither reached the server nor is still pending upload is a backup, not the
     * account's current progress, and placing it would overwrite with a copy nobody chose.
     */
    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId
          AND ownerUserId = :ownerUserId
          AND isRollback = 0
          AND (serverCurrentAtSync = 1 OR needsRemoteSync = 1)
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun getPlaceableForOwner(gameId: Long, ownerUserId: Long): SaveCacheEntity?

    @Query("""
        SELECT * FROM save_cache
        WHERE gameId = :gameId AND ownerUserId = :ownerUserId
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun getNewestForOwner(gameId: Long, ownerUserId: Long): SaveCacheEntity?

    @Query("""
        UPDATE save_cache
        SET remoteSyncError = :error
        WHERE id = :id
    """)
    suspend fun markSyncError(id: Long, error: String?)

    @Query("SELECT COUNT(*) FROM save_cache WHERE needsRemoteSync = 1")
    suspend fun countNeedingRemoteSync(): Int

    @Query("""
        SELECT ownerUserId AS ownerUserId, COUNT(*) AS pendingCount FROM save_cache
        WHERE needsRemoteSync = 1
        GROUP BY ownerUserId
        ORDER BY pendingCount DESC
    """)
    suspend fun countNeedingRemoteSyncByOwner(): List<OwnerPendingUploads>

    @Query("SELECT COUNT(*) FROM save_cache WHERE gameId = :gameId AND needsRemoteSync = 1")
    suspend fun countNeedingRemoteSyncForGame(gameId: Long): Int

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM save_cache
            WHERE gameId = :gameId
              AND needsRemoteSync = 1
              AND IFNULL(channelName, '') = IFNULL(:channelName, '')
        )
    """)
    suspend fun hasNeedingRemoteSync(gameId: Long, channelName: String?): Boolean

    @Query("SELECT COUNT(*) FROM save_cache WHERE needsRemoteSync = 1")
    fun observeNeedingRemoteSyncCount(): Flow<Int>

    @Query("UPDATE save_cache SET needsRemoteSync = 0 WHERE gameId = :gameId AND needsRemoteSync = 1")
    suspend fun clearAllDirtyFlags(gameId: Long)

    @Query("UPDATE save_cache SET rommSaveId = :rommSaveId WHERE id = :id")
    suspend fun updateRommSaveId(id: Long, rommSaveId: Long)

    @Query("UPDATE save_cache SET contentHash = :contentHash WHERE id = :id")
    suspend fun updateContentHash(id: Long, contentHash: String)

    @Query("UPDATE save_cache SET cachedAt = :cachedAt WHERE id = :id")
    suspend fun updateCachedAt(id: Long, cachedAt: Instant)

    @Query(
        """
        SELECT * FROM save_cache
        WHERE gameId = :gameId
          AND isActive = 1
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun getActive(gameId: Long, ownerUserId: Long?): SaveCacheEntity?

    @Query(
        """
        SELECT * FROM save_cache
        WHERE gameId = :gameId
          AND isActive = 1
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    fun observeActive(gameId: Long, ownerUserId: Long?): Flow<List<SaveCacheEntity>>

    @Query(
        """
        SELECT id FROM save_cache
        WHERE gameId = :gameId
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
          AND channelName IS :channelName
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun getNewestIdInChannelForOwner(gameId: Long, ownerUserId: Long?, channelName: String?): Long?

    @Query(
        """
        SELECT id FROM save_cache
        WHERE gameId = :gameId
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
          AND cachedAt = :timestamp
        ORDER BY cachedAt DESC
        LIMIT 1
        """
    )
    suspend fun getIdAtTimestampForOwner(gameId: Long, ownerUserId: Long?, timestamp: Long): Long?

    @Query(
        """
        UPDATE save_cache SET isActive = 0
        WHERE gameId = :gameId
          AND isActive = 1
          AND id IS NOT :exceptId
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        """
    )
    suspend fun clearActive(gameId: Long, ownerUserId: Long?, exceptId: Long? = null)

    @Query(
        """
        UPDATE save_cache SET isActive = 1
        WHERE id = :cacheId
          AND gameId = :gameId
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        """
    )
    suspend fun markActive(gameId: Long, ownerUserId: Long?, cacheId: Long): Int

    /**
     * The only writer of `isActive`. Room cannot declare a partial unique index, so the
     * "one active row per owner and game" invariant is held here: the clear and the set are one
     * transaction and no call site is allowed to perform them separately.
     *
     * Returns false when [cacheId] belongs to another owner. The set is owner-scoped for the same
     * reason the clear is: marking a foreign row leaves the acting owner with no readable active
     * pointer, and an unset pointer is what lets a resume fall through to another owner's newest
     * save and write it over the live one.
     */
    @Transaction
    suspend fun setActiveRow(gameId: Long, ownerUserId: Long?, cacheId: Long): Boolean {
        val marked = markActive(gameId, ownerUserId, cacheId)
        if (marked == 0) return false
        clearActive(gameId, ownerUserId, exceptId = cacheId)
        return true
    }

    @Query(
        """
        UPDATE save_cache SET activeSaveApplied = :applied
        WHERE gameId = :gameId
          AND isActive = 1
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        """
    )
    suspend fun setActiveSaveApplied(gameId: Long, ownerUserId: Long?, applied: Boolean)

    @Query("UPDATE save_cache SET activeSaveApplied = 0 WHERE activeSaveApplied = 1")
    suspend fun resetAllActiveSaveApplied()

    @Query(
        """
        UPDATE save_cache SET pendingDeviceSyncSaveId = :saveId
        WHERE gameId = :gameId
          AND isActive = 1
          AND (ownerUserId IS NULL OR ownerUserId IS :ownerUserId)
        """
    )
    suspend fun setPendingDeviceSyncSaveId(gameId: Long, ownerUserId: Long?, saveId: Long?)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM save_cache
            WHERE gameId = :gameId AND isActive = 1 AND activeSaveApplied = 1
        )
        """
    )
    suspend fun hasActiveSaveApplied(gameId: Long): Boolean

    @Query("SELECT COUNT(*) FROM save_cache WHERE ownerUserId IS NULL")
    suspend fun countUnowned(): Int

    @Query("UPDATE save_cache SET ownerUserId = :ownerUserId WHERE ownerUserId IS NULL")
    suspend fun adoptUnowned(ownerUserId: Long)
}
