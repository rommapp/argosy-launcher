package com.nendo.argosy.data.remote.romm

data class RomMCapabilities(
    val serverVersion: String,
    val isSupportedVersion: Boolean,
    val supportsSyncNegotiate: Boolean,
    val supportsPlaySessionIngest: Boolean,
    val supportsDeviceSyncMode: Boolean,
    val supportsLibretroThumbnails: Boolean,
    val trustsServerHash: Boolean,
    val supportsDeviceAuth: Boolean,
    val supportsScreenshotUpload: Boolean,
    val supportsMusicApi: Boolean,
    val supportsCoverSearch: Boolean = false,
    val supportsDeviceInstall: Boolean = false,
    val supportsMusicPlaylists: Boolean = false,
    val supportsMusicTrackRomFilter: Boolean = false,
    val supportsMusicGames: Boolean = false,
    val supportsSnapshots: Boolean = false,
    val supportsStreaming: Boolean = false,
) {
    companion object {
        /**
         * Argosy supports the latest three RomM minor releases. Below this the server
         * is not refused, but no version-specific behaviour is kept for it and response
         * shapes are not verified against it - see testbed/romm.
         */
        const val MIN_SUPPORTED_VERSION = "4.9.0"

        const val SYNC_ENGINE_MIN_VERSION = "4.9.0"
        const val DEVICE_SYNC_MIN_VERSION = "4.9.0"
        const val HASH_TRUST_MIN_VERSION = "4.9.0"
        const val DEVICE_AUTH_MIN_VERSION = "5.0.0"
        const val SCREENSHOT_UPLOAD_MIN_VERSION = "5.0.0"
        const val MUSIC_API_MIN_VERSION = "5.0.0"
        const val DEVICE_INSTALL_MIN_VERSION = "5.4.0"
        const val MUSIC_PLAYLISTS_MIN_VERSION = "5.1.0"
        const val MUSIC_TRACK_ROM_FILTER_MIN_VERSION = "5.1.0"
        const val MUSIC_GAMES_MIN_VERSION = "5.3.0"
        const val STREAMING_MIN_VERSION = "5.3.0"

        val NONE = RomMCapabilities(
            serverVersion = "",
            isSupportedVersion = false,
            supportsSyncNegotiate = false,
            supportsPlaySessionIngest = false,
            supportsDeviceSyncMode = false,
            supportsLibretroThumbnails = false,
            trustsServerHash = false,
            supportsDeviceAuth = false,
            supportsScreenshotUpload = false,
            supportsMusicApi = false,
        )

        fun from(
            version: String?,
            libretroEnabled: Boolean? = null,
            steamGridDbEnabled: Boolean? = null,
            snapshotsEnabled: Boolean = false
        ): RomMCapabilities {
            if (version.isNullOrBlank() || version == "unknown") return NONE
            val gate = comparableVersion(version)
            val syncEngine = compareVersions(gate, SYNC_ENGINE_MIN_VERSION) >= 0
            val deviceSync = compareVersions(gate, DEVICE_SYNC_MIN_VERSION) >= 0
            return RomMCapabilities(
                serverVersion = version,
                isSupportedVersion = compareVersions(gate, MIN_SUPPORTED_VERSION) >= 0,
                supportsSyncNegotiate = syncEngine,
                supportsPlaySessionIngest = syncEngine,
                supportsDeviceSyncMode = deviceSync,
                supportsLibretroThumbnails = libretroEnabled ?: syncEngine,
                trustsServerHash = compareVersions(gate, HASH_TRUST_MIN_VERSION) >= 0,
                supportsDeviceAuth = compareVersions(gate, DEVICE_AUTH_MIN_VERSION) >= 0,
                supportsScreenshotUpload = compareVersions(gate, SCREENSHOT_UPLOAD_MIN_VERSION) >= 0,
                supportsMusicApi = compareVersions(gate, MUSIC_API_MIN_VERSION) >= 0,
                supportsCoverSearch = steamGridDbEnabled == true,
                supportsDeviceInstall = compareVersions(gate, DEVICE_INSTALL_MIN_VERSION) >= 0,
                supportsMusicPlaylists = compareVersions(gate, MUSIC_PLAYLISTS_MIN_VERSION) >= 0,
                supportsMusicTrackRomFilter =
                    compareVersions(gate, MUSIC_TRACK_ROM_FILTER_MIN_VERSION) >= 0,
                supportsMusicGames = compareVersions(gate, MUSIC_GAMES_MIN_VERSION) >= 0,
                supportsSnapshots = snapshotsEnabled,
                supportsStreaming = compareVersions(gate, STREAMING_MIN_VERSION) >= 0,
            )
        }

        /**
         * The version to compare feature gates against. A RomM source checkout reports
         * [DEVELOPMENT_VERSION]; debug builds read it as newer than any release so a local dev
         * server exercises every gate, while release builds keep treating it as unversioned.
         */
        fun comparableVersion(version: String): String =
            if (com.nendo.argosy.BuildConfig.DEBUG && version == DEVELOPMENT_VERSION) {
                DEVELOPMENT_COMPARABLE_VERSION
            } else {
                version
            }

        private const val DEVELOPMENT_VERSION = "development"
        private const val DEVELOPMENT_COMPARABLE_VERSION = "9999.0.0"

        fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }
    }
}
