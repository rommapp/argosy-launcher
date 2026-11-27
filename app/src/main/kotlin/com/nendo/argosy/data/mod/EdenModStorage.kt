package com.nendo.argosy.data.mod

import java.util.Locale

internal object EdenModStorage {
    private val sectionPattern = Regex("^\\[([^]]+)](?:\\s*[;#].*)?$")

    fun requireDefaults(config: String, root: String) {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var section = ""
        config.lineSequence().forEach { line ->
            val text = line.trim()
            val header = sectionPattern.matchEntire(text)
            when {
                header != null -> section = header.groupValues[1].lowercase(Locale.ROOT)
                text.startsWith(';') || text.startsWith('#') -> Unit
                '=' in text -> {
                    val key = text.substringBefore('=').trim().lowercase(Locale.ROOT)
                    val value = text.substringAfter('=').trim().replace("\"", "")
                    sections.getOrPut(section) { mutableMapOf() }[key] = value
                }
            }
        }
        val data = sections["data%20storage"] ?: sections["datastorage"] ?: sections["data storage"].orEmpty()
        val load = data["load_directory"].orEmpty()
        val dataSdmc = data["sdmc_directory"].orEmpty()
        val uiSdmc = sections["ui"]?.get("paths\\sdmc_directory").orEmpty()
        requireDefault(load, "$root/load")
        requireDefault(uiSdmc.ifEmpty { dataSdmc }, "$root/sdmc")
    }

    private fun requireDefault(value: String, expected: String) {
        if (value.isNotEmpty() && value.trimEnd('/') != expected.trimEnd('/')) {
            throw ModInstallException(ModInstallFailure.UNSUPPORTED_PATH)
        }
    }
}
