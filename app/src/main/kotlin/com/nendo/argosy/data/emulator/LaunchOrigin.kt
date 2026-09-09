package com.nendo.argosy.data.emulator

/**
 * Who asked for the play session. An EXTERNAL session was handed to Argosy by another front-end
 * through the argosy://launch deep link, so once it settles the launcher steps out of the way and
 * reveals the caller. Navigation links and everything started inside Argosy stay INTERNAL.
 */
enum class LaunchOrigin {
    INTERNAL,
    EXTERNAL;

    companion object {
        const val EXTRA_LAUNCH_ORIGIN = "launch_origin"

        fun fromString(value: String?): LaunchOrigin =
            entries.firstOrNull { it.name == value } ?: INTERNAL
    }
}
