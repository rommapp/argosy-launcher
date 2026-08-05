package com.nendo.argosy.libretro.coreoptions

data class CoreOptionDef(
    val key: String,
    val displayName: String,
    val values: List<String>,
    val defaultValue: String,
    val coreDefault: String = defaultValue,
    val description: String? = null,
    val valueLabels: Map<String, String> = emptyMap(),
    val legacyValues: Map<String, String> = emptyMap()
) {
    fun displayValueFor(value: String): String = valueLabels[value] ?: value

    /**
     * A stored override can hold a token this option no longer offers, because the core renamed it
     * or because Argosy once shipped a token the core never accepted. A renamed token still carries
     * the user's choice and is repaired to its current spelling; anything else falls back to the
     * default rather than being sent to a core that would reject it.
     */
    fun resolveStored(stored: String): String =
        if (stored in values) stored else legacyValues[stored] ?: defaultValue
}
