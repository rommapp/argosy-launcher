package com.nendo.argosy.ui.filebrowser

import androidx.annotation.StringRes
import com.nendo.argosy.core.media.AudioFileTypes
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

enum class FileBrowserAction {
    USE_FOLDER,
    NEW_FOLDER
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
            extensions = AudioFileTypes.EXTENSIONS
        )

        val CERTIFICATE = FileFilter(
            extensions = setOf("crt", "cer", "pem", "der")
        )

        val CLIENT_CERTIFICATE = FileFilter(
            extensions = setOf("p12", "pfx")
        )

        val IMAGE = FileFilter(
            extensions = setOf("png", "jpg", "jpeg", "webp")
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
    @StringRes val errorRes: Int? = null,
    val hasPermission: Boolean = true,
    val showCreateFolderDialog: Boolean = false,
    val newFolderName: String = "",
    @StringRes val createFolderErrorRes: Int? = null
) {
    val actions: List<FileBrowserAction>
        get() = when {
            currentPath.isEmpty() -> emptyList()
            mode == FileBrowserMode.FOLDER_SELECTION ->
                listOf(FileBrowserAction.USE_FOLDER, FileBrowserAction.NEW_FOLDER)
            mode == FileBrowserMode.FILE_OR_FOLDER_SELECTION -> listOf(FileBrowserAction.USE_FOLDER)
            else -> emptyList()
        }

    val firstEntryIndex: Int
        get() = if (entries.isEmpty()) 0 else actions.size

    val focusedAction: FileBrowserAction?
        get() = actions.getOrNull(fileFocusIndex)

    val focusedEntry: FileEntry?
        get() = entries.getOrNull(fileFocusIndex - actions.size)

    fun withFileFocusMoved(delta: Int): FileBrowserState {
        val rowCount = actions.size + entries.size
        if (rowCount == 0) return this
        return copy(fileFocusIndex = (fileFocusIndex + delta).mod(rowCount))
    }
}

