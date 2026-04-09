package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.AchievementEntity

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: Long): AchievementEntity?

    @Query("SELECT * FROM achievements WHERE gameId = :gameId ORDER BY points DESC, title ASC")
    suspend fun getByGameId(gameId: Long): List<AchievementEntity>

    @Query("SELECT COUNT(*) FROM achievements WHERE gameId = :gameId")
    suspend fun countByGameId(gameId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<AchievementEntity>)

    @Query("DELETE FROM achievements WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    data class SocialSharedRow(val raId: Long, val socialSharedAt: Long?)

    @Query("SELECT raId, socialSharedAt FROM achievements WHERE gameId = :gameId")
    suspend fun getSocialSharedState(gameId: Long): List<SocialSharedRow>

    data class LocalStateRow(
        val raId: Long,
        val socialSharedAt: Long?,
        val unlockedAt: Long?,
        val unlockedHardcoreAt: Long?,
        val cachedBadgeUrl: String?,
        val cachedBadgeUrlLock: String?
    )

    @Query("SELECT raId, socialSharedAt, unlockedAt, unlockedHardcoreAt, cachedBadgeUrl, cachedBadgeUrlLock FROM achievements WHERE gameId = :gameId")
    suspend fun getLocalState(gameId: Long): List<LocalStateRow>

    @Transaction
    suspend fun replaceForGame(gameId: Long, achievements: List<AchievementEntity>) {
        val existing = getLocalState(gameId).associateBy { it.raId }
        deleteByGameId(gameId)
        insertAll(achievements.map { ach ->
            val local = existing[ach.raId]
            ach.copy(
                socialSharedAt = local?.socialSharedAt ?: ach.socialSharedAt,
                unlockedAt = mergeTimestamp(ach.unlockedAt, local?.unlockedAt),
                unlockedHardcoreAt = mergeTimestamp(ach.unlockedHardcoreAt, local?.unlockedHardcoreAt),
                cachedBadgeUrl = ach.cachedBadgeUrl ?: local?.cachedBadgeUrl,
                cachedBadgeUrlLock = ach.cachedBadgeUrlLock ?: local?.cachedBadgeUrlLock
            )
        })
    }

    private fun mergeTimestamp(incoming: Long?, local: Long?): Long? = when {
        incoming != null && local != null -> minOf(incoming, local)
        else -> incoming ?: local
    }

    @Query("SELECT * FROM achievements WHERE badgeUrl IS NOT NULL AND cachedBadgeUrl IS NULL")
    suspend fun getWithUncachedBadges(): List<AchievementEntity>

    @Query("UPDATE achievements SET cachedBadgeUrl = :cachedPath WHERE id = :id")
    suspend fun updateCachedBadgeUrl(id: Long, cachedPath: String)

    @Query("UPDATE achievements SET cachedBadgeUrlLock = :cachedPath WHERE id = :id")
    suspend fun updateCachedBadgeUrlLock(id: Long, cachedPath: String)

    @Query("SELECT COUNT(*) FROM achievements WHERE badgeUrl IS NOT NULL")
    suspend fun countWithBadges(): Int

    @Query("SELECT COUNT(*) FROM achievements WHERE cachedBadgeUrl IS NOT NULL")
    suspend fun countWithCachedBadges(): Int

    @Query("UPDATE achievements SET unlockedAt = :unlockedAt WHERE gameId = :gameId AND raId = :raId")
    suspend fun markUnlocked(gameId: Long, raId: Long, unlockedAt: Long)

    @Query("UPDATE achievements SET unlockedHardcoreAt = :unlockedAt WHERE gameId = :gameId AND raId = :raId")
    suspend fun markUnlockedHardcore(gameId: Long, raId: Long, unlockedAt: Long)

    @Query("SELECT COUNT(*) FROM achievements WHERE gameId = :gameId AND (unlockedAt IS NOT NULL OR unlockedHardcoreAt IS NOT NULL)")
    suspend fun countUnlockedByGameId(gameId: Long): Int

    data class UnsharedAchievementRow(
        val gameId: Long,
        val raId: Long,
        val title: String,
        val description: String?,
        val points: Int,
        val badgeUrl: String?,
        val unlockedAt: Long?,
        val unlockedHardcoreAt: Long?,
        val gameIgdbId: Long?,
        val gameRaId: Long?,
        val gameTitle: String
    )

    @Query("""
        SELECT a.gameId, a.raId, a.title, a.description, a.points, a.badgeUrl,
               a.unlockedAt, a.unlockedHardcoreAt, g.igdbId as gameIgdbId, g.raId as gameRaId,
               g.title as gameTitle
        FROM achievements a INNER JOIN games g ON a.gameId = g.id
        WHERE (a.unlockedAt IS NOT NULL OR a.unlockedHardcoreAt IS NOT NULL)
          AND (a.socialSharedAt IS NULL OR a.socialSharedAt < :syncCutoff)
        ORDER BY COALESCE(a.unlockedHardcoreAt, a.unlockedAt) DESC
        LIMIT :limit
    """)
    suspend fun getUnsharedUnlocked(syncCutoff: Long = 0L, limit: Int = 50): List<UnsharedAchievementRow>

    @Query("UPDATE achievements SET socialSharedAt = :sharedAt WHERE raId IN (:raIds)")
    suspend fun markSocialSharedBatch(raIds: List<Long>, sharedAt: Long)

    @Query("UPDATE achievements SET socialSharedAt = :sharedAt WHERE gameId = :gameId AND raId = :raId")
    suspend fun markSocialShared(gameId: Long, raId: Long, sharedAt: Long)
}
