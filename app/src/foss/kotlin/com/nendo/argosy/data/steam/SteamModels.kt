package com.nendo.argosy.data.steam

/**
 * FOSS-flavor shared Steam value types.
 *
 * In the `full` flavor these types are declared inside the JavaSteam-backed manager
 * implementations (see app/src/full/.../data/steam/). The `foss` flavor ships no Steam
 * integration, but shared code in src/main still references and pattern-matches on these
 * types, so identical definitions are provided here. Keep them in sync with the `full`
 * declarations. `KeyValue` (a JavaSteam type) is replaced with `Any?` since no shared code
 * reads that field.
 */

enum class SteamConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LOGGING_IN,
    LOGGED_IN,
    LOGGED_OUT
}

data class SteamServiceState(
    val connectionState: SteamConnectionState = SteamConnectionState.DISCONNECTED,
    val username: String? = null,
    val steamId: Long? = null,
    val error: String? = null
)

sealed class QrAuthState {
    data object Idle : QrAuthState()
    data object Starting : QrAuthState()
    data class WaitingForScan(val challengeUrl: String) : QrAuthState()
    data object Polling : QrAuthState()
    data class Success(val username: String, val steamId: Long) : QrAuthState()
    data class Error(val message: String) : QrAuthState()
}

sealed class SteamAuthEvent {
    data class LoggedIn(val steamId: Long, val username: String) : SteamAuthEvent()
    data object LoggedOut : SteamAuthEvent()
    data class LoginFailed(val reason: String) : SteamAuthEvent()
}

sealed class SteamDownloadState {
    data object Idle : SteamDownloadState()
    data class Preparing(val appId: Long, val gameName: String) : SteamDownloadState()
    data class Connecting(val appId: Long, val gameName: String) : SteamDownloadState()
    data class FetchingManifest(val appId: Long, val gameName: String, val depotId: Int) : SteamDownloadState()
    data class Validating(val appId: Long, val gameName: String, val statusDetail: String = "") : SteamDownloadState()
    data class Downloading(
        val appId: Long,
        val gameName: String,
        val progress: Float,
        val currentDepot: Int,
        val totalDepots: Int
    ) : SteamDownloadState()
    data class Moving(val appId: Long, val gameName: String) : SteamDownloadState()
    data class Completed(val appId: Long, val gameName: String, val installPath: String) : SteamDownloadState()
    data class Failed(val appId: Long, val gameName: String, val error: String) : SteamDownloadState()
    data class Paused(val appId: Long, val gameName: String, val progress: Float, val needsVerification: Boolean = false) : SteamDownloadState()
    data class Cleaning(val appId: Long, val gameName: String) : SteamDownloadState()
}

data class SteamDownloadProgress(
    val appId: Long,
    val gameName: String,
    val coverPath: String?,
    val progress: Float,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val state: SteamDownloadState,
    val bytesPerSecond: Long = 0L
) {
    val progressPercent: Int get() = (progress * 100).toInt()
}

data class QueuedSteamDownload(
    val appId: Long,
    val gameName: String,
    val coverPath: String?,
    val appInfo: Any? = null,
    val targetInstallPath: String? = null
)

sealed class LibrarySyncState {
    data object Idle : LibrarySyncState()
    data object SyncingLicenses : LibrarySyncState()
    data class FetchingPackages(val current: Int, val total: Int) : LibrarySyncState()
    data class FetchingApps(val current: Int, val total: Int) : LibrarySyncState()
    data class FetchingProtonDbRatings(val current: Int, val total: Int) : LibrarySyncState()
    data class Complete(val gamesAdded: Int, val gamesUpdated: Int) : LibrarySyncState()
    data class Error(val message: String) : LibrarySyncState()
}
