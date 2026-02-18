package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PickerModalState(
    val showEmulatorPicker: Boolean = false,
    val availableEmulators: List<InstalledEmulator> = emptyList(),
    val emulatorPickerFocusIndex: Int = 0,

    val showCorePicker: Boolean = false,
    val availableCores: List<RetroArchCore> = emptyList(),
    val corePickerFocusIndex: Int = 0,

    val showSteamLauncherPicker: Boolean = false,
    val availableSteamLaunchers: List<SteamLauncher> = emptyList(),
    val steamLauncherPickerFocusIndex: Int = 0,

    val showDiscPicker: Boolean = false,
    val discPickerOptions: List<DiscOption> = emptyList(),
    val discPickerFocusIndex: Int = 0,

    val showUpdatesPicker: Boolean = false,
    val updatesPickerFocusIndex: Int = 0
) {
    val hasAnyPickerOpen: Boolean
        get() = showEmulatorPicker || showCorePicker || showSteamLauncherPicker ||
                showDiscPicker || showUpdatesPicker
}

sealed class PickerSelection {
    data class Emulator(val emulator: InstalledEmulator?) : PickerSelection()
    data class Core(val coreId: String?) : PickerSelection()
    data class SteamLauncher(val launcher: com.nendo.argosy.data.launcher.SteamLauncher?) : PickerSelection()
    data class Disc(val discPath: String) : PickerSelection()
    data class UpdateFile(val file: UpdateFileUi) : PickerSelection()
    data class ApplyUpdate(val file: UpdateFileUi) : PickerSelection()
}

@Singleton
class PickerModalDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorDetector: EmulatorDetector,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(PickerModalState())
    val state: StateFlow<PickerModalState> = _state.asStateFlow()

    private val _selection = MutableStateFlow<PickerSelection?>(null)
    val selection: StateFlow<PickerSelection?> = _selection.asStateFlow()

    fun clearSelection() {
        _selection.value = null
    }

    // region Emulator Picker

    suspend fun showEmulatorPicker(platformSlug: String) {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
        val available = emulatorDetector.getInstalledForPlatform(platformSlug)
        _state.update {
            it.copy(
                showEmulatorPicker = true,
                availableEmulators = available,
                emulatorPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissEmulatorPicker() {
        _state.update { it.copy(showEmulatorPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = state.availableEmulators.size
            val newIndex = (state.emulatorPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(emulatorPickerFocusIndex = newIndex)
        }
    }

    fun confirmEmulatorSelection() {
        val state = _state.value
        val index = state.emulatorPickerFocusIndex
        val emulator = if (index == 0) null else state.availableEmulators.getOrNull(index - 1)
        selectEmulator(emulator)
    }

    fun selectEmulator(emulator: InstalledEmulator?) {
        _selection.value = PickerSelection.Emulator(emulator)
        _state.update { it.copy(showEmulatorPicker = false) }
    }

    // endregion

    // region Core Picker

    fun showCorePicker(platformSlug: String, selectedCoreId: String?) {
        val cores = EmulatorRegistry.getCoresForPlatform(platformSlug)
        if (cores.isEmpty()) return

        val initialIndex = selectedCoreId?.let { id ->
            val idx = cores.indexOfFirst { it.id == id }
            if (idx >= 0) idx + 1 else 1
        } ?: 0

        _state.update {
            it.copy(
                showCorePicker = true,
                availableCores = cores,
                corePickerFocusIndex = initialIndex
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissCorePicker() {
        _state.update { it.copy(showCorePicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveCorePickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = state.availableCores.size
            val newIndex = (state.corePickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(corePickerFocusIndex = newIndex)
        }
    }

    fun confirmCoreSelection() {
        val state = _state.value
        val index = state.corePickerFocusIndex
        val coreId = if (index == 0) null else state.availableCores.getOrNull(index - 1)?.id
        selectCore(coreId)
    }

    fun selectCore(coreId: String?) {
        _selection.value = PickerSelection.Core(coreId)
        _state.update { it.copy(showCorePicker = false) }
    }

    // endregion

    // region Steam Launcher Picker

    fun showSteamLauncherPicker() {
        val available = SteamLaunchers.getInstalled(context)
        _state.update {
            it.copy(
                showSteamLauncherPicker = true,
                availableSteamLaunchers = available,
                steamLauncherPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissSteamLauncherPicker() {
        _state.update { it.copy(showSteamLauncherPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveSteamLauncherPickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = state.availableSteamLaunchers.size
            val newIndex = (state.steamLauncherPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(steamLauncherPickerFocusIndex = newIndex)
        }
    }

    fun confirmSteamLauncherSelection() {
        val state = _state.value
        val index = state.steamLauncherPickerFocusIndex
        val launcher = if (index == 0) null else state.availableSteamLaunchers.getOrNull(index - 1)
        selectSteamLauncher(launcher)
    }

    fun selectSteamLauncher(launcher: SteamLauncher?) {
        _selection.value = PickerSelection.SteamLauncher(launcher)
        _state.update { it.copy(showSteamLauncherPicker = false) }
    }

    // endregion

    // region Disc Picker

    fun showDiscPicker(options: List<DiscOption>) {
        _state.update {
            it.copy(
                showDiscPicker = true,
                discPickerOptions = options,
                discPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissDiscPicker() {
        _state.update {
            it.copy(
                showDiscPicker = false,
                discPickerOptions = emptyList(),
                discPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveDiscPickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = (state.discPickerOptions.size - 1).coerceAtLeast(0)
            val newIndex = (state.discPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(discPickerFocusIndex = newIndex)
        }
    }

    fun confirmDiscSelection() {
        val state = _state.value
        val disc = state.discPickerOptions.getOrNull(state.discPickerFocusIndex) ?: return
        selectDisc(disc.filePath)
    }

    fun selectDisc(discPath: String) {
        _selection.value = PickerSelection.Disc(discPath)
        _state.update {
            it.copy(
                showDiscPicker = false,
                discPickerOptions = emptyList(),
                discPickerFocusIndex = 0
            )
        }
    }

    // endregion

    // region Updates Picker

    fun showUpdatesPicker() {
        _state.update {
            it.copy(
                showUpdatesPicker = true,
                updatesPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissUpdatesPicker() {
        _state.update { it.copy(showUpdatesPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveUpdatesPickerFocus(delta: Int, updateFiles: List<UpdateFileUi>, dlcFiles: List<UpdateFileUi>) {
        _state.update { state ->
            val allFiles = updateFiles + dlcFiles
            val maxIndex = (allFiles.size - 1).coerceAtLeast(0)
            val newIndex = (state.updatesPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(updatesPickerFocusIndex = newIndex)
        }
    }

    fun confirmUpdatesSelection(updateFiles: List<UpdateFileUi>, dlcFiles: List<UpdateFileUi>, isEdenGame: Boolean) {
        val state = _state.value
        val allFiles = updateFiles + dlcFiles
        val focusedFile = allFiles.getOrNull(state.updatesPickerFocusIndex) ?: return

        when {
            !focusedFile.isDownloaded && focusedFile.gameFileId != null ->
                _selection.value = PickerSelection.UpdateFile(focusedFile)
            focusedFile.isDownloaded && !focusedFile.isAppliedToEmulator && isEdenGame ->
                _selection.value = PickerSelection.ApplyUpdate(focusedFile)
        }
    }

    // endregion

    fun reset() {
        _state.value = PickerModalState()
        _selection.value = null
    }
}
