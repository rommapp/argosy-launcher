package com.nendo.argosy.domain.model

sealed class SyncProgress {
    data object Idle : SyncProgress()

    sealed class PreLaunch : SyncProgress() {
        abstract val channelName: String?

        data class CheckingSave(
            override val channelName: String?,
            val found: Boolean? = null
        ) : PreLaunch()

        data class Connecting(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PreLaunch()

        data class Downloading(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PreLaunch()

        data class Writing(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PreLaunch()

        data class Launching(
            override val channelName: String?
        ) : PreLaunch()

        data class TimedOut(
            override val channelName: String?
        ) : PreLaunch()
    }

    data class Error(val message: String) : SyncProgress()
    data object Skipped : SyncProgress()

    data class HardcoreConflict(
        val gameId: Long,
        val gameName: String,
        val tempFilePath: String,
        val emulatorId: String,
        val targetPath: String,
        val isFolderBased: Boolean,
        val channelName: String?
    ) : SyncProgress()

    data class LocalModified(
        val gameId: Long,
        val localSavePath: String,
        val channelName: String?,
        val serverSaveId: Long? = null,
        val restoreFailed: Boolean = false,
        val snapshotConflict: Boolean = false
    ) : SyncProgress()

    sealed class BlockedReason : SyncProgress() {
        abstract val emulatorName: String?

        data class PermissionRequired(
            override val emulatorName: String? = null
        ) : BlockedReason()

        data class AccessDenied(
            override val emulatorName: String? = null,
            val path: String? = null,
            val platformSlug: String? = null
        ) : BlockedReason()
    }

    val displayChannelName: String?
        get() = when (this) {
            is PreLaunch -> channelName
            else -> null
        }
}
