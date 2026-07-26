package com.nendo.argosy.ui.screens.quaypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.QuayPassRepository
import com.nendo.argosy.data.quaypass.QuayPassService
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.FriendshipStatus
import com.nendo.argosy.data.social.QuayPassCheckin
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class CheckInCard(
    val key: String,
    val accountId: String?,
    val username: String,
    val displayName: String?,
    val avatarPngBase64: String?,
    val avatarSparse: String?,
    val greeting: String?,
    val lastGameTitle: String?,
    val coverThumbUrl: String?,
    val encounteredAt: Instant,
    val isFriend: Boolean,
    val isBlocked: Boolean,
    val requestSent: Boolean,
    val requestReceived: Boolean
)

data class QuayPassCheckInUiState(
    val focusedIndex: Int = 0,
    val pendingArrivals: List<String> = emptyList(),
    val revealedArrivals: Set<String> = emptySet(),
    val rushedArrivals: Set<String> = emptySet(),
    val showGreetingEditor: Boolean = false,
    val ticketAwardPerEncounter: Int = QuayPassCheckInViewModel.TICKETS_PER_ENCOUNTER
) {
    val arrivalSequenceRunning: Boolean get() = pendingArrivals.isNotEmpty()
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

    private val sessionSentAccountIds = MutableStateFlow<Set<String>>(emptySet())

    private val encounters: StateFlow<List<QuayPassEncounterEntity>> =
        repository.observeEncounters()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cards: StateFlow<List<CheckInCard>> =
        combine(
            encounters,
            socialRepository.quayPassCheckins,
            socialRepository.friends,
            syncPreferencesRepository.quayPassPendingFriendRequests(),
            sessionSentAccountIds
        ) { encounters, checkins, friends, queued, sessionSent ->
            buildCards(encounters, checkins, friends, queued, sessionSent)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ticketBalance: StateFlow<Int> =
        preferencesRepository.userPreferences
            .map { it.quayPassTicketBalance }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val greeting: StateFlow<String> =
        preferencesRepository.userPreferences
            .map { it.quayPassGreeting ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val serviceState: StateFlow<QuayPassService.QuayPassRunState> = service.runState

    fun setGreeting(text: String) {
        viewModelScope.launch {
            syncPreferencesRepository.setQuayPassGreeting(text)
        }
    }

    private val trackedArrivals = mutableSetOf<String>()
    private var arrivalJob: Job? = null

    init {
        socialRepository.requestQuayPassCheckins()
        viewModelScope.launch {
            encounters.collect { list -> onEncountersChanged(list) }
        }
        viewModelScope.launch {
            cards.collect { list ->
                val maxIndex = (list.size - 1).coerceAtLeast(0)
                _uiState.update { it.copy(focusedIndex = it.focusedIndex.coerceIn(0, maxIndex)) }
            }
        }
    }

    private fun buildCards(
        encounters: List<QuayPassEncounterEntity>,
        checkins: List<QuayPassCheckin>,
        friends: List<Friend>,
        queued: Set<String>,
        sessionSent: Set<String>
    ): List<CheckInCard> {
        val manifestByKey = checkins.associateBy { it.userId to it.encounteredAtEpochSec }
        val acceptedFriends = friends.filter { it.friendshipStatus == FriendshipStatus.ACCEPTED }
        val friendIds = acceptedFriends.mapTo(mutableSetOf()) { it.id }
        val friendAvatarById = acceptedFriends.associate { it.id to it.quayPassAvatar }

        val localCards = encounters.map { enc ->
            val manifest = enc.accountId?.let { manifestByKey[it to enc.encounteredAt.epochSecond] }
            val isFriend = manifest?.isFriend ?: (enc.accountId in friendIds)
            CheckInCard(
                key = enc.credentialFingerprint,
                accountId = enc.accountId,
                username = enc.username,
                displayName = enc.displayName,
                avatarPngBase64 = enc.avatarBlobBase64,
                avatarSparse = if (isFriend) enc.accountId?.let { friendAvatarById[it] } else null,
                greeting = manifest?.message ?: enc.greeting,
                lastGameTitle = if (manifest != null) manifest.lastGameTitle else enc.lastGameTitle,
                coverThumbUrl = manifest?.coverThumbUrl,
                encounteredAt = enc.encounteredAt,
                isFriend = isFriend,
                isBlocked = manifest?.isBlocked ?: false,
                requestSent = manifest?.requestSent
                    ?: (enc.accountId != null && (enc.accountId in sessionSent || enc.accountId in queued)),
                requestReceived = manifest?.requestReceived ?: false
            )
        }

        val localKeys = encounters
            .mapNotNull { enc -> enc.accountId?.let { it to enc.encounteredAt.epochSecond } }
            .toSet()
        val manifestOnly = checkins
            .filterNot { (it.userId to it.encounteredAtEpochSec) in localKeys }
            .map { manifest ->
                CheckInCard(
                    key = "m:${manifest.userId}:${manifest.encounteredAtEpochSec}",
                    accountId = manifest.userId,
                    username = manifest.username,
                    displayName = manifest.displayName,
                    avatarPngBase64 = manifest.avatarRasterPng,
                    avatarSparse = if (manifest.isFriend) friendAvatarById[manifest.userId] else null,
                    greeting = manifest.message,
                    lastGameTitle = manifest.lastGameTitle,
                    coverThumbUrl = manifest.coverThumbUrl,
                    encounteredAt = Instant.ofEpochSecond(manifest.encounteredAtEpochSec),
                    isFriend = manifest.isFriend,
                    isBlocked = manifest.isBlocked,
                    requestSent = manifest.requestSent,
                    requestReceived = manifest.requestReceived
                )
            }

        return (localCards + manifestOnly).sortedByDescending { it.encounteredAt }
    }

    fun createInputHandler(): InputHandler = object : InputHandler {
        override fun onUp(): InputResult =
            if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onDown(): InputResult =
            if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onConfirm(): InputResult =
            if (activateCard(_uiState.value.focusedIndex)) InputResult.HANDLED
            else InputResult.UNHANDLED

        override fun onSecondaryAction(): InputResult {
            if (!_uiState.value.arrivalSequenceRunning) openGreetingEditor()
            return InputResult.HANDLED
        }
    }

    fun openGreetingEditor() = _uiState.update { it.copy(showGreetingEditor = true) }

    fun dismissGreetingEditor() = _uiState.update { it.copy(showGreetingEditor = false) }

    fun moveFocus(delta: Int): Boolean {
        val count = cards.value.size
        if (count == 0) return false
        _uiState.update { it.copy(focusedIndex = (it.focusedIndex + delta).mod(count)) }
        return true
    }

    fun onCardTapped(index: Int) {
        if (index in cards.value.indices) {
            _uiState.update { it.copy(focusedIndex = index) }
        }
        activateCard(index)
    }

    fun activateCard(index: Int): Boolean {
        if (_uiState.value.arrivalSequenceRunning) {
            rushArrivals()
            return true
        }
        val card = cards.value.getOrNull(index) ?: return false
        val accountId = card.accountId ?: return false
        return when {
            card.isBlocked || card.isFriend || card.requestSent -> false
            card.requestReceived -> {
                socialRepository.acceptFriend(accountId)
                socialRepository.requestQuayPassCheckins()
                true
            }
            socialRepository.isConnected() -> {
                socialRepository.sendFriendRequest(accountId)
                sessionSentAccountIds.update { it + accountId }
                true
            }
            else -> {
                viewModelScope.launch {
                    syncPreferencesRepository.addQuayPassPendingFriendRequest(accountId)
                }
                true
            }
        }
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
