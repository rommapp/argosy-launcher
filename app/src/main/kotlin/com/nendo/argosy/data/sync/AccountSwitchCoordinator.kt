package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.PendingSocialSyncDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveOwnershipDao
import com.nendo.argosy.data.local.dao.StateOwnershipDao
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.local.entity.SaveOwnershipEntity
import com.nendo.argosy.data.local.entity.StateOwnershipEntity
import com.nendo.argosy.data.preferences.AccountSwitchMarkerStore
import com.nendo.argosy.data.remote.romm.RomMAchievementService
import com.nendo.argosy.data.remote.romm.RomMApiProvider
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.util.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AccountSwitch"

/**
 * Moves the device from one RomM account to another.
 *
 * The order is forced by what each step needs from the identity: unsent queued work and archives
 * have to be written while the outgoing account is still current so they are attributed to it, and
 * placements have to run after the swap so the new owner is recorded against the bytes. Within
 * each pass the work is per artifact and each artifact's state is persisted as it advances, so an
 * interrupted switch resumes file by file rather than restarting a phase.
 *
 * A marker in SharedPreferences spans the whole run. It blocks launches and a second switch,
 * gates every background writer of save bytes, and is what [resumeIfInterrupted] keys off at
 * boot.
 */
@Singleton
class AccountSwitchCoordinator @Inject constructor(
    private val markerStore: AccountSwitchMarkerStore,
    private val blockerService: AccountSwitchBlockerService,
    private val artifactService: AccountSwitchArtifactService,
    private val stateService: AccountSwitchStateService,
    private val rommAccountRepository: RomMAccountRepository,
    private val overlayWriter: GameUserOverlayWriter,
    private val saveOwnershipDao: SaveOwnershipDao,
    private val stateOwnershipDao: StateOwnershipDao,
    private val saveCacheDao: SaveCacheDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val pendingSocialSyncDao: PendingSocialSyncDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val connectionManager: Lazy<RomMConnectionManager>,
    private val rommApiProvider: RomMApiProvider,
    private val rommAchievementService: Lazy<RomMAchievementService>,
    private val retroAchievementsRepository: Lazy<RetroAchievementsRepository>,
    private val socialRepository: Lazy<SocialRepository>
) {
    private val switchMutex = Mutex()

    private val _progress = MutableStateFlow<AccountSwitchProgress>(AccountSwitchProgress.Idle)
    val progress: StateFlow<AccountSwitchProgress> = _progress.asStateFlow()

    /**
     * True while a switch is marked, including across a process restart.
     */
    fun isSwitching(): Boolean = markerStore.isSwitching()

    /**
     * Saves left empty because the incoming account's only local copy was known to be behind the
     * server. They stay empty until a sync fetches the current one.
     */
    fun observeNeedsSync(): Flow<List<SaveOwnershipEntity>> = saveOwnershipDao.observeNeedingSync()

    /**
     * Slots left empty because the incoming account's cached state was known to be behind the
     * server, or was written by a core build this device no longer runs. They stay empty until a
     * sync fetches a placeable one.
     */
    fun observeStatesNeedingSync(): Flow<List<StateOwnershipEntity>> =
        stateOwnershipDao.observeNeedingSync()

    suspend fun checkBlockers(confirmedExternalGamesClosed: Boolean): AccountSwitchBlocker? =
        blockerService.check(confirmedExternalGamesClosed)

    /**
     * Switches to [accountId]. [confirmedExternalGamesClosed] is the user's answer to the prompt
     * asking them to fully exit anything launched outside Argosy; without it the switch refuses,
     * because Argosy has no session record for an externally launched game and cannot tell.
     */
    suspend fun switchTo(
        accountId: Long,
        confirmedExternalGamesClosed: Boolean
    ): AccountSwitchProgress = switchMutex.withLock {
        withContext(Dispatchers.IO) {
            blockerService.check(confirmedExternalGamesClosed)?.let {
                Logger.info(TAG, "Switch refused: $it")
                return@withContext AccountSwitchProgress.Blocked(it).also { p -> _progress.value = p }
            }

            val target = rommAccountRepository.accounts().firstOrNull { it.id == accountId }
                ?: return@withContext AccountSwitchProgress.Blocked(AccountSwitchBlocker.UnknownAccount)
                    .also { _progress.value = it }
            val fromUserId = rommAccountRepository.activeAccount()?.rommUserId

            if (!markerStore.begin(fromUserId, target.rommUserId)) {
                return@withContext AccountSwitchProgress.Blocked(AccountSwitchBlocker.AlreadySwitching)
                    .also { _progress.value = it }
            }

            execute(target, fromUserId)
        }
    }

    /**
     * Finishes a switch that a process death interrupted. Safe to call when none is pending.
     */
    suspend fun resumeIfInterrupted(): AccountSwitchProgress = switchMutex.withLock {
        withContext(Dispatchers.IO) {
            val marker = markerStore.current() ?: return@withContext AccountSwitchProgress.Idle
            val target = rommAccountRepository.accounts()
                .firstOrNull { it.rommUserId == marker.toUserId }
            if (target == null) {
                Logger.error(
                    TAG,
                    "Interrupted switch names user ${marker.toUserId}, which has no account row; clearing the marker"
                )
                markerStore.clear()
                return@withContext AccountSwitchProgress.Failed("Target account no longer exists")
            }
            Logger.info(TAG, "Resuming interrupted switch to user ${marker.toUserId}")
            execute(target, marker.fromUserId)
        }
    }

    private suspend fun execute(
        target: RomMAccountEntity,
        fromUserId: Long?
    ): AccountSwitchProgress {
        val toUserId = target.rommUserId
        var reclaimed = 0
        var aborted = 0
        var placed = 0
        var needsSync = 0
        var statesReclaimed = 0
        var statesAborted = 0
        var statesPlaced = 0
        var statesNeedingSync = 0

        try {
            _progress.value = AccountSwitchProgress.Preparing
            socialRepository.get().suspendForAccountSwitch()
            bindQueuedWorkToOutgoing(fromUserId)

            val outgoing = collectOutgoing(fromUserId)
            val outgoingStates = stateService.outgoingRows(fromUserId)
            val teardownTotal = outgoing.size + outgoingStates.size
            outgoing.forEachIndexed { index, row ->
                _progress.value = AccountSwitchProgress.TearingDown(index, teardownTotal)
                val result = if (row.transitionState == SaveOwnershipEntity.STATE_RECLAIMED) {
                    artifactService.resumeRemoval(row, fromUserId ?: toUserId)
                } else {
                    artifactService.tearDown(row, fromUserId ?: toUserId, toUserId)
                }
                when (result) {
                    AccountSwitchArtifactService.TeardownResult.RECLAIMED -> reclaimed++
                    AccountSwitchArtifactService.TeardownResult.ABORTED -> aborted++
                    AccountSwitchArtifactService.TeardownResult.NOTHING_TO_DO -> Unit
                }
            }
            outgoingStates.forEachIndexed { index, row ->
                _progress.value = AccountSwitchProgress.TearingDown(outgoing.size + index, teardownTotal)
                val result = if (row.transitionState == StateOwnershipEntity.STATE_RECLAIMED) {
                    stateService.resumeRemoval(row, fromUserId ?: toUserId)
                } else {
                    stateService.tearDown(row, fromUserId ?: toUserId, toUserId)
                }
                when (result) {
                    AccountSwitchStateService.TeardownResult.RECLAIMED -> statesReclaimed++
                    AccountSwitchStateService.TeardownResult.ABORTED -> statesAborted++
                    AccountSwitchStateService.TeardownResult.NOTHING_TO_DO -> Unit
                }
            }

            _progress.value = AccountSwitchProgress.SwappingIdentity
            swapIdentity(target, fromUserId)

            val artifacts = collectIncoming(toUserId)
            val stateArtifacts = stateService.incomingArtifacts(toUserId)
            val placementTotal = artifacts.size + stateArtifacts.size
            artifacts.forEachIndexed { index, artifact ->
                _progress.value = AccountSwitchProgress.Placing(index, placementTotal)
                when (artifactService.place(artifact, toUserId)) {
                    AccountSwitchArtifactService.PlacementResult.PLACED -> placed++
                    AccountSwitchArtifactService.PlacementResult.NEEDS_SYNC -> needsSync++
                    AccountSwitchArtifactService.PlacementResult.FAILED -> needsSync++
                    AccountSwitchArtifactService.PlacementResult.NOTHING_TO_PLACE -> Unit
                }
            }
            stateArtifacts.forEachIndexed { index, artifact ->
                _progress.value = AccountSwitchProgress.Placing(artifacts.size + index, placementTotal)
                when (stateService.place(artifact, toUserId)) {
                    AccountSwitchStateService.PlacementResult.PLACED -> statesPlaced++
                    AccountSwitchStateService.PlacementResult.NEEDS_SYNC -> statesNeedingSync++
                    AccountSwitchStateService.PlacementResult.FAILED -> statesNeedingSync++
                    AccountSwitchStateService.PlacementResult.NOTHING_TO_PLACE -> Unit
                }
            }

            _progress.value = AccountSwitchProgress.Finishing
            socialRepository.get().resumeAfterAccountSwitch()
            markerStore.clear()

            val outcome = AccountSwitchOutcome(
                fromUserId = fromUserId,
                toUserId = toUserId,
                reclaimedArtifacts = reclaimed,
                abortedArtifacts = aborted,
                placedArtifacts = placed,
                needsSyncArtifacts = needsSync,
                reclaimedStates = statesReclaimed,
                abortedStates = statesAborted,
                placedStates = statesPlaced,
                needsSyncStates = statesNeedingSync
            )
            Logger.info(TAG, "Switch complete | $outcome")
            return AccountSwitchProgress.Completed(outcome).also { _progress.value = it }
        } catch (e: Exception) {
            Logger.error(TAG, "Switch to user $toUserId failed; marker left set so it resumes", e)
            return AccountSwitchProgress.Failed(e.message ?: "Account switch failed")
                .also { _progress.value = it }
        }
    }

    /**
     * Stamps the outgoing account onto everything it queued and never sent, while it is still the
     * live identity. Deletes nothing and drains nothing: a queue row belongs to its own account
     * and waits for that account's token, which is the whole point of the offline-apply path.
     *
     * An unowned row is the leak. Every drain reads a null owner as "the account signed in now",
     * so a row left unattributed here would be sent under the incoming account after the swap.
     * Null can only mean the outgoing account: every writer stamps the active id, so the row was
     * either written with no credentials or predates the owner columns, and in both cases the
     * account being left is the one that was live. Rows owned by a third account are untouched.
     *
     * IN_PROGRESS rows go back to PENDING. The switch marker abandons an in-flight drain rather
     * than finishing it, and nothing else ever moves that status back, so such a row would sit
     * unreachable instead of being re-offered to its owner on the next pass.
     *
     * Only meaningful before the identity swap; a resume that finds another account already live
     * has run this in its earlier attempt.
     */
    private suspend fun bindQueuedWorkToOutgoing(fromUserId: Long?) {
        if (fromUserId == null) return
        val liveUserId = rommAccountRepository.activeAccount()?.rommUserId
        if (liveUserId != fromUserId) {
            Logger.info(
                TAG,
                "Queue binding skipped: live account is user $liveUserId, not the outgoing $fromUserId"
            )
            return
        }

        val adopted = pendingSyncQueueDao.countUnowned()
        if (adopted > 0) pendingSyncQueueDao.adoptUnowned(fromUserId)
        val reopened = pendingSyncQueueDao.resetInProgressForOwner(fromUserId)

        val socialUnowned = pendingSocialSyncDao.countUnowned()
        if (socialUnowned > 0) pendingSocialSyncDao.adoptUnowned(fromUserId)
        val downloadsUnowned = downloadQueueDao.countUnowned()
        if (downloadsUnowned > 0) downloadQueueDao.adoptUnowned(fromUserId)

        Logger.info(
            TAG,
            "Queued work bound to user $fromUserId | syncAdopted=$adopted, syncReopened=$reopened, " +
                "socialAdopted=$socialUnowned, downloadsAdopted=$downloadsUnowned"
        )
    }

    /**
     * Artifacts the outgoing account still holds on disk, plus anything a previous attempt left
     * part-way through. Rows already past removal are handled by the placement pass instead.
     */
    private suspend fun collectOutgoing(fromUserId: Long?): List<SaveOwnershipEntity> {
        val owned = fromUserId?.let { saveOwnershipDao.getByOwner(it) } ?: emptyList()
        val interrupted = saveOwnershipDao.getInTransition().filter {
            it.transitionState == SaveOwnershipEntity.STATE_RECLAIMING ||
                it.transitionState == SaveOwnershipEntity.STATE_RECLAIMED
        }
        return (owned + interrupted)
            .filter { it.transitionState != SaveOwnershipEntity.STATE_CLEARED }
            .distinctBy { it.id }
    }

    /**
     * Every slot the incoming account should end up owning: the ones just torn down, anything a
     * previous attempt left mid-placement, and every game they hold a cache row for.
     */
    private suspend fun collectIncoming(toUserId: Long): List<AccountSwitchArtifact> {
        val fromRows = saveOwnershipDao.getInTransition()
            .filter {
                it.transitionState == SaveOwnershipEntity.STATE_CLEARED ||
                    it.transitionState == SaveOwnershipEntity.STATE_APPLYING
            }
            .mapNotNull { artifactService.artifactFor(it) }

        val covered = fromRows.map { it.gameId }.toSet()
        val fromCache = saveCacheDao.getGameIdsForOwner(toUserId)
            .filter { it !in covered }
            .mapNotNull { gameId ->
                val cache = saveCacheDao.getPlaceableForOwner(gameId, toUserId) ?: return@mapNotNull null
                AccountSwitchArtifact(
                    gameId = gameId,
                    emulatorId = cache.emulatorId,
                    channelName = cache.channelName,
                    ownershipId = null,
                    savePath = null
                )
            }

        return fromRows + fromCache
    }

    /**
     * Points every identity holder at the new account. The stored row moves first so a crash
     * between here and the rebind comes back up on the new account rather than a mix of both.
     */
    private suspend fun swapIdentity(target: RomMAccountEntity, fromUserId: Long?) {
        fromUserId?.let { overlayWriter.adoptLibraryIfUnclaimed(it) }
        rommAccountRepository.activate(target.id)
        overlayWriter.materialiseForOwner(target.rommUserId)
        rommApiProvider.invalidateAll()
        connectionManager.get().rebindToActiveAccount()
        rommAchievementService.get().onAppResumed()
        retroAchievementsRepository.get().invalidateUnlocksCache()
        pushRetroArchCredentials()
    }

    /**
     * RetroArch's cheevos login is one device-global config, and for a game launched outside
     * Argosy it is the only thing deciding whose RA account earns the unlocks. Leaving the
     * outgoing account's login in it hands the incoming player's progress to the wrong person.
     */
    private suspend fun pushRetroArchCredentials() {
        val written = runCatching { retroAchievementsRepository.get().syncRetroArchCredentials() }
            .getOrElse { e ->
                Logger.error(TAG, "Could not rewrite RetroArch RA credentials after the swap", e)
                return
            }
        Logger.info(TAG, "Rewrote RA credentials into $written RetroArch config(s)")
    }
}
