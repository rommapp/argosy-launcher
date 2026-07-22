package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Merges a consolidated sibling game row into its version-group winner: every
 * gameId-bearing table is re-pointed or deduped, then the loser row is deleted.
 */
@Dao
abstract class GameAbsorptionDao {

    @Query(
        "UPDATE save_cache SET gameId = :winnerId, channelName = " +
            "CASE WHEN channelName IS NULL THEN :channelPrefix " +
            "ELSE :channelPrefix || '/' || channelName END WHERE gameId = :loserId"
    )
    protected abstract suspend fun repointSaveCache(loserId: Long, winnerId: Long, channelPrefix: String)

    @Query(
        "UPDATE save_sync SET gameId = :winnerId, channelName = " +
            "CASE WHEN channelName IS NULL THEN :channelPrefix " +
            "ELSE :channelPrefix || '/' || channelName END WHERE gameId = :loserId"
    )
    protected abstract suspend fun repointSaveSync(loserId: Long, winnerId: Long, channelPrefix: String)

    @Query(
        "UPDATE state_cache SET gameId = :winnerId, channelName = " +
            "CASE WHEN channelName IS NULL THEN :channelPrefix " +
            "ELSE :channelPrefix || '/' || channelName END WHERE gameId = :loserId"
    )
    protected abstract suspend fun repointStateCache(loserId: Long, winnerId: Long, channelPrefix: String)

    @Query("UPDATE state_tombstones SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointStateTombstones(loserId: Long, winnerId: Long)

    @Query("UPDATE pending_sync_queue SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointPendingSyncQueue(loserId: Long, winnerId: Long)

    @Query("UPDATE pending_conflicts SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointPendingConflicts(loserId: Long, winnerId: Long)

    @Query("UPDATE play_sessions SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointPlaySessions(loserId: Long, winnerId: Long)

    @Query("UPDATE OR IGNORE achievements SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointAchievements(loserId: Long, winnerId: Long)

    @Query("DELETE FROM achievements WHERE gameId = :loserId")
    protected abstract suspend fun deleteLeftoverAchievements(loserId: Long)

    @Query("UPDATE OR IGNORE collection_games SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointCollectionGames(loserId: Long, winnerId: Long)

    @Query("DELETE FROM collection_games WHERE gameId = :loserId")
    protected abstract suspend fun deleteLeftoverCollectionGames(loserId: Long)

    @Query(
        "UPDATE emulator_configs SET gameId = :winnerId WHERE gameId = :loserId AND NOT EXISTS " +
            "(SELECT 1 FROM emulator_configs WHERE gameId = :winnerId)"
    )
    protected abstract suspend fun repointEmulatorConfigIfAbsent(loserId: Long, winnerId: Long)

    @Query("DELETE FROM emulator_configs WHERE gameId = :loserId")
    protected abstract suspend fun deleteLeftoverEmulatorConfigs(loserId: Long)

    @Query("UPDATE OR IGNORE game_core_option_overrides SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointCoreOptionOverrides(loserId: Long, winnerId: Long)

    @Query("DELETE FROM game_core_option_overrides WHERE gameId = :loserId")
    protected abstract suspend fun deleteLeftoverCoreOptionOverrides(loserId: Long)

    @Query("UPDATE OR IGNORE game_controller_mappings SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointControllerMappings(loserId: Long, winnerId: Long)

    @Query("DELETE FROM game_controller_mappings WHERE gameId = :loserId")
    protected abstract suspend fun deleteLeftoverControllerMappings(loserId: Long)

    @Query("UPDATE OR IGNORE cheats SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointCheats(loserId: Long, winnerId: Long)

    @Query("DELETE FROM cheats WHERE gameId = :loserId")
    protected abstract suspend fun deleteLeftoverCheats(loserId: Long)

    @Query("UPDATE game_discs SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointGameDiscs(loserId: Long, winnerId: Long)

    @Query("UPDATE speedrun_categories SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointSpeedrunCategories(loserId: Long, winnerId: Long)

    @Query("UPDATE download_queue SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointDownloadQueue(loserId: Long, winnerId: Long)

    @Query("UPDATE game_files SET gameId = :winnerId WHERE gameId = :loserId")
    protected abstract suspend fun repointRemainingGameFiles(loserId: Long, winnerId: Long)

    @Query(
        "UPDATE game_files SET localPath = :localPath, downloadedAt = :downloadedAtEpoch " +
            "WHERE gameId = :winnerId AND versionGroup = :versionGroup " +
            "AND category = 'game' AND localPath IS NULL"
    )
    protected abstract suspend fun transferBaseLocalPath(
        winnerId: Long,
        versionGroup: String,
        localPath: String,
        downloadedAtEpoch: Long
    )

    @Query(
        "UPDATE games SET " +
            "playTimeMinutes = playTimeMinutes + :playTimeMinutes, " +
            "playCount = playCount + :playCount, " +
            "isFavorite = isFavorite OR :isFavorite, " +
            "userRating = CASE WHEN userRating = 0 THEN :userRating ELSE userRating END, " +
            "userDifficulty = CASE WHEN userDifficulty = 0 THEN :userDifficulty ELSE userDifficulty END, " +
            "status = COALESCE(status, :status) " +
            "WHERE id = :winnerId"
    )
    protected abstract suspend fun mergeGameScalars(
        winnerId: Long,
        playTimeMinutes: Int,
        playCount: Int,
        isFavorite: Boolean,
        userRating: Int,
        userDifficulty: Int,
        status: String?
    )

    @Query("DELETE FROM games WHERE id = :loserId")
    protected abstract suspend fun deleteLoser(loserId: Long)

    @Transaction
    open suspend fun absorb(
        loserId: Long,
        winnerId: Long,
        channelPrefix: String,
        versionGroup: String,
        loserLocalPath: String?,
        loserDownloadedAtEpoch: Long,
        playTimeMinutes: Int,
        playCount: Int,
        isFavorite: Boolean,
        userRating: Int,
        userDifficulty: Int,
        status: String?
    ) {
        if (loserLocalPath != null) {
            transferBaseLocalPath(winnerId, versionGroup, loserLocalPath, loserDownloadedAtEpoch)
        }
        repointSaveCache(loserId, winnerId, channelPrefix)
        repointSaveSync(loserId, winnerId, channelPrefix)
        repointStateCache(loserId, winnerId, channelPrefix)
        repointStateTombstones(loserId, winnerId)
        repointPendingSyncQueue(loserId, winnerId)
        repointPendingConflicts(loserId, winnerId)
        repointPlaySessions(loserId, winnerId)
        repointAchievements(loserId, winnerId)
        deleteLeftoverAchievements(loserId)
        repointCollectionGames(loserId, winnerId)
        deleteLeftoverCollectionGames(loserId)
        repointEmulatorConfigIfAbsent(loserId, winnerId)
        deleteLeftoverEmulatorConfigs(loserId)
        repointCoreOptionOverrides(loserId, winnerId)
        deleteLeftoverCoreOptionOverrides(loserId)
        repointControllerMappings(loserId, winnerId)
        deleteLeftoverControllerMappings(loserId)
        repointCheats(loserId, winnerId)
        deleteLeftoverCheats(loserId)
        repointGameDiscs(loserId, winnerId)
        repointSpeedrunCategories(loserId, winnerId)
        repointDownloadQueue(loserId, winnerId)
        repointRemainingGameFiles(loserId, winnerId)
        mergeGameScalars(
            winnerId, playTimeMinutes, playCount, isFavorite,
            userRating, userDifficulty, status
        )
        deleteLoser(loserId)
    }
}
