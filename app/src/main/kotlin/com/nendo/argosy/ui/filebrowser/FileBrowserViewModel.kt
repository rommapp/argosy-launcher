package com.nendo.argosy.ui.filebrowser

import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.storage.StorageVolumeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val volumeDetector: StorageVolumeDetector
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private val _resultPath = MutableSharedFlow<String>()
    val resultPath: SharedFlow<String> = _resultPath.asSharedFlow()

    init {
        checkPermissionAndLoadVolumes()
    }

    private fun checkPermissionAndLoadVolumes() {
        viewModelScope.launch {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

            _state.update { it.copy(hasPermission = hasPermission) }

            if (hasPermission) {
                loadVolumes()
            }
        }
    }

    private fun loadVolumes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val volumes = withContext(Dispatchers.IO) {
                volumeDetector.detectStorageVolumes()
            }

            _state.update { state ->
                state.copy(
                    volumes = volumes,
                    isLoading = false
                )
            }

            if (volumes.isNotEmpty()) {
                navigate(volumes.first().path)
            }
        }
    }

    fun selectVolume(volume: StorageVolume) {
        val currentIndex = _state.value.volumes.indexOf(volume)
        if (currentIndex >= 0) {
            _state.update { it.copy(volumeFocusIndex = currentIndex) }
        }
        navigate(volume.path)
    }

    fun navigate(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val entries = withContext(Dispatchers.IO) {
                    loadDirectory(path)
                }
                _state.update { state ->
                    state.copy(
                        currentPath = path,
                        entries = entries,
                        fileFocusIndex = 0,
                        focusedPane = FocusedPane.FILES,
                        isLoading = false
                    )
                }
            } catch (e: SecurityException) {
                _state.update { state ->
                    state.copy(
                        error = "Cannot access directory",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(
                        error = e.message ?: "Unknown error",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadDirectory(path: String): List<FileEntry> {
        val dir = File(path)
        if (!dir.canRead()) throw SecurityException("Cannot read directory")

        val entries = mutableListOf<FileEntry>()
        val isVolumeRoot = _state.value.volumes.any { it.path == path }

        if (!isVolumeRoot && dir.parent != null) {
            entries.add(
                FileEntry(
                    name = "..",
                    path = dir.parent!!,
                    isDirectory = true,
                    isParentLink = true
                )
            )
        }

        val children = dir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?: emptyList()

        val sortedDirs = children
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .map { it.toFileEntry() }

        val sortedFiles = children
            .filter { it.isFile }
            .sortedBy { it.name.lowercase() }
            .map { it.toFileEntry() }

        entries.addAll(sortedDirs)
        entries.addAll(sortedFiles)

        return entries
    }

    fun goUp() {
        val currentPath = _state.value.currentPath
        val isVolumeRoot = _state.value.volumes.any { it.path == currentPath }

        if (!isVolumeRoot) {
            val parent = File(currentPath).parent
            if (parent != null) {
                navigate(parent)
            }
        }
    }

    fun isAtVolumeRoot(): Boolean {
        return _state.value.volumes.any { it.path == _state.value.currentPath }
    }

    fun moveFocus(delta: Int) {
        _state.update { state ->
            when (state.focusedPane) {
                FocusedPane.VOLUMES -> {
                    val maxIndex = (state.volumes.size - 1).coerceAtLeast(0)
                    val newIndex = (state.volumeFocusIndex + delta).coerceIn(0, maxIndex)
                    state.copy(volumeFocusIndex = newIndex)
                }
                FocusedPane.FILES -> {
                    val maxIndex = (state.entries.size - 1).coerceAtLeast(0)
                    val newIndex = (state.fileFocusIndex + delta).coerceIn(0, maxIndex)
                    state.copy(fileFocusIndex = newIndex)
                }
            }
        }
    }

    fun switchPane(direction: Int) {
        _state.update { state ->
            val newPane = when {
                direction < 0 && state.focusedPane == FocusedPane.FILES -> FocusedPane.VOLUMES
                direction > 0 && state.focusedPane == FocusedPane.VOLUMES -> FocusedPane.FILES
                else -> state.focusedPane
            }
            state.copy(focusedPane = newPane)
        }
    }

    fun confirmFocusedItem() {
        val state = _state.value
        when (state.focusedPane) {
            FocusedPane.VOLUMES -> {
                val volume = state.volumes.getOrNull(state.volumeFocusIndex)
                if (volume != null) {
                    selectVolume(volume)
                }
            }
            FocusedPane.FILES -> {
                val entry = state.entries.getOrNull(state.fileFocusIndex)
                if (entry != null && entry.isDirectory) {
                    navigate(entry.path)
                } else if (entry != null && state.mode == FileBrowserMode.FILE_SELECTION) {
                    selectPath(entry.path)
                }
            }
        }
    }

    fun selectCurrentDirectory() {
        val path = _state.value.currentPath
        if (path.isNotEmpty()) {
            selectPath(path)
        }
    }

    private fun selectPath(path: String) {
        viewModelScope.launch {
            _resultPath.emit(path)
        }
    }

    fun setMode(mode: FileBrowserMode) {
        _state.update { it.copy(mode = mode) }
    }
}
