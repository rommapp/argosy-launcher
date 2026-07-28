package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameUserOverlayDao
import com.nendo.argosy.data.local.dao.UserRomsHiddenDao
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for every write to a per-account game property.
 *
 * Callers keep the signatures they had when these were plain `games` updates; the active RomM
 * user is resolved here, once per call, and handed to the write-through DAO. Nothing writes the
 * materialised `games` column on its own, so a property can never end up recorded for the wrong
 * account or recorded on the library row alone.
 */
@Singleton
class GameUserOverlayWriter @Inject constructor(
    private val overlayDao: GameUserOverlayDao,
    private val userRomsHiddenDao: UserRomsHiddenDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend fun activeOwnerId(): Long? = syncPreferencesRepository.getRommUserId()

    /**
     * The user's own hide choice, which lives in `user_roms_hidden` and not on the overlay row.
     * It is routed through here only so that the active account is resolved in one place; it is
     * not the admin-imposed `serverHidden` and never touches it.
     */
    suspend fun setHidden(gameId: Long, hidden: Boolean) {
        val owner = activeOwnerId()
        if (hidden) {
            userRomsHiddenDao.hide(owner, gameId)
        } else {
            userRomsHiddenDao.unhide(owner, gameId)
        }
    }

    suspend fun updateFavorite(gameId: Long, favorite: Boolean) =
        overlayDao.setFavorite(activeOwnerId(), gameId, favorite)

    suspend fun setFavoritesByRommIds(rommIds: List<Long>) =
        overlayDao.setFavoritesByRommIds(activeOwnerId(), rommIds)

    suspend fun setFavoriteByRommId(rommId: Long) =
        overlayDao.setFavoriteByRommId(activeOwnerId(), rommId, true)

    suspend fun clearFavoriteByRommId(rommId: Long) =
        overlayDao.setFavoriteByRommId(activeOwnerId(), rommId, false)

    suspend fun updateUserRating(gameId: Long, rating: Int) =
        overlayDao.setUserRating(activeOwnerId(), gameId, rating)

    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int) =
        overlayDao.setUserDifficulty(activeOwnerId(), gameId, difficulty)

    suspend fun updateCompletion(gameId: Long, completion: Int) =
        overlayDao.setCompletion(activeOwnerId(), gameId, completion)

    suspend fun updateStatus(gameId: Long, status: String?) =
        overlayDao.setStatus(activeOwnerId(), gameId, status)

    suspend fun updateBacklogged(gameId: Long, backlogged: Boolean) =
        overlayDao.setBacklogged(activeOwnerId(), gameId, backlogged)

    suspend fun updateNowPlaying(gameId: Long, nowPlaying: Boolean) =
        overlayDao.setNowPlaying(activeOwnerId(), gameId, nowPlaying)

    suspend fun recordPlayStart(gameId: Long, timestamp: Instant) =
        overlayDao.recordPlayStart(activeOwnerId(), gameId, timestamp)

    suspend fun addPlayTime(gameId: Long, minutes: Int) =
        overlayDao.addPlayTime(activeOwnerId(), gameId, minutes)

    suspend fun updateAchievementCount(gameId: Long, count: Int, earnedCount: Int) =
        overlayDao.setAchievementCounts(activeOwnerId(), gameId, count, earnedCount)

    suspend fun incrementEarnedAchievementCount(gameId: Long) =
        overlayDao.incrementEarnedAchievementCount(activeOwnerId(), gameId)

    /**
     * Applies one account's whole overlay onto the materialised `games` columns. Used by the
     * account switch after the identity swap and by nothing else; ordinary writes stay
     * incremental.
     */
    suspend fun materialiseForOwner(ownerUserId: Long) = overlayDao.materialiseForOwner(ownerUserId)

    /**
     * Claims the existing library for [ownerUserId] when no account has claimed any of it.
     *
     * A device that only ever had one account carries that account's state on `games` alone, and
     * the first materialise would blank it. The test is deliberately "the overlay table is empty",
     * not "this account has no rows": once any account holds rows the library is partitioned, and
     * a second account adopting it would take the first account's favorites and playtime with it.
     */
    suspend fun adoptLibraryIfUnclaimed(ownerUserId: Long) {
        if (overlayDao.countAll() > 0) return
        overlayDao.adoptWholeLibrary(ownerUserId)
    }

    suspend fun dropMembership(ownerUserId: Long, gameId: Long, serverHidden: Boolean) =
        overlayDao.dropMembership(ownerUserId, gameId, serverHidden)

    suspend fun grantMembership(ownerUserId: Long, gameId: Long) =
        overlayDao.grantMembership(ownerUserId, gameId)

    suspend fun favoriteRommIdsForOwner(ownerUserId: Long): List<Long> =
        overlayDao.getFavoriteRommIdsForOwner(ownerUserId)
}
