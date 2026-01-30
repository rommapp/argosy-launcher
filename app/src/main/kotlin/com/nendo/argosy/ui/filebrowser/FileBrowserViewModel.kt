package com.nendo.argosy.ui.filebrowser

import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.storage.ManagedStorageAccessor
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

private const val TAG = "FileBrowserViewModel"

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val volumeDetector: StorageVolumeDetector,
    private val managedStorageAccessor: ManagedStorageAccessor
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
            } catch (@Suppress("SwallowedException") e: SecurityException) {
                _state.update { state ->
                    state.copy(
                        error = "Cannot access directory",
                        isLoading = false
                    )
                }
            } catch (@Suppress("SwallowedException") e: Exception) {
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
        val entries = mutableListOf<FileEntry>()
        val isVolumeRoot = _state.value.volumes.any { it.path == path }
        val fileFilter = _state.value.fileFilter

        // Check if this is an Android/data or Android/obb path that needs managed access
        val managedResult = tryManagedAccess(path)

        if (managedResult != null) {
            // Using managed storage access for Android/data paths
            if (!isVolumeRoot) {
                val parentPath = File(path).parent
                if (parentPath != null) {
                    entries.add(
                        FileEntry(
                            name = "..",
                            path = parentPath,
                            isDirectory = true,
                            isParentLink = true
                        )
                    )
                }
            }

            val sortedDirs = managedResult
                .filter { it.isDirectory && !it.displayName.startsWith(".") }
                .sortedBy { it.displayName.lowercase() }
                .map { it.toFileEntry(path) }

            val sortedFiles = managedResult
                .filter { !it.isDirectory && !it.displayName.startsWith(".") }
                .filter { fileFilter?.matches(it.displayName) ?: true }
                .sortedBy { it.displayName.lowercase() }
                .map { it.toFileEntry(path) }

            entries.addAll(sortedDirs)
            entries.addAll(sortedFiles)
            return entries
        }

        // Standard file access for non-restricted paths
        val dir = File(path)
        if (!dir.canRead()) throw SecurityException("Cannot read directory")

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
            .filter { fileFilter?.matches(it.name) ?: true }
            .sortedBy { it.name.lowercase() }
            .map { it.toFileEntry() }

        entries.addAll(sortedDirs)
        entries.addAll(sortedFiles)

        return entries
    }

    private fun tryManagedAccess(path: String): List<ManagedStorageAccessor.DocumentFile>? {
        // Extract volume and relative path from absolute path
        val primaryRoot = "/storage/emulated/0"
        val sdcardPattern = Regex("^/storage/([A-F0-9-]+)")

        val (volumeId, relativePath) = when {
            path.startsWith(primaryRoot) -> {
                val rel = path.removePrefix(primaryRoot).trimStart('/')
                "primary" to rel
            }
            path.matches(Regex("^/storage/[A-F0-9-]+.*")) -> {
                val match = sdcardPattern.find(path)
                if (match != null) {
                    val volId = match.groupValues[1]
                    val rel = path.removePrefix("/storage/$volId").trimStart('/')
                    volId to rel
                } else {
                    return null
                }
            }
            else -> return null
        }

        // Only use managed access for Android/data and Android/obb paths
        if (!relativePath.startsWith("Android/data") && !relativePath.startsWith("Android/obb")) {
            return null
        }

        Log.d(TAG, "Attempting managed access: volumeId=$volumeId, relativePath=$relativePath")
        return managedStorageAccessor.listFiles(volumeId, relativePath)
    }

    private fun ManagedStorageAccessor.DocumentFile.toFileEntry(parentPath: String): FileEntry {
        return FileEntry(
            name = displayName,
            path = "$parentPath/$displayName",
            isDirectory = isDirectory,
            isParentLink = false,
            size = size,
            lastModified = lastModified
        )
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

    fun setFileFilter(filter: FileFilter?) {
        _state.update { it.copy(fileFilter = filter) }
        if (_state.value.currentPath.isNotEmpty()) {
            navigate(_state.value.currentPath)
        }
    }

    fun showCreateFolderDialog() {
        if (_state.value.currentPath.isEmpty()) return
        _state.update {
            it.copy(
                showCreateFolderDialog = true,
                newFolderName = "",
                createFolderError = null
            )
        }
    }

    fun dismissCreateFolderDialog() {
        _state.update {
            it.copy(
                showCreateFolderDialog = false,
                newFolderName = "",
                createFolderError = null
            )
        }
    }

    fun setNewFolderName(name: String) {
        _state.update { it.copy(newFolderName = name, createFolderError = null) }
    }

    fun confirmCreateFolder() {
        val state = _state.value
        val folderName = state.newFolderName.trim()

        if (folderName.isEmpty()) {
            _state.update { it.copy(createFolderError = "Folder name cannot be empty") }
            return
        }

        if (folderName.contains("/") || folderName.contains("\\")) {
            _state.update { it.copy(createFolderError = "Invalid folder name") }
            return
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val newFolder = File(state.currentPath, folderName)
                    if (newFolder.exists()) {
                        _state.update { it.copy(createFolderError = "Folder already exists") }
                        return@withContext false
                    }
                    newFolder.mkdir()
                } catch (e: Exception) {
                    _state.update { it.copy(createFolderError = e.message ?: "Failed to create folder") }
                    false
                }
            }

            if (success) {
                dismissCreateFolderDialog()
                navigate(state.currentPath)
            }
        }
    }
}
