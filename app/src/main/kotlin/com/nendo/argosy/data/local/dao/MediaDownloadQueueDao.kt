package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.MediaDownloadQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Every read is scoped to a media account: a download is fetched with one account's token, into one
 * account's library, and is that account's pending work.
 */
@Dao
interface MediaDownloadQueueDao {

    @Query(
        "SELECT * FROM media_download_queue WHERE ownerUserId = :ownerUserId " +
            "AND state IN ('QUEUED', 'PREPARING', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt ASC"
    )
    suspend fun getPendingDownloads(ownerUserId: String): List<MediaDownloadQueueEntity>

    @Query("SELECT * FROM media_download_queue WHERE ownerUserId = :ownerUserId ORDER BY createdAt ASC")
    fun observeQueue(ownerUserId: String): Flow<List<MediaDownloadQueueEntity>>

    @Query("SELECT * FROM media_download_queue WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    suspend fun getByItemId(ownerUserId: String, itemId: String): MediaDownloadQueueEntity?

    @Query("SELECT * FROM media_download_queue WHERE ownerUserId = :ownerUserId AND itemId = :itemId LIMIT 1")
    fun observeByItemId(ownerUserId: String, itemId: String): Flow<MediaDownloadQueueEntity?>

    @Query(
        "SELECT * FROM media_download_queue WHERE ownerUserId = :ownerUserId AND seriesId = :seriesId " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getBySeries(ownerUserId: String, seriesId: String): List<MediaDownloadQueueEntity>

    @Query(
        "SELECT COUNT(*) FROM media_download_queue WHERE ownerUserId = :ownerUserId " +
            "AND state IN ('QUEUED', 'PREPARING', 'DOWNLOADING')"
    )
    fun observeActiveCount(ownerUserId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaDownloadQueueEntity): Long

    @Query(
        "UPDATE media_download_queue SET bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes " +
            "WHERE ownerUserId = :ownerUserId AND itemId = :itemId"
    )
    suspend fun updateProgress(
        ownerUserId: String,
        itemId: String,
        bytesDownloaded: Long,
        totalBytes: Long
    )

    @Query(
        "UPDATE media_download_queue SET state = :state, errorReason = :errorReason " +
            "WHERE ownerUserId = :ownerUserId AND itemId = :itemId"
    )
    suspend fun updateState(
        ownerUserId: String,
        itemId: String,
        state: String,
        errorReason: String? = null
    )

    /**
     * Records the transcode the server agreed to produce. Held on the row so the session can be
     * cancelled server-side if the download is abandoned, rather than left to expire.
     */
    @Query(
        "UPDATE media_download_queue SET mediaSourceId = :mediaSourceId, playSessionId = :playSessionId " +
            "WHERE ownerUserId = :ownerUserId AND itemId = :itemId"
    )
    suspend fun updateSource(
        ownerUserId: String,
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?
    )

    @Query(
        "UPDATE media_download_queue SET destinationPath = :destinationPath, tempFilePath = :tempFilePath " +
            "WHERE ownerUserId = :ownerUserId AND itemId = :itemId"
    )
    suspend fun updatePaths(
        ownerUserId: String,
        itemId: String,
        destinationPath: String?,
        tempFilePath: String?
    )

    @Query("DELETE FROM media_download_queue WHERE ownerUserId = :ownerUserId AND itemId = :itemId")
    suspend fun deleteByItemId(ownerUserId: String, itemId: String)

    @Query("DELETE FROM media_download_queue WHERE ownerUserId = :ownerUserId AND state = 'COMPLETED'")
    suspend fun clearCompleted(ownerUserId: String)

    /**
     * Drops failures old enough to have stopped being work the user meant to finish. A failed row is
     * kept so the partial file behind it can be resumed, and that is only worth anything while the
     * partial is worth anything: rows are otherwise held for the life of the install.
     */
    @Query(
        "DELETE FROM media_download_queue WHERE ownerUserId = :ownerUserId " +
            "AND state = 'FAILED' AND createdAt < :before"
    )
    suspend fun clearFailedBefore(ownerUserId: String, before: Long)

    @Query(
        "SELECT * FROM media_download_queue WHERE ownerUserId = :ownerUserId " +
            "AND state = 'FAILED' ORDER BY createdAt ASC"
    )
    suspend fun getFailedDownloads(ownerUserId: String): List<MediaDownloadQueueEntity>

    @Query("DELETE FROM media_download_queue WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: String)

    @Query("DELETE FROM media_download_queue WHERE ownerUserId != :ownerUserId")
    suspend fun deleteOtherOwners(ownerUserId: String)
}
