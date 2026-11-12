package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.setup.SetupFolderProbe
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.FolderSpace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The game folder and cover storage choices, their live free space, and the write that confirms
 * them.
 */
class SetupFoldersDelegate @Inject constructor(
    private val probe: SetupFolderProbe,
    private val preferencesRepository: UserPreferencesRepository,
    private val setupStateRepository: SetupStateRepository
) {
    private val _state = MutableStateFlow(FolderChoices())
    val state: StateFlow<FolderChoices> = _state.asStateFlow()

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private lateinit var scope: CoroutineScope

    fun bind(scope: CoroutineScope) {
        this.scope = scope
    }

    fun enter() {
        scope.launch {
            if (!_state.value.loaded) {
                val prefs = preferencesRepository.userPreferences.first()
                val choices = withContext(ioDispatcher) {
                    folderChoicesFrom(
                        internalPath = probe.internalRomPath(),
                        sdCardPath = probe.sdCardRomPath(),
                        savedRomPath = prefs.romStoragePath,
                        savedCoverPath = prefs.imageCachePath
                    )
                }
                _state.update { if (it.loaded) it else choices }
            }
            refresh()
        }
    }

    fun refresh() {
        scope.launch {
            val current = _state.value
            if (!current.loaded) return@launch
            val (sdCard, space) = withContext(ioDispatcher) {
                val sd = probe.sdCardRomPath()
                val paths = listOfNotNull(current.internalPath, sd ?: current.sdCardPath, current.customPath)
                sd to paths.mapNotNull { path -> probe.spaceOf(path)?.let { path to it } }.toMap()
            }
            _state.update { it.withSdCard(sdCard).copy(space = it.space + space) }
        }
    }

    fun select(kind: RomFolderKind) {
        _state.update { if (it.isAvailable(kind)) it.copy(selectedRom = kind, unwritable = null) else it }
    }

    fun selectCover(kind: CoverStorageKind) {
        _state.update { it.copy(selectedCover = kind) }
    }

    fun setCustomPath(path: String) {
        _state.update { it.copy(customPath = path, selectedRom = RomFolderKind.CUSTOM, unwritable = null) }
        refresh()
    }

    fun setPublicCoverPath(path: String) {
        _state.update { it.copy(publicCoverPath = path, selectedCover = CoverStorageKind.PUBLIC) }
    }

    /**
     * Creates the chosen game folder when missing and records both choices with their
     * confirmation. False, with the row marked, when Argosy cannot write to the folder.
     */
    suspend fun confirm(): Boolean {
        val choices = _state.value
        val path = choices.selectedRomPath ?: return false
        val writable = withContext(ioDispatcher) { probe.prepareWritable(path) }
        if (!writable) {
            _state.update { it.copy(unwritable = choices.selectedRom) }
            return false
        }
        setupStateRepository.confirmRomFolder(path)
        preferencesRepository.setImageCachePath(choices.coverPath)
        _state.update { it.copy(unwritable = null, space = it.space + spaceAfterCreate(path)) }
        return true
    }

    private suspend fun spaceAfterCreate(path: String): Map<String, FolderSpace> =
        withContext(ioDispatcher) { probe.spaceOf(path)?.let { mapOf(path to it) }.orEmpty() }
}
