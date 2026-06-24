package com.nendo.argosy.data.model

enum class VariantCategory(
    val key: String,
    val displayLabel: String,
    val sortOrder: Int,
    val isLaunchTarget: Boolean
) {
    GAME("game", "Game", 0, true),
    PATCH("patch", "Versions / Patches", 1, true),
    TRANSLATION("translation", "Translations", 2, true),
    MOD("mod", "Mods", 3, true),
    HACK("hack", "Hacks", 4, true),
    DEMO("demo", "Demo", 5, true),
    PROTOTYPE("prototype", "Prototype", 6, true),
    UPDATE("update", "Updates", 10, false),
    DLC("dlc", "DLC", 11, false),
    UNKNOWN("unknown", "Other", 99, true);

    companion object {
        fun fromKey(key: String?): VariantCategory = entries.find { it.key == key } ?: UNKNOWN
        val VARIANT_EXCLUDED_PLATFORMS = setOf("switch", "3ds", "vita", "psvita", "wiiu")
        val CATEGORY_FOLDER_NAMES: Set<String> = entries.map { it.key }.toSet() + "extcontent"
    }
}
