package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.SocialGameCacheEntity

@Dao
interface SocialGameCacheDao {

    @Query("SELECT * FROM social_game_cache WHERE igdbId = :igdbId")
    suspend fun getByIgdbId(igdbId: Int): SocialGameCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SocialGameCacheEntity)

    @Query("DELETE FROM social_game_cache WHERE igdbId = :igdbId")
    suspend fun deleteByIgdbId(igdbId: Int)

    @Query("SELECT COUNT(*) FROM social_game_cache")
    suspend fun count(): Int

    @Query("""
        DELETE FROM social_game_cache
        WHERE igdbId IN (
            SELECT igdbId FROM social_game_cache
            ORDER BY fetchedAt ASC
            LIMIT :count
        )
    """)
    suspend fun deleteOldest(count: Int)

    @Query("DELETE FROM social_game_cache WHERE fetchedAt < :before")
    suspend fun deleteExpired(before: java.time.Instant)
}
