package com.nendo.argosy.libretro

/**
 * Codec for the `emulator_configs.controllerTypes` column: a per-port map of libretro device
 * ids, stored as `port:deviceId` pairs. Device ids are the core's own
 * `RETRO_ENVIRONMENT_SET_CONTROLLER_INFO` values and are never interpreted here.
 */
object ControllerTypeSelection {

    fun decode(encoded: String?): Map<Int, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            if (parts.size != 2) return@mapNotNull null
            val port = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val deviceId = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (port < 0) null else port to deviceId
        }.toMap()
    }

    fun encode(selections: Map<Int, Int>): String? {
        if (selections.isEmpty()) return null
        return selections.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }
    }
}
