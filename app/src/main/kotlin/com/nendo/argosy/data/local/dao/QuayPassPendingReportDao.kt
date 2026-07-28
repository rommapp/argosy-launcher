package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nendo.argosy.data.local.entity.QuayPassPendingReportEntity

@Dao
interface QuayPassPendingReportDao {

    @Insert
    suspend fun enqueue(report: QuayPassPendingReportEntity)

    /**
     * Only the live account's reports. A report is signed by that account's install key and is
     * sent under its bearer token, so draining another account's rows would have the server
     * attribute the meeting to whoever happens to be signed in.
     */
    @Query(
        "SELECT * FROM quaypass_pending_reports " +
            "WHERE localOwnerUserId = " +
            "COALESCE((SELECT rommUserId FROM romm_accounts WHERE isActive = 1 LIMIT 1), 0) " +
            "ORDER BY id ASC"
    )
    suspend fun all(): List<QuayPassPendingReportEntity>

    @Query("DELETE FROM quaypass_pending_reports WHERE localOwnerUserId = :localOwnerUserId")
    suspend fun deleteByOwner(localOwnerUserId: Long)

    @Query("SELECT COUNT(*) FROM quaypass_pending_reports WHERE localOwnerUserId = :localOwnerUserId")
    suspend fun countForOwner(localOwnerUserId: Long): Int

    @Query("DELETE FROM quaypass_pending_reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM quaypass_pending_reports WHERE tsSecs < :cutoffSecs")
    suspend fun deleteOlderThan(cutoffSecs: Long)

    @Query("DELETE FROM quaypass_pending_reports")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM quaypass_pending_reports WHERE localOwnerUserId = 0")
    suspend fun countUnowned(): Int

    @Query("UPDATE quaypass_pending_reports SET localOwnerUserId = :ownerUserId WHERE localOwnerUserId = 0")
    suspend fun adoptUnowned(ownerUserId: Long)
}
