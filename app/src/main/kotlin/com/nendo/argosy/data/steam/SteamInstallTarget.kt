package com.nendo.argosy.data.steam

sealed class SteamInstallTarget {
    object ExternalAuto : SteamInstallTarget()
    object Internal : SteamInstallTarget()
    data class CustomVolume(val path: String) : SteamInstallTarget()

    fun toPreferenceValue(): String? = when (this) {
        is ExternalAuto -> null
        is Internal -> INTERNAL_SENTINEL
        is CustomVolume -> path
    }

    companion object {
        const val INTERNAL_SENTINEL = "internal"

        fun fromPreferenceValue(raw: String?): SteamInstallTarget = when (raw) {
            null -> ExternalAuto
            INTERNAL_SENTINEL -> Internal
            else -> CustomVolume(raw)
        }
    }
}
