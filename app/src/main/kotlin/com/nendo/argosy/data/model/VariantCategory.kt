package com.nendo.argosy.data.model

enum class VariantCategory(
    val key: String,
    val displayLabel: String,
    val sortOrder: Int,
    val isLaunchTarget: Boolean,
    val isInstallable: Boolean = true
) {
    GAME("game", "Game", 0, true),
    PATCH("patch", "Versions / Patches", 1, true),
    TRANSLATION("translation", "Translations", 2, true),
    MOD("mod", "Mods", 3, false),
    HACK("hack", "Hacks", 4, false),
    DEMO("demo", "Demo", 5, true),
    PROTOTYPE("prototype", "Prototype", 6, true),
    UPDATE("update", "Updates", 10, false),
    DLC("dlc", "DLC", 11, false),
    MANUAL("manual", "Manuals", 20, false),
    WALKTHROUGH("walkthrough", "Walkthroughs", 21, false),
    CHEAT("cheat", "Cheats", 22, false, isInstallable = false),
    SOUNDTRACK("soundtrack", "Soundtracks", 23, false, isInstallable = false),
    SCREENSHOT("screenshot", "Screenshots", 24, false, isInstallable = false),
    UNKNOWN("unknown", "Other", 99, true);

    companion object {
        fun fromKey(key: String?): VariantCategory = entries.find { it.key == key } ?: UNKNOWN
        val TITLE_ID_PLATFORMS = setOf("switch", "switch2", "3ds", "vita", "psvita", "wiiu")
        val VARIANT_EXCLUDED_PLATFORMS = TITLE_ID_PLATFORMS + setOf("win", "windows", "dos", "steam")
        val CATEGORY_FOLDER_NAMES: Set<String> = entries.map { it.key }.toSet() + "extcontent"
    }
}
