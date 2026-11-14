package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.mod.DownloadedSwitchMod
import com.nendo.argosy.data.mod.ModInstallException
import com.nendo.argosy.data.mod.ModInstallFailure
import com.nendo.argosy.data.mod.SwitchModProfile
import com.nendo.argosy.data.mod.SwitchModRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModInstallAction { HANDHELD_PROFILE, ORIGINAL_PROFILE, ACKNOWLEDGE, INSTALL, REMOVE, RECOVER, BACK }

data class ModInstallUiState(
    val visible: Boolean = false,
    val gameId: Long = 0,
    val loading: Boolean = false,
    val working: Boolean = false,
    val emulatorName: String = "",
    val packages: List<DownloadedSwitchMod> = emptyList(),
    val selected: DownloadedSwitchMod? = null,
    val profile: SwitchModProfile = SwitchModProfile.ORIGINAL,
    val confirmedClosed: Boolean = false,
    val focusIndex: Int = 0,
    val error: ModInstallFailure? = null,
    val succeeded: Boolean = false,
    val skippedDesktopSettings: Boolean = false
) {
    val actions: List<ModInstallAction> get() = buildList {
        val item = selected
        if (item != null) {
            if (item.hasHandheldProfile && !item.needsRecovery) {
                add(ModInstallAction.HANDHELD_PROFILE)
                add(ModInstallAction.ORIGINAL_PROFILE)
            }
            add(ModInstallAction.ACKNOWLEDGE)
            if (item.archivePath != null && !item.needsRecovery) add(ModInstallAction.INSTALL)
            if (item.installed && !item.needsRecovery) add(ModInstallAction.REMOVE)
            if (item.needsRecovery) add(ModInstallAction.RECOVER)
        }
        add(ModInstallAction.BACK)
    }
    val rowCount: Int get() = if (selected == null) packages.size + 1 else actions.size
}

class ModInstallDelegate @Inject constructor(
    private val repository: SwitchModRepository,
    private val sounds: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(ModInstallUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun open(scope: CoroutineScope, gameId: Long) {
        if (_state.value.working) return
        loadJob?.cancel()
        _state.update { ModInstallUiState(visible = true, gameId = gameId, loading = true) }
        sounds.play(SoundType.OPEN_MODAL)
        loadJob = scope.launch {
            try {
                val library = repository.list(gameId)
                _state.update { it.copy(loading = false, packages = library.packages, emulatorName = library.emulatorName) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.update { it.copy(loading = false, error = reason(failure)) }
            }
        }
    }

    fun dismiss() {
        if (_state.value.working) return
        loadJob?.cancel()
        _state.update { it.copy(visible = false) }
        sounds.play(SoundType.CLOSE_MODAL)
    }

    fun reset() {
        if (_state.value.working) return
        loadJob?.cancel()
        _state.update { ModInstallUiState() }
    }

    fun back() {
        if (_state.value.working) return
        if (_state.value.selected == null) dismiss()
        else _state.update { it.copy(selected = null, focusIndex = 0, confirmedClosed = false, error = null) }
    }

    fun move(delta: Int) {
        _state.update { if (it.working || it.loading) it else it.copy(focusIndex = (it.focusIndex + delta).mod(it.rowCount)) }
    }

    fun activate(scope: CoroutineScope, index: Int = _state.value.focusIndex) {
        val state = _state.value
        if (state.working || state.loading) return
        if (index !in 0 until state.rowCount) return
        _state.update { it.copy(focusIndex = index) }
        if (state.selected == null) {
            val item = state.packages.getOrNull(index)
            if (item == null) dismiss() else _state.update {
                it.copy(selected = item, focusIndex = 0, confirmedClosed = false, error = null, succeeded = false,
                    profile = if (item.hasHandheldProfile) SwitchModProfile.HANDHELD_720 else SwitchModProfile.ORIGINAL)
            }
            return
        }
        when (state.actions.getOrNull(index)) {
            ModInstallAction.BACK -> back()
            ModInstallAction.ACKNOWLEDGE -> _state.update { it.copy(confirmedClosed = !it.confirmedClosed, error = null) }
            ModInstallAction.HANDHELD_PROFILE -> _state.update { it.copy(profile = SwitchModProfile.HANDHELD_720) }
            ModInstallAction.ORIGINAL_PROFILE -> _state.update { it.copy(profile = SwitchModProfile.ORIGINAL) }
            ModInstallAction.INSTALL, ModInstallAction.REMOVE, ModInstallAction.RECOVER -> {
                if (!state.confirmedClosed) {
                    _state.update { it.copy(error = ModInstallFailure.EMULATOR_CLOSE_REQUIRED) }
                    return
                }
                val action = state.actions[index]
                _state.update { it.copy(working = true, error = null, succeeded = false) }
                scope.launch {
                    try {
                        val result = when (action) {
                            ModInstallAction.INSTALL -> repository.install(state.gameId, state.selected.packageId, state.profile, true)
                            ModInstallAction.REMOVE -> { repository.remove(state.gameId, state.selected.titleId, state.selected.packageId, true); null }
                            ModInstallAction.RECOVER -> { repository.recover(state.gameId, state.selected.titleId, state.selected.packageId, true); null }
                            else -> null
                        }
                        val library = repository.list(state.gameId)
                        _state.update { it.copy(working = false, packages = library.packages,
                            selected = library.packages.firstOrNull { item -> item.packageId == state.selected.packageId && item.titleId == state.selected.titleId },
                            focusIndex = 0, confirmedClosed = false, succeeded = true,
                            skippedDesktopSettings = result?.skippedDesktopSettings == true) }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        val refreshed = runCatching { repository.list(state.gameId) }.getOrNull()
                        _state.update { it.copy(working = false, error = reason(failure),
                            packages = refreshed?.packages ?: it.packages,
                            selected = refreshed?.packages?.firstOrNull { item -> item.packageId == state.selected.packageId && item.titleId == state.selected.titleId } ?: it.selected,
                            focusIndex = 0, confirmedClosed = false) }
                    }
                }
            }
            null -> Unit
        }
    }

    fun inputHandler(scope: CoroutineScope): InputHandler = object : InputHandler {
        override fun onUp(): InputResult { move(-1); return InputResult.HANDLED }
        override fun onDown(): InputResult { move(1); return InputResult.HANDLED }
        override fun onConfirm(): InputResult { activate(scope); return InputResult.HANDLED }
        override fun onBack(): InputResult { back(); return InputResult.HANDLED }
        override fun onLeft(): InputResult = InputResult.HANDLED
        override fun onRight(): InputResult = InputResult.HANDLED
        override fun onMenu(): InputResult = InputResult.HANDLED
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onContextMenu(): InputResult = InputResult.HANDLED
        override fun onPrevSection(): InputResult = InputResult.HANDLED
        override fun onNextSection(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onLongSelect(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
        override fun onLeftStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickUp(): InputResult = InputResult.HANDLED
        override fun onRightStickDown(): InputResult = InputResult.HANDLED
        override fun onRightStickLeft(): InputResult = InputResult.HANDLED
        override fun onRightStickRight(): InputResult = InputResult.HANDLED
    }

    private fun reason(failure: Exception): ModInstallFailure = (failure as? ModInstallException)?.reason ?: ModInstallFailure.ACCESS_DENIED
}
