package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.QuayPassDailyStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuayPassDailyStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: QuayPassDailyStatsEntity)

    @Query("SELECT * FROM quaypass_daily_stats WHERE date = :date")
    suspend fun forDate(date: String): QuayPassDailyStatsEntity?

    @Query("SELECT * FROM quaypass_daily_stats ORDER BY date DESC LIMIT :days")
    fun observeRecent(days: Int): Flow<List<QuayPassDailyStatsEntity>>

    @Query(
        """
        UPDATE quaypass_daily_stats
        SET encounterCount = encounterCount + 1,
            ticketsEarned = ticketsEarned + :tickets
        WHERE date = :date
        """
    )
    suspend fun incrementForDate(date: String, tickets: Int): Int

    @Query("DELETE FROM quaypass_daily_stats")
    suspend fun clear()
}
