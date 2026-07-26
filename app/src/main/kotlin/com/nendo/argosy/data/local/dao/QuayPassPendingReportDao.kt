package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nendo.argosy.data.local.entity.QuayPassPendingReportEntity

@Dao
interface QuayPassPendingReportDao {

    @Insert
    suspend fun enqueue(report: QuayPassPendingReportEntity)

    @Query("SELECT * FROM quaypass_pending_reports ORDER BY id ASC")
    suspend fun all(): List<QuayPassPendingReportEntity>

    @Query("DELETE FROM quaypass_pending_reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM quaypass_pending_reports WHERE tsSecs < :cutoffSecs")
    suspend fun deleteOlderThan(cutoffSecs: Long)

    @Query("DELETE FROM quaypass_pending_reports")
    suspend fun clear()
}
