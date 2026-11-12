package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.remote.ssl.ClientCertStore
import com.nendo.argosy.data.remote.ssl.ClientCertTarget
import com.nendo.argosy.ui.common.ClientCertImportStatus
import com.nendo.argosy.ui.common.ClientCertPasswordEntry
import com.nendo.argosy.ui.common.ClientCertSummary
import com.nendo.argosy.ui.common.toImportStatus
import com.nendo.argosy.ui.common.toSummary
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryRow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ClientCertAction { TOGGLE_ROMM, TOGGLE_JELLYFIN, IMPORT, REMOVE }

data class ClientCertState(
    val summary: ClientCertSummary? = null,
    val managerOpenFor: ClientCertTarget? = null,
    val managerFocusIndex: Int = 0,
    val passwordEntry: ClientCertPasswordEntry? = null,
    val confirmingRemoval: Boolean = false
) {
    val actions: List<ClientCertAction>
        get() = if (summary == null) {
            listOf(ClientCertAction.IMPORT)
        } else {
            listOf(
                ClientCertAction.TOGGLE_ROMM,
                ClientCertAction.TOGGLE_JELLYFIN,
                ClientCertAction.IMPORT,
                ClientCertAction.REMOVE
            )
        }

    val isImporting: Boolean
        get() = passwordEntry?.status == ClientCertImportStatus.IMPORTING
}

class ClientCertDelegate @Inject constructor(
    private val store: ClientCertStore
) {
    private val _state = MutableStateFlow(ClientCertState())
    val state: StateFlow<ClientCertState> = _state.asStateFlow()

    private val _openFilePickerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openFilePickerEvent: SharedFlow<Unit> = _openFilePickerEvent.asSharedFlow()

    fun start(scope: CoroutineScope) {
        store.info.onEach { info ->
            _state.update { state ->
                val summary = info?.toSummary()
                val next = state.copy(summary = summary)
                next.copy(managerFocusIndex = state.managerFocusIndex.coerceIn(0, next.actions.lastIndex))
            }
        }.launchIn(scope)
    }

    fun openManager(target: ClientCertTarget) {
        _state.update { state ->
            val toggle = when (target) {
                ClientCertTarget.ROMM -> ClientCertAction.TOGGLE_ROMM
                ClientCertTarget.JELLYFIN -> ClientCertAction.TOGGLE_JELLYFIN
            }
            state.copy(
                managerOpenFor = target,
                managerFocusIndex = state.actions.indexOf(toggle).coerceAtLeast(0)
            )
        }
    }

    fun closeManager() {
        _state.update { it.copy(managerOpenFor = null, managerFocusIndex = 0) }
    }

    fun moveManagerFocus(delta: Int) {
        _state.update {
            it.copy(managerFocusIndex = (it.managerFocusIndex + delta).mod(it.actions.size))
        }
    }

    fun selectManagerAction(scope: CoroutineScope, index: Int? = null) {
        val state = _state.value
        val action = state.actions.getOrNull(index ?: state.managerFocusIndex) ?: return
        if (index != null) _state.update { it.copy(managerFocusIndex = index) }
        val summary = state.summary
        when (action) {
            ClientCertAction.TOGGLE_ROMM -> scope.launch {
                store.setTargetEnabled(ClientCertTarget.ROMM, summary?.forRomm != true)
            }
            ClientCertAction.TOGGLE_JELLYFIN -> scope.launch {
                store.setTargetEnabled(ClientCertTarget.JELLYFIN, summary?.forJellyfin != true)
            }
            ClientCertAction.IMPORT -> _openFilePickerEvent.tryEmit(Unit)
            ClientCertAction.REMOVE -> _state.update { it.copy(confirmingRemoval = true) }
        }
    }

    fun setFocusedToggle(scope: CoroutineScope, enabled: Boolean): Boolean {
        val state = _state.value
        val target = when (state.actions.getOrNull(state.managerFocusIndex)) {
            ClientCertAction.TOGGLE_ROMM -> ClientCertTarget.ROMM
            ClientCertAction.TOGGLE_JELLYFIN -> ClientCertTarget.JELLYFIN
            else -> return false
        }
        scope.launch { store.setTargetEnabled(target, enabled) }
        return true
    }

    fun confirmRemoval(scope: CoroutineScope) {
        _state.update { it.copy(confirmingRemoval = false, managerFocusIndex = 0) }
        scope.launch { store.remove() }
    }

    fun cancelRemoval() {
        _state.update { it.copy(confirmingRemoval = false) }
    }

    fun onFilePicked(path: String) {
        _state.update { it.copy(passwordEntry = ClientCertPasswordEntry(path = path)) }
    }

    fun setPasswordDraft(draft: String) {
        _state.update { state ->
            val entry = state.passwordEntry ?: return@update state
            if (entry.status == ClientCertImportStatus.IMPORTING) return@update state
            state.copy(passwordEntry = entry.copy(draft = draft, status = ClientCertImportStatus.IDLE))
        }
    }

    fun movePasswordRow(row: TextEntryRow) {
        _state.update { state ->
            val entry = state.passwordEntry ?: return@update state
            state.copy(passwordEntry = entry.copy(row = row))
        }
    }

    fun movePasswordButton(delta: Int) {
        _state.update { state ->
            val entry = state.passwordEntry ?: return@update state
            if (entry.row != TextEntryRow.BUTTONS) return@update state
            state.copy(passwordEntry = entry.copy(buttonIndex = (entry.buttonIndex + delta).mod(2)))
        }
    }

    fun confirmPasswordEntry(scope: CoroutineScope) {
        val entry = _state.value.passwordEntry ?: return
        when {
            entry.row == TextEntryRow.FIELD -> _state.update { state ->
                val current = state.passwordEntry ?: return@update state
                state.copy(passwordEntry = current.copy(keyboardRequest = current.keyboardRequest + 1))
            }
            entry.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> closePasswordEntry()
            else -> submitPassword(scope)
        }
    }

    fun submitPassword(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.passwordEntry ?: return
        if (entry.status == ClientCertImportStatus.IMPORTING) return
        val target = state.managerOpenFor ?: ClientCertTarget.ROMM
        _state.update {
            it.copy(passwordEntry = entry.copy(status = ClientCertImportStatus.IMPORTING))
        }
        scope.launch {
            val result = store.importFrom(entry.path, entry.draft.toCharArray(), target)
            _state.update { current ->
                val pending = current.passwordEntry ?: return@update current
                result.fold(
                    onSuccess = { current.copy(passwordEntry = null) },
                    onFailure = { error ->
                        current.copy(
                            passwordEntry = pending.copy(
                                status = error.toImportStatus(),
                                row = TextEntryRow.FIELD,
                                buttonIndex = TEXT_ENTRY_CONFIRM_BUTTON
                            )
                        )
                    }
                )
            }
        }
    }

    fun closePasswordEntry() {
        _state.update { state ->
            if (state.isImporting) state else state.copy(passwordEntry = null)
        }
    }
}
