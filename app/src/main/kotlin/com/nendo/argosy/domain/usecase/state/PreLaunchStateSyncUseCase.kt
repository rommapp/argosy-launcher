package com.nendo.argosy.domain.usecase.state

import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.StateSerialResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMState
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import javax.inject.Inject

private const val TAG = "PreLaunchStateSync"
private const val MAX_SLOT_ATTEMPTS = 5

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
internal fun stateChannelKey(channel: String?): String =
    SaveSyncApiClient.namedChannelOrNull(channel)?.lowercase().orEmpty()

internal fun belongsToChannel(stateChannel: String?, activeChannel: String?): Boolean =
    stateChannelKey(stateChannel) == stateChannelKey(activeChannel)

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
    private val restoreStateUseCase: RestoreStateUseCase,
    private val stateSerialResolver: StateSerialResolver
) {
    sealed class Result {
        data object Ready : Result()
        data object NoConnection : Result()
        data object NotConfigured : Result()
        data class Downloaded(val count: Int) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(
        gameId: Long,
        emulatorPackage: String,
        channelName: String? = null
    ): Result {
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
        val coreVersion = if (coreId != null && emulatorId.startsWith("retroarch")) {
            coreVersionExtractor.getRetroArchCoreVersion(coreId, packageToResolve)
        } else {
            null
        }

        Log.d(TAG, "Checking server states for ${game.title}")

        val serverStates = stateCacheManager.checkServerStates(rommId, api)
        if (serverStates.isEmpty()) {
            Log.d(TAG, "No server states found for ${game.title}")
            return Result.Ready
        }

        Log.d(TAG, "Found ${serverStates.size} server states for ${game.title}")

        stateCacheManager.detachForeignSerialStates(gameId, stateSerialResolver.serialsFor(gameId, emulatorId))
        val tombstoned = stateCacheManager.getStateTombstones(gameId)

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

        val runningCore = stateCacheManager.runningCore(gameId, emulatorId)
        val repaired = reconciled.map { it.state }.filter { state ->
            val unloadable = state.rommSaveId != null && !stateCacheManager.loadableByRunningCore(state, emulatorId)
            if (unloadable) {
                Log.w(TAG, "Slot ${state.slotNumber} holds a state $runningCore cannot load; refilling it from the server")
            }
            !unloadable
        }
        val localByRommId = repaired.filter { it.rommSaveId != null }.associateBy { it.rommSaveId }
        val localBySlot = repaired.associateBy { it.slotNumber to stateChannelKey(it.channelName) }

        var downloadedCount = 0

        for (slotCandidates in candidatesPerSlot(serverStates.filter { it.id !in tombstoned })) {
            for ((attempt, serverState) in slotCandidates.withIndex()) {
                when (
                    syncSlot(
                        serverState, localByRommId, localBySlot, channelName, api, game, gameId,
                        emulatorId, coreId, coreVersion, runningCore
                    )
                ) {
                    SlotOutcome.DOWNLOADED -> downloadedCount++
                    SlotOutcome.OTHER_CORE -> if (attempt + 1 < MAX_SLOT_ATTEMPTS) continue
                    SlotOutcome.KEPT -> Unit
                }
                break
            }
        }

        return if (downloadedCount > 0) {
            Log.i(TAG, "Downloaded $downloadedCount states for ${game.title}")
            Result.Downloaded(downloadedCount)
        } else {
            Result.Ready
        }
    }

    private enum class SlotOutcome { DOWNLOADED, OTHER_CORE, KEPT }

    private suspend fun syncSlot(
        serverState: RomMState,
        localByRommId: Map<Long?, StateCacheEntity>,
        localBySlot: Map<Pair<Int, String>, StateCacheEntity>,
        channelName: String?,
        api: RomMApi,
        game: GameEntity,
        gameId: Long,
        emulatorId: String,
        coreId: String?,
        coreVersion: String?,
        runningCore: String?
    ): SlotOutcome {
        val parsed = stateCacheManager.parseStateFileName(serverState.fileName)
        if (!belongsToChannel(parsed.channelName, channelName)) return SlotOutcome.KEPT
        val slotNumber = parsed.slotNumber
        val linked = localByRommId[serverState.id]
        val localState = linked ?: localBySlot[slotNumber to stateChannelKey(parsed.channelName)]
        val serverUpdatedAt = com.nendo.argosy.data.repository.SaveSyncApiClient.parseTimestampOrNull(serverState.updatedAt)

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
            else -> false
        }

        if (!shouldDownload) return SlotOutcome.KEPT

        val result = stateCacheManager.downloadStateFromRomM(
            rommStateId = serverState.id,
            fileName = serverState.fileName,
            api = api,
            gameId = gameId,
            platformSlug = game.platformSlug,
            emulatorId = emulatorId,
            coreId = coreId,
            serverState = serverState,
            readableByCore = runningCore
        )

        return when (result) {
            is StateCacheManager.StateCloudResult.Success -> {
                Log.d(TAG, "Downloaded state ${serverState.fileName} for ${game.title}")
                materializeToLiveDir(serverState.id, game.localPath, game.platformSlug, emulatorId, coreId, coreVersion)
                SlotOutcome.DOWNLOADED
            }
            is StateCacheManager.StateCloudResult.OtherCoreFormat -> {
                Log.w(
                    TAG,
                    "Skipping ${serverState.fileName}: written by ${result.writerCore ?: "another core"}, " +
                        "$runningCore cannot load it; trying the slot's previous state"
                )
                SlotOutcome.OTHER_CORE
            }
            is StateCacheManager.StateCloudResult.Error -> {
                Log.e(TAG, "Failed to download state ${serverState.fileName}: ${result.message}")
                SlotOutcome.KEPT
            }
            else -> {
                Log.w(TAG, "Download result for ${serverState.fileName}: $result")
                SlotOutcome.KEPT
            }
        }
    }

    private fun candidatesPerSlot(serverStates: List<RomMState>): List<List<RomMState>> =
        serverStates
            .groupBy {
                val parsed = stateCacheManager.parseStateFileName(it.fileName)
                parsed.slotNumber to stateChannelKey(parsed.channelName)
            }
            .map { (_, candidates) ->
                candidates.sortedByDescending { stateCacheManager.serverStateWrittenAt(it) ?: Instant.MIN }
            }

    private suspend fun materializeToLiveDir(
        rommStateId: Long,
        romPath: String?,
        platformSlug: String,
        emulatorId: String,
        coreId: String?,
        coreVersion: String?
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
            currentCoreId = coreId,
            currentCoreVersion = coreVersion
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
