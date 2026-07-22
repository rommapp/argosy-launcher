package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.model.visibleWithCollapsed
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

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

    val showVariantPicker: Boolean = false,
    val variantPickerOptions: List<com.nendo.argosy.data.emulator.VariantOption> = emptyList(),
    val variantPickerFocusIndex: Int = 0,
    val variantPickerActiveFileId: Long? = null,

    val showFilePicker: Boolean = false,
    val filePickerRows: List<com.nendo.argosy.data.model.FilePickerRow> = emptyList(),
    val filePickerSelected: Set<Long> = emptySet(),
    val filePickerSelectedVersions: Set<Long> = emptySet(),
    val filePickerFocusIndex: Int = 0,
    val filePickerManageMode: Boolean = false,
    val filePickerCollapsed: Set<String> = emptySet(),

    val showCoverPicker: Boolean = false,
    val coverCandidates: List<com.nendo.argosy.ui.screens.gamedetail.CoverCandidate> = emptyList(),
    val coverPickerFocusIndex: Int = 0,
    val coverPickerLoading: Boolean = false,
    val coverPickerError: String? = null
) {
    val hasAnyPickerOpen: Boolean
        get() = showEmulatorPicker || showCorePicker || showSteamLauncherPicker ||
                showDiscPicker || showVariantPicker || showFilePicker || showCoverPicker

    val visibleFilePickerRows: List<com.nendo.argosy.data.model.FilePickerRow>
        get() = filePickerRows.visibleWithCollapsed(filePickerCollapsed)
}

sealed class PickerSelection {
    data class Emulator(val emulator: InstalledEmulator?) : PickerSelection()
    data class Core(val coreId: String?) : PickerSelection()
    data class SteamLauncher(val launcher: com.nendo.argosy.data.launcher.SteamLauncher?) : PickerSelection()
    data class Disc(val discPath: String) : PickerSelection()
    data class Variant(val variantFileId: Long?) : PickerSelection()
}

class PickerModalDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emulatorDetector: EmulatorDetector,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(PickerModalState())
    val state: StateFlow<PickerModalState> = _state.asStateFlow()

    private val _selection = MutableStateFlow<PickerSelection?>(null)
    val selection: StateFlow<PickerSelection?> = _selection.asStateFlow()

    var menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP

    fun clearSelection() {
        _selection.value = null
    }

    fun showCoverPicker() {
        _state.update {
            it.copy(
                showCoverPicker = true,
                coverCandidates = emptyList(),
                coverPickerFocusIndex = 0,
                coverPickerLoading = true,
                coverPickerError = null
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun setCoverCandidates(candidates: List<com.nendo.argosy.ui.screens.gamedetail.CoverCandidate>) {
        _state.update {
            it.copy(
                coverCandidates = candidates,
                coverPickerFocusIndex = 0,
                coverPickerLoading = false,
                coverPickerError = null
            )
        }
    }

    fun setCoverPickerError(message: String) {
        _state.update {
            it.copy(coverPickerLoading = false, coverPickerError = message)
        }
    }

    fun dismissCoverPicker() {
        _state.update {
            it.copy(showCoverPicker = false, coverCandidates = emptyList(), coverPickerError = null)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    /** Grid navigation: [delta] of +/-1 steps within a row, +/-columns moves between rows. */
    fun moveCoverPickerFocus(delta: Int) {
        _state.update { state ->
            if (state.coverCandidates.isEmpty()) return@update state
            val target = state.coverPickerFocusIndex + delta
            state.copy(coverPickerFocusIndex = target.coerceIn(0, state.coverCandidates.lastIndex))
        }
    }

    // region Emulator Picker

    suspend fun showEmulatorPicker(platformSlug: String, builtinEnabled: Boolean = true) {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
        val available = emulatorDetector.getInstalledForPlatform(platformSlug).let { list ->
            if (builtinEnabled) list
            else list.filterNot { it.def.packageName == com.nendo.argosy.data.emulator.EmulatorRegistry.BUILTIN_PACKAGE }
        }
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
            val newIndex = computeWrappedIndex(state.emulatorPickerFocusIndex, delta, maxIndex, menuWrapMode)
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

    fun showCorePicker(platformSlug: String, selectedCoreId: String?, isBuiltIn: Boolean) {
        val cores = EmulatorRegistry.getSelectableCores(platformSlug, isBuiltIn)
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
            val newIndex = computeWrappedIndex(state.corePickerFocusIndex, delta, maxIndex, menuWrapMode)
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
            val newIndex = computeWrappedIndex(state.steamLauncherPickerFocusIndex, delta, maxIndex, menuWrapMode)
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
            val newIndex = computeWrappedIndex(state.discPickerFocusIndex, delta, maxIndex, menuWrapMode)
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

    // region Variant Picker

    fun showVariantPicker(options: List<com.nendo.argosy.data.emulator.VariantOption>, activeFileId: Long? = null) {
        val sorted = options.sortedBy { com.nendo.argosy.data.model.VariantCategory.fromKey(it.category).sortOrder }
        _state.update {
            it.copy(
                showVariantPicker = true,
                variantPickerOptions = sorted,
                variantPickerFocusIndex = 0,
                variantPickerActiveFileId = activeFileId
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissVariantPicker() {
        _state.update {
            it.copy(
                showVariantPicker = false,
                variantPickerOptions = emptyList(),
                variantPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveVariantPickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = (state.variantPickerOptions.size - 1).coerceAtLeast(0)
            val newIndex = computeWrappedIndex(state.variantPickerFocusIndex, delta, maxIndex, menuWrapMode)
            state.copy(variantPickerFocusIndex = newIndex)
        }
    }

    fun confirmVariantSelection() {
        val state = _state.value
        val variant = state.variantPickerOptions.getOrNull(state.variantPickerFocusIndex) ?: return
        confirmVariant(variant)
    }

    fun confirmVariantSelection(fileId: Long?) {
        val variant = _state.value.variantPickerOptions.firstOrNull { it.fileId == fileId } ?: return
        confirmVariant(variant)
    }

    private fun confirmVariant(variant: com.nendo.argosy.data.emulator.VariantOption) {
        _selection.value = PickerSelection.Variant(variant.fileId)
        _state.update {
            it.copy(
                showVariantPicker = false,
                variantPickerOptions = emptyList(),
                variantPickerFocusIndex = 0
            )
        }
    }

    // endregion

    // region File Picker

    fun showFilePicker(
        rows: List<com.nendo.argosy.data.model.FilePickerRow>,
        preselectedFileIds: Set<Long>,
        preselectedVersionIds: Set<Long>,
        manageMode: Boolean = false
    ) {
        _state.update {
            it.copy(
                showFilePicker = true,
                filePickerRows = rows,
                filePickerSelected = preselectedFileIds,
                filePickerSelectedVersions = preselectedVersionIds,
                filePickerFocusIndex = 0,
                filePickerManageMode = manageMode,
                filePickerCollapsed = emptySet()
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissFilePicker() {
        _state.update { it.copy(showFilePicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveFilePickerFocus(delta: Int) {
        _state.update { st ->
            val maxIndex = st.visibleFilePickerRows.size + 1
            st.copy(filePickerFocusIndex = computeWrappedIndex(st.filePickerFocusIndex, delta, maxIndex, menuWrapMode))
        }
    }

    fun moveFilePickerButtonFocus(delta: Int): Boolean {
        val st = _state.value
        val buttonStart = st.visibleFilePickerRows.size
        if (st.filePickerFocusIndex < buttonStart) return false
        _state.update {
            it.copy(filePickerFocusIndex = (it.filePickerFocusIndex + delta).coerceIn(buttonStart, buttonStart + 1))
        }
        return true
    }

    fun jumpFilePickerGroup(direction: Int) {
        _state.update { st ->
            val headers = st.visibleFilePickerRows.withIndex().filter { it.value.isHeader }.map { it.index }
            if (headers.isEmpty()) return@update st
            val target = if (direction > 0) {
                headers.firstOrNull { it > st.filePickerFocusIndex }
            } else {
                headers.lastOrNull { it < st.filePickerFocusIndex }
            } ?: return@update st
            st.copy(filePickerFocusIndex = target)
        }
    }

    fun toggleFilePickerGroupCollapse(groupKey: String) {
        _state.update { st ->
            val oldVisible = st.visibleFilePickerRows
            val focusedRow = oldVisible.getOrNull(st.filePickerFocusIndex)
            val newCollapsed = if (groupKey in st.filePickerCollapsed) {
                st.filePickerCollapsed - groupKey
            } else {
                st.filePickerCollapsed + groupKey
            }
            val newVisible = st.filePickerRows.visibleWithCollapsed(newCollapsed)
            val newIndex = when {
                st.filePickerFocusIndex >= oldVisible.size ->
                    newVisible.size + (st.filePickerFocusIndex - oldVisible.size)
                focusedRow != null ->
                    newVisible.indexOf(focusedRow).takeIf { it >= 0 }
                        ?: newVisible.indexOfFirst { it.isHeader && it.groupKey == focusedRow.groupKey }.coerceAtLeast(0)
                else -> 0
            }
            st.copy(filePickerCollapsed = newCollapsed, filePickerFocusIndex = newIndex.coerceAtLeast(0))
        }
    }

    fun setFocusedFilePickerGroupCollapsed(collapse: Boolean): Boolean {
        val st = _state.value
        val row = st.visibleFilePickerRows.getOrNull(st.filePickerFocusIndex) ?: return false
        if (!row.isHeader) return false
        val isCollapsed = row.groupKey in st.filePickerCollapsed
        if (collapse == isCollapsed) return false
        toggleFilePickerGroupCollapse(row.groupKey)
        return true
    }

    fun toggleFilePickerRow(row: com.nendo.argosy.data.model.FilePickerRow) {
        _state.update { st ->
            var selected = st.filePickerSelected
            var versions = st.filePickerSelectedVersions
            if (row.isHeader) {
                val members = st.filePickerRows.filter { !it.isHeader && it.groupKey == row.groupKey && !it.isLocked }
                val fileIds = members.mapNotNull { it.rommFileId }
                val versionIds = members.mapNotNull { it.versionRommId }
                val allSelected = fileIds.all { it in selected } && versionIds.all { it in versions }
                if (allSelected) {
                    selected = selected - fileIds.toSet()
                    versions = versions - versionIds.toSet()
                    if (versionIds.isNotEmpty() && versions.isEmpty()) {
                        versions = setOf(versionIds.first())
                    }
                } else {
                    selected = selected + fileIds
                    versions = versions + versionIds
                }
            } else if (row.versionRommId != null) {
                versions = if (row.versionRommId in versions) {
                    (versions - row.versionRommId).ifEmpty { versions }
                } else {
                    versions + row.versionRommId
                }
            } else if (row.rommFileId != null && !row.isLocked) {
                selected = if (row.rommFileId in selected) {
                    selected - row.rommFileId
                } else {
                    selected + row.rommFileId
                }
            }
            st.copy(filePickerSelected = selected, filePickerSelectedVersions = versions)
        }
        soundManager.play(SoundType.TOGGLE)
    }

    fun toggleFocusedFilePickerRow() {
        val st = _state.value
        st.visibleFilePickerRows.getOrNull(st.filePickerFocusIndex)?.let { toggleFilePickerRow(it) }
    }

    // endregion

    fun reset() {
        _state.value = PickerModalState()
        _selection.value = null
    }
}
