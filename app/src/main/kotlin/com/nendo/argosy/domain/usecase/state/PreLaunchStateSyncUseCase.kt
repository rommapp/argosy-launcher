package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.remote.romm.RomMState
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import javax.inject.Inject

private const val TAG = "PreLaunchStateSync"

internal data class ReconciledState(
    val state: StateCacheEntity,
    val linkWasDropped: Boolean
)

/**
 * Drops links to server states that are no longer on the server. A rom's own state list is the
 * authority on which ids are live, so an id missing from it can never be repaired by retrying:
 * downloads stay blocked and uploads keep failing against a dead object.
 *
 * A dropped row keeps its cached file and is queued to be re-created, so the slot is never
 * resolved by discarding what the player has locally.
 */
internal fun reconcileDeadServerLinks(
    localStates: List<StateCacheEntity>,
    liveServerIds: Set<Long>
): List<ReconciledState> = localStates.map { local ->
    if (local.rommSaveId == null || local.rommSaveId in liveServerIds) {
        ReconciledState(local, linkWasDropped = false)
    } else {
        ReconciledState(
            local.copy(
                rommSaveId = null,
                syncStatus = StateCacheEntity.STATUS_PENDING_UPLOAD,
                serverUpdatedAt = null,
                lastUploadedHash = null
            ),
            linkWasDropped = true
        )
    }
}

class PreLaunchStateSyncUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val preferencesRepository: UserPreferencesRepository,
    private val restoreStateUseCase: RestoreStateUseCase
) {
    sealed class Result {
        data object Ready : Result()
        data object NoConnection : Result()
        data object NotConfigured : Result()
        data class Downloaded(val count: Int) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(gameId: Long, emulatorPackage: String): Result {
        Log.d(TAG, "Pre-launch state sync check for gameId=$gameId, emulator=$emulatorPackage")

        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            Log.d(TAG, "Save sync disabled, skipping pre-launch state sync")
            return Result.NotConfigured
        }

        val api = saveSyncRepository.getApi()
        if (api == null) {
            Log.d(TAG, "No API connection, skipping pre-launch state sync")
            return Result.NoConnection
        }

        val game = gameDao.getById(gameId) ?: return Result.Ready
        val rommId = game.rommId ?: run {
            Log.d(TAG, "Game ${game.title} has no rommId, skipping pre-launch state sync")
            return Result.Ready
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val packageToResolve = emulatorConfig?.packageName ?: emulatorPackage
        val emulatorId = emulatorResolver.resolveEmulatorId(packageToResolve)
            ?: run {
                Log.d(TAG, "Cannot resolve emulator for package: $packageToResolve")
                return Result.Ready
            }

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return Result.NotConfigured
        }

        val coreId = coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)

        Log.d(TAG, "Checking server states for ${game.title}")

        val serverStates = stateCacheManager.checkServerStates(rommId, api)
        if (serverStates.isEmpty()) {
            Log.d(TAG, "No server states found for ${game.title}")
            return Result.Ready
        }

        Log.d(TAG, "Found ${serverStates.size} server states for ${game.title}")

        val localStates = stateCacheManager.getByGameAndEmulator(gameId, emulatorId)
        val reconciled = reconcileDeadServerLinks(localStates, serverStates.map { it.id }.toSet())
        reconciled.filter { it.linkWasDropped }.forEach {
            Log.w(
                TAG,
                "Slot ${it.state.slotNumber} points at a server state that no longer exists, " +
                    "dropping the link"
            )
            stateCacheManager.clearServerLink(it.state.id)
        }

        val repaired = reconciled.map { it.state }
        val localByRommId = repaired.filter { it.rommSaveId != null }.associateBy { it.rommSaveId }
        val localBySlot = repaired.associateBy { it.slotNumber }

        var downloadedCount = 0

        for (serverState in newestPerSlot(serverStates)) {
            val parsed = stateCacheManager.parseStateFileName(serverState.fileName)
            val slotNumber = parsed.slotNumber
            val linked = localByRommId[serverState.id]
            val localState = linked ?: localBySlot[slotNumber]
            val serverUpdatedAt = stateCacheManager.parseTimestamp(serverState.updatedAt)

            val shouldDownload = when {
                localState == null -> {
                    Log.d(TAG, "Server state ${serverState.fileName} (slot $slotNumber) not cached locally")
                    true
                }
                localState.rommSaveId == null ||
                    localState.syncStatus == StateCacheEntity.STATUS_PENDING_UPLOAD ||
                    localState.syncStatus == StateCacheEntity.STATUS_LOCAL_NEWER -> {
                    Log.d(TAG, "Slot $slotNumber has unsent local changes, keeping local over ${serverState.fileName}")
                    false
                }
                linked == null -> {
                    Log.d(TAG, "Slot $slotNumber is linked to a different server state, skipping ${serverState.fileName}")
                    false
                }
                localState.syncStatus == StateCacheEntity.STATUS_SERVER_NEWER -> {
                    Log.d(TAG, "Server state ${serverState.fileName} marked as newer")
                    true
                }
                serverUpdatedAt == null -> {
                    Log.w(TAG, "Server state ${serverState.fileName} has unparseable updated_at, skipping")
                    false
                }
                localState.serverUpdatedAt == null || serverUpdatedAt.isAfter(localState.serverUpdatedAt) -> {
                    Log.d(TAG, "Server state ${serverState.fileName} updated since last sync (slot $slotNumber)")
                    true
                }
                else -> {
                    false
                }
            }

            if (shouldDownload) {
                val result = stateCacheManager.downloadStateFromRomM(
                    rommStateId = serverState.id,
                    fileName = serverState.fileName,
                    api = api,
                    gameId = gameId,
                    platformSlug = game.platformSlug,
                    emulatorId = emulatorId,
                    coreId = coreId,
                    serverState = serverState
                )

                when (result) {
                    is StateCacheManager.StateCloudResult.Success -> {
                        downloadedCount++
                        Log.d(TAG, "Downloaded state ${serverState.fileName} for ${game.title}")
                        materializeToLiveDir(serverState.id, game.localPath, game.platformSlug, emulatorId, coreId)
                    }
                    is StateCacheManager.StateCloudResult.Error -> {
                        Log.e(TAG, "Failed to download state ${serverState.fileName}: ${result.message}")
                    }
                    else -> {
                        Log.w(TAG, "Download result for ${serverState.fileName}: $result")
                    }
                }
            }
        }

        return if (downloadedCount > 0) {
            Log.i(TAG, "Downloaded $downloadedCount states for ${game.title}")
            Result.Downloaded(downloadedCount)
        } else {
            Result.Ready
        }
    }

    /**
     * The one state per slot worth bringing down: the newest.
     *
     * A slot holds the latest quick-save, but a server accumulates one file per session, so
     * several of them address the same slot. Reconciled as they come, the first claims the slot and
     * the rest look like they belong to someone else and are dropped - including the recent one.
     *
     * Recency is read from the name rather than the server's `updated_at`, which is the time the
     * file was last rescanned rather than last written.
     */
    private fun newestPerSlot(serverStates: List<RomMState>): List<RomMState> =
        serverStates
            .groupBy {
                val parsed = stateCacheManager.parseStateFileName(it.fileName)
                parsed.slotNumber to parsed.channelName
            }
            .map { (_, candidates) ->
                candidates.maxByOrNull { writtenAt(it) ?: Instant.MIN } ?: candidates.first()
            }

    private fun writtenAt(serverState: RomMState): Instant? =
        stateCacheManager.parseStateFileTimestamp(serverState.fileName)
            ?: stateCacheManager.parseTimestamp(serverState.updatedAt)

    private suspend fun materializeToLiveDir(
        rommStateId: Long,
        romPath: String?,
        platformSlug: String,
        emulatorId: String,
        coreId: String?
    ) {
        if (romPath == null) {
            Log.w(TAG, "Cannot restore downloaded state to live dir: game has no local path")
            return
        }

        val cached = stateCacheManager.getByRommSaveId(rommStateId)
        if (cached == null) {
            Log.w(TAG, "Downloaded state $rommStateId not found in cache, cannot restore to live dir")
            return
        }

        when (val restore = restoreStateUseCase(
            cacheId = cached.id,
            emulatorId = emulatorId,
            platformId = platformSlug,
            romPath = romPath,
            currentCoreId = coreId
        )) {
            is RestoreStateResult.Success ->
                Log.d(TAG, "Restored downloaded state slot ${cached.slotNumber} to live dir")
            is RestoreStateResult.VersionMismatch ->
                Log.w(TAG, "Downloaded state slot ${cached.slotNumber} left in cache: core version mismatch")
            else ->
                Log.w(TAG, "Could not restore downloaded state slot ${cached.slotNumber} to live dir: $restore")
        }
    }
}
