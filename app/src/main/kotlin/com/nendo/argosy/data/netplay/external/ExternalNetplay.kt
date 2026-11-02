package com.nendo.argosy.data.netplay.external

enum class ExternalNetplayRole(val token: String) {
    HOST("host"),
    CLIENT("client"),
    UNKNOWN("unknown");

    companion object {
        fun fromToken(token: String?): ExternalNetplayRole? = entries.firstOrNull { it.token == token }
    }
}

enum class ExternalNetplaySource(val token: String) {
    SOCKET("socket"),
    LOBBY("lobby");

    companion object {
        fun fromToken(token: String?): ExternalNetplaySource? = entries.firstOrNull { it.token == token }
    }
}

/**
 * A netplay session running inside an external emulator, as the playing device detected it.
 * [emulatorId] is an [com.nendo.argosy.data.emulator.EmulatorRegistry] id, never a display label.
 */
data class ExternalNetplay(
    val emulatorId: String,
    val role: ExternalNetplayRole,
    val source: ExternalNetplaySource
) {
    fun toWire(): Map<String, String> = mapOf(
        "emulator" to emulatorId,
        "role" to role.token,
        "source" to source.token
    )

    companion object {
        const val MAX_EMULATOR_ID_LENGTH = 32

        fun fromWire(emulator: String?, role: String?, source: String?): ExternalNetplay? {
            val id = emulator?.takeIf { it.isNotBlank() && it.length <= MAX_EMULATOR_ID_LENGTH } ?: return null
            return ExternalNetplay(
                emulatorId = id,
                role = ExternalNetplayRole.fromToken(role) ?: return null,
                source = ExternalNetplaySource.fromToken(source) ?: return null
            )
        }
    }
}
