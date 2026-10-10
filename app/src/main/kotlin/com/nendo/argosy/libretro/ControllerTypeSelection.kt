package com.nendo.argosy.libretro

/**
 * Codec for the `emulator_configs.controllerTypes` column: `coreId:port:deviceId` entries, each
 * device id a value from that core's `RETRO_ENVIRONMENT_SET_CONTROLLER_INFO`. An untagged
 * `port:deviceId` entry belongs to the platform's only built-in core.
 */
object ControllerTypeSelection {

    private data class Entry(val coreId: String?, val port: Int, val deviceId: Int)

    private fun parse(encoded: String?): List<Entry> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(',').mapNotNull { raw ->
            val parts = raw.trim().split(':')
            val (coreId, portText, deviceText) = when (parts.size) {
                2 -> Triple(null, parts[0], parts[1])
                3 -> Triple(parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null, parts[1], parts[2])
                else -> return@mapNotNull null
            }
            val port = portText.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val deviceId = deviceText.trim().toIntOrNull() ?: return@mapNotNull null
            Entry(coreId, port, deviceId)
        }
    }

    fun decode(encoded: String?, coreId: String, legacyApplies: Boolean): Map<Int, Int> {
        val entries = parse(encoded)
        val legacy = if (legacyApplies) entries.filter { it.coreId == null } else emptyList()
        return (legacy + entries.filter { it.coreId == coreId }).associate { it.port to it.deviceId }
    }

    fun update(encoded: String?, coreId: String, legacyApplies: Boolean, selections: Map<Int, Int>): String? {
        val kept = parse(encoded).filter { entry ->
            if (entry.coreId == null) !legacyApplies else entry.coreId != coreId
        }
        val written = selections.map { (port, deviceId) -> Entry(coreId, port, deviceId) }
        val all = (kept + written).sortedWith(compareBy({ it.coreId.orEmpty() }, { it.port }))
        if (all.isEmpty()) return null
        return all.joinToString(",") { entry ->
            listOfNotNull(entry.coreId, entry.port, entry.deviceId).joinToString(":")
        }
    }

    fun legacyApplies(platformSlug: String): Boolean =
        LibretroCoreRegistry.getCoresForPlatform(platformSlug).size == 1
}
