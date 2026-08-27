package com.nendo.argosy.data.preferences

/**
 * A launcher-wide display language. [tag] is the persisted, BCP 47 token (or the "system"
 * sentinel) written to DataStore and to [SessionStateStore]; it is never a display label and
 * never the enum's own name, so a future locale can be added without touching stored data.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    FRENCH("fr"),
    SPANISH("es"),
    GERMAN("de"),
    CHINESE_SIMPLIFIED("zh-Hans"),
    CHINESE_TRADITIONAL("zh-Hant"),
    RUSSIAN("ru"),
    HINDI("hi");

    companion object {
        fun fromString(value: String?): AppLanguage =
            entries.find { it.tag == value } ?: SYSTEM
    }
}
