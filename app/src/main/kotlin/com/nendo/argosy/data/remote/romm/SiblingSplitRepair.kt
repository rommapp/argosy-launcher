package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.GameUserOverlayDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SiblingSplitRepair"

data class SiblingSplitRepairOutcome(
    val pathsHandedOver: Int,
    val selectionsCleared: Int,
    val saveSyncRowsMoved: Int,
    val cacheRowsMoved: Int,
    val historyMoved: Int,
    val variantSavesCopied: Int
)

/**
 * Untangles libraries written while RomM sibling roms were merged into one game. Runs after a
 * library pass has given every sibling rom its own game and moved its `game_files` rows there,
 * then repoints what still refers to the merged game: its launch path, its file selections, the
 * save sync rows it absorbed, and built-in saves isolated under the variant directory.
 */
@Singleton
class SiblingSplitRepair @Inject constructor(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val stateCacheDao: StateCacheDao,
    private val gameUserOverlayDao: GameUserOverlayDao,
    private val playSessionDao: PlaySessionDao,
    private val syncPreferences: SyncPreferencesRepository,
    private val variantSaveCarryOver: VariantSaveCarryOver
) {
    /**
     * Runs the repair unless it already completed on this device. A failed run leaves the flag
     * unset so the next clean library pass retries it; every step is safe to repeat.
     */
    suspend fun runOnce(): Unit = withContext(Dispatchers.IO) {
        if (syncPreferences.isSiblingSplitRepairDone()) return@withContext
        try {
            val outcome = repair()
            syncPreferences.setSiblingSplitRepairDone()
            Logger.info(TAG, "runOnce: complete $outcome")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "runOnce: failed, retrying after the next library pass: ${e.message}")
        }
    }

    /**
     * Moves save sync rows still keyed to a regional copy's rom onto that copy once the copy's
     * slot is empty. An occupied slot is never overwritten and no row is deleted. Returns 0
     * until [runOnce] has completed.
     */
    suspend fun retryBlockedSaveSyncMoves(): Int = withContext(Dispatchers.IO) {
        if (!syncPreferences.isSiblingSplitRepairDone()) return@withContext 0
        try {
            val moves = pendingSaveSyncMoves()
            if (moves.isEmpty()) return@withContext 0
            val moved = moveSaveSyncGroup(moves, mutableMapOf(), mutableSetOf())
            if (moved > 0) Logger.info(TAG, "retryBlockedSaveSyncMoves: moved $moved of ${moves.size} rows")
            moved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "retryBlockedSaveSyncMoves: failed, retrying after the next library pass: ${e.message}")
            0
        }
    }

    internal suspend fun repair(): SiblingSplitRepairOutcome {
        val pathsHandedOver = handOverForeignLaunchPaths() + releaseSharedPaths() + releaseSharedAdoptedPaths() +
            relinkDownloadsWithoutPath()
        val formerSelections = clearForeignFileSelections()
        val saveSyncOwners = mutableMapOf<Long, MutableSet<GameEntity>>()
        val saveSync = moveForeignSaveSyncRows(saveSyncOwners)
        val cacheRowsMoved = moveForeignCacheRows(saveSyncOwners)
        val variantSavesCopied = carryOverVariantSaves(formerSelections)
        return SiblingSplitRepairOutcome(
            pathsHandedOver = pathsHandedOver,
            selectionsCleared = formerSelections.values.sumOf { it.size },
            saveSyncRowsMoved = saveSync.rowsMoved,
            cacheRowsMoved = cacheRowsMoved,
            historyMoved = saveSync.historiesMoved,
            variantSavesCopied = variantSavesCopied
        )
    }

    private suspend fun handOverForeignLaunchPaths(): Int {
        val filesByPath = gameFileDao.getAllWithLocalPath()
            .mapNotNull { row -> row.localPath?.let { it to row } }
            .groupBy({ it.first }, { it.second })
        var handedOver = 0
        for (game in gameDao.getGamesWithLocalPath()) {
            val path = game.localPath ?: continue
            val row = filesByPath[path]?.firstOrNull { it.gameId != game.id } ?: continue
            if (filesByPath[path].orEmpty().any { it.gameId == game.id }) continue
            val owner = gameDao.getById(row.gameId) ?: continue

            if (owner.localPath == null) {
                gameDao.updateLocalPath(owner.id, path, GameSource.ROMM_SYNCED, game.fileOrigin)
                Logger.info(TAG, "handOver: ${owner.title} (${owner.id}) takes $path from ${game.title} (${game.id})")
            } else if (owner.localPath != path) {
                Logger.info(TAG, "handOver: ${owner.title} (${owner.id}) keeps ${owner.localPath}; $path left unassigned")
            }

            val ownLaunchPath = ownDownloadedLaunchTarget(game.id, path)
            if (ownLaunchPath != null) {
                gameDao.updateLocalPath(game.id, ownLaunchPath, game.source, game.fileOrigin, game.addedAt)
            } else {
                gameDao.clearLocalPath(game.id)
            }
            Logger.info(TAG, "handOver: ${game.title} (${game.id}) localPath $path -> ${ownLaunchPath ?: "none"}")
            handedOver++
        }
        return handedOver
    }

    private suspend fun releaseSharedPaths(): Int {
        var released = 0
        val claims = gameFileDao.getAllWithLocalPath()
            .filter { it.localPath != null }
            .groupBy { it.localPath!! }
            .filterValues { rows -> rows.map { it.gameId }.distinct().size > 1 }
        for ((path, rows) in claims) {
            val keeperId = rows.minOf { it.gameId }
            for (row in rows.filter { it.gameId != keeperId }) {
                gameFileDao.clearLocalPath(row.id)
                val game = gameDao.getById(row.gameId) ?: continue
                if (game.localPath == path) {
                    val ownLaunchPath = ownDownloadedLaunchTarget(game.id, path)
                    if (ownLaunchPath != null) {
                        gameDao.updateLocalPath(game.id, ownLaunchPath, game.source, game.fileOrigin, game.addedAt)
                    } else {
                        gameDao.clearLocalPath(game.id)
                    }
                }
                Logger.info(TAG, "sharedPath: ${game.title} (${game.id}) releases $path to game $keeperId")
                released++
            }
        }
        return released
    }

    private suspend fun releaseSharedAdoptedPaths(): Int {
        val pathsWithFileRows = gameFileDao.getAllWithLocalPath().mapNotNull { it.localPath }.toSet()
        val claims = gameDao.getGamesWithLocalPath()
            .filter { it.localPath != null && it.localPath !in pathsWithFileRows }
            .groupBy { it.localPath!! }
            .filterValues { it.size > 1 }
        var released = 0
        for ((path, games) in claims) {
            val stem = java.io.File(path).nameWithoutExtension
            val keeper = games.firstOrNull { game ->
                gameFileDao.getFilesForGame(game.id).any { java.io.File(it.fileName).nameWithoutExtension == stem }
            } ?: games.minBy { it.id }
            for (game in games.filter { it.id != keeper.id }) {
                gameDao.clearLocalPath(game.id)
                Logger.info(TAG, "sharedAdoptedPath: ${game.title} (${game.id}) releases $path to ${keeper.title} (${keeper.id})")
                released++
            }
        }
        return released
    }

    private suspend fun relinkDownloadsWithoutPath(): Int {
        var relinked = 0
        val gameIds = gameFileDao.getAllWithLocalPath().map { it.gameId }.distinct()
        for (gameId in gameIds) {
            val game = gameDao.getById(gameId) ?: continue
            if (game.localPath != null) continue
            val path = ownDownloadedLaunchTarget(gameId, excludedPath = "")
                ?.takeIf { java.io.File(it).exists() }
                ?: continue
            gameDao.updateLocalPath(game.id, path, GameSource.ROMM_SYNCED, com.nendo.argosy.data.model.FileOrigin.ROMM_DOWNLOAD)
            Logger.info(TAG, "relink: ${game.title} (${game.id}) localPath -> $path")
            relinked++
        }
        return relinked
    }

    private suspend fun ownDownloadedLaunchTarget(gameId: Long, excludedPath: String): String? {
        val downloaded = gameFileDao.getFilesForGame(gameId)
            .filter {
                it.isLaunchTarget &&
                    it.localPath != null &&
                    it.localPath != excludedPath
            }
        return (downloaded.firstOrNull { VariantCategory.fromKey(it.category) == VariantCategory.GAME }
            ?: downloaded.firstOrNull())?.localPath
    }

    private suspend fun clearForeignFileSelections(): Map<Long, Set<Long>> {
        val formerSelections = mutableMapOf<Long, MutableSet<Long>>()
        for (game in gameDao.getGamesWithFileSelection()) {
            game.activeVariantFileId?.takeIf { ownedByAnotherGame(it, game) }?.let { fileId ->
                gameDao.updateActiveVariantFileId(game.id, null)
                formerSelections.getOrPut(fileId) { mutableSetOf() }.add(game.id)
                Logger.info(TAG, "selections: cleared activeVariantFileId $fileId on ${game.title} (${game.id})")
            }
            game.lastPlayedFileId?.takeIf { ownedByAnotherGame(it, game) }?.let { fileId ->
                gameDao.updateLastPlayedFileId(game.id, null)
                formerSelections.getOrPut(fileId) { mutableSetOf() }.add(game.id)
                Logger.info(TAG, "selections: cleared lastPlayedFileId $fileId on ${game.title} (${game.id})")
            }
        }
        return formerSelections
    }

    private suspend fun ownedByAnotherGame(fileId: Long, game: GameEntity): Boolean {
        val row = gameFileDao.getById(fileId) ?: return false
        return row.gameId != game.id
    }

    private suspend fun moveForeignCacheRows(saveSyncOwners: Map<Long, Set<GameEntity>>): Int {
        val owners = mutableMapOf<Long, List<GameEntity>>()
        suspend fun ownerFor(gameId: Long, channel: String?): GameEntity? {
            val candidates = owners.getOrPut(gameId) { regionalCopies(gameId, saveSyncOwners[gameId].orEmpty()) }
            return candidates.filter { stripAbsorbedChannelPrefix(channel, it) != channel }.singleOrNull()
        }

        var moved = 0
        for (row in saveCacheDao.getRowsWithChannel()) {
            val owner = ownerFor(row.gameId, row.channelName) ?: continue
            val channel = stripAbsorbedChannelPrefix(row.channelName, owner)
            if (saveCacheDao.moveToGame(row.id, owner.id, channel) == 0) continue
            moved++
            Logger.info(TAG, "saveCache: row ${row.id} game ${row.gameId} -> ${owner.id}, channel ${row.channelName} -> $channel")
        }
        for (row in stateCacheDao.getRowsWithChannel()) {
            val owner = ownerFor(row.gameId, row.channelName) ?: continue
            val channel = stripAbsorbedChannelPrefix(row.channelName, owner)
            if (stateCacheDao.moveToGame(row.id, owner.id, channel) == 0) {
                Logger.warn(TAG, "stateCache: row ${row.id} left on game ${row.gameId}; game ${owner.id} holds that slot")
                continue
            }
            moved++
            Logger.info(TAG, "stateCache: row ${row.id} game ${row.gameId} -> ${owner.id}, channel ${row.channelName} -> $channel")
        }
        return moved
    }

    private suspend fun regionalCopies(gameId: Long, saveSyncOwners: Set<GameEntity>): List<GameEntity> {
        val game = gameDao.getById(gameId) ?: return emptyList()
        val sameTitle = game.igdbId?.let { gameDao.getAllByIgdbIdAndPlatform(it, game.platformId) }.orEmpty()
        return (saveSyncOwners + sameTitle).filter { it.id != gameId }.distinctBy { it.id }
    }

    private data class SaveSlot(val gameId: Long, val emulatorId: String, val channel: String?, val ownerUserId: Long?) {
        fun overlaps(other: SaveSlot): Boolean =
            gameId == other.gameId && emulatorId == other.emulatorId && channel == other.channel &&
                (ownerUserId == null || other.ownerUserId == null || ownerUserId == other.ownerUserId)
    }

    private class SaveSyncMove(val row: SaveSyncEntity, val owner: GameEntity, val channel: String?) {
        val slot = SaveSlot(owner.id, row.emulatorId, channel, row.ownerUserId)
    }

    private class SaveSyncOutcome(val rowsMoved: Int, val historiesMoved: Int)

    private suspend fun pendingSaveSyncMoves(): List<SaveSyncMove> =
        saveSyncDao.getRowsKeyedToAnotherRom().mapNotNull { row ->
            val owner = gameDao.getByRommId(row.rommId) ?: return@mapNotNull null
            if (owner.id == row.gameId) return@mapNotNull null
            SaveSyncMove(row, owner, stripAbsorbedChannelPrefix(row.channelName, owner))
        }

    private suspend fun moveForeignSaveSyncRows(owners: MutableMap<Long, MutableSet<GameEntity>>): SaveSyncOutcome {
        val moves = pendingSaveSyncMoves()
        val claimed = mutableSetOf<SaveSlot>()
        var histories = 0
        var moved = 0
        for ((mergedId, group) in moves.groupBy { it.row.gameId }) {
            if (moveHistoryWithSaves(mergedId, group, claimed)) histories++
            moved += moveSaveSyncGroup(group, owners, claimed)
        }
        return SaveSyncOutcome(rowsMoved = moved, historiesMoved = histories)
    }

    private suspend fun slotFree(move: SaveSyncMove, claimed: Set<SaveSlot>): Boolean =
        claimed.none { it.overlaps(move.slot) } && !destinationTaken(move.row, move.owner.id, move.channel)

    private suspend fun moveHistoryWithSaves(mergedId: Long, group: List<SaveSyncMove>, claimed: Set<SaveSlot>): Boolean {
        val target = group.map { it.owner }.distinctBy { it.id }.singleOrNull() ?: return false
        if (group.withIndex().any { (i, a) -> group.drop(i + 1).any { b -> a.slot.overlaps(b.slot) } }) return false
        if (group.any { !slotFree(it, claimed) }) return false
        if (saveSyncDao.countForGame(mergedId) != group.size) return false
        gameUserOverlayDao.movePlayTotals(fromGameId = mergedId, toGameId = target.id)
        val sessions = playSessionDao.moveToGame(mergedId, target.id)
        Logger.info(TAG, "history: game $mergedId -> ${target.title} (${target.id}), $sessions sessions")
        return true
    }

    private suspend fun moveSaveSyncGroup(
        group: List<SaveSyncMove>,
        owners: MutableMap<Long, MutableSet<GameEntity>>,
        claimed: MutableSet<SaveSlot>
    ): Int {
        var moved = 0
        for (move in group) {
            val row = move.row
            val owner = move.owner
            val channel = move.channel
            if (!slotFree(move, claimed)) {
                Logger.warn(
                    TAG,
                    "saveSync: row ${row.id} (${row.emulatorId}, channel=${row.channelName}) left on game " +
                        "${row.gameId}; game ${owner.id} already holds channel=$channel"
                )
                continue
            }
            if (saveSyncDao.moveToGame(row.id, owner.id, SaveSyncApiClient.syncKeyOf(channel)) == 0) {
                Logger.warn(TAG, "saveSync: row ${row.id} not moved to game ${owner.id}, unique key collision")
                continue
            }
            claimed += move.slot
            owners.getOrPut(row.gameId) { mutableSetOf() }.add(owner)
            moved++
            Logger.info(
                TAG,
                "saveSync: row ${row.id} (${row.emulatorId}) game ${row.gameId} -> ${owner.id}, " +
                    "channel ${row.channelName} -> $channel"
            )
        }
        return moved
    }

    private suspend fun destinationTaken(row: SaveSyncEntity, gameId: Long, channel: String?): Boolean {
        return saveSyncDao.getByGameEmulatorAndChannel(
            gameId, row.emulatorId, SaveSyncApiClient.syncKeyOf(channel), row.ownerUserId
        ) != null
    }

    private suspend fun carryOverVariantSaves(formerSelections: Map<Long, Set<Long>>): Int {
        var copied = 0
        for (file in gameFileDao.getVersionGroupedFiles()) {
            val owner = gameDao.getById(file.gameId) ?: continue
            if (owner.rommId == null || file.romId != owner.rommId) continue
            copied += carryOverSafely(file, owner, formerSelections[file.id].orEmpty())
        }
        return copied
    }

    private suspend fun carryOverSafely(file: GameFileEntity, owner: GameEntity, formerGameIds: Set<Long>): Int =
        try {
            variantSaveCarryOver.carryOver(file, owner, formerGameIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "variantSaves: file ${file.id} for game ${owner.id} failed: ${e.message}")
            0
        }
}

internal fun stripAbsorbedChannelPrefix(channelName: String?, owner: GameEntity): String? {
    channelName ?: return null
    val regionPrefix = owner.regions
        ?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    val prefixes = listOfNotNull(regionPrefix, owner.rommId?.let { "Version $it" })
    for (prefix in prefixes) {
        if (channelName == prefix) return null
        if (channelName.startsWith("$prefix/")) return channelName.removePrefix("$prefix/")
    }
    return channelName
}
