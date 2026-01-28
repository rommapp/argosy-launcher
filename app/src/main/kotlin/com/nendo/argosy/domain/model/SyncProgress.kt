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
    }

    sealed class PostSession : SyncProgress() {
        abstract val channelName: String?

        data class CheckingSave(
            override val channelName: String?,
            val found: Boolean? = null
        ) : PostSession()

        data class Connecting(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PostSession()

        data class Uploading(
            override val channelName: String?,
            val success: Boolean? = null
        ) : PostSession()

        data object Complete : PostSession() {
            override val channelName: String? = null
        }
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

    sealed class BlockedReason : SyncProgress() {
        abstract val emulatorName: String?

        data class PermissionRequired(
            override val emulatorName: String? = null
        ) : BlockedReason()

        data class SavePathNotFound(
            override val emulatorName: String? = null,
            val checkedPath: String? = null
        ) : BlockedReason()

        data class AccessDenied(
            override val emulatorName: String? = null,
            val path: String? = null
        ) : BlockedReason()
    }

    val displayChannelName: String?
        get() = when (this) {
            is PreLaunch -> channelName
            is PostSession -> channelName
            else -> null
        }

    val statusMessage: String
        get() = when (this) {
            is Idle -> ""
            is PreLaunch.CheckingSave -> when (found) {
                null -> "Checking save file..."
                true -> "Save found"
                false -> "No save file"
            }
            is PreLaunch.Connecting -> when (success) {
                null -> "Connecting..."
                true -> "Connected"
                false -> "Connection failed"
            }
            is PreLaunch.Downloading -> when (success) {
                null -> "Downloading..."
                true -> "Download complete"
                false -> "Download failed"
            }
            is PreLaunch.Writing -> when (success) {
                null -> "Writing save..."
                true -> "Save written"
                false -> "Write failed"
            }
            is PreLaunch.Launching -> "Launching game..."
            is PostSession.CheckingSave -> when (found) {
                null -> "Checking save file..."
                true -> "Save found"
                false -> "Save not found"
            }
            is PostSession.Connecting -> when (success) {
                null -> "Connecting..."
                true -> "Connected"
                false -> "Connection failed"
            }
            is PostSession.Uploading -> when (success) {
                null -> "Uploading..."
                true -> "Upload complete"
                false -> "Upload queued"
            }
            is PostSession.Complete -> "Sync complete"
            is Error -> message
            is Skipped -> "Sync skipped"
            is HardcoreConflict -> "Hardcore save conflict"
            is BlockedReason.PermissionRequired -> "File access permission required"
            is BlockedReason.SavePathNotFound -> "Save folder not found"
            is BlockedReason.AccessDenied -> "Save folder access blocked"
        }

    val detailMessage: String?
        get() = when (this) {
            is BlockedReason.PermissionRequired ->
                "Grant \"All files access\" permission to sync saves for ${emulatorName ?: "this emulator"}."
            is BlockedReason.SavePathNotFound ->
                "The save folder for ${emulatorName ?: "this emulator"} was not found. " +
                    "The emulator may use a non-standard save location."
            is BlockedReason.AccessDenied ->
                "Your device is blocking access to ${emulatorName ?: "emulator"} save data. " +
                    "This may be due to manufacturer restrictions (Samsung, Xiaomi, etc.) or security policies."
            else -> null
        }
}
