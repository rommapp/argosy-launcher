package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.QuayPassDailyStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuayPassDailyStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: QuayPassDailyStatsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: QuayPassDailyStatsEntity)

    /**
     * Atomically credits a day: ensures the row exists, then increments. The
     * insert-or-ignore plus update avoids the lost increment when two passes with
     * different peers land at once on the day's first credit.
     */
    @Transaction
    suspend fun creditDay(date: String, tickets: Int) {
        insertIfAbsent(QuayPassDailyStatsEntity(date = date, encounterCount = 0, ticketsEarned = 0))
        incrementForDate(date, tickets)
    }

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
