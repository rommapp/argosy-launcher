package com.nendo.argosy.ui.screens.quaypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.QuayPassRepository
import com.nendo.argosy.data.quaypass.QuayPassService
import com.nendo.argosy.data.social.FriendshipStatus
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.generated.MotionTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuayPassCheckInUiState(
    val focusedIndex: Int = 0,
    val pendingArrivals: List<String> = emptyList(),
    val revealedArrivals: Set<String> = emptySet(),
    val rushedArrivals: Set<String> = emptySet(),
    val friendAccountIds: Set<String> = emptySet(),
    val pendingFriendAccountIds: Set<String> = emptySet(),
    val sessionSentAccountIds: Set<String> = emptySet(),
    val queuedFriendAccountIds: Set<String> = emptySet(),
    val ticketAwardPerEncounter: Int = QuayPassCheckInViewModel.TICKETS_PER_ENCOUNTER
) {
    val arrivalSequenceRunning: Boolean get() = pendingArrivals.isNotEmpty()
    val sentAccountIds: Set<String>
        get() = sessionSentAccountIds + pendingFriendAccountIds + queuedFriendAccountIds
}

@HiltViewModel
class QuayPassCheckInViewModel @Inject constructor(
    private val repository: QuayPassRepository,
    private val service: QuayPassService,
    private val socialRepository: SocialRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuayPassCheckInUiState())
    val uiState: StateFlow<QuayPassCheckInUiState> = _uiState.asStateFlow()

    val encounters: StateFlow<List<QuayPassEncounterEntity>> =
        repository.observeEncounters()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ticketBalance: StateFlow<Int> =
        preferencesRepository.userPreferences
            .map { it.quayPassTicketBalance }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val serviceState: StateFlow<QuayPassService.QuayPassRunState> = service.runState

    private val trackedArrivals = mutableSetOf<String>()
    private var arrivalJob: Job? = null

    init {
        viewModelScope.launch {
            encounters.collect { list -> onEncountersChanged(list) }
        }
        viewModelScope.launch {
            socialRepository.friends.collect { friends ->
                val accepted = friends
                    .filter { it.friendshipStatus == FriendshipStatus.ACCEPTED }
                    .mapTo(mutableSetOf()) { it.id }
                val pending = friends
                    .filter { it.friendshipStatus == FriendshipStatus.PENDING }
                    .mapTo(mutableSetOf()) { it.id }
                _uiState.update {
                    it.copy(friendAccountIds = accepted, pendingFriendAccountIds = pending)
                }
            }
        }
        viewModelScope.launch {
            syncPreferencesRepository.quayPassPendingFriendRequests().collect { queued ->
                _uiState.update { it.copy(queuedFriendAccountIds = queued) }
            }
        }
    }

    fun createInputHandler(): InputHandler = object : InputHandler {
        override fun onUp(): InputResult =
            if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onDown(): InputResult =
            if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onConfirm(): InputResult =
            if (activateCard(_uiState.value.focusedIndex)) InputResult.HANDLED
            else InputResult.UNHANDLED
    }

    fun moveFocus(delta: Int): Boolean {
        val count = encounters.value.size
        if (count == 0) return false
        _uiState.update { it.copy(focusedIndex = (it.focusedIndex + delta).mod(count)) }
        return true
    }

    fun onCardTapped(index: Int) {
        if (index in encounters.value.indices) {
            _uiState.update { it.copy(focusedIndex = index) }
        }
        activateCard(index)
    }

    fun activateCard(index: Int): Boolean {
        val state = _uiState.value
        if (state.arrivalSequenceRunning) {
            rushArrivals()
            return true
        }
        val encounter = encounters.value.getOrNull(index) ?: return false
        val accountId = encounter.accountId ?: return false
        if (accountId in state.friendAccountIds || accountId in state.sentAccountIds) return false
        if (socialRepository.isConnected()) {
            socialRepository.sendFriendRequest(accountId)
            _uiState.update { it.copy(sessionSentAccountIds = it.sessionSentAccountIds + accountId) }
        } else {
            viewModelScope.launch {
                syncPreferencesRepository.addQuayPassPendingFriendRequest(accountId)
            }
        }
        return true
    }

    fun rushArrivals() {
        arrivalJob?.cancel()
        var rushed: List<String> = emptyList()
        _uiState.update {
            rushed = it.pendingArrivals
            it.copy(
                pendingArrivals = emptyList(),
                revealedArrivals = it.revealedArrivals + it.pendingArrivals,
                rushedArrivals = it.rushedArrivals + it.pendingArrivals
            )
        }
        rushed.forEach { fingerprint ->
            viewModelScope.launch { repository.markSeen(fingerprint) }
        }
    }

    fun markAllSeen() {
        viewModelScope.launch { repository.markAllSeen() }
    }

    fun delete(fingerprint: String) {
        viewModelScope.launch { repository.deleteEncounter(fingerprint) }
    }

    private fun onEncountersChanged(list: List<QuayPassEncounterEntity>) {
        val arrivals = list
            .filter { !it.seenByUser && it.credentialFingerprint !in trackedArrivals }
            .map { it.credentialFingerprint }
        if (arrivals.isNotEmpty()) {
            trackedArrivals.addAll(arrivals)
            _uiState.update { it.copy(pendingArrivals = it.pendingArrivals + arrivals) }
            startArrivalSequence()
        }
        val maxIndex = (list.size - 1).coerceAtLeast(0)
        _uiState.update { it.copy(focusedIndex = it.focusedIndex.coerceIn(0, maxIndex)) }
    }

    private fun startArrivalSequence() {
        if (arrivalJob?.isActive == true) return
        arrivalJob = viewModelScope.launch {
            while (true) {
                val next = _uiState.value.pendingArrivals.firstOrNull() ?: break
                _uiState.update {
                    it.copy(
                        pendingArrivals = it.pendingArrivals - next,
                        revealedArrivals = it.revealedArrivals + next,
                        rushedArrivals = it.rushedArrivals - next
                    )
                }
                viewModelScope.launch { repository.markSeen(next) }
                delay(ARRIVAL_STAGGER_MS)
            }
        }
    }

    companion object {
        const val TICKETS_PER_ENCOUNTER = 1
        private val ARRIVAL_STAGGER_MS = MotionTokens.Tween.fastMs.toLong()
    }
}
