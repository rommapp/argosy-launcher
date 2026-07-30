package com.nendo.argosy.data.sync

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveOwnershipDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveOwnershipEntity
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AccountSwitchArtifact"

/**
 * The per-artifact half of an account switch: one save at a time, through
 * STABLE -> RECLAIMING -> RECLAIMED -> CLEARED -> APPLYING -> STABLE.
 *
 * Each step is persisted on the artifact's ownership row as it completes, never batched to the
 * end, so a process death leaves a state whose recovery action is unambiguous: RECLAIMING redoes
 * the archive because disk is still authoritative, RECLAIMED removes, CLEARED places, APPLYING
 * redoes the placement.
 *
 * The invariant the whole class exists to hold: the live bytes are not removed until an archive
 * of them has been read back and hash-matched, and the live bytes are re-hashed immediately
 * before removal so an artifact that changed since the archive is left alone instead of lost.
 */
@Singleton
class AccountSwitchArtifactService @Inject constructor(
    private val gameDao: GameDao,
    private val saveCacheDao: SaveCacheDao,
    private val saveOwnershipDao: SaveOwnershipDao,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val emulatorResolver: EmulatorResolver,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry,
    private val fal: FileAccessLayer
) {
    enum class TeardownResult { RECLAIMED, ABORTED, NOTHING_TO_DO }

    enum class PlacementResult { PLACED, NEEDS_SYNC, NOTHING_TO_PLACE, FAILED }

    /**
     * Archives, verifies, re-hashes and removes one outgoing artifact. Returns ABORTED when the
     * live bytes could not be proven identical to the archive, in which case they stay on disk.
     */
    suspend fun tearDown(
        row: SaveOwnershipEntity,
        fromUserId: Long,
        toUserId: Long
    ): TeardownResult = withContext(Dispatchers.IO) {
        val gameId = row.gameId
        if (gameId == null) {
            Logger.warn(
                TAG,
                "Ownership row has no game attribution; leaving its bytes alone | path=${row.savePath}, emulator=${row.emulatorId}"
            )
            return@withContext TeardownResult.NOTHING_TO_DO
        }
        val game = gameDao.getById(gameId) ?: run {
            Logger.warn(TAG, "Game $gameId is gone; leaving its save bytes alone | path=${row.savePath}")
            return@withContext TeardownResult.NOTHING_TO_DO
        }

        if (!fal.exists(row.savePath)) {
            Logger.debug(TAG, "Nothing on disk for game $gameId; the slot is already free | path=${row.savePath}")
            persist(
                row.copy(
                    transitionState = SaveOwnershipEntity.STATE_CLEARED,
                    ownerUserId = null,
                    contentHash = null,
                    pendingOwnerUserId = toUserId
                )
            )
            return@withContext TeardownResult.NOTHING_TO_DO
        }

        var current = persist(
            row.copy(
                transitionState = SaveOwnershipEntity.STATE_RECLAIMING,
                pendingOwnerUserId = toUserId
            )
        )

        val archiveId = archive(current, gameId, fromUserId)
        if (archiveId == null) {
            restoreStable(current, fromUserId)
            return@withContext TeardownResult.ABORTED
        }

        if (!saveCacheManager.verifyCachedArchive(archiveId)) {
            Logger.error(TAG, "Archive for game $gameId did not verify; refusing to remove live save | path=${current.savePath}")
            restoreStable(current, fromUserId)
            return@withContext TeardownResult.ABORTED
        }

        current = persist(
            current.copy(
                transitionState = SaveOwnershipEntity.STATE_RECLAIMED,
                archivedCacheId = archiveId
            )
        )

        if (!removeVerified(current, game, archiveId)) {
            restoreStable(current, fromUserId)
            return@withContext TeardownResult.ABORTED
        }

        persist(
            current.copy(
                transitionState = SaveOwnershipEntity.STATE_CLEARED,
                ownerUserId = null,
                contentHash = null
            )
        )
        TeardownResult.RECLAIMED
    }

    /**
     * Redoes the removal half for an artifact that is already RECLAIMED. Recovery entry point:
     * the archive exists, so the only outstanding work is the re-hash and the delete.
     */
    suspend fun resumeRemoval(
        row: SaveOwnershipEntity,
        fromUserId: Long
    ): TeardownResult = withContext(Dispatchers.IO) {
        val gameId = row.gameId ?: return@withContext TeardownResult.NOTHING_TO_DO
        val game = gameDao.getById(gameId) ?: return@withContext TeardownResult.NOTHING_TO_DO
        val archiveId = row.archivedCacheId
        if (archiveId == null || !saveCacheManager.verifyCachedArchive(archiveId)) {
            Logger.warn(TAG, "Resumed artifact has no verified archive; re-archiving | game=$gameId")
            return@withContext tearDown(
                row.copy(transitionState = SaveOwnershipEntity.STATE_RECLAIMING),
                fromUserId,
                row.pendingOwnerUserId ?: fromUserId
            )
        }
        if (!removeVerified(row, game, archiveId)) {
            restoreStable(row, fromUserId)
            return@withContext TeardownResult.ABORTED
        }
        persist(
            row.copy(
                transitionState = SaveOwnershipEntity.STATE_CLEARED,
                ownerUserId = null,
                contentHash = null
            )
        )
        TeardownResult.RECLAIMED
    }

    /**
     * Writes the incoming account's cached save into the live slot.
     *
     * Reads only the local cache, so it works with no server reachable. A game whose newest local
     * copy is known to be behind the server is left empty and flagged rather than filled with a
     * stale save: an empty slot is recovered by a sync, an overwritten one is not.
     */
    suspend fun place(
        artifact: AccountSwitchArtifact,
        toUserId: Long
    ): PlacementResult = withContext(Dispatchers.IO) {
        val game = gameDao.getById(artifact.gameId) ?: return@withContext PlacementResult.NOTHING_TO_PLACE
        val priorRow = artifact.ownershipId?.let { saveOwnershipDao.getById(it) }

        val targetPath = priorRow?.savePath
            ?: artifact.savePath
            ?: resolveTargetPath(game, artifact.emulatorId)

        if (priorRow != null && targetPath != null && priorRow.savePath != targetPath) {
            saveOwnershipDao.delete(priorRow.savePath, priorRow.emulatorId)
        }
        val existingRow = priorRow?.takeIf { it.savePath == targetPath }

        if (heldByAnotherAccount(existingRow, toUserId)) {
            Logger.warn(
                TAG,
                "Refusing to place over game ${artifact.gameId} at $targetPath: still owned by user ${existingRow?.ownerUserId} with bytes on disk"
            )
            return@withContext PlacementResult.FAILED
        }

        val cache = saveCacheDao.getPlaceableForOwner(artifact.gameId, toUserId)
        if (cache == null) {
            val anyCopy = saveCacheDao.getNewestForOwner(artifact.gameId, toUserId)
            if (anyCopy == null) {
                existingRow?.let { saveOwnershipDao.delete(it.savePath, it.emulatorId) }
                return@withContext PlacementResult.NOTHING_TO_PLACE
            }
            Logger.info(
                TAG,
                "Leaving game ${artifact.gameId} empty for user $toUserId: newest local copy was not the server's at last sync"
            )
            markNeedsSync(existingRow, artifact, targetPath, toUserId)
            return@withContext PlacementResult.NEEDS_SYNC
        }

        if (targetPath == null) {
            Logger.warn(TAG, "Cannot resolve a save location for game ${artifact.gameId}; leaving it empty")
            markNeedsSync(existingRow, artifact, null, toUserId)
            return@withContext PlacementResult.NEEDS_SYNC
        }

        val applying = persist(
            (existingRow ?: newRow(artifact, targetPath)).copy(
                savePath = targetPath,
                gameId = artifact.gameId,
                channelName = cache.channelName ?: artifact.channelName,
                transitionState = SaveOwnershipEntity.STATE_APPLYING,
                pendingOwnerUserId = toUserId,
                incomingCacheId = cache.id,
                needsSync = false
            )
        )

        if (!saveCacheManager.restoreSave(cache.id, targetPath)) {
            Logger.error(TAG, "Failed to place cache ${cache.id} for game ${artifact.gameId} at $targetPath")
            markNeedsSync(applying, artifact, targetPath, toUserId)
            return@withContext PlacementResult.FAILED
        }

        persist(
            applying.copy(
                ownerUserId = toUserId,
                contentHash = cache.contentHash,
                transitionState = SaveOwnershipEntity.STATE_STABLE,
                pendingOwnerUserId = null,
                archivedCacheId = null,
                incomingCacheId = null,
                needsSync = false
            )
        )
        PlacementResult.PLACED
    }

    fun artifactFor(row: SaveOwnershipEntity): AccountSwitchArtifact? {
        val gameId = row.gameId ?: return null
        return AccountSwitchArtifact(
            gameId = gameId,
            emulatorId = row.emulatorId,
            channelName = row.channelName,
            ownershipId = row.id,
            savePath = row.savePath
        )
    }

    private suspend fun archive(row: SaveOwnershipEntity, gameId: Long, fromUserId: Long): Long? {
        val result = saveCacheManager.cacheCurrentSave(
            gameId = gameId,
            emulatorId = row.emulatorId,
            savePath = row.savePath,
            channelName = row.channelName,
            needsRemoteSync = true
        )
        val cacheId = when (result) {
            is SaveCacheManager.CacheResult.Created -> result.cacheId
            is SaveCacheManager.CacheResult.Duplicate -> result.cacheId
            SaveCacheManager.CacheResult.Failed -> null
        }
        if (cacheId == null || cacheId == 0L) {
            Logger.error(TAG, "Could not archive game $gameId before removal | path=${row.savePath}")
            return null
        }
        saveCacheManager.reassignCacheOwner(cacheId, fromUserId)
        return cacheId
    }

    /**
     * Deletes the artifact's live bytes, but only after re-hashing them against the archive.
     *
     * The window between the archive and this call is exactly where an emulator Argosy did not
     * launch can flush its own save, and that flush would otherwise be deleted with no copy
     * anywhere. A mismatch aborts the artifact instead.
     */
    private suspend fun removeVerified(
        row: SaveOwnershipEntity,
        game: GameEntity,
        archiveId: Long
    ): Boolean {
        val expected = saveCacheDao.getById(archiveId)?.contentHash
        val liveHash = saveCacheManager.calculateArtifactHash(game.id, row.savePath)
        if (expected == null || liveHash == null || expected != liveHash) {
            Logger.warn(
                TAG,
                "Live save changed since the archive; aborting this artifact | game=${game.id}, path=${row.savePath}, archived=$expected, live=$liveHash"
            )
            return false
        }

        val saveId = game.saveId ?: game.titleId
        val folderHandler = saveHandlerRegistry.getFolderHandler(game.platformSlug)
        if (folderHandler != null) {
            val ok = saveSyncRepository.clearSavesForTitle(row.savePath, game.platformSlug, saveId)
            if (!ok) {
                Logger.error(
                    TAG,
                    "Scoped clear refused or failed for game ${game.id} | platform=${game.platformSlug}, saveId=$saveId, path=${row.savePath}"
                )
            }
            return ok
        }

        val paths = sourcePaths(row, game)
        if (paths.isEmpty()) {
            Logger.warn(TAG, "No source paths resolved for game ${game.id}; nothing removed | path=${row.savePath}")
            return false
        }
        var allOk = true
        for (path in paths) {
            if (!saveSyncRepository.clearSaveAtPath(path)) allOk = false
        }
        return allOk
    }

    /**
     * Every live path this artifact occupies. GameCube resolves to the first matching .gci while
     * the artifact is all of them, so the resolved path alone is not the removal set.
     */
    private suspend fun sourcePaths(row: SaveOwnershipEntity, game: GameEntity): List<String> {
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
        val config = emulatorPackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, game.platformSlug) }
            ?: SavePathRegistry.getConfigForPlatform(row.emulatorId, game.platformSlug)
            ?: return listOf(row.savePath)
        val handler = saveHandlerRegistry.getHandler(config, game.platformSlug, row.emulatorId)
        val context = SaveContext(
            config = config,
            romPath = game.localPath,
            saveId = game.saveId ?: game.titleId,
            emulatorPackage = emulatorPackage,
            gameId = game.id,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            emulatorId = row.emulatorId,
            localSavePath = row.savePath,
            coreName = saveSyncRepository.resolveCoreForGame(game.id)
        )
        return handler.sourcePathsFor(row.savePath, context).ifEmpty { listOf(row.savePath) }
    }

    private suspend fun resolveTargetPath(game: GameEntity, emulatorId: String): String? {
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
        val coreName = saveSyncRepository.resolveCoreForGame(game.id)
        return saveSyncRepository.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = game.id
        ) ?: saveSyncRepository.constructSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            coreName = coreName,
            cachedSaveId = game.saveId ?: game.titleId,
            gameId = game.id
        )
    }

    /**
     * True when the bytes at this path still belong to another account and are physically there.
     * A teardown that aborted restores the row to stable under its original owner without
     * removing the file, so placing over it would destroy exactly what the abort protected.
     */
    private fun heldByAnotherAccount(row: SaveOwnershipEntity?, toUserId: Long): Boolean {
        if (row == null) return false
        if (row.transitionState != SaveOwnershipEntity.STATE_STABLE) return false
        val owner = row.ownerUserId ?: return false
        return owner != toUserId && java.io.File(row.savePath).exists()
    }

    private suspend fun markNeedsSync(
        row: SaveOwnershipEntity?,
        artifact: AccountSwitchArtifact,
        targetPath: String?,
        toUserId: Long
    ) {
        val base = row ?: targetPath?.let { newRow(artifact, it) } ?: return
        persist(
            base.copy(
                gameId = artifact.gameId,
                ownerUserId = toUserId,
                contentHash = null,
                transitionState = SaveOwnershipEntity.STATE_CLEARED,
                pendingOwnerUserId = null,
                incomingCacheId = null,
                needsSync = true
            )
        )
    }

    /**
     * Settles a row left mid-transition by an attempt that can never be resumed.
     *
     * An unresolved row is not inert: the collect passes read every in-transition row regardless
     * of owner, so the next unrelated switch would finish this one under its own pair of accounts.
     * A row that still names an owner goes back to stable under that owner; one that named only
     * the vanished account holds nothing and no longer identifies anybody, so it goes.
     */
    suspend fun abandonTransition(row: SaveOwnershipEntity) {
        val owner = row.ownerUserId
        if (owner == null) {
            saveOwnershipDao.delete(row.savePath, row.emulatorId)
            Logger.info(TAG, "Dropped abandoned ownership row for ${row.savePath}: it names no owner")
            return
        }
        restoreStable(row, owner)
        Logger.info(TAG, "Returned abandoned ownership row for ${row.savePath} to user $owner")
    }

    private suspend fun restoreStable(row: SaveOwnershipEntity, fromUserId: Long) {
        persist(
            row.copy(
                ownerUserId = fromUserId,
                transitionState = SaveOwnershipEntity.STATE_STABLE,
                pendingOwnerUserId = null,
                needsSync = false
            )
        )
    }

    private fun newRow(artifact: AccountSwitchArtifact, savePath: String) = SaveOwnershipEntity(
        savePath = savePath,
        emulatorId = artifact.emulatorId,
        ownerUserId = null,
        contentHash = null,
        updatedAt = Instant.now(),
        gameId = artifact.gameId,
        channelName = artifact.channelName
    )

    /**
     * Writes the row, resolving its id from the path key first.
     *
     * The table is uniquely keyed on (savePath, emulatorId) while the primary key is the row id,
     * so an upsert carrying id 0 for a path that already has a row updates nothing at all. Every
     * state advance has to land, or recovery reads a state the disk has already moved past.
     */
    private suspend fun persist(row: SaveOwnershipEntity): SaveOwnershipEntity {
        val resolvedId = row.id.takeIf { it != 0L }
            ?: saveOwnershipDao.get(row.savePath, row.emulatorId)?.id
            ?: 0L
        val stamped = row.copy(id = resolvedId, updatedAt = Instant.now())
        saveOwnershipDao.upsert(stamped)
        return saveOwnershipDao.get(stamped.savePath, stamped.emulatorId) ?: stamped
    }
}
