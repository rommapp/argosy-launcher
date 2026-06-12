package com.nendo.argosy.data.remote.romm

data class RomMCapabilities(
    val serverVersion: String,
    val supportsSyncNegotiate: Boolean,
    val supportsPlaySessionIngest: Boolean,
    val supportsDeviceSyncMode: Boolean,
    val supportsLibretroThumbnails: Boolean,
    val trustsServerHash: Boolean,
) {
    companion object {
        const val SYNC_ENGINE_MIN_VERSION = "4.9.0"
        const val DEVICE_SYNC_MIN_VERSION = "4.8.0"
        const val HASH_TRUST_MIN_VERSION = "4.9.0"

        val NONE = RomMCapabilities(
            serverVersion = "",
            supportsSyncNegotiate = false,
            supportsPlaySessionIngest = false,
            supportsDeviceSyncMode = false,
            supportsLibretroThumbnails = false,
            trustsServerHash = false,
        )

        fun from(version: String?, libretroEnabled: Boolean? = null): RomMCapabilities {
            if (version.isNullOrBlank() || version == "unknown") return NONE
            val syncEngine = compareVersions(version, SYNC_ENGINE_MIN_VERSION) >= 0
            val deviceSync = compareVersions(version, DEVICE_SYNC_MIN_VERSION) >= 0
            return RomMCapabilities(
                serverVersion = version,
                supportsSyncNegotiate = syncEngine,
                supportsPlaySessionIngest = syncEngine,
                supportsDeviceSyncMode = deviceSync,
                supportsLibretroThumbnails = libretroEnabled ?: syncEngine,
                trustsServerHash = compareVersions(version, HASH_TRUST_MIN_VERSION) >= 0,
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
