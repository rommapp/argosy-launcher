package com.nendo.argosy.data.model

enum class PrivilegedRoute(val productName: String) {
    MAGISK("Magisk"),
    SHIZUKU("Shizuku")
}

enum class PrivilegedReach {
    NONE,
    CHECKING,
    ROOT,
    APP_DATA,
    UNREACHABLE,
    REFUSED;

    val isReady: Boolean get() = this == ROOT || this == APP_DATA
}

data class PrivilegedAccessState(
    val detected: PrivilegedRoute? = null,
    val enabled: PrivilegedRoute? = null,
    val reach: PrivilegedReach = PrivilegedReach.NONE
) {
    val route: PrivilegedRoute? get() = enabled ?: detected
}
