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
         * The oldest release that answers every route this client speaks. 10.9.0 is where resume
         * became `/UserItems/Resume` and the played and favourite writes became `/UserPlayedItems`
         * and `/UserFavoriteItems`; a server older than that answers 404 on all three. `/Users/Me`
         * and `/UserViews?userId=` are present at 10.9.0 too, so nothing else moves the floor.
         */
        const val MIN_SUPPORTED_VERSION = "10.9.0"

        /**
         * Media segments arrived in 10.10.0 and trickplay in 10.9.0, both read from the release
         * sources rather than assumed from the floor. Each is gated on its own release because a
         * capability claimed a version too early produces a 404 in the middle of playback.
         */
        const val MEDIA_SEGMENTS_MIN_VERSION = "10.10.0"
        const val TRICKPLAY_MIN_VERSION = "10.9.0"

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
