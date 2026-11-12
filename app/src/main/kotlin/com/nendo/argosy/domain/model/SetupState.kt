package com.nendo.argosy.domain.model

enum class SetupItem {
    LIBRARY_SOURCE,
    ALL_FILES_ACCESS,
    USAGE_ACCESS,
    NOTIFICATIONS,
    ROM_FOLDER,
    SAVE_SYNC,
    DEFAULT_HOME
}

/**
 * The live and stored facts setup is judged against. Confirmation flags are true only when the
 * person accepted the value on a setup page; a value written anywhere else leaves them false.
 */
data class SetupFacts(
    val librarySignedIn: Boolean = false,
    val librarySourceConfirmed: Boolean = false,
    val allFilesAccess: Boolean = false,
    val usageAccess: Boolean = false,
    val usageAccessGrantable: Boolean = true,
    val notifications: Boolean = false,
    val romFolderPresent: Boolean = false,
    val romFolderConfirmed: Boolean = false,
    val saveSyncAnswered: Boolean = false,
    val dualScreenDevice: Boolean = false,
    val defaultHome: Boolean = false,
    val defaultHomeDeclined: Boolean = false,
    val flowFinished: Boolean = false,
    val pagesSeen: Set<String> = emptySet()
) {
    val anyConfirmed: Boolean
        get() = librarySourceConfirmed || romFolderConfirmed || saveSyncAnswered
}

sealed interface SetupState {
    data object Complete : SetupState
    data class Incomplete(val firstMissing: SetupItem) : SetupState
}

val SetupState.isComplete: Boolean
    get() = this is SetupState.Complete

/**
 * Built-in emulator downloads that setup started. They run past the end of setup; [running] holds
 * until every one has either downloaded or failed.
 */
data class CoreDownloadProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0
) {
    val running: Boolean get() = completed + failed < total
}

data class FolderSpace(
    val freeBytes: Long,
    val totalBytes: Long,
    val exists: Boolean
)
