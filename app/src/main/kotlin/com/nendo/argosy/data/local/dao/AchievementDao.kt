package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.AchievementEntity

/**
 * Every per-game read and write takes the owning RomM user id.
 *
 * Unlock state is the RA account's, not the ROM's, and the insert strategy is REPLACE against a
 * unique index that now includes the owner; an unscoped query returns and overwrites both
 * accounts' rows. Badge-cache maintenance is the exception and stays owner-agnostic, because a
 * cached badge file is a property of the achievement, not of who unlocked it.
 */
@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE id = :id")
    suspend fun getById(id: Long): AchievementEntity?

    @Query(
        "SELECT * FROM achievements WHERE gameId = :gameId AND ownerUserId = :ownerUserId " +
            "ORDER BY points DESC, title ASC"
    )
    suspend fun getByGameId(gameId: Long, ownerUserId: Long): List<AchievementEntity>

    @Query(
        "SELECT COALESCE(SUM(points), 0) FROM achievements " +
            "WHERE ownerUserId = :ownerUserId AND unlockedAt IS NOT NULL"
    )
    suspend fun sumUnlockedPoints(ownerUserId: Long): Int

    @Query("SELECT COUNT(*) FROM achievements WHERE ownerUserId = :ownerUserId AND unlockedAt IS NOT NULL")
    suspend fun countUnlocked(ownerUserId: Long): Int

    @Query(
        "SELECT * FROM achievements WHERE ownerUserId = :ownerUserId AND unlockedAt IS NOT NULL " +
            "ORDER BY unlockedAt DESC LIMIT :limit"
    )
    suspend fun getRecentUnlocks(ownerUserId: Long, limit: Int): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE gameId = :gameId ORDER BY points DESC, title ASC")
    suspend fun getAllForGame(gameId: Long): List<AchievementEntity>

    @Query("SELECT COUNT(*) FROM achievements WHERE gameId = :gameId AND ownerUserId = :ownerUserId")
    suspend fun countByGameId(gameId: Long, ownerUserId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<AchievementEntity>)

    @Query("DELETE FROM achievements WHERE gameId = :gameId AND ownerUserId = :ownerUserId")
    suspend fun deleteByGameId(gameId: Long, ownerUserId: Long)

    @Query("DELETE FROM achievements WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("SELECT COUNT(*) FROM achievements WHERE ownerUserId = :sentinel")
    suspend fun countUnowned(sentinel: Long = AchievementEntity.NO_OWNER): Int

    @Query("SELECT COUNT(*) FROM achievements WHERE ownerUserId != :sentinel")
    suspend fun countOwned(sentinel: Long = AchievementEntity.NO_OWNER): Int

    @Query("UPDATE achievements SET ownerUserId = :ownerUserId WHERE ownerUserId = :sentinel")
    suspend fun adoptUnowned(ownerUserId: Long, sentinel: Long = AchievementEntity.NO_OWNER)

    data class SocialSharedRow(val raId: Long, val socialSharedAt: Long?)

    @Query(
        "SELECT raId, socialSharedAt FROM achievements " +
            "WHERE gameId = :gameId AND ownerUserId = :ownerUserId"
    )
    suspend fun getSocialSharedState(gameId: Long, ownerUserId: Long): List<SocialSharedRow>

    data class LocalStateRow(
        val raId: Long,
        val socialSharedAt: Long?,
        val unlockedAt: Long?,
        val unlockedHardcoreAt: Long?,
        val cachedBadgeUrl: String?,
        val cachedBadgeUrlLock: String?
    )

    @Query(
        "SELECT raId, socialSharedAt, unlockedAt, unlockedHardcoreAt, cachedBadgeUrl, " +
            "cachedBadgeUrlLock FROM achievements " +
            "WHERE gameId = :gameId AND ownerUserId = :ownerUserId"
    )
    suspend fun getLocalState(gameId: Long, ownerUserId: Long): List<LocalStateRow>

    @Transaction
    suspend fun replaceForGame(
        gameId: Long,
        ownerUserId: Long,
        achievements: List<AchievementEntity>
    ) {
        val existing = getLocalState(gameId, ownerUserId).associateBy { it.raId }
        deleteByGameId(gameId, ownerUserId)
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

    @Query(
        "UPDATE achievements SET unlockedAt = :unlockedAt " +
            "WHERE gameId = :gameId AND raId = :raId AND ownerUserId = :ownerUserId"
    )
    suspend fun markUnlocked(gameId: Long, raId: Long, ownerUserId: Long, unlockedAt: Long)

    @Query(
        "UPDATE achievements SET unlockedHardcoreAt = :unlockedAt " +
            "WHERE gameId = :gameId AND raId = :raId AND ownerUserId = :ownerUserId"
    )
    suspend fun markUnlockedHardcore(gameId: Long, raId: Long, ownerUserId: Long, unlockedAt: Long)

    @Query(
        "SELECT COUNT(*) FROM achievements WHERE gameId = :gameId AND ownerUserId = :ownerUserId " +
            "AND (unlockedAt IS NOT NULL OR unlockedHardcoreAt IS NOT NULL)"
    )
    suspend fun countUnlockedByGameId(gameId: Long, ownerUserId: Long): Int

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
          AND a.ownerUserId = :ownerUserId
          AND (a.socialSharedAt IS NULL OR a.socialSharedAt < :syncCutoff)
        ORDER BY COALESCE(a.unlockedHardcoreAt, a.unlockedAt) DESC
        LIMIT :limit
    """)
    suspend fun getUnsharedUnlocked(
        ownerUserId: Long,
        syncCutoff: Long = 0L,
        limit: Int = 50
    ): List<UnsharedAchievementRow>

    @Query(
        "UPDATE achievements SET socialSharedAt = :sharedAt " +
            "WHERE raId IN (:raIds) AND ownerUserId = :ownerUserId"
    )
    suspend fun markSocialSharedBatch(raIds: List<Long>, ownerUserId: Long, sharedAt: Long)

    @Query(
        "UPDATE achievements SET socialSharedAt = :sharedAt " +
            "WHERE gameId = :gameId AND raId = :raId AND ownerUserId = :ownerUserId"
    )
    suspend fun markSocialShared(gameId: Long, raId: Long, ownerUserId: Long, sharedAt: Long)
}
