package com.nendo.argosy.ui.filebrowser

import com.nendo.argosy.core.storage.StorageVolume

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isParentLink: Boolean = false
)

enum class FocusedPane {
    VOLUMES,
    FILES
}

enum class FileBrowserMode {
    FOLDER_SELECTION,
    FILE_SELECTION,
    FILE_OR_FOLDER_SELECTION
}

data class FileFilter(
    val extensions: Set<String> = emptySet(),
    val mimeTypes: Set<String> = emptySet()
) {
    fun matches(fileName: String): Boolean {
        if (extensions.isEmpty()) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return extensions.contains(ext)
    }

    companion object {
        val AUDIO = FileFilter(
            extensions = setOf("mp3", "ogg", "wav", "flac", "m4a", "aac", "opus", "wma")
        )
    }
}

data class FileBrowserState(
    val volumes: List<StorageVolume> = emptyList(),
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val focusedPane: FocusedPane = FocusedPane.FILES,
    val volumeFocusIndex: Int = 0,
    val fileFocusIndex: Int = 0,
    val mode: FileBrowserMode = FileBrowserMode.FOLDER_SELECTION,
    val fileFilter: FileFilter? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasPermission: Boolean = true,
    val showCreateFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val createFolderError: String? = null
)

