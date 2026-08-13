package com.nendo.argosy.data.remote.jellyfin

data class JellyfinCapabilities(
    val serverVersion: String,
    val serverName: String,
    val isSupportedVersion: Boolean,
    val supportsQuickConnect: Boolean,
    val supportsMediaSegments: Boolean,
    val supportsTrickplay: Boolean
) {
    companion object {
        /**
         * The oldest release whose response shapes have been checked against this client. Below it
         * the server is not refused, but nothing here is verified for it: 10.9 moved item listing to
         * `/Items?userId=`, resume to `/UserItems/Resume` and the played/favourite writes to
         * `/UserPlayedItems` and `/UserFavoriteItems`, and a server predating that move answers 404
         * on every one of them.
         */
        const val MIN_SUPPORTED_VERSION = "10.11.0"

        /**
         * Media segments and trickplay are gated at the verified floor rather than at the release
         * that first shipped them. Which release that was has not been confirmed against upstream,
         * and a capability claimed a version too early produces a 404 in the middle of playback,
         * whereas one claimed a version too late costs only a feature that stays dark.
         */
        const val MEDIA_SEGMENTS_MIN_VERSION = MIN_SUPPORTED_VERSION
        const val TRICKPLAY_MIN_VERSION = MIN_SUPPORTED_VERSION

        val NONE = JellyfinCapabilities(
            serverVersion = "",
            serverName = "",
            isSupportedVersion = false,
            supportsQuickConnect = false,
            supportsMediaSegments = false,
            supportsTrickplay = false
        )

        /**
         * Quick Connect is a server setting rather than a version fact, so [quickConnectEnabled]
         * carries what `/QuickConnect/Enabled` answered. Null means the question was never asked or
         * could not be answered, and the capability reads false: offering the code path on a server
         * that has it switched off strands the user on a screen that can never resolve.
         */
        fun from(
            version: String?,
            serverName: String? = null,
            quickConnectEnabled: Boolean? = null
        ): JellyfinCapabilities {
            if (version.isNullOrBlank()) return NONE
            return JellyfinCapabilities(
                serverVersion = version,
                serverName = serverName.orEmpty(),
                isSupportedVersion = compareVersions(version, MIN_SUPPORTED_VERSION) >= 0,
                supportsQuickConnect = quickConnectEnabled == true,
                supportsMediaSegments = compareVersions(version, MEDIA_SEGMENTS_MIN_VERSION) >= 0,
                supportsTrickplay = compareVersions(version, TRICKPLAY_MIN_VERSION) >= 0
            )
        }

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
