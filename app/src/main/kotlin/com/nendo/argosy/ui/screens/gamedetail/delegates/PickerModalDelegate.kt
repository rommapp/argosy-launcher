package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.allSelectableSelected
import com.nendo.argosy.data.model.selectableFileIds
import com.nendo.argosy.data.model.visibleWithCollapsed
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.common.BuiltinCoreSummary
import com.nendo.argosy.ui.common.BuiltinCoreSummaryLoader
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.ArtCandidate
import com.nendo.argosy.ui.screens.gamedetail.artGridStep
import com.nendo.argosy.ui.screens.gamedetail.pickerConfig
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
    val builtinCore: BuiltinCoreSummary? = null,

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
    val filePickerFocusIndex: Int = 0,
    val filePickerManageMode: Boolean = false,
    val filePickerCollapsed: Set<String> = emptySet(),

    val showArtPicker: Boolean = false,
    val artPickerSlot: ArtSlot = ArtSlot.COVER,
    val artPickerCanSearch: Boolean = false,
    val artCandidates: List<ArtCandidate> = emptyList(),
    val artPickerFocusIndex: Int = 0,
    val artPickerLoading: Boolean = false,
    val artPickerError: String? = null,
    /**
     * What the artwork search is looking for. Seeded from the title and editable by the user,
     * whose stored title is often not what the artwork is filed under.
     */
    val artPickerQuery: String = "",
    val artPickerSearchFocused: Boolean = false,
    val artPickerKeyboardOpen: Boolean = false,
    val showArtFileBrowser: Boolean = false
) {
    val hasAnyPickerOpen: Boolean
        get() = showEmulatorPicker || showCorePicker || showSteamLauncherPicker ||
                showDiscPicker || showVariantPicker || showFilePicker || showArtPicker

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
    private val soundManager: SoundFeedbackManager,
    private val builtinCoreSummaryLoader: BuiltinCoreSummaryLoader
) {
    private val _state = MutableStateFlow(PickerModalState())
    val state: StateFlow<PickerModalState> = _state.asStateFlow()

    private val _selection = MutableStateFlow<PickerSelection?>(null)
    val selection: StateFlow<PickerSelection?> = _selection.asStateFlow()

    var menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP

    fun clearSelection() {
        _selection.value = null
    }

    fun showArtPicker(slot: ArtSlot, query: String, canSearch: Boolean) {
        openArtSlot(slot, query, canSearch)
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun switchArtSlot(slot: ArtSlot, query: String, canSearch: Boolean) {
        openArtSlot(slot, query, canSearch)
        soundManager.play(SoundType.NAVIGATE)
    }

    fun closeArtFileBrowser() {
        _state.update { it.copy(showArtFileBrowser = false) }
    }

    private fun openArtSlot(slot: ArtSlot, query: String, canSearch: Boolean) {
        _state.update {
            it.copy(
                showArtPicker = true,
                artPickerSlot = slot,
                artPickerCanSearch = canSearch,
                artCandidates = emptyList(),
                artPickerFocusIndex = 0,
                artPickerLoading = false,
                artPickerError = null,
                artPickerQuery = query,
                artPickerSearchFocused = false,
                artPickerKeyboardOpen = false,
                showArtFileBrowser = false
            )
        }
    }

    fun openArtPickerKeyboard() {
        _state.update {
            if (!it.artPickerCanSearch) it
            else it.copy(artPickerSearchFocused = true, artPickerKeyboardOpen = true)
        }
    }

    fun closeArtPickerKeyboard() {
        _state.update { it.copy(artPickerKeyboardOpen = false) }
    }

    fun setArtPickerQuery(query: String) {
        _state.update { it.copy(artPickerQuery = query) }
    }

    fun openArtFileBrowser() {
        _state.update { it.copy(showArtFileBrowser = true) }
    }

    fun setArtPickerSearching() {
        _state.update {
            it.copy(artPickerLoading = true, artPickerError = null, artCandidates = emptyList())
        }
    }

    fun setArtCandidates(candidates: List<ArtCandidate>, error: String? = null) {
        _state.update {
            it.copy(
                artCandidates = candidates.distinctBy { candidate -> candidate.source },
                artPickerFocusIndex = 0,
                artPickerLoading = false,
                artPickerError = error
            )
        }
    }

    fun dismissArtPicker() {
        _state.update {
            it.copy(
                showArtPicker = false,
                showArtFileBrowser = false,
                artCandidates = emptyList(),
                artPickerError = null
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveArtPickerFocus(delta: Int) {
        _state.update { state ->
            val columns = state.artPickerSlot.pickerConfig.columns
            val movesUp = delta < 0 && kotlin.math.abs(delta) >= columns
            val movesDown = delta > 0 && delta >= columns
            when {
                state.artPickerSearchFocused ->
                    if (movesDown && state.artCandidates.isNotEmpty()) {
                        state.copy(artPickerSearchFocused = false, artPickerFocusIndex = 0)
                    } else {
                        state
                    }
                state.artPickerCanSearch && movesUp &&
                    (state.artCandidates.isEmpty() || state.artPickerFocusIndex < columns) ->
                    state.copy(artPickerSearchFocused = true)
                state.artCandidates.isEmpty() -> state
                else -> state.copy(
                    artPickerFocusIndex = artGridStep(
                        index = state.artPickerFocusIndex,
                        delta = delta,
                        size = state.artCandidates.size,
                        columns = columns
                    )
                )
            }
        }
    }

    // region Emulator Picker

    suspend fun showEmulatorPicker(
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        builtinEnabled: Boolean = true
    ) {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
        val available = emulatorDetector.getInstalledForPlatform(platformSlug).let { list ->
            if (builtinEnabled) list
            else list.filterNot { it.def.packageName == com.nendo.argosy.data.emulator.EmulatorRegistry.BUILTIN_PACKAGE }
        }
        val builtinCore = if (available.any { it.def.id == EmulatorRegistry.BUILTIN_ID }) {
            builtinCoreSummaryLoader.load(gameId = gameId, platformId = platformId, platformSlug = platformSlug)
        } else {
            null
        }
        _state.update {
            it.copy(
                showEmulatorPicker = true,
                availableEmulators = available,
                emulatorPickerFocusIndex = 0,
                builtinCore = builtinCore
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
        manageMode: Boolean = false
    ) {
        _state.update {
            it.copy(
                showFilePicker = true,
                filePickerRows = rows,
                filePickerSelected = preselectedFileIds,
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
            val maxIndex = st.visibleFilePickerRows.size + 2
            st.copy(filePickerFocusIndex = computeWrappedIndex(st.filePickerFocusIndex, delta, maxIndex, menuWrapMode))
        }
    }

    fun moveFilePickerButtonFocus(delta: Int): Boolean {
        val st = _state.value
        val buttonStart = st.visibleFilePickerRows.size
        if (st.filePickerFocusIndex < buttonStart) return false
        _state.update {
            it.copy(filePickerFocusIndex = (it.filePickerFocusIndex + delta).coerceIn(buttonStart, buttonStart + 2))
        }
        return true
    }

    fun toggleFilePickerSelectAll() {
        _state.update { st ->
            val selection = if (st.filePickerRows.allSelectableSelected(st.filePickerSelected)) {
                emptySet()
            } else {
                st.filePickerRows.selectableFileIds()
            }
            st.copy(filePickerSelected = selection)
        }
        soundManager.play(SoundType.TOGGLE)
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
            if (row.isHeader) {
                val fileIds = st.filePickerRows
                    .filter { !it.isHeader && it.groupKey == row.groupKey && !it.isLocked }
                    .mapNotNull { it.rommFileId }
                selected = if (fileIds.all { it in selected }) selected - fileIds.toSet() else selected + fileIds
            } else if (row.rommFileId != null && !row.isLocked) {
                selected = if (row.rommFileId in selected) {
                    selected - row.rommFileId
                } else {
                    selected + row.rommFileId
                }
            }
            st.copy(filePickerSelected = selected)
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
