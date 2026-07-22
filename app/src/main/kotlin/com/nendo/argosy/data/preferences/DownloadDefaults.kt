package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.model.VariantCategory

/**
 * Per-category include defaults for cherry-pick downloads. `game` is always
 * included and not configurable; OTHER_KEY covers non-standard subfolders.
 */
object DownloadDefaults {
    const val OTHER_KEY = "other"

    val CONFIGURABLE_KEYS: List<String> =
        VariantCategory.entries
            .filter { it != VariantCategory.GAME && it != VariantCategory.UNKNOWN }
            .map { it.key } + OTHER_KEY

    val FACTORY: Map<String, Boolean> = mapOf(
        VariantCategory.UPDATE.key to true,
        VariantCategory.DLC.key to true,
        VariantCategory.PATCH.key to true,
        VariantCategory.TRANSLATION.key to true,
        VariantCategory.MOD.key to false,
        VariantCategory.HACK.key to true,
        VariantCategory.DEMO.key to false,
        VariantCategory.PROTOTYPE.key to false,
        VariantCategory.CHEAT.key to false,
        VariantCategory.MANUAL.key to false,
        VariantCategory.SOUNDTRACK.key to false,
        VariantCategory.SCREENSHOT.key to false,
        OTHER_KEY to false
    )

    fun serialize(map: Map<String, Boolean>): String =
        map.entries.joinToString(",") { "${it.key}:${if (it.value) 1 else 0}" }

    fun deserialize(raw: String?): Map<String, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            val key = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val value = parts.getOrNull(1)?.trim() ?: return@mapNotNull null
            key to (value == "1")
        }.toMap()
    }

    fun serializeOverrides(overrides: Map<String, Map<String, Boolean>>): String =
        overrides.entries
            .filter { it.value.isNotEmpty() }
            .joinToString(";") { "${it.key}=${serialize(it.value)}" }

    fun deserializeOverrides(raw: String?): Map<String, Map<String, Boolean>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            entry.substring(0, idx) to deserialize(entry.substring(idx + 1))
        }.toMap()
    }

    /** factory -> global -> platform override, later layers win per key. */
    fun resolve(
        global: Map<String, Boolean>,
        platformOverride: Map<String, Boolean>
    ): Map<String, Boolean> = FACTORY + global + platformOverride
}
