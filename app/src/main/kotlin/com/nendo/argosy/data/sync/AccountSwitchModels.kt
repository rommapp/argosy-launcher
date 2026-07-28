package com.nendo.argosy.data.sync

/**
 * Why an account switch will not start.
 *
 * Every one of these means work is in flight that would either be attributed to the wrong
 * account or be destroyed by the teardown, so the switch refuses rather than racing it.
 */
sealed interface AccountSwitchBlocker {
    data object AlreadySwitching : AccountSwitchBlocker
    data object ActiveSession : AccountSwitchBlocker
    data class ExternalGameRecentlyForeground(
        val packageName: String,
        val secondsAgo: Long
    ) : AccountSwitchBlocker

    data object ActiveDownloads : AccountSwitchBlocker
    data object EmulatorDownload : AccountSwitchBlocker
    data object SteamDownload : AccountSwitchBlocker
    data object LibrarySyncRunning : AccountSwitchBlocker
    data object NetplaySession : AccountSwitchBlocker

    /**
     * Argosy cannot see an externally launched game, so the user has to state that they closed
     * one. Raised until the caller passes an explicit acknowledgement.
     */
    data object ExitConfirmationRequired : AccountSwitchBlocker

    data object UnknownAccount : AccountSwitchBlocker
}

sealed interface AccountSwitchProgress {
    data object Idle : AccountSwitchProgress
    data object Preparing : AccountSwitchProgress
    data class TearingDown(val done: Int, val total: Int) : AccountSwitchProgress
    data object SwappingIdentity : AccountSwitchProgress
    data class Placing(val done: Int, val total: Int) : AccountSwitchProgress
    data object Finishing : AccountSwitchProgress
    data class Blocked(val blocker: AccountSwitchBlocker) : AccountSwitchProgress
    data class Failed(val message: String) : AccountSwitchProgress
    data class Completed(val outcome: AccountSwitchOutcome) : AccountSwitchProgress
}

/**
 * What the switch did, per artifact class.
 *
 * [abortedArtifacts] is the count whose live bytes changed between the archive and the removal
 * check. Those keep the outgoing account's save on disk on purpose -- the invariant is that
 * bytes survive until an archive of them is proven good, and an artifact that moved under us has
 * no such archive.
 *
 * [needsSyncArtifacts] is the count left empty because the incoming account's only local copy is
 * known to be behind the server. An empty slot is recoverable by syncing; a slot overwritten with
 * a stale save is not.
 */
data class AccountSwitchOutcome(
    val fromUserId: Long?,
    val toUserId: Long,
    val reclaimedArtifacts: Int,
    val abortedArtifacts: Int,
    val placedArtifacts: Int,
    val needsSyncArtifacts: Int,
    val reclaimedStates: Int = 0,
    val abortedStates: Int = 0,
    val placedStates: Int = 0,
    val needsSyncStates: Int = 0
)

/**
 * One save on disk, or one save that wants to be on disk, as the switch sees it. Teardown and
 * placement for the same slot are the same artifact so the two halves cannot disagree on which
 * emulator or channel they are acting for.
 */
data class AccountSwitchArtifact(
    val gameId: Long,
    val emulatorId: String,
    val channelName: String?,
    val ownershipId: Long?,
    val savePath: String?
)

/**
 * One save state on disk, or one that wants to be, as the switch sees it.
 *
 * A game has as many of these as it has occupied slots, so the identity is the slot tuple
 * `state_cache` is uniquely indexed on rather than the game: teardown and placement for the same
 * slot must be the same artifact or the two halves act on different files.
 */
data class StateSwitchArtifact(
    val gameId: Long,
    val emulatorId: String,
    val slotNumber: Int,
    val channelName: String?,
    val coreId: String?,
    val cacheId: Long?,
    val ownershipId: Long?,
    val statePath: String?
)
