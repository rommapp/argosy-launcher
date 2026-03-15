package com.nendo.argosy.libretro

enum class AutoRestoreMode(val key: String, val label: String) {
    RESTORE("restore", "Restore"),
    PROMPT("prompt", "Prompt"),
    OFF("off", "Off");

    companion object {
        fun fromKey(key: String): AutoRestoreMode =
            entries.find { it.key == key } ?: RESTORE
    }
}
