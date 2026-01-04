package com.nendo.argosy.ui.filebrowser

import java.io.File

enum class StorageVolumeType {
    INTERNAL,
    SD_CARD,
    USB,
    UNKNOWN
}

data class StorageVolume(
    val id: String,
    val displayName: String,
    val path: String,
    val type: StorageVolumeType,
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L
)

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
    FILE_SELECTION
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

fun File.toFileEntry(): FileEntry = FileEntry(
    name = name,
    path = absolutePath,
    isDirectory = isDirectory,
    size = if (isFile) length() else 0L,
    lastModified = lastModified()
)
