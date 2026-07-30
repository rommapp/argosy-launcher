package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.remote.romm.DeviceAuthOutcome
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.pollDeviceAuthUntilResolved
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.sync.AccountSwitchBlocker
import com.nendo.argosy.data.sync.AccountSwitchCoordinator
import com.nendo.argosy.data.sync.AccountSwitchOutcome
import com.nendo.argosy.data.sync.AccountSwitchProgress
import com.nendo.argosy.data.sync.AccountPendingWork
import com.nendo.argosy.data.sync.AccountRemovalService
import com.nendo.argosy.data.sync.UnflushedQueuePolicy
import com.nendo.argosy.ui.screens.settings.AccountPairingState
import com.nendo.argosy.ui.screens.settings.AccountUi
import com.nendo.argosy.ui.screens.settings.AccountsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the accounts list, the add-account pairing loop, switching and removal.
 *
 * Add-account deliberately does not reuse [ServerSettingsDelegate.connectToRomm]: that path is
 * URL-entry-first and models a successful pairing as replacing the single stored connection. A
 * second account on a server this device already knows has no URL step, so pairing starts from
 * the active account's baseUrl and reports its own outcome.
 */
class AccountsSettingsDelegate @Inject constructor(
    private val accountRepository: RomMAccountRepository,
    private val switchCoordinator: AccountSwitchCoordinator,
    private val removalService: AccountRemovalService,
    private val romMRepository: RomMRepository,
    private val gameRepository: GameRepository
) {
    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    private var pairingJob: Job? = null

    fun start(scope: CoroutineScope) {
        accountRepository.observeAccounts()
            .onEach { rows ->
                val ui = rows
                    .sortedByDescending { it.lastLoginAt }
                    .map { row ->
                        AccountUi(
                            id = row.id,
                            username = row.username.ifBlank { "RomM user ${row.rommUserId}" },
                            serverLabel = serverLabel(row.baseUrl),
                            isActive = row.isActive
                        )
                    }
                _state.update { it.copy(accounts = ui, isLoading = false) }
            }
            .launchIn(scope)

        var seededProgress = false
        switchCoordinator.progress
            .onEach { progress ->
                if (!seededProgress) {
                    seededProgress = true
                    return@onEach
                }
                applyProgress(progress)
            }
            .launchIn(scope)

        switchCoordinator.observeNeedsSync()
            .onEach { rows ->
                val titles = titlesFor(rows.mapNotNull { it.gameId })
                _state.update { it.copy(needsSyncSaveTitles = titles) }
            }
            .launchIn(scope)

        switchCoordinator.observeStatesNeedingSync()
            .onEach { rows ->
                val titles = titlesFor(rows.mapNotNull { it.gameId })
                _state.update { it.copy(needsSyncStateTitles = titles) }
            }
            .launchIn(scope)
    }

    fun moveRowActionFocus(direction: Int, actionCount: Int): Boolean {
        if (actionCount <= 1) return false
        _state.update { it.copy(rowActionIndex = (it.rowActionIndex + direction).mod(actionCount)) }
        return true
    }

    fun resetRowActionFocus() {
        _state.update { it.copy(rowActionIndex = 0) }
    }

    fun setRowActionIndex(index: Int) {
        _state.update { it.copy(rowActionIndex = index.coerceAtLeast(0)) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null, switchBlocker = null, switchFailure = null) }
    }

    fun requestSwitch(accountId: Long) {
        _state.update {
            it.copy(
                exitPromptAccountId = accountId,
                exitPromptIsForAdd = false,
                switchBlocker = null,
                switchFailure = null,
                notice = null
            )
        }
    }

    fun requestAddAccount() {
        val active = _state.value.activeAccount
        if (active == null) {
            _state.update { it.copy(notice = "Sign in to a RomM server first, under RomM.") }
            return
        }
        _state.update {
            it.copy(
                exitPromptAccountId = active.id,
                exitPromptIsForAdd = true,
                switchBlocker = null,
                switchFailure = null,
                notice = null
            )
        }
    }

    fun cancelExitPrompt() {
        _state.update { it.copy(exitPromptAccountId = null, exitPromptIsForAdd = false) }
    }

    /**
     * The user's answer to the prompt asking them to fully exit anything launched outside Argosy.
     * It is passed straight through as `confirmedExternalGamesClosed`; Argosy has no session
     * record for an externally launched game and cannot establish this on its own.
     */
    fun confirmExitPrompt(scope: CoroutineScope) {
        val current = _state.value
        val accountId = current.exitPromptAccountId ?: return
        val isForAdd = current.exitPromptIsForAdd
        _state.update { it.copy(exitPromptAccountId = null, exitPromptIsForAdd = false) }
        if (!isForAdd) {
            runSwitch(scope, accountId)
            return
        }
        scope.launch {
            val blocker = switchCoordinator.checkBlockers(confirmedExternalGamesClosed = true)
            if (blocker != null) {
                _state.update { it.copy(switchBlocker = blockerMessage(blocker)) }
                return@launch
            }
            startPairing(scope)
        }
    }

    private fun runSwitch(scope: CoroutineScope, accountId: Long) {
        if (_state.value.switchInProgress) return
        scope.launch {
            _state.update {
                it.copy(
                    switchInProgress = true,
                    switchProgressLabel = "Preparing",
                    switchBlocker = null,
                    switchFailure = null,
                    notice = null
                )
            }
            clearSwitchIfIdle(
                switchCoordinator.switchTo(accountId, confirmedExternalGamesClosed = true)
            )
        }
    }

    /**
     * A failed switch leaves the marker set on purpose, which keeps launches blocked until the
     * run is finished. Without this the only way out is a reboot.
     */
    fun retryInterruptedSwitch(scope: CoroutineScope) {
        if (_state.value.isResumingSwitch) return
        scope.launch {
            _state.update {
                it.copy(
                    isResumingSwitch = true,
                    switchInProgress = true,
                    switchFailure = null,
                    switchProgressLabel = "Resuming"
                )
            }
            val result = switchCoordinator.resumeIfInterrupted()
            _state.update { it.copy(isResumingSwitch = false) }
            clearSwitchIfIdle(result)
        }
    }

    /**
     * The coordinator publishes every terminal outcome to its progress flow, so [applyProgress]
     * already owns them. The one case it cannot see is a resume with no marker set, which returns
     * Idle without publishing anything.
     */
    private fun clearSwitchIfIdle(result: AccountSwitchProgress) {
        if (result != AccountSwitchProgress.Idle) return
        _state.update { it.copy(switchInProgress = false, switchProgressLabel = null) }
    }

    private fun applyProgress(progress: AccountSwitchProgress) {
        _state.update { current ->
            when (progress) {
                AccountSwitchProgress.Idle -> current
                AccountSwitchProgress.Preparing ->
                    current.copy(switchInProgress = true, switchProgressLabel = "Preparing")
                is AccountSwitchProgress.TearingDown -> current.copy(
                    switchInProgress = true,
                    switchProgressLabel =
                        "Archiving saves ${progress.done + 1} of ${progress.total.coerceAtLeast(1)}"
                )
                AccountSwitchProgress.SwappingIdentity ->
                    current.copy(switchInProgress = true, switchProgressLabel = "Swapping accounts")
                is AccountSwitchProgress.Placing -> current.copy(
                    switchInProgress = true,
                    switchProgressLabel =
                        "Restoring saves ${progress.done + 1} of ${progress.total.coerceAtLeast(1)}"
                )
                AccountSwitchProgress.Finishing ->
                    current.copy(switchInProgress = true, switchProgressLabel = "Finishing")
                is AccountSwitchProgress.Completed -> current.copy(
                    switchInProgress = false,
                    switchProgressLabel = null,
                    rowActionIndex = 0,
                    notice = completionNotice(progress.outcome)
                )
                is AccountSwitchProgress.Blocked -> current.copy(
                    switchInProgress = false,
                    switchProgressLabel = null,
                    switchBlocker = blockerMessage(progress.blocker)
                )
                is AccountSwitchProgress.Failed -> current.copy(
                    switchInProgress = false,
                    switchProgressLabel = null,
                    switchFailure = progress.message
                )
            }
        }
    }

    fun requestRemoval(scope: CoroutineScope, accountId: Long) {
        scope.launch {
            val pending = removalService.pendingWork(accountId)
            val isLast = _state.value.accounts.size <= 1
            _state.update {
                it.copy(
                    removalAccountId = accountId,
                    removalPendingSummary = pending?.let { work -> pendingSummary(work) },
                    removalHasPendingWork = pending?.isEmpty == false,
                    removalIsLastAccount = isLast,
                    notice = null
                )
            }
        }
    }

    fun cancelRemoval() {
        _state.update {
            it.copy(
                removalAccountId = null,
                removalPendingSummary = null,
                removalHasPendingWork = false,
                removalIsLastAccount = false
            )
        }
    }

    fun confirmRemoval(scope: CoroutineScope, policy: UnflushedQueuePolicy) {
        val accountId = _state.value.removalAccountId ?: return
        if (_state.value.isRemoving) return
        scope.launch {
            _state.update { it.copy(isRemoving = true) }
            val result = removalService.remove(accountId, policy)
            _state.update { current ->
                current.copy(
                    isRemoving = false,
                    removalAccountId = null,
                    removalPendingSummary = null,
                    removalHasPendingWork = false,
                    removalIsLastAccount = false,
                    rowActionIndex = 0,
                    notice = removalNotice(result)
                )
            }
        }
    }

    fun startPairing(scope: CoroutineScope) {
        pairingJob?.cancel()
        romMRepository.cancelDeviceAuth()
        pairingJob = scope.launch {
            val baseUrl = accountRepository.activeAccount()?.baseUrl
            if (baseUrl.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        pairing = AccountPairingState(
                            active = true,
                            error = "No server is configured on this device yet."
                        )
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    pairing = AccountPairingState(active = true, connecting = true),
                    notice = null
                )
            }
            when (val init = romMRepository.beginDeviceAuth(baseUrl)) {
                is RomMResult.Success -> {
                    val data = init.data
                    _state.update {
                        it.copy(
                            pairing = AccountPairingState(
                                active = true,
                                connecting = false,
                                userCode = data.userCode,
                                verificationUrl = data.verificationPathComplete
                            )
                        )
                    }
                    pollForApproval(data.deviceCode, data.interval, data.expiresIn)
                }
                is RomMResult.Error -> failPairing(init.message)
            }
        }
    }

    /**
     * Stops the poll loop as well as clearing the screen. Leaving it running means a pairing the
     * user backed out of can still land and move the device onto another account.
     */
    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        romMRepository.cancelDeviceAuth()
        _state.update { it.copy(pairing = AccountPairingState()) }
    }

    private suspend fun pollForApproval(deviceCode: String, interval: Int, expiresIn: Int) {
        val outcome = pollDeviceAuthUntilResolved(interval, expiresIn) {
            val hasExisting = accountRepository.accountCount() > 0
            romMRepository.pollDeviceAuthOnce(deviceCode, activateOnSuccess = !hasExisting)
        }
        if (!currentCoroutineContext().isActive) return
        when (outcome) {
            is DeviceAuthOutcome.AddedAccount -> {
                val added = accountRepository.accounts().firstOrNull { it.id == outcome.accountId }
                _state.update {
                    it.copy(
                        pairing = AccountPairingState(),
                        rowActionIndex = 0,
                        notice = "Added ${added?.username?.ifBlank { null } ?: "the account"}. " +
                            "Still signed in as before; switch to start using it."
                    )
                }
            }
            is DeviceAuthOutcome.Approved -> {
                val added = accountRepository.activeAccount()
                _state.update {
                    it.copy(
                        pairing = AccountPairingState(),
                        rowActionIndex = 0,
                        notice = added?.let { row ->
                            "Signed in as ${row.username.ifBlank { "RomM user ${row.rommUserId}" }}."
                        } ?: "Account added."
                    )
                }
            }
            DeviceAuthOutcome.Denied -> failPairing("Pairing was denied on the server")
            DeviceAuthOutcome.Expired -> failPairing("Pairing code expired, start again")
            is DeviceAuthOutcome.Failed -> failPairing(outcome.message)
        }
    }

    private fun failPairing(message: String) {
        romMRepository.cancelDeviceAuth()
        _state.update {
            it.copy(pairing = AccountPairingState(active = true, error = message))
        }
    }

    private suspend fun titlesFor(gameIds: List<Long>): List<String> {
        val ids = gameIds.distinct()
        if (ids.isEmpty()) return emptyList()
        return gameRepository.getByIds(ids).map { it.title }.sorted()
    }

    private fun serverLabel(baseUrl: String): String = baseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    private fun pendingSummary(work: AccountPendingWork): String? = work.describe()

    private fun removalNotice(result: AccountRemovalResult): String = when (result) {
        AccountRemovalResult.UnknownAccount -> "That account is no longer on this device."
        AccountRemovalResult.SwitchInProgress ->
            "An account switch is still running. Let it finish, then try again."
        is AccountRemovalResult.Refused ->
            "Kept the queued work, so the account was not removed. " +
                (pendingSummary(result.pending) ?: "Nothing is outstanding.")
        is AccountRemovalResult.Removed -> buildString {
            append("Account removed.")
            if (result.reclaimedArtifacts > 0) {
                append(" Archived ${result.reclaimedArtifacts} saves before clearing them.")
            }
            if (result.abortedArtifacts > 0) {
                append(
                    " ${result.abortedArtifacts} saves changed while archiving and were left on disk."
                )
            }
            append(" The device is still registered on the RomM server; revoke it there.")
        }
    }

    private fun completionNotice(outcome: AccountSwitchOutcome): String = buildString {
        append("Switched account.")
        if (outcome.abortedArtifacts > 0 || outcome.abortedStates > 0) {
            append(
                " ${outcome.abortedArtifacts + outcome.abortedStates} files changed mid-archive " +
                    "and were left as they were."
            )
        }
        if (outcome.needsSyncArtifacts > 0 || outcome.needsSyncStates > 0) {
            append(
                " ${outcome.needsSyncArtifacts + outcome.needsSyncStates} slots were left empty " +
                    "because the local copy was known to be behind the server."
            )
        }
    }

    private fun blockerMessage(blocker: AccountSwitchBlocker): String = when (blocker) {
        AccountSwitchBlocker.AlreadySwitching ->
            "A switch is already running. Wait for it to finish."
        AccountSwitchBlocker.ActiveSession ->
            "A game session is still open. Close the game, then try again."
        is AccountSwitchBlocker.ExternalGameRecentlyForeground ->
            "${blocker.packageName} was on screen ${blocker.secondsAgo}s ago. " +
                "Fully exit it, then try again."
        AccountSwitchBlocker.ActiveDownloads ->
            "Game downloads are still running. Let them finish or cancel them."
        AccountSwitchBlocker.EmulatorDownload ->
            "An emulator download is still running. Let it finish or cancel it."
        AccountSwitchBlocker.SteamDownload ->
            "A Steam download is still running. Let it finish or cancel it."
        AccountSwitchBlocker.LibrarySyncRunning ->
            "A library sync is running. Let it finish, then try again."
        AccountSwitchBlocker.NetplaySession ->
            "A netplay session is open. Leave it, then try again."
        AccountSwitchBlocker.ExitConfirmationRequired ->
            "Confirm that any game launched outside Argosy is fully closed."
        AccountSwitchBlocker.UnknownAccount ->
            "That account is no longer on this device."
    }
}
