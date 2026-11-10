package com.nendo.argosy.data.mod

enum class SwitchModProfile(val key: String) {
    ORIGINAL("original"), HANDHELD_720("handheld720")
}

internal object UltraCamHandheldProfile {
    const val CONFIG_PATH = "sdmc/UltraCam/TOTK/Config/maxlastbreath.ini"
    private val replacements = mapOf(
        "AspectRatio" to mapOf("Width" to "16", "Height" to "9"),
        "Resolution" to mapOf("Width" to "1280", "Height" to "720", "ShadowResolution" to "1024", "QualityImprovements" to "False"),
        "Handheld" to mapOf("Width" to "1280", "Height" to "720", "OverrideHandheld_Resolution" to "True"),
        "UltraCamFPS" to mapOf("DynamicFPS" to "True", "MaxFPS" to "60")
    )

    fun apply(text: String): String {
        val seen = mutableMapOf<Pair<String, String>, Int>()
        var section = ""
        val lines = text.split('\n').map { line ->
            val content = line.removeSuffix("\r")
            val trimmed = content.trim()
            if (trimmed.startsWith('[') && trimmed.endsWith(']')) section = trimmed.drop(1).dropLast(1)
            val key = content.substringBefore('=', "").trim()
            val replacement = replacements[section]?.get(key)
            if (replacement == null || '=' !in content) line else {
                val identity = section to key
                seen[identity] = (seen[identity] ?: 0) + 1
                content.substringBefore('=') + "=" + replacement + if (line.endsWith('\r')) "\r" else ""
            }
        }
        if (replacements.any { (sectionName, keys) -> keys.any { (key, _) -> seen[sectionName to key] != 1 } }) {
            throw ModInstallException(ModInstallFailure.UNSUPPORTED_PROFILE, CONFIG_PATH)
        }
        return lines.joinToString("\n")
    }
}
