package com.nendo.argosy.domain.model

enum class PermissionTier { REQUIRED, RECOMMENDED, OTHER }

/**
 * Every permission Argosy asks for. [token] is the stored form and never changes. [syncRequired]
 * marks the permissions save sync cannot run without. Only [tracksChanges] permissions are
 * compared against the recorded state before a launch and on returning to the foreground.
 */
enum class ArgosyPermission(
    val token: String,
    val tier: PermissionTier,
    val syncRequired: Boolean = false
) {
    ALL_FILES_ACCESS("all_files_access", PermissionTier.REQUIRED, syncRequired = true),
    USAGE_ACCESS("usage_access", PermissionTier.REQUIRED, syncRequired = true),
    NOTIFICATIONS("notifications", PermissionTier.REQUIRED, syncRequired = true),
    DISPLAY_OVERLAY("display_overlay", PermissionTier.RECOMMENDED),
    ACCESSIBILITY("accessibility", PermissionTier.RECOMMENDED),
    BATTERY_UNRESTRICTED("battery_unrestricted", PermissionTier.RECOMMENDED),
    WRITE_SETTINGS("write_settings", PermissionTier.OTHER),
    SCREEN_CAPTURE("screen_capture", PermissionTier.OTHER),
    LIVE_WALLPAPER("live_wallpaper", PermissionTier.OTHER),
    PRIVILEGED_ACCESS("privileged_access", PermissionTier.OTHER);

    val tracksChanges: Boolean get() = tier != PermissionTier.OTHER

    companion object {
        fun fromToken(token: String): ArgosyPermission? = entries.firstOrNull { it.token == token }

        fun inTier(tier: PermissionTier): List<ArgosyPermission> = entries.filter { it.tier == tier }
    }
}

enum class PermissionAvailability {
    GRANTED,
    MISSING,
    CHECKING,
    UNAVAILABLE;

    companion object {
        fun of(granted: Boolean, grantable: Boolean): PermissionAvailability = when {
            granted -> GRANTED
            grantable -> MISSING
            else -> UNAVAILABLE
        }
    }
}

data class PermissionSnapshot(
    val availability: Map<ArgosyPermission, PermissionAvailability> = emptyMap(),
    val relevant: Set<ArgosyPermission> = emptySet()
) {
    fun of(permission: ArgosyPermission): PermissionAvailability =
        availability[permission] ?: PermissionAvailability.MISSING

    fun isGranted(permission: ArgosyPermission): Boolean = of(permission) == PermissionAvailability.GRANTED

    val missingForSaveSync: List<ArgosyPermission>
        get() = ArgosyPermission.entries.filter { it.syncRequired && of(it) == PermissionAvailability.MISSING }

    val grantedTracked: Set<ArgosyPermission>
        get() = ArgosyPermission.entries.filter { it.tracksChanges && isGranted(it) }.toSet()
}

enum class PermissionSheetKind { BEFORE_LAUNCH, SAVE_SYNC }

data class PermissionSheetRequest(
    val kind: PermissionSheetKind,
    val permissions: List<ArgosyPermission>
)
